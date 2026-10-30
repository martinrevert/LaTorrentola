@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieResult
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieSummary
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.utils.GenreTranslation
import com.martinrevert.latorrentola.utils.PreferenceManager
import java.util.Locale

/**
 * Android TV Immersive List container that renders a dynamic high-resolution backdrop
 * and metadata hero preview for the currently focused movie, extending edge-to-edge
 * behind the status bar, top navigation, and filter chips.
 *
 * @param focusedMovie Optional movie card currently possessing D-pad focus (YTS model).
 * @param focusedMovieTmdb Optional enriched metadata and backdrop paths from TMDB.
 * @param focusedTmdbSummary Optional TMDB discovery/OTT movie summary currently possessing D-pad focus.
 * @param backdropUrl High-resolution TMDB or fallback backdrop image URL.
 * @param modifier Layout modifiers applied to the container.
 * @param themeMode Theme mode override for previewing and multi-window rendering.
 * @param topBar Optional top app bar rendered transparently over the backdrop and behind status bar.
 * @param chips Optional filter or category chips rendered over the backdrop.
 * @param content The scrollable catalog list displayed below the hero preview.
 */
@Composable
fun TvMovieImmersiveContainer(
    focusedMovie: Movie? = null,
    focusedMovieTmdb: TmdbMovieResult? = null,
    focusedTmdbSummary: TmdbMovieSummary? = null,
    backdropUrl: String?,
    logoUrl: String? = null,
    isLogoLoading: Boolean = false,
    modifier: Modifier = Modifier,
    themeMode: Int? = null,
    topBar: @Composable (() -> Unit)? = null,
    chips: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }

    TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
        val surfaceColor = MaterialTheme.colorScheme.surface
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
        val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
        val primaryColor = MaterialTheme.colorScheme.primary

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(surfaceColor)
        ) {
            // 1. Dynamic high-resolution backdrop with smooth crossfade spanning edge-to-edge
            Crossfade(
                targetState = backdropUrl,
                animationSpec = tween(durationMillis = 400),
                label = "TvImmersiveBackdropCrossfade"
            ) { url ->
                if (!url.isNullOrBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f)
                            .align(Alignment.TopCenter)
                    )
                }
            }

            // 2. Top gradient scrim for status bar and top navigation legibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                surfaceColor.copy(alpha = 0.70f),
                                surfaceColor.copy(alpha = 0.30f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // 3. Vertical gradient scrim to merge smoothly into the catalog background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
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
                    .fillMaxWidth(0.72f)
                    .fillMaxHeight(0.88f)
                    .align(Alignment.TopStart)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                surfaceColor.copy(alpha = 0.95f),
                                surfaceColor.copy(alpha = 0.65f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // 5. Foreground layout: Top Bar -> Chips -> Hero Pane -> Scrollable Catalog
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (topBar != null) {
                    topBar()
                }

                if (chips != null) {
                    chips()
                }

                // Hero Preview Section
                val title = focusedMovie?.title
                    ?: focusedMovieTmdb?.title
                    ?: focusedTmdbSummary?.title.orEmpty()
                val year = focusedMovie?.year?.toString()
                    ?: focusedMovieTmdb?.releaseYear
                    ?: focusedTmdbSummary?.releaseYear?.takeIf(String::isNotBlank)
                val rating = focusedMovie?.rating?.takeIf(String::isNotBlank)?.let { "★ $it" }
                    ?: focusedMovieTmdb?.voteAverage?.let { "★ ${"%.1f".format(Locale.US, it)}" }
                    ?: focusedTmdbSummary?.voteAverage?.takeIf { it > 0.0 }?.let { "★ ${"%.1f".format(Locale.US, it)}" }
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
                            .height(140.dp)
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        MovieLogoHeader(
                            title = title,
                            logoUrl = logoUrl,
                            isLogoLoading = isLogoLoading,
                            textStyle = MaterialTheme.typography.headlineLarge,
                            textColor = onSurfaceColor,
                            minHeight = 36.dp,
                            maxHeight = 56.dp,
                            maxWidth = 320.dp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Metadata Badges Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!rating.isNullOrBlank()) {
                                Text(
                                    text = rating,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            }
                            if (!year.isNullOrBlank()) {
                                Text(
                                    text = year,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = onSurfaceVariantColor
                                )
                            }
                            if (!runtime.isNullOrBlank()) {
                                Text(
                                    text = runtime,
                                    style = MaterialTheme.typography.labelLarge,
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
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = summaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurfaceVariantColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 700.dp)
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
}

@Preview(device = "id:tv_1080p")
@Composable
private fun TvMovieImmersiveContainerPreview() {
    val sampleMovie = Movie(
        id = 1,
        title = "Inception",
        year = 2010,
        rating = "8.8",
        runtime = "148",
        mpaRating = "PG-13",
        genres = listOf("Action", "Sci-Fi"),
        summary = "A thief who steals corporate secrets through the use of dream-sharing technology is given the inverse task of planting an idea into the mind of a C.E.O."
    )
    TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        TvMovieImmersiveContainer(
            focusedMovie = sampleMovie,
            focusedMovieTmdb = null,
            backdropUrl = null,
            themeMode = PreferenceManager.THEME_DARK
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            )
        }
    }
}

@Preview(name = "Mobile Light Preview")
@Composable
private fun TvMovieImmersiveContainerMobilePreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_LIGHT) {
        TvMovieImmersiveContainer(
            focusedMovie = null,
            backdropUrl = null,
            themeMode = PreferenceManager.THEME_LIGHT
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}
