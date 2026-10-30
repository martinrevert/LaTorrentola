package com.martinrevert.latorrentola.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.martinrevert.latorrentola.utils.isTvDevice

/** Applies an animated theme-aware shimmer gradient to a modifier's content. */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    background(brush)
}

/**
 * Displays a clean poster-shaped loading placeholder for the current device class.
 * Matches the aspect ratio and shape of MovieItem without metadata area.
 *
 * @param isTv Whether to use the TV card layout.
 * @param modifier Modifier applied to the placeholder card.
 */
@Composable
fun MovieItemPlaceholder(
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    if (isTv) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(0.67f)
                .clip(MaterialTheme.shapes.medium)
                .shimmer()
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f)
                    .shimmer()
            )
        }
    }
}

/**
 * Displays a grid of movie poster placeholders while list content is loading.
 *
 * @param contentPadding Padding matching the corresponding loaded grid.
 * @param modifier Modifier applied to the placeholder grid.
 */
@Composable
fun MovieListPlaceholder(
    contentPadding: PaddingValues = PaddingValues(16.dp),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val columns = when {
        isTv -> GridCells.Fixed(6)
        screenWidth < 600.dp -> GridCells.Fixed(2)
        screenWidth < 900.dp -> GridCells.Adaptive(minSize = 160.dp)
        else -> GridCells.Adaptive(minSize = 200.dp)
    }

    LazyVerticalGrid(
        columns = columns,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false
    ) {
        items(12) {
            MovieItemPlaceholder(isTv = isTv)
        }
    }
}

/**
 * Displays a loading skeleton matching the adaptive movie-detail layout for TV and mobile.
 */
@Composable
fun MovieDetailPlaceholder(
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600 || isTv

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
    ) {
        if (isTv) {
            // TV Hero Section: Poster on the left, Title/Chips/Director/Actions/Overview on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Movie Poster Card
                Box(
                    modifier = Modifier
                        .width(190.dp)
                        .aspectRatio(2 / 3f)
                        .clip(MaterialTheme.shapes.medium)
                        .shimmer()
                )

                // Details Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Top
                ) {
                    // Title
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .shimmer()
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Metadata chips row (Year, Rating, Runtime, Genres)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.width(55.dp).height(24.dp).clip(CircleShape).shimmer())
                        Box(modifier = Modifier.width(65.dp).height(24.dp).clip(CircleShape).shimmer())
                        Box(modifier = Modifier.width(70.dp).height(24.dp).clip(CircleShape).shimmer())
                        Box(modifier = Modifier.width(90.dp).height(24.dp).clip(CircleShape).shimmer())
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    // Director and Studio row
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmer()
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    // Action buttons row (Trailer, Favorite, Share)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.width(140.dp).height(44.dp).clip(CircleShape).shimmer())
                        Box(modifier = Modifier.width(180.dp).height(44.dp).clip(CircleShape).shimmer())
                        Box(modifier = Modifier.width(140.dp).height(44.dp).clip(CircleShape).shimmer())
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    // Overview lines
                    Box(modifier = Modifier.fillMaxWidth(0.95f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth(0.90f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth(0.70f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Cast Section
            Box(modifier = Modifier.width(100.dp).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(7) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(105.dp)
                    ) {
                        Box(modifier = Modifier.size(72.dp).clip(CircleShape).shimmer())
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.width(68.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.width(52.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Torrents Section
            Box(modifier = Modifier.width(120.dp).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .shimmer()
                    )
                }
            }
        } else if (isWideScreen) {
            // Handheld / Tablet wide layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .aspectRatio(16 / 9f)
                        .clip(MaterialTheme.shapes.medium)
                        .shimmer()
                )
                Column(modifier = Modifier.weight(0.4f)) {
                    Box(modifier = Modifier.fillMaxWidth(0.5f).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    Spacer(modifier = Modifier.height(16.dp))
                    repeat(4) {
                        Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    repeat(3) {
                        Box(modifier = Modifier.width(100.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.width(100.dp).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(6) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(70.dp).clip(CircleShape).shimmer())
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.width(60.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.width(120.dp).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .shimmer()
                    )
                }
            }
        } else {
            // Handheld phone portrait layout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
                    .clip(MaterialTheme.shapes.medium)
                    .shimmer()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth(0.4f).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            Spacer(modifier = Modifier.height(12.dp))
            repeat(3) {
                Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Box(modifier = Modifier.fillMaxWidth(0.4f).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            Spacer(modifier = Modifier.height(12.dp))
            repeat(3) {
                Box(modifier = Modifier.width(120.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.width(100.dp).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(70.dp).clip(CircleShape).shimmer())
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.width(60.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.width(120.dp).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .shimmer()
                    )
                }
            }
        }
    }
}

@com.martinrevert.latorrentola.ui.theme.LightDarkPreviews
@Composable
private fun MovieListPlaceholderPreview() {
    com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme {
        androidx.compose.material3.Surface {
            MovieListPlaceholder()
        }
    }
}

@com.martinrevert.latorrentola.ui.theme.LightDarkPreviews
@Composable
private fun MovieDetailPlaceholderPreview() {
    com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme {
        androidx.compose.material3.Surface {
            MovieDetailPlaceholder()
        }
    }
}
