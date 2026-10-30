package com.martinrevert.latorrentola.ui.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.YTS.Data
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.MovieDetails
import com.martinrevert.latorrentola.network.AuthRepository
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.network.YtsRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: YtsRepository = mockk(relaxed = true)
    private val userLibraryRepository: UserLibraryRepository = mockk(relaxed = true)
    private val preferenceManager: com.martinrevert.latorrentola.utils.PreferenceManager = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val tmdbRepository: TmdbRepository = mockk(relaxed = true)
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        // Default mocks to prevent crashes during init
        every { userLibraryRepository.getDownloadedMovies() } returns flowOf(emptyList())
        every { repository.getFavoriteMovies() } returns flowOf(emptyList())
        every { repository.getTopGenres(any()) } returns flowOf(emptyList())
        every { preferenceManager.getFilteredLanguages() } returns ""
        every { preferenceManager.filteredLanguagesFlow } returns MutableStateFlow("")
        every { preferenceManager.getMinimumRating() } returns 6f
        every { preferenceManager.minimumRatingFlow } returns MutableStateFlow(6f)
        every { authRepository.authStateFlow } returns MutableStateFlow(null)
        coEvery { repository.getLastVisitDate() } returns null
        // Default to empty list for any page to avoid infinite loops in ViewModel
        coEvery { repository.getMovies(any(), any(), any()) } returns MovieDetails(data = Data(movies = emptyList()))
    }

    @Test
    fun `initial state should be Loading then Success if repository returns movies`() = runTest {
        val movies = listOf(
            Movie(id = 1, title = "English Movie", language = "en", rating = "7"),
            Movie(id = 2, title = "Spanish Movie", language = "es", rating = "7")
        )
        coEvery { repository.getMovies(1, null, 6f) } returns MovieDetails(data = Data(movies = movies))

        viewModel = HomeViewModel(repository, userLibraryRepository, preferenceManager, authRepository, tmdbRepository)

        viewModel.uiState.test {
            // First item might be Loading or Success depending on how fast init runs with UnconfinedTestDispatcher
            val first = awaitItem()
            if (first is HomeUiState.Loading) {
                val second = awaitItem()
                assertThat(second).isInstanceOf(HomeUiState.Success::class.java)
                assertThat((second as HomeUiState.Success).movies).hasSize(2)
            } else {
                assertThat(first).isInstanceOf(HomeUiState.Success::class.java)
                assertThat((first as HomeUiState.Success).movies).hasSize(2)
            }
        }
    }

    @Test
    fun `loadMovies should filter movies based on preferences`() = runTest {
        val movies = listOf(
            Movie(id = 1, title = "EN", language = "en", rating = "7"),
            Movie(id = 2, title = "ES", language = "es", rating = "7")
        )
        every { preferenceManager.getFilteredLanguages() } returns "es"

        // Return movies for page 1, then empty for page 2+ to avoid infinite loop in ViewModel
        coEvery { repository.getMovies(1, any(), any()) } returns MovieDetails(data = Data(movies = movies))
        coEvery { repository.getMovies(more(1), any(), any()) } returns MovieDetails(data = Data(movies = emptyList()))

        viewModel = HomeViewModel(repository, userLibraryRepository, preferenceManager, authRepository, tmdbRepository)

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(HomeUiState.Success::class.java)
        val successState = state as HomeUiState.Success
        assertThat(successState.movies).hasSize(1)
        assertThat(successState.movies[0].language).isEqualTo("en")
    }

    @Test
    fun `loadMovies should exclude miscataloged movie 59301 From the Ashes`() = runTest {
        val movies = listOf(
            Movie(id = 59301, title = "From the Ashes", language = "en", rating = "7.4"),
            Movie(id = 1, title = "Valid Movie", language = "en", rating = "7.0")
        )
        coEvery { repository.getMovies(1, any(), any()) } returns MovieDetails(data = Data(movies = movies))
        coEvery { repository.getMovies(more(1), any(), any()) } returns MovieDetails(data = Data(movies = emptyList()))

        viewModel = HomeViewModel(repository, userLibraryRepository, preferenceManager, authRepository, tmdbRepository)

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(HomeUiState.Success::class.java)
        val successState = state as HomeUiState.Success
        assertThat(successState.movies).hasSize(1)
        assertThat(successState.movies[0].id).isEqualTo(1)
    }

    @Test
    fun `refresh should accumulate across multiple full pages when filtering leaves sparse batch`() = runTest {
        every { preferenceManager.getFilteredLanguages() } returns "es"

        // Page 1 has 20 items: 5 English (kept) + 15 Spanish (filtered out)
        val page1 = (1..5).map { Movie(id = it, title = "EN $it", language = "en", rating = "7") } +
            (6..20).map { Movie(id = it, title = "ES $it", language = "es", rating = "7") }
        // Page 2 has 20 items: 15 English (kept) + 5 Spanish (filtered out)
        val page2 = (21..35).map { Movie(id = it, title = "EN $it", language = "en", rating = "7") } +
            (36..40).map { Movie(id = it, title = "ES $it", language = "es", rating = "7") }

        coEvery { repository.getMovies(1, null, 6f) } returns MovieDetails(data = Data(movies = page1))
        coEvery { repository.getMovies(2, null, 6f) } returns MovieDetails(data = Data(movies = page2))

        viewModel = HomeViewModel(repository, userLibraryRepository, preferenceManager, authRepository, tmdbRepository)

        val state = viewModel.uiState.value as HomeUiState.Success
        assertThat(state.movies).hasSize(20)
        coVerify(exactly = 1) { repository.getMovies(1, null, 6f) }
        coVerify(exactly = 1) { repository.getMovies(2, null, 6f) }
    }

    @Test
    fun `refresh should clear existing movies and reload`() = runTest {
        val initialMovies = listOf(Movie(id = 1, title = "EN 1", language = "en", rating = "7"))
        coEvery { repository.getMovies(1, null, 6f) } returns MovieDetails(data = Data(movies = initialMovies))

        viewModel = HomeViewModel(repository, userLibraryRepository, preferenceManager, authRepository, tmdbRepository)
        assertThat(viewModel.uiState.value).isInstanceOf(HomeUiState.Success::class.java)

        val newMovies = listOf(Movie(id = 2, title = "EN 2", language = "en", rating = "7"))
        coEvery { repository.getMovies(1, null, 6f) } returns MovieDetails(data = Data(movies = newMovies))

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(HomeUiState.Success::class.java)
        assertThat((state as HomeUiState.Success).movies[0].id).isEqualTo(2)
        assertThat(state.movies).hasSize(1)
    }

    @Test
    fun `error from repository should update uiState to Error`() = runTest {
        coEvery { repository.getMovies(any(), any(), any()) } throws Exception("Network Error")

        viewModel = HomeViewModel(repository, userLibraryRepository, preferenceManager, authRepository, tmdbRepository)

        assertThat(viewModel.uiState.value).isInstanceOf(HomeUiState.Error::class.java)
        assertThat((viewModel.uiState.value as HomeUiState.Error).message)
            .isEqualTo(UiText.DynamicString("Network Error"))
    }

    @Test
    fun `setQuality should update state and refresh`() = runTest {
        coEvery { repository.getMovies(1, null, any()) } returns MovieDetails(data = Data(movies = listOf(Movie(id = 1, rating = "7"))))
        viewModel = HomeViewModel(repository, userLibraryRepository, preferenceManager, authRepository, tmdbRepository)

        coEvery { repository.getMovies(1, "720p", any()) } returns MovieDetails(data = Data(movies = listOf(Movie(id = 2, rating = "7"))))
        viewModel.setQuality("720p")

        assertThat(viewModel.selectedQuality.value).isEqualTo("720p")
        val state = viewModel.uiState.value
        assertThat((state as HomeUiState.Success).movies[0].id).isEqualTo(2)
    }

    @Test
    fun `toggleFavorite should call repository`() = runTest {
        viewModel = HomeViewModel(repository, userLibraryRepository, preferenceManager, authRepository, tmdbRepository)
        val movie = Movie(id = 1)

        coEvery { repository.isFavorite(1) } returns false
        viewModel.toggleFavorite(movie)
        coVerify { repository.addFavorite(movie) }

        coEvery { repository.isFavorite(1) } returns true
        viewModel.toggleFavorite(movie)
        coVerify { repository.removeFavorite(movie) }
    }

    @Test
    fun `setLastClickedMovieId should update state`() = runTest {
        viewModel = HomeViewModel(repository, userLibraryRepository, preferenceManager, authRepository, tmdbRepository)
        viewModel.setLastClickedMovieId(456)
        assertThat(viewModel.lastClickedMovieId.value).isEqualTo(456)
    }

    @Test
    fun `onMovieFocused should update focusedMovie and resolve TMDB backdrop`() = runTest {
        val movie = Movie(
            id = 10,
            title = "Test Movie",
            imdbCode = "tt1234567",
            backgroundImageOriginal = "http://fallback.com/bg.jpg"
        )
        val tmdbResult = com.martinrevert.latorrentola.model.TMDB.TmdbMovieResult(
            id = 999,
            title = "Test Movie TMDB",
            backdropPath = "/tmdb_backdrop.jpg"
        )
        coEvery { tmdbRepository.findMovieByImdbId("tt1234567") } returns tmdbResult

        viewModel = HomeViewModel(repository, userLibraryRepository, preferenceManager, authRepository, tmdbRepository)
        viewModel.onMovieFocused(movie)

        assertThat(viewModel.focusedMovie.value?.id).isEqualTo(10)
        assertThat(viewModel.focusedMovieBackdrop.value).isEqualTo("http://fallback.com/bg.jpg")

        testScheduler.advanceTimeBy(200)

        assertThat(viewModel.focusedMovieTmdb.value?.id).isEqualTo(999)
        assertThat(viewModel.focusedMovieBackdrop.value).isEqualTo("https://image.tmdb.org/t/p/w1280/tmdb_backdrop.jpg")
    }
}
