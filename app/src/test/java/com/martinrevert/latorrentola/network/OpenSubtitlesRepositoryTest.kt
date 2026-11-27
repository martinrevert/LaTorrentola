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

    @Test
    fun `normalizeImdbIdForApi strips tt prefix and leading zeros`() {
        assertThat(OpenSubtitlesRepository.normalizeImdbIdForApi("tt1099212")).isEqualTo("1099212")
        assertThat(OpenSubtitlesRepository.normalizeImdbIdForApi("tt0111161")).isEqualTo("111161")
        assertThat(OpenSubtitlesRepository.normalizeImdbIdForApi("TT00133093")).isEqualTo("133093")
        assertThat(OpenSubtitlesRepository.normalizeImdbIdForApi("0944947")).isEqualTo("944947")
        assertThat(OpenSubtitlesRepository.normalizeImdbIdForApi("tt0000000")).isNull()
        assertThat(OpenSubtitlesRepository.normalizeImdbIdForApi("invalid")).isNull()
        assertThat(OpenSubtitlesRepository.normalizeImdbIdForApi("")).isNull()
    }

    @Test
    fun `isSpanishLanguageCode recognizes generic es, Latin American ea, and European sp`() {
        assertThat(OpenSubtitlesRepository.isSpanishLanguageCode("es")).isTrue()
        assertThat(OpenSubtitlesRepository.isSpanishLanguageCode("ea")).isTrue()
        assertThat(OpenSubtitlesRepository.isSpanishLanguageCode("sp")).isTrue()
        assertThat(OpenSubtitlesRepository.isSpanishLanguageCode("spa")).isTrue()
        assertThat(OpenSubtitlesRepository.isSpanishLanguageCode("es-419")).isTrue()
        assertThat(OpenSubtitlesRepository.isSpanishLanguageCode("en")).isFalse()
        assertThat(OpenSubtitlesRepository.isSpanishLanguageCode("fr")).isFalse()
    }

    @Test
    fun `searchByImdbId queries Spanish ea-es-sp and English en in parallel so English never crowds out Spanish`() = runTest {
        val spanishEntries = listOf(
            OpenSubtitlesSearchEntry(
                id = "es-1",
                attributes = OpenSubtitlesAttributes(
                    language = "es",
                    release = "Twilight.2008.1080p.BluRay",
                    downloadCount = 8_000L,
                    files = listOf(OpenSubtitlesFile(fileId = 101L, fileName = "twilight.es.srt")),
                    featureDetails = OpenSubtitlesFeatureDetails(title = null, movieName = "Twilight", year = 2008)
                )
            ),
            OpenSubtitlesSearchEntry(
                id = "ea-1",
                attributes = OpenSubtitlesAttributes(
                    language = "ea",
                    release = "Twilight.2008.1080p.Latam",
                    downloadCount = 5_000L,
                    files = listOf(OpenSubtitlesFile(fileId = 102L, fileName = "twilight.ea.srt")),
                    featureDetails = OpenSubtitlesFeatureDetails(title = null, movieName = "Twilight", year = 2008)
                )
            ),
            OpenSubtitlesSearchEntry(
                id = "sp-1",
                attributes = OpenSubtitlesAttributes(
                    language = "sp",
                    release = "Twilight.2008.1080p.Castellano",
                    downloadCount = 4_000L,
                    files = listOf(OpenSubtitlesFile(fileId = 103L, fileName = "twilight.sp.srt")),
                    featureDetails = OpenSubtitlesFeatureDetails(title = null, movieName = "Twilight", year = 2008)
                )
            )
        )
        val englishEntries = listOf(
            OpenSubtitlesSearchEntry(
                id = "en-1",
                attributes = OpenSubtitlesAttributes(
                    language = "en",
                    release = "Twilight.2008.1080p.YIFY",
                    downloadCount = 150_000L,
                    files = listOf(OpenSubtitlesFile(fileId = 201L, fileName = "twilight.en.srt")),
                    featureDetails = OpenSubtitlesFeatureDetails(title = null, movieName = "Twilight", year = 2008)
                )
            )
        )

        coEvery {
            openSubtitlesService.searchSubtitles(
                apiKey = any(),
                userAgent = any(),
                authorization = "Bearer test_token",
                episodeNumber = null,
                imdbId = "1099212",
                languages = "ea,es,sp",
                orderBy = any(),
                orderDirection = any(),
                page = any(),
                parentImdbId = null,
                query = null,
                seasonNumber = null,
                type = "movie"
            )
        } returns Response.success(OpenSubtitlesSearchResponse(data = spanishEntries))

        coEvery {
            openSubtitlesService.searchSubtitles(
                apiKey = any(),
                userAgent = any(),
                authorization = "Bearer test_token",
                episodeNumber = null,
                imdbId = "1099212",
                languages = "en",
                orderBy = any(),
                orderDirection = any(),
                page = any(),
                parentImdbId = null,
                query = null,
                seasonNumber = null,
                type = "movie"
            )
        } returns Response.success(OpenSubtitlesSearchResponse(data = englishEntries))

        val results = repository.searchByImdbId("tt1099212")

        assertThat(results.map { it.language }).containsExactly("en", "es", "ea", "sp").inOrder()
        assertThat(results.map { it.fileId }).containsExactly(201L, 101L, 102L, 103L).inOrder()
    }

    @Test
    fun `searchByImdbId strips leading zeros for episodes and passes parentImdbId`() = runTest {
        val episodeEntry = OpenSubtitlesSearchEntry(
            id = "ep-ea",
            attributes = OpenSubtitlesAttributes(
                language = "ea",
                release = "Game.of.Thrones.S01E02",
                downloadCount = 12_000L,
                files = listOf(OpenSubtitlesFile(fileId = 301L, fileName = "got.s01e02.srt")),
                featureDetails = OpenSubtitlesFeatureDetails(title = "The Kingsroad", movieName = null, year = 2011)
            )
        )

        coEvery {
            openSubtitlesService.searchSubtitles(
                apiKey = any(),
                userAgent = any(),
                authorization = "Bearer test_token",
                episodeNumber = 2,
                imdbId = null,
                languages = "ea,es,sp",
                orderBy = any(),
                orderDirection = any(),
                page = any(),
                parentImdbId = "944947",
                query = null,
                seasonNumber = 1,
                type = "episode"
            )
        } returns Response.success(OpenSubtitlesSearchResponse(data = listOf(episodeEntry)))

        val results = repository.searchByImdbId(
            imdbId = "tt0944947",
            seasonNumber = 1,
            episodeNumber = 2,
            language = "es"
        )

        assertThat(results).hasSize(1)
        assertThat(results.first().language).isEqualTo("ea")
        assertThat(results.first().fileId).isEqualTo(301L)
    }

    @Test
    fun `searchByImdbId returns surviving batch when one parallel language request fails`() = runTest {
        val spanishEntry = OpenSubtitlesSearchEntry(
            id = "es-1",
            attributes = OpenSubtitlesAttributes(
                language = "es",
                release = "Twilight.2008.1080p",
                downloadCount = 8_000L,
                files = listOf(OpenSubtitlesFile(fileId = 101L, fileName = "twilight.es.srt")),
                featureDetails = OpenSubtitlesFeatureDetails(title = null, movieName = "Twilight", year = 2008)
            )
        )

        coEvery {
            openSubtitlesService.searchSubtitles(
                apiKey = any(),
                userAgent = any(),
                authorization = "Bearer test_token",
                episodeNumber = null,
                imdbId = "1099212",
                languages = "ea,es,sp",
                orderBy = any(),
                orderDirection = any(),
                page = any(),
                parentImdbId = null,
                query = null,
                seasonNumber = null,
                type = "movie"
            )
        } returns Response.success(OpenSubtitlesSearchResponse(data = listOf(spanishEntry)))

        coEvery {
            openSubtitlesService.searchSubtitles(
                apiKey = any(),
                userAgent = any(),
                authorization = "Bearer test_token",
                episodeNumber = null,
                imdbId = "1099212",
                languages = "en",
                orderBy = any(),
                orderDirection = any(),
                page = any(),
                parentImdbId = null,
                query = null,
                seasonNumber = null,
                type = "movie"
            )
        } returns Response.error(503, """{"message":"Service busy"}""".toResponseBody())

        val results = repository.searchByImdbId("tt1099212")

        assertThat(results).hasSize(1)
        assertThat(results.first().fileId).isEqualTo(101L)
    }
}
