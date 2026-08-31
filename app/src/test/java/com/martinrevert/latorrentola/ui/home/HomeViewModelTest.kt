package com.martinrevert.latorrentola.ui.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.YTS.Data
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.MovieDetails
import com.martinrevert.latorrentola.network.UserLibraryRepository
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

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: YtsRepository = mockk(relaxed = true)
    private val userLibraryRepository: UserLibraryRepository = mockk(relaxed = true)
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        // Default mocks to prevent crashes during init
        every { userLibraryRepository.getDownloadedMovies() } returns flowOf(emptyList())
        every { repository.getFavoriteMovies() } returns flowOf(emptyList())
        every { repository.getTopGenres(any()) } returns flowOf(emptyList())
        coEvery { repository.getLastVisitDate() } returns null
        // Default to empty list for any page to avoid infinite loops in ViewModel
        coEvery { repository.getMovies(any(), any()) } returns MovieDetails(data = Data(movies = emptyList()))
    }

    @Test
    fun `initial state should be Loading then Success if repository returns movies`() = runTest {
        val movies = listOf(
            Movie(id = 1, title = "English Movie", language = "en"),
            Movie(id = 2, title = "Spanish Movie", language = "es")
        )
        coEvery { repository.getMovies(1, null) } returns MovieDetails(data = Data(movies = movies))

        viewModel = HomeViewModel(repository, userLibraryRepository)

        viewModel.uiState.test {
            // First item might be Loading or Success depending on how fast init runs with UnconfinedTestDispatcher
            val first = awaitItem()
            if (first is HomeUiState.Loading) {
                val second = awaitItem()
                assertThat(second).isInstanceOf(HomeUiState.Success::class.java)
                assertThat((second as HomeUiState.Success).movies).hasSize(1)
                assertThat(second.movies[0].id).isEqualTo(1)
            } else {
                assertThat(first).isInstanceOf(HomeUiState.Success::class.java)
                assertThat((first as HomeUiState.Success).movies).hasSize(1)
            }
        }
    }

    @Test
    fun `loadMovies should filter for English movies only`() = runTest {
        val movies = listOf(
            Movie(id = 1, title = "EN", language = "en"),
            Movie(id = 2, title = "ES", language = "es")
        )
        // Return movies for page 1, then empty for page 2+ to avoid infinite loop in ViewModel
        coEvery { repository.getMovies(1, any()) } returns MovieDetails(data = Data(movies = movies))
        coEvery { repository.getMovies(more(1), any()) } returns MovieDetails(data = Data(movies = emptyList()))

        viewModel = HomeViewModel(repository, userLibraryRepository)
        // init already calls loadMovies()
        
        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(HomeUiState.Success::class.java)
        val successState = state as HomeUiState.Success
        assertThat(successState.movies).hasSize(1)
        assertThat(successState.movies[0].language).isEqualTo("en")
    }

    @Test
    fun `refresh should clear existing movies and reload`() = runTest {
        val initialMovies = listOf(Movie(id = 1, title = "EN 1", language = "en"))
        coEvery { repository.getMovies(1, null) } returns MovieDetails(data = Data(movies = initialMovies))
        
        viewModel = HomeViewModel(repository, userLibraryRepository)
        assertThat(viewModel.uiState.value).isInstanceOf(HomeUiState.Success::class.java)

        val newMovies = listOf(Movie(id = 2, title = "EN 2", language = "en"))
        coEvery { repository.getMovies(1, null) } returns MovieDetails(data = Data(movies = newMovies))

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(HomeUiState.Success::class.java)
        assertThat((state as HomeUiState.Success).movies[0].id).isEqualTo(2)
        assertThat(state.movies).hasSize(1)
    }

    @Test
    fun `error from repository should update uiState to Error`() = runTest {
        coEvery { repository.getMovies(any(), any()) } throws Exception("Network Error")

        viewModel = HomeViewModel(repository, userLibraryRepository)
        // Since init calls loadMovies, it might already be in error state
        
        assertThat(viewModel.uiState.value).isInstanceOf(HomeUiState.Error::class.java)
        assertThat((viewModel.uiState.value as HomeUiState.Error).message).isEqualTo("Network Error")
    }
}
