package com.martinrevert.latorrentola.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.isTvDevice

/**
 * TV-optimized chip component that uses `androidx.tv.material3.Surface` on Android TV / Chromecast
 * for native D-pad focus scaling, high-contrast borders, and focus restoration, while gracefully falling
 * back to Material 3 `FilterChip` on handheld devices.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }

    if (isTv) {
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        Surface(
            onClick = onClick,
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.primary,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                focusedContentColor = MaterialTheme.colorScheme.onPrimary
            ),
            interactionSource = interactionSource,
            modifier = modifier
                .padding(vertical = 4.dp)
                .border(
                    width = if (isFocused) 3.dp else 0.dp,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = MaterialTheme.shapes.small
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                label()
            }
        }
    } else {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            leadingIcon = leadingIcon,
            modifier = modifier.focusHighlight(shape = MaterialTheme.shapes.small)
        )
    }
}
