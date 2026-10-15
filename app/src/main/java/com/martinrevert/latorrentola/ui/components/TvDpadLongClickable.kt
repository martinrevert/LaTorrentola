package com.martinrevert.latorrentola.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Modifier extension for Android TV `androidx.tv.material3.Surface` elements.
 *
 * Intercepts D-Pad Center / Enter repeat events on TV devices to trigger [onLongClick]
 * and consumes subsequent key release events so key-up does not bleed into newly opened dialogs.
 */
fun Modifier.tvDpadLongClickable(
    onLongClick: (() -> Unit)?
): Modifier {
    if (onLongClick == null) return this

    var isLongPressHandled = false

    return this.onKeyEvent { keyEvent ->
        val nativeEvent = keyEvent.nativeKeyEvent
        val isCenterKey = keyEvent.key == Key.DirectionCenter ||
                keyEvent.key == Key.Enter ||
                keyEvent.key == Key.NumPadEnter

        if (isCenterKey) {
            if (keyEvent.type == KeyEventType.KeyDown) {
                if (nativeEvent.repeatCount > 0 || nativeEvent.isLongPress) {
                    if (!isLongPressHandled) {
                        isLongPressHandled = true
                        onLongClick()
                    }
                    return@onKeyEvent true
                }
            } else if (keyEvent.type == KeyEventType.KeyUp) {
                if (isLongPressHandled) {
                    isLongPressHandled = false
                    return@onKeyEvent true
                }
            }
        }
        false
    }
}
