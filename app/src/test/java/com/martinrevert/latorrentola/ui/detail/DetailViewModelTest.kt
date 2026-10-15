package com.martinrevert.latorrentola.ui.detail

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.YTS.Data
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.MovieDetails
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.network.YtsRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.TranslationManager
import com.martinrevert.latorrentola.utils.VoiceManager
import io.mockk.clearMocks
import io.mockk.coEvery
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
    private val userLibraryRepository: UserLibraryRepository = mockk(relaxed = true)
    private val tmdbRepository: TmdbRepository = mockk(relaxed = true)
    private val voiceManager: VoiceManager = mockk(relaxed = true)
    private val translationManager: TranslationManager = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private lateinit var viewModel: DetailViewModel

    @Before
    fun setUp() {
        clearMocks(repository, userLibraryRepository, tmdbRepository, voiceManager, translationManager, preferenceManager)
        every { userLibraryRepository.getDownloadedMovies() } returns flowOf(emptyList())
        every { userLibraryRepository.getWatchHistory() } returns flowOf(emptyList())
        every { preferenceManager.getVoiceSystem() } returns true
        every { preferenceManager.getVoiceTranslation() } returns false
        every { preferenceManager.getVoiceSummary() } returns true
        viewModel = DetailViewModel(
            repository,
            userLibraryRepository,
            tmdbRepository,
            voiceManager,
            translationManager,
            preferenceManager
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
            userLibraryRepository,
            tmdbRepository,
            voiceManager,
            translationManager,
            preferenceManager
        )

        viewModel.watchHistoryMap.test {
            val map = awaitItem()
            assertThat(map).containsKey("100")
            assertThat(map["100"]?.positionMs).isEqualTo(500)
            cancelAndIgnoreRemainingEvents()
        }
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
        val tvSeries = com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary(id = 1399, name = "Game of Thrones")
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
}
