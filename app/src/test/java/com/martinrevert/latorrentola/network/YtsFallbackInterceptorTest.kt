package com.martinrevert.latorrentola.network

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException

class YtsFallbackInterceptorTest {

    private lateinit var interceptor: YtsFallbackInterceptor
    private val chain: Interceptor.Chain = mockk()

    private val primaryBaseUrl = "https://movies-api.accel.li/api/v2/"
    private val fallbackBaseUrl = "https://yts.gg/api/v2/"

    @Before
    fun setUp() {
        interceptor = YtsFallbackInterceptor(
            primaryBaseUrl = primaryBaseUrl,
            fallbackBaseUrl = fallbackBaseUrl,
            primaryCooldownMillis = 60_000L
        )
    }

    private fun createResponse(request: Request, code: Int, contentType: String = "application/json"): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .header("Content-Type", contentType)
            .body("{}".toResponseBody(contentType.toMediaType()))
            .build()
    }

    @Test
    fun `unrelated request should proceed without modification`() {
        val request = Request.Builder().url("https://api.themoviedb.org/3/movie/popular").build()
        val expectedResponse = createResponse(request, 200)

        every { chain.request() } returns request
        every { chain.proceed(request) } returns expectedResponse

        val response = interceptor.intercept(chain)

        assertThat(response).isEqualTo(expectedResponse)
    }

    @Test
    fun `primary succeeds should return primary response`() {
        val request = Request.Builder().url("${primaryBaseUrl}list_movies.json?limit=50").build()
        val expectedResponse = createResponse(request, 200)

        every { chain.request() } returns request
        every { chain.proceed(request) } returns expectedResponse

        val response = interceptor.intercept(chain)

        assertThat(response.code).isEqualTo(200)
        assertThat(response.request.url.host).isEqualTo("movies-api.accel.li")
    }

    @Test
    fun `primary fails with HTTP 521 should fallback to alternative URL and return success`() {
        val request = Request.Builder().url("${primaryBaseUrl}list_movies.json?limit=50").build()
        val errorResponse = createResponse(request, 521)

        every { chain.request() } returns request
        every { chain.proceed(any()) } answers {
            val req = firstArg<Request>()
            if (req.url.host == "movies-api.accel.li") {
                errorResponse
            } else {
                createResponse(req, 200)
            }
        }

        val response = interceptor.intercept(chain)

        assertThat(response.code).isEqualTo(200)
        assertThat(response.request.url.host).isEqualTo("yts.gg")
        assertThat(response.request.url.query).isEqualTo("limit=50")
    }

    @Test
    fun `primary fails with IOException should fallback to alternative URL and return success`() {
        val request = Request.Builder().url("${primaryBaseUrl}list_movies.json?page=2").build()

        every { chain.request() } returns request
        every { chain.proceed(any()) } answers {
            val req = firstArg<Request>()
            if (req.url.host == "movies-api.accel.li") {
                throw IOException("Connection timed out")
            } else {
                createResponse(req, 200)
            }
        }

        val response = interceptor.intercept(chain)

        assertThat(response.code).isEqualTo(200)
        assertThat(response.request.url.host).isEqualTo("yts.gg")
        assertThat(response.request.url.encodedPath).isEqualTo("/api/v2/list_movies.json")
    }

    @Test
    fun `primary returns HTML response should fallback to alternative URL`() {
        val request = Request.Builder().url("${primaryBaseUrl}list_movies.json").build()
        val htmlResponse = createResponse(request, 200, "text/html; charset=utf-8")

        every { chain.request() } returns request
        every { chain.proceed(any()) } answers {
            val req = firstArg<Request>()
            if (req.url.host == "movies-api.accel.li") {
                htmlResponse
            } else {
                createResponse(req, 200, "application/json")
            }
        }

        val response = interceptor.intercept(chain)

        assertThat(response.code).isEqualTo(200)
        assertThat(response.request.url.host).isEqualTo("yts.gg")
        assertThat(response.header("Content-Type")).contains("application/json")
    }

    @Test
    fun `both primary and fallback fail with HTTP errors should return fallback error response`() {
        val request = Request.Builder().url("${primaryBaseUrl}list_movies.json").build()

        every { chain.request() } returns request
        every { chain.proceed(any()) } answers {
            val req = firstArg<Request>()
            if (req.url.host == "movies-api.accel.li") {
                createResponse(req, 521)
            } else {
                createResponse(req, 503)
            }
        }

        val response = interceptor.intercept(chain)

        assertThat(response.code).isEqualTo(503)
        assertThat(response.request.url.host).isEqualTo("yts.gg")
    }

    @Test
    fun `both primary and fallback fail with IOException should throw fallback exception with suppressed primary`() {
        val request = Request.Builder().url("${primaryBaseUrl}list_movies.json").build()

        every { chain.request() } returns request
        every { chain.proceed(any()) } answers {
            val req = firstArg<Request>()
            if (req.url.host == "movies-api.accel.li") {
                throw IOException("Primary host unreachable")
            } else {
                throw IOException("Fallback host unreachable")
            }
        }

        val exception = assertThrows(IOException::class.java) {
            interceptor.intercept(chain)
        }

        assertThat(exception.message).contains("Fallback host unreachable")
        assertThat(exception.suppressed).hasLength(1)
        assertThat(exception.suppressed[0].message).contains("Primary host unreachable")
    }

    @Test
    fun `subsequent request during cooldown should skip primary and use fallback directly`() {
        val request1 = Request.Builder().url("${primaryBaseUrl}list_movies.json?page=1").build()
        val request2 = Request.Builder().url("${primaryBaseUrl}list_movies.json?page=2").build()

        val hostsContacted = mutableListOf<String>()

        every { chain.proceed(any()) } answers {
            val req = firstArg<Request>()
            hostsContacted.add(req.url.host)
            if (req.url.host == "movies-api.accel.li") {
                createResponse(req, 521)
            } else {
                createResponse(req, 200)
            }
        }

        // First call fails primary, succeeds fallback
        every { chain.request() } returns request1
        val response1 = interceptor.intercept(chain)
        assertThat(response1.code).isEqualTo(200)
        assertThat(hostsContacted).containsExactly("movies-api.accel.li", "yts.gg").inOrder()

        // Second call within cooldown should skip primary entirely
        hostsContacted.clear()
        every { chain.request() } returns request2
        val response2 = interceptor.intercept(chain)
        assertThat(response2.code).isEqualTo(200)
        assertThat(hostsContacted).containsExactly("yts.gg")
    }
}
