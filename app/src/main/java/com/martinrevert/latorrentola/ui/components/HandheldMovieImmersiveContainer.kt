package com.martinrevert.latorrentola.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieResult
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieSummary
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.utils.GenreTranslation
import java.util.Locale

/**
 * Immersive hero header and catalog container for handheld tablets and unfolded foldables.
 *
 * Implements Material 3 styling with dynamic high-resolution TMDB backdrops,
 * directional scrims for text legibility, title logo header, rich metadata badges,
 * and direct action controls above the movie catalog.
 *
 * @param focusedMovie Currently selected movie data.
 * @param focusedTmdbSummary Optional TMDB discovery/OTT movie summary possessing focus.
 * @param focusedMovieTmdb Optional enriched metadata and backdrop paths from TMDB.
 * @param backdropUrl High-resolution TMDB or fallback backdrop URL.
 * @param logoUrl Resolved transparent title logo URL from TMDB.
 * @param isLogoLoading Whether logo resolution is in flight.
 * @param onDetailsClick Invoked when the user taps "Details" in the hero header.
 * @param modifier Layout modifiers.
 * @param topBar Optional top application bar composable.
 * @param chips Optional filter or category chips composable.
 * @param content The scrollable catalog grid displayed below the hero header.
 */
@Composable
fun HandheldMovieImmersiveContainer(
    focusedMovie: Movie? = null,
    focusedTmdbSummary: TmdbMovieSummary? = null,
    focusedMovieTmdb: TmdbMovieResult? = null,
    backdropUrl: String?,
    logoUrl: String?,
    isLogoLoading: Boolean,
    onDetailsClick: () -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable (() -> Unit)? = null,
    chips: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 600

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        // 1. Dynamic high-resolution backdrop with smooth crossfade
        Crossfade(
            targetState = backdropUrl,
            animationSpec = tween(durationMillis = 400),
            label = "HandheldImmersiveBackdropCrossfade"
        ) { url ->
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(if (isCompact) 0.65f else 0.72f)
                        .align(Alignment.TopCenter)
                )
            }
        }

        // 2. Top gradient scrim for status bar and top navigation legibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            surfaceColor.copy(alpha = 0.75f),
                            surfaceColor.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 3. Vertical gradient scrim to merge smoothly into the catalog background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(if (isCompact) 0.68f else 0.75f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            surfaceColor.copy(alpha = 0.50f),
                            surfaceColor
                        )
                    )
                )
        )

        // 4. Horizontal gradient scrim for text, badges, and chips legibility
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isCompact) 0.88f else 0.75f)
                .fillMaxHeight(if (isCompact) 0.68f else 0.75f)
                .align(Alignment.TopStart)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            surfaceColor.copy(alpha = 0.95f),
                            surfaceColor.copy(alpha = 0.70f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 5. Foreground layout: Top Bar -> Chips -> Hero Pane -> Scrollable Catalog
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            if (topBar != null) {
                topBar()
            }

            if (chips != null) {
                chips()
            }

            // Hero Preview Section
            val title = focusedMovie?.title
                ?: focusedTmdbSummary?.title
                ?: focusedMovieTmdb?.title.orEmpty()
            val year = focusedMovie?.year?.toString()
                ?: focusedTmdbSummary?.releaseYear?.takeIf(String::isNotBlank)
                ?: focusedMovieTmdb?.releaseYear
            val rating = focusedMovie?.rating?.takeIf(String::isNotBlank)?.let { "★ $it" }
                ?: focusedTmdbSummary?.voteAverage?.takeIf { it > 0.0 }?.let { "★ ${"%.1f".format(Locale.US, it)}" }
                ?: focusedMovieTmdb?.voteAverage?.let { "★ ${"%.1f".format(Locale.US, it)}" }
            val runtime = focusedMovie?.runtime?.takeIf(String::isNotBlank)?.let { "$it min" }
            val mpaRating = focusedMovie?.mpaRating?.takeIf(String::isNotBlank)
            val genresText = remember(focusedMovie?.genres, resources) {
                focusedMovie?.genres
                    ?.take(3)
                    ?.joinToString(" · ") { GenreTranslation.getGenreText(it).asString(resources) }
                    .orEmpty()
            }
            val summaryText = focusedMovieTmdb?.overview?.takeIf(String::isNotBlank)
                ?: focusedTmdbSummary?.overview?.takeIf(String::isNotBlank)
                ?: focusedMovie?.descriptionFull?.takeIf(String::isNotBlank)
                ?: focusedMovie?.summary.orEmpty()

            if (title.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isCompact) 16.dp else 24.dp,
                            vertical = if (isCompact) 4.dp else 6.dp
                        )
                ) {
                    MovieLogoHeader(
                        title = title,
                        logoUrl = logoUrl,
                        isLogoLoading = isLogoLoading,
                        textStyle = if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                        textColor = onSurfaceColor,
                        minHeight = if (isCompact) 26.dp else 32.dp,
                        maxHeight = if (isCompact) 42.dp else 52.dp,
                        maxWidth = if (isCompact) 280.dp else 340.dp,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Metadata Badges Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 10.dp)
                    ) {
                        if (!rating.isNullOrBlank()) {
                            Text(
                                text = rating,
                                style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        }
                        if (!year.isNullOrBlank()) {
                            Text(
                                text = year,
                                style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                                color = onSurfaceVariantColor
                            )
                        }
                        if (!runtime.isNullOrBlank()) {
                            Text(
                                text = runtime,
                                style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                                color = onSurfaceVariantColor
                            )
                        }
                        if (!mpaRating.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = onSurfaceVariantColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = mpaRating,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = onSurfaceColor
                                )
                            }
                        }
                        if (genresText.isNotBlank()) {
                            Text(
                                text = genresText,
                                style = MaterialTheme.typography.labelMedium,
                                color = onSurfaceVariantColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (summaryText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = summaryText,
                            style = if (isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                            color = onSurfaceVariantColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 720.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = onDetailsClick,
                        contentPadding = PaddingValues(
                            horizontal = if (isCompact) 14.dp else 18.dp,
                            vertical = if (isCompact) 6.dp else 8.dp
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(if (isCompact) 16.dp else 18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.details_title),
                            style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Remaining catalog area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                content()
            }
        }
    }
}
