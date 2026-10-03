package com.martinrevert.latorrentola.network

import com.google.gson.annotations.SerializedName
import okhttp3.HttpUrl
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/** Retrofit endpoints for OpenSubtitles API v1. */
interface OpenSubtitlesService {
    /**
     * Logs in to OpenSubtitles using the account credentials supplied in [request].
     *
     * @param apiKey OpenSubtitles consumer key.
     * @param userAgent Identifying client name and version.
     * @param request Account login payload.
     * @return HTTP response containing the short-lived bearer token on success.
     */
    @Headers("Accept: application/json")
    @POST("login/")
    suspend fun login(
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String,
        @Body request: OpenSubtitlesLoginRequest
    ): Response<OpenSubtitlesLoginResponse>

    /**
     * Searches subtitles by title, IMDb identity, and optional episode coordinates.
     *
     * @param apiKey OpenSubtitles consumer key.
     * @param userAgent Identifying client name and version.
     * @param authorization Bearer token returned by [login].
     * @param query Optional title or release query.
     * @param imdbId IMDb identifier of a movie or standalone feature.
     * @param parentImdbId IMDb identifier of a series when searching for an episode.
     * @param seasonNumber Optional TV season number.
     * @param episodeNumber Optional TV episode number.
     * @param languages Optional comma-separated OpenSubtitles language codes.
     * @param type Optional OpenSubtitles media type filter.
     * @param orderBy Search result property used to sort results.
     * @param orderDirection Direction used to order search results.
     * @param page Page number to retrieve.
     * @return HTTP response containing typed subtitle results on success.
     */
    @Headers("Accept: application/json")
    @GET("subtitles/")
    suspend fun searchSubtitles(
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String,
        @Header("Authorization") authorization: String,
        @Query("query") query: String? = null,
        @Query("imdb_id") imdbId: String? = null,
        @Query("parent_imdb_id") parentImdbId: String? = null,
        @Query("season_number") seasonNumber: Int? = null,
        @Query("episode_number") episodeNumber: Int? = null,
        @Query("languages") languages: String? = null,
        @Query("type") type: String? = null,
        @Query("order_by") orderBy: String = "download_count",
        @Query("order_direction") orderDirection: String = "desc",
        @Query("page") page: Int = 1
    ): Response<OpenSubtitlesSearchResponse>

    /**
     * Requests a temporary subtitle download link for a search result file.
     *
     * @param apiKey OpenSubtitles consumer key.
     * @param userAgent Identifying client name and version.
     * @param authorization Bearer token returned by [login].
     * @param request File identifier to download without requesting a server-side format conversion.
     * @return HTTP response containing a short-lived HTTPS download link.
     */
    @Headers("Accept: application/json")
    @POST("download/")
    suspend fun requestSubtitleDownload(
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String,
        @Header("Authorization") authorization: String,
        @Body request: OpenSubtitlesDownloadRequest
    ): Response<OpenSubtitlesDownloadResponse>

    /**
     * Streams a subtitle from its validated temporary download URL.
     *
     * @param url HTTPS download URL returned by OpenSubtitles.
     * @return HTTP response whose body is the subtitle or its ZIP archive.
     */
    @Streaming
    @GET
    suspend fun downloadFile(@Url url: HttpUrl): Response<ResponseBody>
}

/**
 * JSON payload used to authenticate an OpenSubtitles account.
 *
 * @property username OpenSubtitles account username.
 * @property password OpenSubtitles account password.
 */
data class OpenSubtitlesLoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

/**
 * Successful OpenSubtitles account login response.
 *
 * @property token Short-lived account access token.
 */
data class OpenSubtitlesLoginResponse(
    @SerializedName("token") val token: String?
)

/**
 * JSON payload asking OpenSubtitles to prepare the selected subtitle file.
 *
 * @property fileId Identifier of the subtitle file selected from search results.
 */
data class OpenSubtitlesDownloadRequest(
    @SerializedName("file_id") val fileId: Long
)

/**
 * Response containing a temporary subtitle download link and account quota information.
 *
 * @property link HTTPS URL from which the selected subtitle file can be retrieved.
 * @property fileName Subtitle filename reported by the API.
 * @property requests Number of download requests used in the current quota period.
 * @property remaining Number of download requests remaining in the current quota period.
 * @property message API message describing the quota or download result.
 * @property resetTime Human-readable time until the quota resets.
 * @property resetTimeUtc UTC timestamp when the quota resets.
 */
data class OpenSubtitlesDownloadResponse(
    @SerializedName("link") val link: String?,
    @SerializedName("file_name") val fileName: String?,
    @SerializedName("requests") val requests: Int?,
    @SerializedName("remaining") val remaining: Int?,
    @SerializedName("message") val message: String?,
    @SerializedName("reset_time") val resetTime: String?,
    @SerializedName("reset_time_utc") val resetTimeUtc: String?
)

/**
 * Typed top-level response from an OpenSubtitles subtitle search.
 *
 * @property data Subtitle entries returned on the current page.
 */
data class OpenSubtitlesSearchResponse(
    @SerializedName("data") val data: List<OpenSubtitlesSearchEntry>?
)

/**
 * One subtitle search result and its attributes.
 *
 * @property id OpenSubtitles result identifier.
 * @property attributes Search result metadata.
 */
data class OpenSubtitlesSearchEntry(
    @SerializedName("id") val id: String?,
    @SerializedName("attributes") val attributes: OpenSubtitlesAttributes?
)

/**
 * Search result attributes used to present a matching subtitle.
 *
 * @property language OpenSubtitles language code for the subtitle.
 * @property release Release name supplied for the subtitle.
 * @property downloadCount Download count reported by OpenSubtitles.
 * @property files Files available for this subtitle result.
 * @property featureDetails Feature details when provided by the API.
 */
data class OpenSubtitlesAttributes(
    @SerializedName("language") val language: String?,
    @SerializedName("release") val release: String?,
    @SerializedName("download_count") val downloadCount: Long?,
    @SerializedName("files") val files: List<OpenSubtitlesFile>?,
    @SerializedName("feature_details") val featureDetails: OpenSubtitlesFeatureDetails?
)

/**
 * One downloadable file associated with a subtitle result.
 *
 * @property fileId File identifier accepted by the OpenSubtitles download endpoint.
 * @property fileName Original subtitle filename reported by OpenSubtitles.
 */
data class OpenSubtitlesFile(
    @SerializedName("file_id") val fileId: Long?,
    @SerializedName("file_name") val fileName: String?
)

/**
 * Media feature metadata supplied with a subtitle result.
 *
 * @property title Display title of the matched movie or television episode.
 */
data class OpenSubtitlesFeatureDetails(
    @SerializedName("title") val title: String?
)
