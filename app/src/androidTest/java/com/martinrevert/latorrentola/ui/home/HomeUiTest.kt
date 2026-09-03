package com.martinrevert.latorrentola.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.martinrevert.latorrentola.model.YTS.Movie
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class HomeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: HomeViewModel = mockk(relaxed = true)

    @Test
    fun homeScreen_showsMovies_whenSuccessState() {
        val movies = listOf(Movie(id = 1, title = "Test Movie UI"))
        val uiState = HomeUiState.Success(movies)
        
        every { viewModel.uiState } returns MutableStateFlow(uiState)
        every { viewModel.selectedQuality } returns MutableStateFlow(null)
        every { viewModel.topGenres } returns MutableStateFlow(emptyList())
        every { viewModel.isRefreshing } returns MutableStateFlow(false)
        every { viewModel.lastVisitDate } returns MutableStateFlow(null)
        every { viewModel.lastClickedMovieId } returns MutableStateFlow(null)
        every { viewModel.downloadedMovieIds } returns MutableStateFlow(emptySet())
        every { viewModel.favoritesCount } returns MutableStateFlow(0)

        composeTestRule.setContent {
            HomeScreen(
                viewModel = viewModel,
                userPhotoUrl = null,
                onMovieClick = {},
                onSearchClick = {},
                onSettingsClick = {},
                onFavoritesClick = {},
                onGenreClick = {}
            )
        }

        composeTestRule.onNodeWithText("Test Movie UI").assertIsDisplayed()
    }
}
