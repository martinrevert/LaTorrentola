package com.martinrevert.latorrentola.ui.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import org.junit.Rule
import org.junit.Test

class PlayerDialogFocusTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playerDialogContent_tracksState_displaysOptionsAndCancel() {
        var dismissed = false
        val options = listOf(
            PlaybackTrackOption(
                label = "English Audio",
                group = null,
                trackIndex = 0,
                castTrackId = null,
                isSelected = true
            )
        )

        val state = PlayerDialogState.Tracks(
            type = PlaybackTrackType.AUDIO,
            title = "Audio Tracks",
            options = options,
            selectedIndex = 0,
            onSelected = {}
        )

        composeTestRule.setContent {
            LaTorrentolaTheme {
                PlayerDialogContent(
                    state = state,
                    onDismiss = { dismissed = true }
                )
            }
        }

        // Verify title and option are displayed
        composeTestRule.onNodeWithText("Audio Tracks").assertIsDisplayed()
        composeTestRule.onNodeWithText("English Audio").assertIsDisplayed()
    }

    @Test
    fun playerDialogContent_messageState_displaysMessageAndOk() {
        val state = PlayerDialogState.Message(
            title = "OpenSubtitles",
            message = "No subtitles found.",
            onDismiss = {}
        )

        composeTestRule.setContent {
            LaTorrentolaTheme {
                PlayerDialogContent(
                    state = state,
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("OpenSubtitles").assertIsDisplayed()
        composeTestRule.onNodeWithText("No subtitles found.").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsDisplayed()
    }
}
