package com.martinrevert.latorrentola.ui.search

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.martinrevert.latorrentola.model.YTS.Movie
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class SearchUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: SearchViewModel = mockk(relaxed = true)

    @Test
    fun searchScreen_showsIdleMessage() {
        every { viewModel.uiState } returns MutableStateFlow(SearchUiState.Idle)
        every { viewModel.selectedQuality } returns MutableStateFlow(null)
        every { viewModel.lastClickedMovieId } returns MutableStateFlow(null)
        every { viewModel.downloadedMovieIds } returns MutableStateFlow(emptySet())
        every { viewModel.selectedFavoriteIds } returns MutableStateFlow(emptySet())

        composeTestRule.setContent {
            SearchScreen(
                viewModel = viewModel,
                onMovieClick = {},
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText("Start searching...").assertIsDisplayed()
    }

    @Test
    fun searchScreen_showsEmptyMessage() {
        every { viewModel.uiState } returns MutableStateFlow(SearchUiState.Empty)
        every { viewModel.selectedQuality } returns MutableStateFlow(null)
        every { viewModel.lastClickedMovieId } returns MutableStateFlow(null)
        every { viewModel.downloadedMovieIds } returns MutableStateFlow(emptySet())
        every { viewModel.selectedFavoriteIds } returns MutableStateFlow(emptySet())

        composeTestRule.setContent {
            SearchScreen(
                viewModel = viewModel,
                onMovieClick = {},
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText("No results found").assertIsDisplayed()
    }

    @Test
    fun searchScreen_showsResults_whenSuccessState() {
        val movies = listOf(Movie(id = 1, title = "Search Result UI"))
        every { viewModel.uiState } returns MutableStateFlow(SearchUiState.Success(movies))
        every { viewModel.selectedQuality } returns MutableStateFlow(null)
        every { viewModel.lastClickedMovieId } returns MutableStateFlow(null)
        every { viewModel.downloadedMovieIds } returns MutableStateFlow(emptySet())
        every { viewModel.selectedFavoriteIds } returns MutableStateFlow(emptySet())

        composeTestRule.setContent {
            SearchScreen(
                viewModel = viewModel,
                onMovieClick = {},
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText("Search Result UI").assertIsDisplayed()
    }
}
