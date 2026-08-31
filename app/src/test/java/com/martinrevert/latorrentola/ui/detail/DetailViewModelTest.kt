package com.martinrevert.latorrentola.ui.detail

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.YTS.Data
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.MovieDetails
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.network.YtsRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.TranslationManager
import com.martinrevert.latorrentola.utils.VoiceManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*

class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: YtsRepository = mockk(relaxed = true)
    private val userLibraryRepository: UserLibraryRepository = mockk(relaxed = true)
    private val voiceManager: VoiceManager = mockk(relaxed = true)
    private val translationManager: TranslationManager = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private lateinit var viewModel: DetailViewModel

    @Before
    fun setUp() {
        viewModel = DetailViewModel(repository, userLibraryRepository, voiceManager, translationManager, preferenceManager)
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
    }

    @Test
    fun `handleVoice should not speak if voice system is disabled`() = runTest {
        val movie = Movie(id = 1, title = "Title")
        every { preferenceManager.getVoiceSystem() } returns false

        viewModel.setMovie(movie)

        verify(exactly = 0) { voiceManager.speak(any(), any()) }
    }

    @Test
    fun `handleVoice should speak title and summary when enabled`() = runTest {
        val movie = Movie(id = 1, title = "Title", summary = "Summary")
        every { preferenceManager.getVoiceSystem() } returns true
        every { preferenceManager.getVoiceSummary() } returns true
        every { preferenceManager.getVoiceTranslation() } returns false
        
        coEvery { repository.getMovieFullDetails(1) } returns MovieDetails(data = Data(movie = movie))

        viewModel.setMovie(movie)

        verify { voiceManager.speak("Title", Locale.US) }
        verify { voiceManager.speak("Summary", Locale.US) }
    }

    @Test
    fun `handleVoice should use translation when enabled`() = runTest {
        val movie = Movie(id = 1, title = "Title", summary = "Summary")
        every { preferenceManager.getVoiceSystem() } returns true
        every { preferenceManager.getVoiceSummary() } returns true
        every { preferenceManager.getVoiceTranslation() } returns true
        
        // Mock translation success
        every { translationManager.translate(any(), any(), any()) } answers {
            val onSuccess = secondArg<(String) -> Unit>()
            onSuccess("Resumen Traducido")
        }

        coEvery { repository.getMovieFullDetails(1) } returns MovieDetails(data = Data(movie = movie))

        viewModel.setMovie(movie)

        verify { voiceManager.speak("Resumen Traducido", Locale.forLanguageTag("es-ES")) }
    }

    @Test
    fun `toggleFavorite should update repository and state`() = runTest {
        val movie = Movie(id = 1, title = "Title")
        coEvery { repository.isFavorite(1) } returns false
        
        // Initialize state to Success
        viewModel.setMovie(movie) 
        
        viewModel.toggleFavorite(movie)
        
        coVerify { repository.addFavorite(movie) }
        assertThat((viewModel.uiState.value as DetailUiState.Success).isFavorite).isTrue()
        
        coEvery { repository.isFavorite(1) } returns true
        viewModel.toggleFavorite(movie)
        coVerify { repository.removeFavorite(movie) }
        assertThat((viewModel.uiState.value as DetailUiState.Success).isFavorite).isFalse()
    }
}
