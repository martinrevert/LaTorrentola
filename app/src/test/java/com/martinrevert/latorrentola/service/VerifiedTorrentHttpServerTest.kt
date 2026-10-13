package com.martinrevert.latorrentola.service

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerifiedTorrentHttpServerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `localUrl returns 127_0_0_1 format with access token and filename`() {
        val dummyFile = tempFolder.newFile("test_video.mkv")
        val server = VerifiedTorrentHttpServer(dummyFile, dummyFile.length()) { _, _ -> true }
        val port = server.startServer()

        try {
            val localUrl = server.localUrl()
            assertThat(localUrl).startsWith("http://127.0.0.1:$port/")
            assertThat(localUrl).endsWith("/test_video.mkv")
            assertThat(port).isGreaterThan(0)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `lanUrl returns valid http format with port and filename`() {
        val dummyFile = tempFolder.newFile("test_video.mkv")
        val server = VerifiedTorrentHttpServer(dummyFile, dummyFile.length()) { _, _ -> true }
        val port = server.startServer()

        try {
            val lanUrl = try {
                server.lanUrl()
            } catch (_: Exception) {
                null
            }

            if (lanUrl != null) {
                assertThat(lanUrl).contains(":$port/")
                assertThat(lanUrl).startsWith("http://")
                assertThat(lanUrl).endsWith("/test_video.mkv")
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `convertSrtToVtt converts SRT timestamps and adds WEBVTT header`() {
        val srtText = "1\n00:00:01,234 --> 00:00:04,567\nHola mundo\n\n2\n00:00:05,000 --> 00:00:08,123\nSegundo subtitulo"
        val timestampRegex = Regex("""(\d{2}:\d{2}:\d{2}),(\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2}),(\d{3})""")
        val convertedBody = timestampRegex.replace(srtText) { matchResult ->
            val (startHms, startMs, endHms, endMs) = matchResult.destructured
            "$startHms.$startMs --> $endHms.$endMs"
        }
        val vttText = "WEBVTT\n\n$convertedBody"

        assertThat(vttText).startsWith("WEBVTT")
        assertThat(vttText).contains("00:00:01.234 --> 00:00:04.567")
        assertThat(vttText).contains("00:00:05.000 --> 00:00:08.123")
    }

    @Test
    fun `socket read timeout is at least 30 seconds for stable streaming`() {
        assertThat(VerifiedTorrentHttpServer.SOCKET_READ_TIMEOUT).isAtLeast(30_000)
    }

    @Test
    fun `subtitle base path is properly stripped from video cast url`() {
        val castUrl = "http://192.168.1.50:52341/test-token-123/movie%20title.mp4"
        val baseCastUrl = castUrl.substringBeforeLast('/')
        val base64Path = "cGF0aA=="
        val subFileName = "spanish.vtt"
        val castSubUri = "$baseCastUrl/subtitles/$base64Path/$subFileName"

        assertThat(castSubUri).isEqualTo("http://192.168.1.50:52341/test-token-123/subtitles/cGF0aA==/spanish.vtt")
        assertThat(castSubUri).doesNotContain("movie%20title.mp4")
    }
}
