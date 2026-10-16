package com.martinrevert.latorrentola.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Modifier extension supporting touch long-press (Mobile) and D-pad Center long-press (Android TV).
 *
 * Intercepts D-pad Center / Enter repeat events on TV devices to trigger [onLongClick]
 * before key release triggers [onClick]. On touch screens, uses [combinedClickable].
 */
fun Modifier.adaptiveLongClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true
): Modifier {
    if (onLongClick == null) {
        return this.clickable(enabled = enabled, onClick = onClick)
    }

    var isLongPressHandled = false

    return this
        .onKeyEvent { keyEvent ->
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
        .combinedClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick
        )
}
