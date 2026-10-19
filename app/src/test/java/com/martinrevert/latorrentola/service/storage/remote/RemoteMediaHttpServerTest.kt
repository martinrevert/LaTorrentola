package com.martinrevert.latorrentola.service.storage.remote

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class RemoteMediaHttpServerTest {

    private val client: RemoteStorageClient = mockk(relaxed = true)
    private val config = RemoteServerConfig(
        id = "test-server",
        name = "Test NAS",
        type = RemoteServerType.SMB,
        host = "127.0.0.1",
        shareOrExport = "test"
    )

    private val sampleData = "0123456789ABCDEF0123456789ABCDEF".toByteArray(Charsets.UTF_8)
    private lateinit var server: RemoteMediaHttpServer
    private val httpClient = OkHttpClient.Builder().build()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        coEvery { client.openInputStream(config, any(), any()) } answers {
            val offset = thirdArg<Long>().toInt()
            val available = (sampleData.size - offset).coerceAtLeast(0)
            ByteArrayInputStream(sampleData, offset, available)
        }

        server = RemoteMediaHttpServer(
            client = client,
            serverConfig = config,
            remoteFilePath = "videos/sample.mkv",
            fileName = "sample.mkv",
            fileLength = sampleData.size.toLong(),
            accessToken = "secure-token-123",
            getSubtitleVtt = { "WEBVTT\n\n1\n00:00:01.000 --> 00:00:03.000\nHello Remote World!" }
        )
        server.startServer()
    }

    @After
    fun tearDown() {
        server.stop()
        unmockkAll()
    }

    @Test
    fun `localUrl formats valid localhost URL with token and encoded name`() {
        val url = server.localUrl()
        assertThat(url).startsWith("http://127.0.0.1:${server.listeningPort}/secure-token-123/sample.mkv")
    }

    @Test
    fun `request with invalid token returns 403 Forbidden`() {
        val req = Request.Builder()
            .url("http://127.0.0.1:${server.listeningPort}/wrong-token/sample.mkv")
            .build()

        val res = httpClient.newCall(req).execute()
        assertThat(res.code).isEqualTo(403)
        res.close()
    }

    @Test
    fun `full media request returns 200 OK with correct content length and Accept-Ranges`() {
        val req = Request.Builder()
            .url(server.localUrl())
            .build()

        val res = httpClient.newCall(req).execute()
        assertThat(res.code).isEqualTo(200)
        assertThat(res.header("Accept-Ranges")).isEqualTo("bytes")
        assertThat(res.body?.bytes()?.size).isEqualTo(sampleData.size)
        res.close()
    }

    @Test
    fun `range request returns 206 Partial Content with Content-Range`() {
        val req = Request.Builder()
            .url(server.localUrl())
            .header("Range", "bytes=4-10")
            .build()

        val res = httpClient.newCall(req).execute()
        assertThat(res.code).isEqualTo(206)
        assertThat(res.header("Content-Range")).isEqualTo("bytes 4-10/${sampleData.size}")
        val body = res.body?.bytes()
        assertThat(body?.size).isEqualTo(7) // 10 - 4 + 1
        res.close()
    }

    @Test
    fun `subtitle endpoint returns 200 OK with text vtt and CORS headers`() {
        val req = Request.Builder()
            .url("http://127.0.0.1:${server.listeningPort}/secure-token-123/subtitles.vtt")
            .build()

        val res = httpClient.newCall(req).execute()
        assertThat(res.code).isEqualTo(200)
        assertThat(res.header("Content-Type")).isEqualTo("text/vtt")
        assertThat(res.header("Access-Control-Allow-Origin")).isEqualTo("*")
        val body = res.body?.string().orEmpty()
        assertThat(body).contains("WEBVTT")
        assertThat(body).contains("Hello Remote World!")
        res.close()
    }
}
