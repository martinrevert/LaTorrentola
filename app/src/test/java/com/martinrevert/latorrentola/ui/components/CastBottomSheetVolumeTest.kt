package com.martinrevert.latorrentola.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.material3.SliderState
import androidx.compose.ui.input.key.KeyEvent
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class CastBottomSheetVolumeTest {

    private fun createKeyEvent(action: Int, keyCode: Int): KeyEvent {
        val native = mockk<AndroidKeyEvent>(relaxed = true)
        every { native.action } returns action
        every { native.keyCode } returns keyCode
        return KeyEvent(native)
    }

    @Test
    fun `direction right steps by 5 percent of maxVol and notifies callback`() {
        val sliderState = SliderState(value = 10f, trackRange = 0f..100f)
        var reportedVolume: Int? = null
        val event = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_RIGHT)

        val consumed = handleCastVolumePreviewKeyEvent(
            keyEvent = event,
            sliderState = sliderState,
            maxVol = 100,
            onVolumeChanged = { reportedVolume = it }
        )

        assertThat(consumed).isTrue()
        assertThat(sliderState.value).isWithin(0.001f).of(15f)
        assertThat(reportedVolume).isEqualTo(15)
    }

    @Test
    fun `direction left steps by at least 1 unit on typical Cast maxVol 20`() {
        val sliderState = SliderState(value = 10f, trackRange = 0f..20f)
        var reportedVolume: Int? = null
        val event = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_LEFT)

        val consumed = handleCastVolumePreviewKeyEvent(
            keyEvent = event,
            sliderState = sliderState,
            maxVol = 20,
            onVolumeChanged = { reportedVolume = it }
        )

        assertThat(consumed).isTrue()
        assertThat(sliderState.value).isWithin(0.001f).of(9f)
        assertThat(reportedVolume).isEqualTo(9)
    }

    @Test
    fun `direction key up is consumed without changing slider value`() {
        val sliderState = SliderState(value = 10f, trackRange = 0f..20f)
        var callbackCount = 0
        val event = createKeyEvent(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_DPAD_RIGHT)

        val consumed = handleCastVolumePreviewKeyEvent(
            keyEvent = event,
            sliderState = sliderState,
            maxVol = 20,
            onVolumeChanged = { callbackCount++ }
        )

        assertThat(consumed).isTrue()
        assertThat(sliderState.value).isWithin(0.001f).of(10f)
        assertThat(callbackCount).isEqualTo(0)
    }

    @Test
    fun `unrelated key events are not consumed`() {
        val sliderState = SliderState(value = 10f, trackRange = 0f..20f)
        var callbackCount = 0
        val event = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_DOWN)

        val consumed = handleCastVolumePreviewKeyEvent(
            keyEvent = event,
            sliderState = sliderState,
            maxVol = 20,
            onVolumeChanged = { callbackCount++ }
        )

        assertThat(consumed).isFalse()
        assertThat(callbackCount).isEqualTo(0)
    }

    @Test
    fun `syncCastVolumeSliderState ignores external updates while dragging and reconciles after`() {
        val sliderState = SliderState(value = 16f, trackRange = 0f..20f)

        syncCastVolumeSliderState(
            sliderState = sliderState,
            currentVol = 8,
            maxVol = 20,
            isDragging = true
        )
        assertThat(sliderState.value).isWithin(0.001f).of(16f)

        syncCastVolumeSliderState(
            sliderState = sliderState,
            currentVol = 14,
            maxVol = 20,
            isDragging = false
        )
        assertThat(sliderState.value).isWithin(0.001f).of(14f)
    }

    @Test
    fun `castVolumeTarget and castVolumePercent round symmetrically near maxVol`() {
        assertThat(castVolumeTarget(19.6f, maxVol = 20)).isEqualTo(20)
        assertThat(castVolumePercent(19.6f, maxVol = 20)).isEqualTo(100)
        assertThat(castVolumeTarget(19.4f, maxVol = 20)).isEqualTo(19)
        assertThat(castVolumePercent(0.4f, maxVol = 20)).isEqualTo(0)
    }
}
