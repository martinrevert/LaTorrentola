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
}
