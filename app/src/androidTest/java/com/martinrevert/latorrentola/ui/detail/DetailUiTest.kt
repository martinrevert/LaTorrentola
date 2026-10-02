package com.martinrevert.latorrentola.ui.detail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.Torrent
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

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
        every { viewModel.torrentHandlingMode } returns
            MutableStateFlow(TorrentHandlingMode.EXTERNAL_CLIENT)

        composeTestRule.setContent {
            MovieDetailScreen(
                viewModel = viewModel,
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText("Detail Movie UI").assertIsDisplayed()
        composeTestRule.onNodeWithText("A great movie summary").assertIsDisplayed()
    }

    @Test
    fun torrentButton_invokesTorrentClickCallback() {
        val movie = Movie(id = 1, title = "Torrent Movie")
        val torrent = Torrent(
            quality = "1080p",
            size = "2.2 GB",
            type = "bluray",
            hash = "HASH1"
        )
        var clickedTorrent: Torrent? = null

        composeTestRule.setContent {
            LaTorrentolaTheme {
                TorrentItem(
                    movie = movie,
                    torrent = torrent,
                    isDownloaded = false,
                    onTorrentClick = { clickedTorrent = it }
                )
            }
        }

        composeTestRule.onNodeWithText("1080p - 2.2 GB (bluray)").performClick()

        assertEquals(torrent, clickedTorrent)
    }
}
