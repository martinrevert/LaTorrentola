package com.martinrevert.latorrentola.network

import android.content.Context
import android.util.Log
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.martinrevert.latorrentola.BuildConfig
import com.martinrevert.latorrentola.utils.OpenSubtitlesCredentialStore
import com.martinrevert.latorrentola.utils.OpenSubtitlesCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Subtitle search result that can be passed to the OpenSubtitles download endpoint.
 *
 * @property id OpenSubtitles result identifier.
 * @property fileId File identifier accepted by the OpenSubtitles download endpoint.
 * @property language ISO language code reported by OpenSubtitles.
 * @property release Release name reported by the subtitle uploader.
 * @property downloadCount Number of downloads reported by OpenSubtitles.
 * @property fileName Subtitle filename reported by OpenSubtitles.
 * @property featureTitle Feature title when supplied by the API.
 */
data class OpenSubtitleResult(
    val id: String,
    val fileId: Long,
    val language: String,
    val release: String,
    val downloadCount: Long,
    val fileName: String,
    val featureTitle: String?
)

/**
 * Private WebVTT subtitle asset prepared for local playback or later Cast use.
 *
 * @property file App-private WebVTT file available to playback clients.
 * @property language OpenSubtitles language code.
 * @property label User-visible subtitle label.
 * @property mimeType MIME type used by subtitle playback clients.
 */
data class DownloadedSubtitle(
    val file: File,
    val language: String,
    val label: String,
    val mimeType: String = "text/vtt"
)

/** Stable categories for failures returned by the OpenSubtitles integration. */
enum class OpenSubtitlesErrorCode {
    /** Required API key is missing from build configuration. */
    API_KEY_MISSING,
    /** The user has not configured account credentials. */
    CREDENTIALS_MISSING,
    /** Stored credentials could not be decrypted. */
    CREDENTIALS_UNAVAILABLE,
    /** OpenSubtitles rejected the account login. */
    LOGIN_FAILED,
    /** Authentication remained unauthorized after one token refresh. */
    AUTHENTICATION_EXPIRED,
    /** OpenSubtitles returned a non-success HTTP response. */
    HTTP_FAILURE,
    /** Network request failed before a valid response was received. */
    NETWORK_FAILURE,
    /** The API response did not contain the expected typed data. */
    INVALID_RESPONSE,
    /** The requested search inputs are incomplete or invalid. */
    INVALID_SEARCH,
    /** The download response or file could not be safely used. */
    INVALID_SUBTITLE,
    /** A private subtitle cache file could not be written. */
    FILE_STORAGE_FAILURE
}

/**
 * Safe, typed failure from the OpenSubtitles integration.
 *
 * Messages are deliberately independent of server response bodies and never contain account
 * credentials or bearer tokens.
 *
 * @property code Stable category for programmatic error handling.
 * @property httpStatus HTTP status code when the service returned an error response.
 * @param message Safe diagnostic suitable for display.
 * @param cause Optional underlying transport or parsing failure.
 */
