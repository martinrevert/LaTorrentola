package com.martinrevert.latorrentola.ui.detail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.martinrevert.latorrentola.model.YTS.Movie
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class DetailUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: DetailViewModel = mockk(relaxed = true)

    @Test
    fun detailScreen_showsMovieInfo_whenSuccessState() {
        val movie = Movie(id = 1, title = "Detail Movie UI", summary = "A great movie summary")
        val uiState = DetailUiState.Success(movie, isFavorite = true)
        
        every { viewModel.uiState } returns MutableStateFlow(uiState)
        every { viewModel.downloadedHashes } returns MutableStateFlow(emptySet())

        composeTestRule.setContent {
            MovieDetailScreen(
                viewModel = viewModel,
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText("Detail Movie UI").assertIsDisplayed()
        composeTestRule.onNodeWithText("A great movie summary").assertIsDisplayed()
    }
}
