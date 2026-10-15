package com.martinrevert.latorrentola.network

import android.content.Context
import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.utils.OpenSubtitlesCredentialStore
import com.martinrevert.latorrentola.utils.OpenSubtitlesCredentials
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response

class OpenSubtitlesRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = mockk()
    private val openSubtitlesService: OpenSubtitlesService = mockk()
    private val credentialStore: OpenSubtitlesCredentialStore = mockk()

    private lateinit var filesDir: java.io.File
    private lateinit var cacheDir: java.io.File
    private lateinit var repository: OpenSubtitlesRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        filesDir = tempFolder.newFolder("files")
        cacheDir = tempFolder.newFolder("cache")

        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir

        every { credentialStore.load() } returns OpenSubtitlesCredentials("test_user", "test_pass")

        coEvery {
            openSubtitlesService.login(
                apiKey = any(),
                userAgent = any(),
                request = any()
            )
        } returns Response.success(OpenSubtitlesLoginResponse(token = "test_token"))

        repository = OpenSubtitlesRepository(context, openSubtitlesService, credentialStore)
    }

    @Test
    fun `download subtitle with empty infoHash saves to general cache directory without error`() = runTest {
        val result = OpenSubtitleResult(
            id = "sub_1",
            fileId = 12345L,
            language = "es",
            release = "Test Release",
            downloadCount = 100L,
            fileName = "sub.srt",
            featureTitle = "Test Movie"
        )

        val downloadUrl = "https://api.opensubtitles.com/download/sub.srt"
        val downloadResponse = OpenSubtitlesDownloadResponse(
            link = downloadUrl,
            fileName = "sub.srt",
            requests = 1,
            remaining = 99,
            message = "OK",
            resetTime = "24h",
            resetTimeUtc = "2026-01-01T00:00:00Z"
        )

        coEvery {
            openSubtitlesService.requestSubtitleDownload(
                apiKey = any(),
                userAgent = any(),
                authorization = "Bearer test_token",
                request = OpenSubtitlesDownloadRequest(fileId = 12345L)
            )
        } returns Response.success(downloadResponse)

        val srtContent = "1\r\n00:00:01,000 --> 00:00:02,000\r\nHola mundo\r\n"
        coEvery {
            openSubtitlesService.downloadFile(downloadUrl.toHttpUrl())
        } returns Response.success(srtContent.toResponseBody())

        val downloadedSubtitle = repository.download(result, torrentInfoHash = "")

        assertThat(downloadedSubtitle.file.exists()).isTrue()
        assertThat(downloadedSubtitle.file.parentFile?.name).isEqualTo("opensubtitles")
        assertThat(downloadedSubtitle.file.parentFile?.parentFile).isEqualTo(cacheDir)
    }

    @Test
    fun `download subtitle with valid hex infoHash saves to torrent directory`() = runTest {
        val validHash = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0"
        val result = OpenSubtitleResult(
            id = "sub_1",
            fileId = 12345L,
            language = "es",
            release = "Test Release",
            downloadCount = 100L,
            fileName = "sub.srt",
            featureTitle = "Test Movie"
        )

        val downloadUrl = "https://api.opensubtitles.com/download/sub.srt"
        val downloadResponse = OpenSubtitlesDownloadResponse(
            link = downloadUrl,
            fileName = "sub.srt",
            requests = 1,
            remaining = 99,
            message = "OK",
            resetTime = "24h",
            resetTimeUtc = "2026-01-01T00:00:00Z"
        )

        coEvery {
            openSubtitlesService.requestSubtitleDownload(
                apiKey = any(),
                userAgent = any(),
                authorization = "Bearer test_token",
                request = OpenSubtitlesDownloadRequest(fileId = 12345L)
            )
        } returns Response.success(downloadResponse)

        val srtContent = "1\r\n00:00:01,000 --> 00:00:02,000\r\nHola mundo\r\n"
        coEvery {
            openSubtitlesService.downloadFile(downloadUrl.toHttpUrl())
        } returns Response.success(srtContent.toResponseBody())

        val downloadedSubtitle = repository.download(result, torrentInfoHash = validHash)

        assertThat(downloadedSubtitle.file.exists()).isTrue()
        assertThat(downloadedSubtitle.file.parentFile?.name).isEqualTo("opensubtitles")
        assertThat(downloadedSubtitle.file.parentFile?.parentFile?.name).isEqualTo(validHash)
    }

    @Test
    fun `download subtitle with magnet URI normalizes hash and saves to torrent directory`() = runTest {
        val validHash = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0"
        val magnetUri = "magnet:?xt=urn:btih:$validHash&dn=Test+Movie"
        val result = OpenSubtitleResult(
            id = "sub_1",
            fileId = 12345L,
            language = "es",
            release = "Test Release",
            downloadCount = 100L,
            fileName = "sub.srt",
            featureTitle = "Test Movie"
        )

        val downloadUrl = "https://api.opensubtitles.com/download/sub.srt"
        val downloadResponse = OpenSubtitlesDownloadResponse(
            link = downloadUrl,
            fileName = "sub.srt",
            requests = 1,
            remaining = 99,
            message = "OK",
            resetTime = "24h",
            resetTimeUtc = "2026-01-01T00:00:00Z"
        )

        coEvery {
            openSubtitlesService.requestSubtitleDownload(
                apiKey = any(),
                userAgent = any(),
                authorization = "Bearer test_token",
                request = OpenSubtitlesDownloadRequest(fileId = 12345L)
            )
        } returns Response.success(downloadResponse)

        val srtContent = "1\r\n00:00:01,000 --> 00:00:02,000\r\nHola mundo\r\n"
        coEvery {
            openSubtitlesService.downloadFile(downloadUrl.toHttpUrl())
        } returns Response.success(srtContent.toResponseBody())

        val downloadedSubtitle = repository.download(result, torrentInfoHash = magnetUri)

        assertThat(downloadedSubtitle.file.exists()).isTrue()
        assertThat(downloadedSubtitle.file.parentFile?.name).isEqualTo("opensubtitles")
        assertThat(downloadedSubtitle.file.parentFile?.parentFile?.name).isEqualTo(validHash)
    }
}
