package com.martinrevert.latorrentola.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerBottomSheetStateUnitTest {

    @Test
    fun `player bottom sheet state tracks stores properties correctly`() {
        val state = PlayerBottomSheetState.Tracks(
            type = PlaybackTrackType.SUBTITLE,
            title = "Subtitles",
            options = emptyList(),
            selectedIndex = 0,
            onSelected = {}
        )

        assertThat(state.type).isEqualTo(PlaybackTrackType.SUBTITLE)
        assertThat(state.title).isEqualTo("Subtitles")
        assertThat(state.options).isEmpty()
        assertThat(state.selectedIndex).isEqualTo(0)
    }

    @Test
    fun `player bottom sheet state message stores properties correctly`() {
        val state = PlayerBottomSheetState.Message(
            title = "Error",
            message = "Network failed",
            onDismiss = {}
        )

        assertThat(state.title).isEqualTo("Error")
        assertThat(state.message).isEqualTo("Network failed")
    }
}
