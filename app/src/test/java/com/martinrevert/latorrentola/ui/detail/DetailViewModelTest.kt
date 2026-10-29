package com.martinrevert.latorrentola.ui.detail

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.database.TorrentDownloadDao
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.YTS.Data
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.MovieDetails
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.network.TorrentioRepository
import com.martinrevert.latorrentola.model.torrent.PlaybackCompatibility
import com.martinrevert.latorrentola.model.torrent.TorrentRelease
import com.martinrevert.latorrentola.model.torrent.VideoQualityGroup
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.network.YtsRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.TranslationManager
import com.martinrevert.latorrentola.utils.VoiceManager
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: YtsRepository = mockk(relaxed = true)
    private val torrentioRepository: TorrentioRepository = mockk(relaxed = true)
    private val userLibraryRepository: UserLibraryRepository = mockk(relaxed = true)
    private val tmdbRepository: TmdbRepository = mockk(relaxed = true)
    private val voiceManager: VoiceManager = mockk(relaxed = true)
    private val translationManager: TranslationManager = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private val torrentDownloadDao: TorrentDownloadDao = mockk(relaxed = true)
    private lateinit var viewModel: DetailViewModel

    @Before
    fun setUp() {
        clearMocks(repository, torrentioRepository, userLibraryRepository, tmdbRepository, voiceManager, translationManager, preferenceManager, torrentDownloadDao)
        every { userLibraryRepository.getDownloadedMovies() } returns flowOf(emptyList())
        every { userLibraryRepository.getWatchHistory() } returns flowOf(emptyList())
        every { torrentDownloadDao.observeAll() } returns flowOf(emptyList())
        every { preferenceManager.getVoiceSystem() } returns true
        every { preferenceManager.getVoiceTranslation() } returns false
        every { preferenceManager.getVoiceSummary() } returns true
        viewModel = DetailViewModel(
            repository,
            torrentioRepository,
            userLibraryRepository,
            tmdbRepository,
            voiceManager,
            translationManager,
            preferenceManager,
            torrentDownloadDao
        )
    }

    @Test
    fun `setMovie should fetch full details and update success state`() = runTest {
        val movie = Movie(id = 1, title = "Original Title")
        val fullMovie = Movie(id = 1, title = "Full Details Title", descriptionFull = "Full Description")
        
        coEvery { repository.isFavorite(1) } returns true
        coEvery { repository.getMovieFullDetails(1) } returns MovieDetails(data = Data(movie = fullMovie))

        viewModel.setMovie(movie)

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(DetailUiState.Success::class.java)
        val success = state as DetailUiState.Success
        assertThat(success.movie.title).isEqualTo("Full Details Title")
        assertThat(success.isFavorite).isTrue()
    }

    @Test
    fun `setMovie should fallback to initial movie if network fails`() = runTest {
        val movie = Movie(id = 1, title = "Initial Title")
        coEvery { repository.getMovieFullDetails(1) } throws Exception("Network Error")
        coEvery { repository.isFavorite(1) } returns false

        viewModel.setMovie(movie)

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(DetailUiState.Success::class.java)
        val success = state as DetailUiState.Success
        assertThat(success.movie.title).isEqualTo("Initial Title")
        assertThat(success.isFavorite).isFalse()
    }

    @Test
    fun `watchHistoryMap should map watch history items by mediaId`() = runTest {
        val progress = PlaybackProgress(mediaId = "100", title = "Test", positionMs = 500)
        every { userLibraryRepository.getWatchHistory() } returns flowOf(listOf(progress))

        viewModel = DetailViewModel(
            repository,
            torrentioRepository,
            userLibraryRepository,
            tmdbRepository,
            voiceManager,
            translationManager,
            preferenceManager,
            torrentDownloadDao
        )

        viewModel.watchHistoryMap.test {
            val map = awaitItem()
            assertThat(map).containsKey("100")
            assertThat(map["100"]?.positionMs).isEqualTo(500)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setMovie should merge YTS and Torrentio releases, group by quality, and sort by seeds`() = runTest {
        val movie = Movie(id = 1, title = "Inception", imdbCode = "tt1375666")
        val ytsRelease = TorrentRelease(
            hash = "yts_hash_1",
            title = "Inception (2010) [1080p] [YTS.MX]",
            provider = "YTS",
            qualityGroup = VideoQualityGroup.FHD_1080P,
            resolutionLabel = "1080p",
            videoCodec = "x264",
            audioCodec = "AAC 5.1",
            formattedSize = "2.1 GB",
            sizeBytes = 2100000000L,
            seeds = 10,
            peers = 5,
            magnetUrl = "magnet:?xt=urn:btih:yts_hash_1",
            compatibility = PlaybackCompatibility.NATIVE,
            isYts = true
        )
        val torrentioRelease4k = TorrentRelease(
            hash = "torrentio_4k_hash",
            title = "Inception.2010.2160p.UHD.Remux.TrueHD.Atmos",
            provider = "1337x",
            qualityGroup = VideoQualityGroup.UHD_4K,
            resolutionLabel = "2160p 4K",
            videoCodec = "HEVC",
            audioCodec = "TrueHD Atmos",
            formattedSize = "45.0 GB",
            sizeBytes = 45000000000L,
            seeds = 50,
            peers = 10,
            magnetUrl = "magnet:?xt=urn:btih:torrentio_4k_hash",
            compatibility = PlaybackCompatibility.AUDIO_REQUIRES_PASSTHROUGH_OR_EXTERNAL
        )
        val torrentioRelease1080p = TorrentRelease(
            hash = "torrentio_1080p_hash",
            title = "Inception.2010.1080p.BluRay.DTS-HD.MA.5.1",
            provider = "TorrentGalaxy",
            qualityGroup = VideoQualityGroup.FHD_1080P,
            resolutionLabel = "1080p FHD",
            videoCodec = "x264",
            audioCodec = "DTS-HD MA 5.1",
            formattedSize = "12.0 GB",
            sizeBytes = 12000000000L,
            seeds = 80,
            peers = 20,
            magnetUrl = "magnet:?xt=urn:btih:torrentio_1080p_hash",
            compatibility = PlaybackCompatibility.AUDIO_REQUIRES_PASSTHROUGH_OR_EXTERNAL
        )

        coEvery { repository.isFavorite(1) } returns false
        coEvery { repository.getMovieFullDetails(1) } returns MovieDetails(data = Data(movie = movie))
        coEvery { torrentioRepository.getMovieTorrents("tt1375666") } returns listOf(torrentioRelease4k, torrentioRelease1080p)

        viewModel.setMovie(movie)

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(DetailUiState.Success::class.java)
        val success = state as DetailUiState.Success
        assertThat(success.releasesByQuality).containsKey(VideoQualityGroup.UHD_4K)
        assertThat(success.releasesByQuality).containsKey(VideoQualityGroup.FHD_1080P)
        val releases1080p = success.releasesByQuality[VideoQualityGroup.FHD_1080P].orEmpty()
        assertThat(releases1080p).isNotEmpty()
        // Highest seeds should be first
        assertThat(releases1080p.first().seeds).isGreaterThan(0)
    }

    @Test
    fun `setMovieByQuery should set Success state when YTS returns a movie`() = runTest {
        val movie = Movie(id = 123, title = "YTS Movie", imdbCode = "tt0137523")
        coEvery { repository.searchMovies("tt0137523", 1) } returns MovieDetails(data = Data(movies = listOf(movie)))
        coEvery { repository.isFavorite(123) } returns false
        coEvery { repository.getMovieFullDetails(123) } returns MovieDetails(data = Data(movie = movie))

        viewModel.setMovieByQuery("tt0137523")

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(DetailUiState.Success::class.java)
        val success = state as DetailUiState.Success
        assertThat(success.movie.title).isEqualTo("YTS Movie")
    }

    @Test
    fun `setMovieByQuery should fallback to TMDB TV and set OpenTvDetail when YTS returns no movie`() = runTest {
        val tvSeries = TmdbTvSummary(id = 1399, name = "Game of Thrones")
        coEvery { repository.searchMovies("tt0944947", 1) } returns MovieDetails(data = Data(movies = emptyList()))
        coEvery { tmdbRepository.findTvSeriesByImdbId("tt0944947") } returns tvSeries

        viewModel.setMovieByQuery("https://www.imdb.com/title/tt0944947/")

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(DetailUiState.OpenTvDetail::class.java)
        val openTv = state as DetailUiState.OpenTvDetail
        assertThat(openTv.seriesId).isEqualTo(1399)
    }

    @Test
    fun `setMovieByQuery should set Error state when both YTS and TMDB find nothing`() = runTest {
        coEvery { repository.searchMovies("tt0000000", 1) } returns MovieDetails(data = Data(movies = emptyList()))
        coEvery { tmdbRepository.findTvSeriesByImdbId("tt0000000") } returns null

        viewModel.setMovieByQuery("tt0000000")

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(DetailUiState.Error::class.java)
    }

    @Test
    fun `removeMovieDownload delegates hash removal to UserLibraryRepository`() = runTest {
        viewModel.removeMovieDownload("HASH_TO_REMOVE")
        testScheduler.advanceUntilIdle()

        coVerify { userLibraryRepository.removeDownloadedMovieByHash("HASH_TO_REMOVE") }
    }
}
