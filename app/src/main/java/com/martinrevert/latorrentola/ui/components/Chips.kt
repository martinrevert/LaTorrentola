package com.martinrevert.latorrentola.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.GenreTranslation
import com.martinrevert.latorrentola.utils.isTvDevice

/**
 * Adaptive Chip component that renders `androidx.tv.material3.Surface` on Android TV
 * for native D-pad focus scaling, and falls back to Material 3 `FilterChip` on handheld devices.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AdaptiveChip(
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
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
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
                CompositionLocalProvider(
                    LocalContentColor provides if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                ) {
                    label()
                }
            }
        }
    } else {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = {
                CompositionLocalProvider(
                    LocalContentColor provides if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                ) {
                    label()
                }
            },
            leadingIcon = leadingIcon,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = MaterialTheme.colorScheme.onSurface,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = modifier.focusHighlight(shape = MaterialTheme.shapes.small)
        )
    }
}

/**
 * Backward compatibility alias for AdaptiveChip.
 */
@Composable
fun TvChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) = AdaptiveChip(
    selected = selected,
    onClick = onClick,
    label = label,
    modifier = modifier,
    leadingIcon = leadingIcon
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QualityChips(
    options: List<String>,
    selectedQuality: String,
    onQualityClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .focusRestorer()
            .padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        lazyItems(options) { quality ->
            val isSelected = selectedQuality == quality
            AdaptiveChip(
                selected = isSelected,
                onClick = { onQualityClick(quality) },
                label = {
                    Text(
                        text = if (quality == "All") stringResource(R.string.quality_all) else quality
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GenreChips(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    onAllGenresClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .focusRestorer()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            AdaptiveChip(
                selected = false,
                onClick = onAllGenresClick,
                label = { Text(stringResource(R.string.all_genres)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
        item {
            AdaptiveChip(
                selected = false,
                onClick = { onGenreClick("nuevas") },
                label = { Text(stringResource(R.string.new_movies)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.NewReleases,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
        item {
            AdaptiveChip(
                selected = false,
                onClick = { onGenreClick("ya_vistas") },
                label = { Text(stringResource(R.string.already_seen)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
        lazyItems(genres) { genre ->
            AdaptiveChip(
                selected = false,
                onClick = { onGenreClick(genre) },
                label = { Text(GenreTranslation.getGenreText(genre).asString()) }
            )
        }
    }
}
