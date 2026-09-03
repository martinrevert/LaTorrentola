package com.martinrevert.latorrentola.ui.search

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.YTS.Data
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.MovieDetails
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.network.YtsRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: YtsRepository = mockk(relaxed = true)
    private val userLibraryRepository: UserLibraryRepository = mockk(relaxed = true)
    private val preferenceManager: com.martinrevert.latorrentola.utils.PreferenceManager = mockk(relaxed = true)
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        every { userLibraryRepository.getDownloadedMovies() } returns flowOf(emptyList())
        every { preferenceManager.getFilteredLanguages() } returns ""
        every { preferenceManager.filteredLanguagesFlow } returns kotlinx.coroutines.flow.MutableStateFlow("")
        viewModel = SearchViewModel(repository, userLibraryRepository, preferenceManager)
    }

    @Test
    fun `initial state should be Idle`() {
        assertThat(viewModel.uiState.value).isEqualTo(SearchUiState.Idle)
    }

    @Test
    fun `search should update state to Success when results found`() = runTest {
        val movies = listOf(Movie(id = 1, title = "Search Result"))
        coEvery { repository.searchMovies("query", 1, null) } returns MovieDetails(data = Data(movies = movies))

        viewModel.search("query")

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(SearchUiState.Success::class.java)
        assertThat((state as SearchUiState.Success).movies).hasSize(1)
    }

    @Test
    fun `search with empty results should update state to Empty`() = runTest {
        coEvery { repository.searchMovies("empty", 1, null) } returns MovieDetails(data = Data(movies = emptyList()))

        viewModel.search("empty")

        assertThat(viewModel.uiState.value).isEqualTo(SearchUiState.Empty)
    }

    @Test
    fun `searchByGenre should update state to Success`() = runTest {
        val movies = listOf(Movie(id = 1, title = "Genre Movie"))
        coEvery { repository.searchByGenre("Action", 1, null) } returns MovieDetails(data = Data(movies = movies))

        viewModel.searchByGenre("Action")

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(SearchUiState.Success::class.java)
        assertThat((state as SearchUiState.Success).movies[0].title).isEqualTo("Genre Movie")
    }

    @Test
    fun `showFavorites should collect and display favorites`() = runTest {
        val favorites = listOf(Movie(id = 1, title = "Favorite 1"))
        every { repository.getFavoriteMovies() } returns flowOf(favorites)

        viewModel.showFavorites()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(SearchUiState.Success::class.java)
        val success = state as SearchUiState.Success
        assertThat(success.isFavorites).isTrue()
        assertThat(success.movies).hasSize(1)
    }

    @Test
    fun `loadMore should deduplicate and append movies`() = runTest {
        val firstPage = listOf(Movie(id = 1, title = "Movie 1"))
        coEvery { repository.searchMovies("query", 1, null) } returns MovieDetails(data = Data(movies = firstPage))
        
        viewModel.search("query")
        assertThat((viewModel.uiState.value as SearchUiState.Success).movies).hasSize(1)

        val secondPage = listOf(
            Movie(id = 1, title = "Movie 1"), // Duplicate
            Movie(id = 2, title = "Movie 2")  // New
        )
        coEvery { repository.searchMovies("query", 2, null) } returns MovieDetails(data = Data(movies = secondPage))

        viewModel.loadMore()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(SearchUiState.Success::class.java)
        val success = state as SearchUiState.Success
        assertThat(success.movies).hasSize(2)
        assertThat(success.movies.map { it.id }).containsExactly(1, 2)
    }

    @Test
    fun `error should update state to Error`() = runTest {
        coEvery { repository.searchMovies(any(), any(), any()) } throws Exception("Search Error")

        viewModel.search("error")

        assertThat(viewModel.uiState.value).isInstanceOf(SearchUiState.Error::class.java)
        assertThat((viewModel.uiState.value as SearchUiState.Error).message).isEqualTo("Search Error")
    }

    @Test
    fun `loadMore should filter movies based on preferences`() = runTest {
        val movies = listOf(
            Movie(id = 1, title = "EN", language = "en"),
            Movie(id = 2, title = "ES", language = "es")
        )
        every { preferenceManager.getFilteredLanguages() } returns "es"
        coEvery { repository.searchMovies("query", 1, null) } returns MovieDetails(data = Data(movies = movies))

        viewModel.search("query")

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(SearchUiState.Success::class.java)
        val successState = state as SearchUiState.Success
        assertThat(successState.movies).hasSize(1)
        assertThat(successState.movies[0].language).isEqualTo("en")
    }

    @Test
    fun `setQuality should reload results with new quality`() = runTest {
        coEvery { repository.searchMovies("query", 1, null) } returns MovieDetails(data = Data(movies = listOf(Movie(id = 1))))
        viewModel.search("query")
        
        coEvery { repository.searchMovies("query", 1, "720p") } returns MovieDetails(data = Data(movies = listOf(Movie(id = 2))))
        viewModel.setQuality("720p")

        assertThat(viewModel.selectedQuality.value).isEqualTo("720p")
        val state = viewModel.uiState.value
        assertThat((state as SearchUiState.Success).movies[0].id).isEqualTo(2)
    }

    @Test
    fun `resetSearch should return state to Idle`() = runTest {
        viewModel.search("query")
        viewModel.resetSearch()

        assertThat(viewModel.uiState.value).isEqualTo(SearchUiState.Idle)
    }

    @Test
    fun `toggleFavoriteSelection should update selected ids`() {
        viewModel.toggleFavoriteSelection(1)
        assertThat(viewModel.selectedFavoriteIds.value).contains(1)

        viewModel.toggleFavoriteSelection(1)
        assertThat(viewModel.selectedFavoriteIds.value).doesNotContain(1)
    }

    @Test
    fun `clearSelection should empty selected ids`() {
        viewModel.toggleFavoriteSelection(1)
        viewModel.clearSelection()
        assertThat(viewModel.selectedFavoriteIds.value).isEmpty()
    }

    @Test
    fun `deleteSelectedFavorites should call repository for each selected id`() = runTest {
        val movie = Movie(id = 1)
        every { repository.getFavoriteMovies() } returns flowOf(listOf(movie))
        viewModel.showFavorites()
        
        viewModel.toggleFavoriteSelection(1)
        viewModel.deleteSelectedFavorites()

        coVerify { repository.removeFavorite(match { it.id == 1 }) }
        assertThat(viewModel.selectedFavoriteIds.value).isEmpty()
    }

    @Test
    fun `search with empty query should reset search`() = runTest {
        viewModel.search("query")
        viewModel.search("")

        assertThat(viewModel.uiState.value).isEqualTo(SearchUiState.Idle)
    }

    @Test
    fun `removeFavorite should call repository`() = runTest {
        val movie = Movie(id = 1)
        viewModel.removeFavorite(movie)
        coVerify { repository.removeFavorite(movie) }
    }

    @Test
    fun `setLastClickedMovieId should update state`() {
        viewModel.setLastClickedMovieId(123)
        assertThat(viewModel.lastClickedMovieId.value).isEqualTo(123)
    }

    @Test
    fun `observeFilteredLanguages should reload when favorites showing`() = runTest {
        val favorites = listOf(Movie(id = 1, title = "Fav"))
        every { repository.getFavoriteMovies() } returns flowOf(favorites)
        viewModel.showFavorites()
        
        // Trigger language change
        (preferenceManager.filteredLanguagesFlow as MutableStateFlow).value = "en"
        
        // Should trigger showFavorites again
        coVerify(exactly = 2) { repository.getFavoriteMovies() }
    }
}