class OpenSubtitlesException(
    val code: OpenSubtitlesErrorCode,
    message: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * Authenticates to OpenSubtitles, searches releases, and downloads private WebVTT assets.
 *
 * API diagnostics log only selected response fields; credentials, bearer tokens, and signed download
 * URL contents are never written to logs.
 *
 * @property context Application context used for private subtitle storage.
 * @property openSubtitlesService Typed Retrofit interface for OpenSubtitles API v1.
 * @property credentialStore Encrypted per-user OpenSubtitles account credentials.
 */
@Singleton
class OpenSubtitlesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openSubtitlesService: OpenSubtitlesService,
    private val credentialStore: OpenSubtitlesCredentialStore
) {
    /** Serializes login and token refresh operations. */
    private val authenticationLock = Mutex()

    /** Process-local account token; never persisted or logged. */
    private var session: AuthenticatedSession? = null

    /**
     * Loads the saved account session before the user opens the subtitle search menu.
     *
     * @throws OpenSubtitlesException When credentials are missing or the service rejects login.
     */
    suspend fun warmUp() = withContext(Dispatchers.IO) {
        val credentials = loadCredentials()
        authenticatedToken(credentials)
    }

    /**
     * Searches subtitles by title or release query.
     *
     * @param query Media title or release name.
     * @param language Optional preferred language; only English and Spanish are returned.
     * @param type Optional `movie` or `episode` type filter.
     * @return Matching subtitles ordered by download count.
     */
    suspend fun search(
        query: String,
        language: String? = null,
        type: String? = null
    ): List<OpenSubtitleResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            throw OpenSubtitlesException(
                OpenSubtitlesErrorCode.INVALID_SEARCH,
                "A media title is required to search subtitles"
            )
        }
        val credentials = loadCredentials()
        val response = authorizedRequest(credentials, "subtitle search") { authorization ->
            openSubtitlesService.searchSubtitles(
                apiKey = apiKey(),
                userAgent = userAgent(),
                authorization = authorization,
                query = query,
                languages = requestedLanguages(language),
                type = type?.takeIf(String::isNotBlank)
            )
        }
        val mappedResults = response.data.orEmpty()
            .mapNotNull(::mapSearchResult)
            .filter { it.language.isEnglishOrSpanish() }
        Log.i(TAG, "Subtitle search completed: returned=${response.data?.size ?: 0}, usable=${mappedResults.size}")
        mappedResults
    }

    /**
     * Searches subtitles using an IMDb feature or series identifier.
     *
     * Episode searches use the series identifier together with [seasonNumber] and [episodeNumber].
     * Movie searches use the same identifier as the feature IMDb ID.
     *
     * @param imdbId IMDb identifier, with or without its `tt` prefix.
     * @param seasonNumber TV season number; supply together with [episodeNumber].
     * @param episodeNumber TV episode number; supply together with [seasonNumber].
     * @param language Optional preferred language; only English and Spanish are returned.
     * @return Matching subtitles ordered by download count.
     */
    suspend fun searchByImdbId(
        imdbId: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        language: String? = null
    ): List<OpenSubtitleResult> = withContext(Dispatchers.IO) {
        val normalizedImdbId = imdbId.trim().removePrefix("tt")
        if (normalizedImdbId.isEmpty() || normalizedImdbId.any { it !in '0'..'9' }) {
            throw OpenSubtitlesException(
                OpenSubtitlesErrorCode.INVALID_SEARCH,
                "A valid IMDb identifier is required to search subtitles"
            )
        }
        if ((seasonNumber == null) != (episodeNumber == null) ||
            seasonNumber != null && seasonNumber < 0 ||
            episodeNumber != null && episodeNumber < 1
        ) {
            throw OpenSubtitlesException(
                OpenSubtitlesErrorCode.INVALID_SEARCH,
                "Both a valid season and episode number are required for an episode search"
            )
        }

        val isEpisodeSearch = seasonNumber != null
        val credentials = loadCredentials()
        val response = authorizedRequest(credentials, "IMDb subtitle search") { authorization ->
            openSubtitlesService.searchSubtitles(
                apiKey = apiKey(),
                userAgent = userAgent(),
                authorization = authorization,
                imdbId = normalizedImdbId.takeUnless { isEpisodeSearch },
                parentImdbId = normalizedImdbId.takeIf { isEpisodeSearch },
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                languages = requestedLanguages(language),
                type = if (isEpisodeSearch) "episode" else "movie"
            )
        }
        val mappedResults = response.data.orEmpty()
            .mapNotNull(::mapSearchResult)
            .filter { it.language.isEnglishOrSpanish() }
        Log.i(TAG, "IMDb subtitle search completed: returned=${response.data?.size ?: 0}, usable=${mappedResults.size}")
        mappedResults
    }

    /**
     * Downloads a selected result and saves its WebVTT asset alongside its owning torrent when known.
     *
     * @param subtitle Subtitle result selected by the user.
     * @param torrentInfoHash Info hash of the managed torrent that owns this playback, if available.
     * @return Private subtitle file, language, label, and MIME type for a playback client.
     */
    suspend fun download(
        subtitle: OpenSubtitleResult,
        torrentInfoHash: String? = null
    ): DownloadedSubtitle =
        withContext(Dispatchers.IO) {
            if (subtitle.fileId <= 0L) {
                throw OpenSubtitlesException(
                    OpenSubtitlesErrorCode.INVALID_SEARCH,
                    "The selected subtitle does not have a valid file identifier"
                )
            }
            val credentials = loadCredentials()
            val downloadInfo = authorizedRequest(credentials, "subtitle download request") { authorization ->
                openSubtitlesService.requestSubtitleDownload(
                    apiKey = apiKey(),
                    userAgent = userAgent(),
                    authorization = authorization,
                    request = OpenSubtitlesDownloadRequest(fileId = subtitle.fileId)
                )
            }
            logDownloadLinkResponse(downloadInfo)
            val downloadUrl = validateDownloadUrl(downloadInfo.link)
            val response = retryNetworkFailures("subtitle file download") {
                openSubtitlesService.downloadFile(downloadUrl)
            }
            if (!response.isSuccessful) {
                val errorMessage = response.errorBody()?.let(::readSafeApiErrorMessage)
                Log.w(
                    TAG,
                    "Subtitle file request failed: HTTP ${response.code()}, " +
                        "retryAfter=${response.headers()["Retry-After"]?.let(::sanitizeLogValue) ?: "missing"}, " +
                        "message=${errorMessage?.let(::sanitizeLogValue) ?: "unavailable"}"
                )
                throw OpenSubtitlesException(
                    OpenSubtitlesErrorCode.HTTP_FAILURE,
                    buildString {
                        append("OpenSubtitles subtitle download failed (HTTP ${response.code()})")
                        errorMessage?.let { append(": ").append(it) }
                    },
                    response.code()
                )
            }
            Log.i(TAG, "Subtitle file response received: HTTP ${response.code()}")
            val body = response.body()
                ?: throw OpenSubtitlesException(
                    OpenSubtitlesErrorCode.INVALID_SUBTITLE,
                    "OpenSubtitles returned an empty subtitle file"
                )
            val bytes = readSubtitleBody(body)
            val subtitleText = subtitleText(bytes)
            val webVtt = OpenSubtitleFormatConverter.toWebVtt(subtitleText)
            val file = saveSubtitle(subtitle.fileId, webVtt, torrentInfoHash)
            Log.i(TAG, "Subtitle file saved and converted: bytes=${file.length()}")
            DownloadedSubtitle(
                file = file,
                language = subtitle.language,
                label = subtitle.release.ifBlank { subtitle.fileName }
            )
        }

    /**
     * Requests typed API data, retrying transient failures until the service responds.
     *
     * @param credentials Decrypted account credentials associated with the token.
     * @param operation Safe operation description for error messages.
     * @param request Retrofit request accepting an authorization header.
     * @return Parsed response body after a successful HTTP response.
     */
    private suspend fun <T : Any> authorizedRequest(
        credentials: OpenSubtitlesCredentials,
        operation: String,
        request: suspend (String) -> Response<T>
    ): T {
        var token = authenticatedToken(credentials)
        var authenticationRefreshed = false
        while (true) {
            val response = retryNetworkFailures(operation) { request("Bearer $token") }
            if (response.code() == HTTP_UNAUTHORIZED && !authenticationRefreshed) {
                Log.w(TAG, "$operation received HTTP 401; refreshing account session")
                response.errorBody()?.close()
                invalidateToken(token)
                token = authenticatedToken(credentials)
                authenticationRefreshed = true
            } else {
                return responseBody(response, operation)
            }
        }
    }

    /**
     * Retries transport failures with a capped exponential backoff until an HTTP response arrives
     * or the caller cancels the operation. HTTP errors are returned to the caller for presentation.
     *
     * @param operation Safe operation description for error messages.
     * @param request Retrofit request to execute.
     * @return The first non-retryable or successful HTTP response.
     */
    private suspend fun <T : Any> retryNetworkFailures(
        operation: String,
        request: suspend () -> Response<T>
    ): Response<T> {
        var retryDelayMs = INITIAL_RETRY_DELAY_MS
        var attempt = 0
        while (true) {
            attempt++
            val startedAt = System.nanoTime()
            val response = try {
                executeRequest(operation, request)
            } catch (error: OpenSubtitlesException) {
                if (error.code != OpenSubtitlesErrorCode.NETWORK_FAILURE) {
                    throw error
                }
                Log.w(
                    TAG,
                    "$operation network attempt=$attempt failed; retryInMs=$retryDelayMs, " +
                        "cause=${error.cause?.javaClass?.simpleName ?: "unknown"}"
                )
                delay(retryDelayMs)
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                continue
            }
            val durationMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND
            Log.i(
                TAG,
                "$operation HTTP ${response.code()} attempt=$attempt durationMs=$durationMs"
            )
            return response
        }
    }

    /**
     * Returns a valid process-local account token or performs an account login.
     *
     * @param credentials Decrypted account credentials.
     * @return Access token held only in process memory.
     */
    private suspend fun authenticatedToken(credentials: OpenSubtitlesCredentials): String =
        authenticationLock.withLock {
            session?.takeIf { it.username == credentials.username }?.let { return@withLock it.token }
            val response = retryNetworkFailures("login") {
                openSubtitlesService.login(
                    apiKey = apiKey(),
                    userAgent = userAgent(),
                    request = OpenSubtitlesLoginRequest(
                        username = credentials.username,
                        password = credentials.password
                    )
                )
            }
            if (!response.isSuccessful) {
                val errorMessage = response.errorBody()?.let(::readSafeApiErrorMessage)
                Log.w(
                    TAG,
                    "OpenSubtitles login rejected: HTTP ${response.code()}, " +
                        "message=${errorMessage?.let(::sanitizeLogValue) ?: "unavailable"}"
                )
                val code = if (response.code() == HTTP_UNAUTHORIZED ||
                    response.code() == HTTP_FORBIDDEN
                ) {
                    OpenSubtitlesErrorCode.LOGIN_FAILED
                } else {
                    OpenSubtitlesErrorCode.HTTP_FAILURE
                }
                throw OpenSubtitlesException(
                    code,
                    if (code == OpenSubtitlesErrorCode.LOGIN_FAILED) {
                        "OpenSubtitles login failed; check the account credentials"
                    } else {
                        "OpenSubtitles login failed (HTTP ${response.code()})"
                    },
                    response.code()
                )
            }
            val token = response.body()?.token?.takeIf(String::isNotBlank)
                ?: throw OpenSubtitlesException(
                    OpenSubtitlesErrorCode.INVALID_RESPONSE,
                    "OpenSubtitles login did not return an access token"
                )
            session = AuthenticatedSession(credentials.username, token)
            Log.i(TAG, "OpenSubtitles account login succeeded")
            token
        }

    /**
     * Clears the cached token only when it matches the token rejected by the API.
     *
     * @param rejectedToken Token rejected by the most recent request.
     */
    private suspend fun invalidateToken(rejectedToken: String) {
        authenticationLock.withLock {
            if (session?.token == rejectedToken) session = null
        }
    }

    /**
     * Executes a Retrofit request and converts transport/parsing failures to safe typed errors.
     *
     * @param operation Safe operation description for error messages.
     * @param request Retrofit request to execute.
     * @return Typed HTTP response.
     */
    private suspend fun <T : Any> executeRequest(
        operation: String,
        request: suspend () -> Response<T>
    ): Response<T> {
        return try {
            request()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            Log.w(
                TAG,
                "$operation transport failure: ${error.javaClass.simpleName}"
            )
            throw OpenSubtitlesException(
                OpenSubtitlesErrorCode.NETWORK_FAILURE,
                "OpenSubtitles $operation could not reach the service",
                cause = error
            )
        } catch (error: Exception) {
            Log.e(
                TAG,
                "$operation request failed unexpectedly: ${error.javaClass.simpleName}, " +
                    "message=${error.message?.let(::sanitizeLogValue) ?: "unavailable"}"
            )
            throw OpenSubtitlesException(
                OpenSubtitlesErrorCode.INVALID_RESPONSE,
                "OpenSubtitles returned an invalid response during $operation",
                cause = error
            )
        }
    }

    /**
     * Checks an HTTP response and returns its typed body.
     *
     * @param response Response returned by Retrofit.
     * @param operation Safe operation description for error messages.
     * @return The non-null successful response body.
     */
    private fun <T : Any> responseBody(response: Response<T>, operation: String): T {
        if (!response.isSuccessful) {
            val errorMessage = response.errorBody()?.let(::readSafeApiErrorMessage)
            Log.w(
                TAG,
                "$operation failed: HTTP ${response.code()}, " +
                    "retryAfter=${response.headers()["Retry-After"]?.let(::sanitizeLogValue) ?: "missing"}, " +
                    "message=${errorMessage?.let(::sanitizeLogValue) ?: "unavailable"}"
            )
            val code = if (response.code() == HTTP_UNAUTHORIZED) {
                OpenSubtitlesErrorCode.AUTHENTICATION_EXPIRED
            } else {
                OpenSubtitlesErrorCode.HTTP_FAILURE
            }
            throw OpenSubtitlesException(
                code,
                buildString {
                    append("OpenSubtitles $operation failed (HTTP ${response.code()})")
                    errorMessage?.let { append(": ").append(it) }
                },
                response.code()
            )
        }
        Log.i(TAG, "$operation response body accepted: HTTP ${response.code()}")
        return response.body()
            ?: throw OpenSubtitlesException(
                OpenSubtitlesErrorCode.INVALID_RESPONSE,
                "OpenSubtitles returned an empty response during $operation"
            )
    }

    /**
     * Extracts a bounded server message from a JSON error response without exposing raw bodies.
     *
     * @param body HTTP error body, consumed and closed by this function.
     * @return Sanitized server message, or `null` when no JSON message is available.
     */
    private fun readSafeApiErrorMessage(body: okhttp3.ResponseBody): String? {
        val responseText = try {
            body.use { it.string() }
        } catch (_: IOException) {
            return null
        }
        val errorJson = try {
            JsonParser.parseString(responseText).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: JsonParseException) {
            null
        } ?: return null
        val message = errorJson.get("message")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?: return null
        return message
            .filterNot(Char::isISOControl)
            .take(MAX_API_ERROR_MESSAGE_LENGTH)
            .takeIf(String::isNotBlank)
    }

    /**
     * Logs useful download-link response fields while redacting the temporary signed URL.
     *
     * @param response Parsed response from the OpenSubtitles download endpoint.
     */
    private fun logDownloadLinkResponse(response: OpenSubtitlesDownloadResponse) {
        val link = response.link?.toHttpUrlOrNull()
        val safeMessage = response.message?.let(::sanitizeLogValue)
        val safeFileName = response.fileName?.let(::sanitizeLogValue)
        Log.i(
            TAG,
            "Download-link response: status=200, fileName=${safeFileName ?: "missing"}, " +
                "requests=${response.requests ?: "missing"}, " +
                "remaining=${response.remaining ?: "missing"}, " +
                "message=${safeMessage ?: "missing"}, " +
                "resetTime=${response.resetTime?.let(::sanitizeLogValue) ?: "missing"}, " +
                "resetTimeUtc=${response.resetTimeUtc?.let(::sanitizeLogValue) ?: "missing"}, " +
                "link=${describeUrl(link)}"
        )
    }

    /**
     * Sanitizes a server-provided value for logs, removing control characters and URLs.
     *
     * @param value Untrusted text returned by OpenSubtitles.
     * @return Bounded text with URL contents redacted.
     */
    private fun sanitizeLogValue(value: String): String =
        value
            .replace(URL_PATTERN, "[redacted-url]")
            .filterNot(Char::isISOControl)
            .take(MAX_LOG_VALUE_LENGTH)

    /**
     * Describes only non-secret URL properties; signed path and query values are omitted.
     *
     * @param url Parsed response URL, or `null` when the supplied value is malformed.
     * @return Redacted URL diagnostics for logcat.
     */
    private fun describeUrl(url: HttpUrl?): String {
        if (url == null) return "invalid-or-missing"
        return "scheme=${url.scheme},host=${url.host},port=${url.port}," +
            "pathSegments=${url.pathSegments.size},queryPresent=${url.query != null}"
    }

    /**
     * Loads configured API and account credentials without returning sensitive failure details.
     *
     * @return Decrypted account credentials for the current user.
     */
    private fun loadCredentials(): OpenSubtitlesCredentials {
        if (BuildConfig.OPEN_SUBTITLES_API_KEY.isBlank()) {
            throw OpenSubtitlesException(
                OpenSubtitlesErrorCode.API_KEY_MISSING,
                "OpenSubtitles is not configured. Add OPEN_SUBTITLES_API_KEY to local.properties and rebuild."
            )
        }
        return try {
            credentialStore.load()
                ?: throw OpenSubtitlesException(
                    OpenSubtitlesErrorCode.CREDENTIALS_MISSING,
                    "Enter your OpenSubtitles username and password in Settings"
                )
        } catch (error: OpenSubtitlesException) {
            throw error
        } catch (error: Exception) {
            throw OpenSubtitlesException(
                OpenSubtitlesErrorCode.CREDENTIALS_UNAVAILABLE,
                "Saved OpenSubtitles credentials are unavailable; enter them again in Settings",
                cause = error
            )
        }
    }

    /** Returns the consumer API key injected during the build. */
    private fun apiKey(): String = BuildConfig.OPEN_SUBTITLES_API_KEY

    /** Returns the identifying user agent required by OpenSubtitles. */
    private fun userAgent(): String = "LaTorrentola v${BuildConfig.VERSION_NAME}"

    /**
     * Converts one typed API result into the app-facing subtitle search model.
     *
     * @param entry Typed entry returned by OpenSubtitles.
     * @return Usable result with a file identifier, or `null` for incomplete API data.
     */
    private fun mapSearchResult(entry: OpenSubtitlesSearchEntry): OpenSubtitleResult? {
        val attributes = entry.attributes ?: return null
        val file = attributes.files?.firstOrNull() ?: return null
        val fileId = file.fileId?.takeIf { it > 0L } ?: return null
        val fileName = file.fileName.orEmpty()
        return OpenSubtitleResult(
            id = entry.id.orEmpty(),
            fileId = fileId,
            language = attributes.language.orEmpty(),
            release = attributes.release.orEmpty().ifBlank { fileName },
            downloadCount = attributes.downloadCount ?: 0L,
            fileName = fileName,
            featureTitle = attributes.featureDetails?.title?.takeIf(String::isNotBlank)
        )
    }

    /**
     * Builds the API language filter, constraining results to English and Spanish.
     *
     * @param preferredLanguage Optional requested language.
     * @return Comma-separated supported language codes for the API query.
     */
    private fun requestedLanguages(preferredLanguage: String?): String {
        val requestedCode = preferredLanguage
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.substringBefore('-')
            ?.substringBefore('_')
        return when (requestedCode) {
            "en", "eng" -> "en"
            "es", "spa" -> "es"
            else -> SUPPORTED_LANGUAGE_CODES
        }
    }

    /**
     * Checks whether an OpenSubtitles result uses English or Spanish.
     *
     * @return `true` for English and Spanish ISO language codes.
     */
    private fun String.isEnglishOrSpanish(): Boolean = when (
        lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')
    ) {
        "en", "eng", "es", "spa" -> true
        else -> false
    }

    /**
     * Validates an API-supplied temporary link before making an unauthenticated file request.
     *
     * @param link Temporary download URL returned by OpenSubtitles.
     * @return Trusted HTTPS URL hosted by OpenSubtitles.
     */
    private fun validateDownloadUrl(link: String?): HttpUrl {
        val url = link?.toHttpUrlOrNull()
        val host = url?.host.orEmpty()
        val trustedHost = host in TRUSTED_DOWNLOAD_HOSTS
        if (url == null || !url.isHttps || url.port != HTTPS_PORT ||
            url.username.isNotEmpty() || url.password.isNotEmpty() || !trustedHost
        ) {
            Log.w(
                TAG,
                "Rejected subtitle URL: parsed=${url != null}, scheme=${url?.scheme ?: "invalid"}, " +
                    "host=${host.ifBlank { "invalid" }}, port=${url?.port ?: "invalid"}, " +
                    "credentialsPresent=${url?.let { it.username.isNotEmpty() || it.password.isNotEmpty() } ?: false}"
            )
            throw OpenSubtitlesException(
                OpenSubtitlesErrorCode.INVALID_SUBTITLE,
                "OpenSubtitles returned an invalid or untrusted subtitle URL"
            )
        }
        return url
    }

    /**
     * Reads a subtitle download without allowing an unexpectedly large response into memory.
     *
     * @param input Stream containing the subtitle bytes.
     * @return Bounded download bytes.
     */
    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_SUBTITLE_BYTES) {
                throw OpenSubtitlesException(
                    OpenSubtitlesErrorCode.INVALID_SUBTITLE,
                    "The subtitle download exceeded the supported file size"
                )
            }
            output.write(buffer, 0, count)
        }
        if (total == 0) {
            throw OpenSubtitlesException(
                OpenSubtitlesErrorCode.INVALID_SUBTITLE,
                "OpenSubtitles returned an empty subtitle file"
            )
        }
        return output.toByteArray()
    }

    /**
     * Reads and closes the downloaded response body while translating transport read failures.
     *
     * @param body Response body containing the subtitle bytes.
     * @return Bounded subtitle download bytes.
     */
    private fun readSubtitleBody(body: okhttp3.ResponseBody): ByteArray {
        return try {
            body.use { readBounded(it.byteStream()) }
        } catch (error: OpenSubtitlesException) {
            throw error
        } catch (error: IOException) {
            throw OpenSubtitlesException(
                OpenSubtitlesErrorCode.NETWORK_FAILURE,
                "OpenSubtitles subtitle download could not be read",
                cause = error
            )
        }
    }

    /**
     * Extracts subtitle text from raw or ZIP-compressed OpenSubtitles responses.
     *
     * @param bytes Downloaded response bytes.
     * @return UTF-8 subtitle text.
     */
    private fun subtitleText(bytes: ByteArray): String {
        return try {
            val content = if (bytes.size >= ZIP_SIGNATURE_SIZE &&
                bytes[0] == ZIP_SIGNATURE_FIRST && bytes[1] == ZIP_SIGNATURE_SECOND
            ) {
                ZipInputStream(bytes.inputStream()).use { zip ->
                    var selectedContent: ByteArray? = null
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory &&
                            entry.name.substringAfterLast('.', "").lowercase() in
                            SUPPORTED_SUBTITLE_EXTENSIONS
                        ) {
                            selectedContent = readBounded(zip)
                            break
                        }
                        entry = zip.nextEntry
                    }
                    selectedContent ?: throw OpenSubtitlesException(
                        OpenSubtitlesErrorCode.INVALID_SUBTITLE,
                        "The downloaded archive contains no subtitle file"
                    )
                }
            } else {
                bytes
            }
            val text = String(content, Charsets.UTF_8)
                .removePrefix("\uFEFF")
                .replace("\u0000", "")
            if (text.isBlank()) {
                throw OpenSubtitlesException(
                    OpenSubtitlesErrorCode.INVALID_SUBTITLE,
                    "OpenSubtitles returned an empty subtitle file"
                )
            }
            text
        } catch (error: OpenSubtitlesException) {
            throw error
        } catch (error: IOException) {
            throw OpenSubtitlesException(
                OpenSubtitlesErrorCode.INVALID_SUBTITLE,
                "OpenSubtitles returned an unreadable subtitle file",
                cause = error
            )
        }
    }

    /**
     * Stores a finished WebVTT file atomically in private app storage.
     *
     * @param fileId OpenSubtitles file identifier used to name the cache entry.
     * @param webVtt Validated WebVTT content to save.
     * @param torrentInfoHash Optional owning torrent info hash.
     * @return App-private subtitle file, in the torrent directory when an owner is supplied.
     */
    private fun saveSubtitle(fileId: Long, webVtt: String, torrentInfoHash: String?): File {
        val normalizedHash = torrentInfoHash?.trim()?.lowercase(Locale.ROOT)
        if (normalizedHash != null && !TORRENT_INFO_HASH_PATTERN.matches(normalizedHash)) {
            throw OpenSubtitlesException(
                OpenSubtitlesErrorCode.INVALID_SUBTITLE,
                "The torrent identifier for subtitle storage is invalid"
            )
        }
        val ownerDirectory = normalizedHash?.let {
            File(context.filesDir, "$TORRENT_DIRECTORY/$it")
        }
        val directory = if (ownerDirectory != null) {
            File(ownerDirectory, SUBTITLE_DIRECTORY)
        } else {
            File(context.cacheDir, SUBTITLE_DIRECTORY)
        }
        var temporaryFile: File? = null
        try {
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Unable to create subtitle cache directory")
            }
            val canonicalDirectory = directory.canonicalFile
            val expectedParent = (ownerDirectory ?: context.cacheDir).canonicalFile
            if (ownerDirectory != null &&
                expectedParent.parentFile != File(context.filesDir, TORRENT_DIRECTORY).canonicalFile
            ) {
                throw IOException("Torrent subtitle owner is outside private torrent storage")
            }
            if (canonicalDirectory.parentFile != expectedParent) {
                throw IOException("Subtitle directory is outside its private owner directory")
            }
            val destination = File(canonicalDirectory, "$fileId.vtt").canonicalFile
            if (destination.parentFile != canonicalDirectory) {
                throw IOException("Subtitle path is outside the app cache")
            }
            val temporary = File.createTempFile(
                "$fileId-${UUID.randomUUID()}-",
                ".part",
                canonicalDirectory
            )
            temporaryFile = temporary
            FileOutputStream(temporary).use { output ->
                output.write(webVtt.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            java.nio.file.Files.move(
                temporary.toPath(),
                destination.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
            return destination
        } catch (error: Exception) {
            temporaryFile?.delete()
            throw OpenSubtitlesException(
                OpenSubtitlesErrorCode.FILE_STORAGE_FAILURE,
                "Unable to save the subtitle file in app storage",
                cause = error
            )
        }
    }

    /**
     * Holds the current account identity and its process-local token.
     *
     * @property username Account associated with [token].
     * @property token Short-lived bearer token retained only in memory.
     */
    private data class AuthenticatedSession(
        val username: String,
        val token: String
    )

    private companion object {
        /** OpenSubtitles API v1 base host. */
        const val API_HOST = "api.opensubtitles.com"
        /** Android log tag for redacted OpenSubtitles request diagnostics. */
        const val TAG = "OpenSubtitles"
        /** HTTPS default port. */
        const val HTTPS_PORT = 443
        /** Unauthorized response status. */
        const val HTTP_UNAUTHORIZED = 401
        /** Forbidden response status. */
        const val HTTP_FORBIDDEN = 403
        /** Maximum compressed or uncompressed subtitle data accepted. */
        const val MAX_SUBTITLE_BYTES = 16 * 1024 * 1024
        /** Maximum server-provided error detail shown alongside an HTTP status. */
        const val MAX_API_ERROR_MESSAGE_LENGTH = 200
        /** Maximum server-provided field length included in diagnostic logs. */
        const val MAX_LOG_VALUE_LENGTH = 200
        /** Initial delay before retrying transient request failures. */
        const val INITIAL_RETRY_DELAY_MS = 500L
        /** Maximum delay between retries while the service remains unavailable. */
        const val MAX_RETRY_DELAY_MS = 30_000L
        /** Nanoseconds per millisecond when reporting request durations. */
        const val NANOS_PER_MILLISECOND = 1_000_000L
        /** Private app cache subdirectory for downloaded subtitles. */
        const val SUBTITLE_DIRECTORY = "opensubtitles"
        /** App-private directory removed with its corresponding managed torrent. */
        const val TORRENT_DIRECTORY = "torrent_downloads"
        /** Exact OpenSubtitles hosts allowed to serve temporary subtitle files. */
        val TRUSTED_DOWNLOAD_HOSTS = setOf(API_HOST, "www.opensubtitles.com", "opensubtitles.com")
        /** URL pattern used to redact signed URL contents from server-provided log messages. */
        val URL_PATTERN = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
        /** OpenSubtitles language filter covering both languages supported in the result list. */
        const val SUPPORTED_LANGUAGE_CODES = "en,es"
        /** Expected hexadecimal SHA-1 info-hash format used to scope subtitle files. */
        val TORRENT_INFO_HASH_PATTERN = Regex("[a-f0-9]{40}")
        /** Minimum ZIP signature length inspected in a response. */
        const val ZIP_SIGNATURE_SIZE = 2
        /** First byte of a ZIP local-file signature. */
        const val ZIP_SIGNATURE_FIRST: Byte = 0x50
        /** Second byte of a ZIP local-file signature. */
        const val ZIP_SIGNATURE_SECOND: Byte = 0x4b
        /** Supported file extensions extracted from OpenSubtitles archives. */
        val SUPPORTED_SUBTITLE_EXTENSIONS = setOf("srt", "vtt")
    }
}

/**
 * Compatibility type name retained for callers already wired to the subtitle feature.
 *
 * @see OpenSubtitlesRepository
 */
typealias OpenSubtitleRepository = OpenSubtitlesRepository

/** Converts SubRip subtitle text to the WebVTT format accepted by playback clients. */
object OpenSubtitleFormatConverter {
    /**
     * Normalizes line endings, rewrites SubRip timestamps, and adds the WebVTT header.
     *
     * Existing WebVTT documents are normalized but otherwise left intact.
     *
     * @param subtitle Raw SRT or WebVTT subtitle text.
     * @return UTF-8-ready WebVTT document terminated by a newline.
     */
    fun toWebVtt(subtitle: String): String {
        val normalized = subtitle
            .removePrefix("\uFEFF")
            .replace("\u0000", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(SRT_TIMECODE_COMMA, ".")
            .trim()
        if (normalized.isEmpty()) {
            throw OpenSubtitlesException(
                OpenSubtitlesErrorCode.INVALID_SUBTITLE,
                "The subtitle file contains no subtitle text"
            )
        }
        return if (normalized.startsWith("WEBVTT")) {
            "$normalized\n"
        } else {
            "WEBVTT\n\n$normalized\n"
        }
    }

    /** Pattern matching SubRip millisecond separators in timestamps. */
    private val SRT_TIMECODE_COMMA = Regex("""(?<=\d{2}:\d{2}:\d{2}),(?=\d{3})""")
}
