package com.martinrevert.latorrentola.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Evaluates whether a D-pad navigation key event at [index] should be intercepted
 * to advance focus to the next row or consumed to prevent escaping to the top bar.
 *
 * @param keyEvent The incoming key event.
 * @param isTv Whether the current device is a TV.
 * @param index 0-based index of the item within the grid.
 * @param totalItems Total number of items in the grid.
 * @param columns Number of columns in the grid (defaults to 6 for Android TV).
 * @param focusManager FocusManager used to advance focus to the next item.
 * @param onFallbackNext Optional fallback action (e.g. scroll to item) if immediate focus move fails.
 * @return `true` if the event was consumed, `false` otherwise.
 */
fun handleTvGridDpadNavigation(
    keyEvent: KeyEvent,
    isTv: Boolean,
    index: Int,
    totalItems: Int,
    columns: Int = 6,
    focusManager: FocusManager? = null,
    onFallbackNext: (() -> Unit)? = null
): Boolean {
    if (!isTv) return false

    val isDown = keyEvent.type == KeyEventType.KeyDown || keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN
    val isRight = keyEvent.key == Key.DirectionRight || keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT

    if (isDown && isRight) {
        val isEndOfRow = (index + 1) % columns == 0
        val isLastItem = index >= totalItems - 1

        if (isEndOfRow && !isLastItem) {
            val moved = focusManager?.moveFocus(FocusDirection.Next) ?: false
            if (!moved) {
                onFallbackNext?.invoke()
            }
            return true
        } else if (isLastItem) {
            // Consume event on the last item so focus does not escape to the top bar
            return true
        }
    }
    return false
}

/**
 * Modifier extension for TV grid items to handle horizontal row navigation cleanly.
 *
 * When the user reaches the end of a horizontal row in a TV grid (e.g. column 5 in a 6-column grid),
 * pressing D-pad RIGHT intercepts the event before spatial navigation jumps to the top app bar,
 * moving focus down to the first item of the next row.
 * On the very last item of the grid, it consumes D-pad RIGHT so focus does not escape to the top bar.
 */
fun Modifier.tvGridRowDpadNavigation(
    isTv: Boolean,
    index: Int,
    totalItems: Int,
    columns: Int = 6,
    focusManager: FocusManager,
    onFallbackNext: (() -> Unit)? = null
): Modifier {
    if (!isTv) return this

    return this.onPreviewKeyEvent { event ->
        handleTvGridDpadNavigation(
            keyEvent = event,
            isTv = isTv,
            index = index,
            totalItems = totalItems,
            columns = columns,
            focusManager = focusManager,
            onFallbackNext = onFallbackNext
        )
    }
}
