package com.martinrevert.latorrentola.ui.search

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.YTS.Data
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.MovieDetails
import com.martinrevert.latorrentola.network.YtsRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: YtsRepository = mockk(relaxed = true)
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        viewModel = SearchViewModel(repository)
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
}
