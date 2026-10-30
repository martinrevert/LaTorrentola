package com.martinrevert.latorrentola.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.KeyEvent
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class TvDpadGridNavigationTest {

    private val focusManager: FocusManager = mockk(relaxed = true)

    private fun createKeyEvent(action: Int, keyCode: Int): KeyEvent {
        val native = mockk<AndroidKeyEvent>(relaxed = true)
        every { native.action } returns action
        every { native.keyCode } returns keyCode
        return KeyEvent(native)
    }

    @Test
    fun `returns false when isTv is false`() {
        val event = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        val handled = handleTvGridDpadNavigation(
            keyEvent = event,
            isTv = false,
            index = 5,
            totalItems = 20,
            columns = 6,
            focusManager = focusManager
        )
        assertThat(handled).isFalse()
    }

    @Test
    fun `moves focus to next item and returns true when at end of row on TV`() {
        every { focusManager.moveFocus(FocusDirection.Next) } returns true
        val event = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_RIGHT)

        val handled = handleTvGridDpadNavigation(
            keyEvent = event,
            isTv = true,
            index = 5,
            totalItems = 20,
            columns = 6,
            focusManager = focusManager
        )

        assertThat(handled).isTrue()
        verify(exactly = 1) { focusManager.moveFocus(FocusDirection.Next) }
    }

    @Test
    fun `invokes fallback when moveFocus returns false at end of row`() {
        every { focusManager.moveFocus(FocusDirection.Next) } returns false
        var fallbackCalled = false
        val event = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_RIGHT)

        val handled = handleTvGridDpadNavigation(
            keyEvent = event,
            isTv = true,
            index = 11,
            totalItems = 20,
            columns = 6,
            focusManager = focusManager,
            onFallbackNext = { fallbackCalled = true }
        )

        assertThat(handled).isTrue()
        assertThat(fallbackCalled).isTrue()
    }

    @Test
    fun `consumes event on last item to prevent jumping to top bar`() {
        val event = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_RIGHT)

        val handled = handleTvGridDpadNavigation(
            keyEvent = event,
            isTv = true,
            index = 19,
            totalItems = 20,
            columns = 6,
            focusManager = focusManager
        )

        assertThat(handled).isTrue()
        verify(exactly = 0) { focusManager.moveFocus(any()) }
    }

    @Test
    fun `returns false for items not at end of row`() {
        val event = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_RIGHT)

        val handled = handleTvGridDpadNavigation(
            keyEvent = event,
            isTv = true,
            index = 2,
            totalItems = 20,
            columns = 6,
            focusManager = focusManager
        )

        assertThat(handled).isFalse()
        verify(exactly = 0) { focusManager.moveFocus(any()) }
    }

    @Test
    fun `returns false for non-right key events`() {
        val eventDown = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_DOWN)

        val handled = handleTvGridDpadNavigation(
            keyEvent = eventDown,
            isTv = true,
            index = 5,
            totalItems = 20,
            columns = 6,
            focusManager = focusManager
        )

        assertThat(handled).isFalse()
    }
}
