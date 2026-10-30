@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Renders a movie logo when available from TMDB, and falls back to the movie title in text
 * only if no logo exists or fails to load. Does not display text while a logo is loading
 * to prevent text flashing.
 *
 * @param title Movie title string used for text fallback and content description.
 * @param logoUrl High-resolution SVG/PNG logo URL from TMDB, or null if not yet loaded or unavailable.
 * @param isLogoLoading Whether a network request to resolve the movie logo is in flight.
 * @param modifier Layout modifiers applied to the container box.
 * @param textStyle Typography applied to the title text fallback.
 * @param textColor Color applied to the title text fallback.
 * @param minHeight Minimum height for the logo image.
 * @param maxHeight Maximum height for the logo image.
 * @param maxWidth Maximum width for the logo image.
 * @param alignment Alignment of the logo or text within the box.
 * @param maxLines Maximum lines for the title text fallback.
 */
@Composable
fun MovieLogoHeader(
    title: String,
    logoUrl: String?,
    isLogoLoading: Boolean,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.headlineLarge,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    minHeight: Dp = 36.dp,
    maxHeight: Dp = 56.dp,
    maxWidth: Dp = 320.dp,
    alignment: Alignment = Alignment.CenterStart,
    maxLines: Int = 1
) {
    var imageLoaded by remember(logoUrl) { mutableStateOf(false) }
    var imageError by remember(logoUrl) { mutableStateOf(false) }

    val hasLogo = !logoUrl.isNullOrBlank()
    val shouldShowLogo = hasLogo && !imageError
    val shouldShowText = !isLogoLoading && (!hasLogo || imageError)

    val logoAlpha by animateFloatAsState(
        targetValue = if (imageLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "MovieLogoAlpha"
    )

    Box(
        modifier = modifier.heightIn(min = minHeight, max = maxHeight),
        contentAlignment = alignment
    ) {
        if (shouldShowLogo) {
            AsyncImage(
                model = logoUrl,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                alignment = alignment,
                onSuccess = { imageLoaded = true },
                onError = { imageError = true },
                modifier = Modifier
                    .heightIn(min = minHeight, max = maxHeight)
                    .widthIn(max = maxWidth)
                    .graphicsLayer { this.alpha = logoAlpha }
            )
        }

        if (shouldShowText && title.isNotBlank()) {
            Text(
                text = title,
                style = textStyle,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
