package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.constants.Constants
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that automatically falls back to an alternative YTS base URL
 * ([Constants.YTS_BASE_URL_ALTERNATIVE]) when the primary YTS endpoint ([Constants.YTS_BASE_URL])
 * fails due to connectivity issues, network timeouts, HTTP server errors (5xx/4xx),
 * or non-JSON (HTML challenge) responses.
 *
 * If both primary and alternative endpoints fail, the error propagates so that callers
 * (ViewModels) transition to an error state and render the adaptive ApiErrorView.
 */
@Singleton
class YtsFallbackInterceptor(
    private val primaryBaseUrl: String = Constants.YTS_BASE_URL,
    private val fallbackBaseUrl: String = Constants.YTS_BASE_URL_ALTERNATIVE,
    private val primaryCooldownMillis: Long = 60_000L
) : Interceptor {

    @Inject
    constructor() : this(
        primaryBaseUrl = Constants.YTS_BASE_URL,
        fallbackBaseUrl = Constants.YTS_BASE_URL_ALTERNATIVE,
        primaryCooldownMillis = 60_000L
    )

    private val primaryHttpUrl: HttpUrl? = primaryBaseUrl.toHttpUrlOrNull()
    private val fallbackHttpUrl: HttpUrl? = fallbackBaseUrl.toHttpUrlOrNull()

    @Volatile
    private var isPrimaryHealthy: Boolean = true

    @Volatile
    private var lastPrimaryFailureTimestamp: Long = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        val primaryHost = primaryHttpUrl?.host
        val fallbackHost = fallbackHttpUrl?.host

        // Only intercept requests destined for YTS hosts
        if (primaryHost == null || (originalUrl.host != primaryHost && originalUrl.host != fallbackHost)) {
            return chain.proceed(originalRequest)
        }

        val now = System.currentTimeMillis()
        val shouldTryPrimary = isPrimaryHealthy || (now - lastPrimaryFailureTimestamp > primaryCooldownMillis)

        if (!shouldTryPrimary && fallbackHttpUrl != null) {
            // Primary is known to be down and within cooldown; try fallback directly to avoid timeout delays
            val fallbackRequest = buildFallbackRequest(originalRequest, fallbackHttpUrl)
            try {
                val fallbackResponse = chain.proceed(fallbackRequest)
                if (fallbackResponse.isSuccessful && !isHtmlResponse(fallbackResponse)) {
                    return fallbackResponse
                }
                // Fallback also failed or returned error; reset health so next call attempts primary again
                isPrimaryHealthy = true
                lastPrimaryFailureTimestamp = 0L
                return fallbackResponse
            } catch (fallbackException: IOException) {
                isPrimaryHealthy = true
                lastPrimaryFailureTimestamp = 0L
                throw fallbackException
            }
        }

        // Try primary URL first
        val primaryRequest = if (originalUrl.host == primaryHost) {
            originalRequest
        } else {
            // If request was originally pointed to fallback, re-target to primary
            buildFallbackRequest(originalRequest, primaryHttpUrl)
        }

        var primaryException: IOException? = null
        var primaryResponse: Response? = null

        try {
            primaryResponse = chain.proceed(primaryRequest)
        } catch (e: IOException) {
            primaryException = e
        }

        if (primaryResponse != null && primaryResponse.isSuccessful && !isHtmlResponse(primaryResponse)) {
            isPrimaryHealthy = true
            return primaryResponse
        }

        // Primary failed (network error, HTTP status code not 2xx, or HTML error page)
        isPrimaryHealthy = false
        lastPrimaryFailureTimestamp = System.currentTimeMillis()
        primaryResponse?.close()

        if (fallbackHttpUrl != null) {
            val fallbackRequest = buildFallbackRequest(originalRequest, fallbackHttpUrl)
            try {
                val fallbackResponse = chain.proceed(fallbackRequest)
                if (!fallbackResponse.isSuccessful || isHtmlResponse(fallbackResponse)) {
                    // Both failed; reset health state so next retry attempts both again
                    isPrimaryHealthy = true
                    lastPrimaryFailureTimestamp = 0L
                }
                return fallbackResponse
            } catch (fallbackException: IOException) {
                isPrimaryHealthy = true
                lastPrimaryFailureTimestamp = 0L
                if (primaryException != null) {
                    fallbackException.addSuppressed(primaryException)
                }
                throw fallbackException
            }
        }

        if (primaryException != null) {
            throw primaryException
        }

        return primaryResponse ?: throw IOException("Unexpected null response from YTS")
    }

    private fun buildFallbackRequest(original: Request, targetBaseUrl: HttpUrl): Request {
        val targetUrl = original.url.newBuilder()
            .scheme(targetBaseUrl.scheme)
            .host(targetBaseUrl.host)
            .port(targetBaseUrl.port)
            .build()

        return original.newBuilder()
            .url(targetUrl)
            .build()
    }

    private fun isHtmlResponse(response: Response): Boolean {
        val contentType = response.header("Content-Type").orEmpty()
        return contentType.contains("text/html", ignoreCase = true)
    }

    /** Resets the health state to force trying the primary URL on the next request. */
    fun reset() {
        isPrimaryHealthy = true
        lastPrimaryFailureTimestamp = 0L
    }
}
