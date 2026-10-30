package com.martinrevert.latorrentola.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.isTvDevice
import kotlinx.coroutines.delay

/**
 * Displays a clean movie poster card with adaptive TV/handheld interaction,
 * watch history progress indicator, and download/selection badges.
 *
 * @param movie Movie metadata rendered by the card.
 * @param onClick Invoked to open the movie when no selection handler overrides the click.
 * @param onLongClick Optional long-press action.
 * @param onToggleSelection Optional click action used in selection mode.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieItem(
    movie: Movie,
    lastVisitDate: Long? = null,
    isDownloaded: Boolean = false,
    isSelected: Boolean = false,
    progressPercent: Int? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onToggleSelection: (() -> Unit)? = null,
    shouldRequestFocus: Boolean = false,
    onFocusRestored: () -> Unit = {},
    isTv: Boolean? = null,
    onFocus: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val effectiveIsTv = remember(context, isTv) { isTv ?: context.isTvDevice() }
    val focusRequester = remember { FocusRequester() }

    val isPreview = LocalInspectionMode.current
    var isImageLoading by remember(movie.mediumCoverImage) { mutableStateOf(!isPreview) }

    val isRecent = remember(movie.dateUploadedUnix) {
        val movieUploadTime = (movie.dateUploadedUnix ?: 0L) * 1000
        val fifteenDaysInMs = 15L * 24 * 60 * 60 * 1000
        movieUploadTime > (System.currentTimeMillis() - fifteenDaysInMs)
    }

    if (shouldRequestFocus) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
                // Focus request might fail if not yet attached
            }
            delay(150)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    Box {
        if (effectiveIsTv) {
            Surface(
                onClick = onToggleSelection ?: onClick,
                onLongClick = onLongClick,
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    focusedContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            onFocus?.invoke()
                            if (shouldRequestFocus) {
                                onFocusRestored()
                            }
                        }
                    }
                    .tvDpadLongClickable(onLongClick)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.67f)
                        .then(if (isImageLoading) Modifier.shimmer() else Modifier)
                ) {
                    AsyncImage(
                        model = movie.mediumCoverImage,
                        contentDescription = movie.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onState = { state ->
                            isImageLoading = state is AsyncImagePainter.State.Loading
                        }
                    )

                    if (isRecent) {
                        Icon(
                            painter = painterResource(R.drawable.new_badge),
                            contentDescription = stringResource(R.string.new_desc),
                            tint = Color.Yellow,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .size(32.dp)
                                .rotate(-45f)
                        )
                    }

                    if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = stringResource(R.string.downloaded_desc),
                            tint = Color.Yellow,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(24.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    CircleShape
                                )
                                .padding(2.dp)
                        )
                    }

                    WatchProgressBar(
                        progressPercent = progressPercent,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused && shouldRequestFocus) {
                            onFocusRestored()
                        }
                    }
                    .adaptiveLongClickable(
                        onClick = onToggleSelection ?: onClick,
                        onLongClick = onLongClick
                    )
                    .focusHighlight(shape = MaterialTheme.shapes.medium),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.67f)
                        .then(if (isImageLoading) Modifier.shimmer() else Modifier)
                ) {
                    AsyncImage(
                        model = movie.mediumCoverImage,
                        contentDescription = movie.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onState = { state ->
                            isImageLoading = state is AsyncImagePainter.State.Loading
                        }
                    )

                    if (isRecent) {
                        Icon(
                            painter = painterResource(R.drawable.new_badge),
                            contentDescription = stringResource(R.string.new_desc),
                            tint = Color.Yellow,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .size(32.dp)
                                .rotate(-45f)
                        )
                    }

                    if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = stringResource(R.string.downloaded_desc),
                            tint = Color.Yellow,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(24.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    CircleShape
                                )
                                .padding(2.dp)
                        )
                    }

                    WatchProgressBar(
                        progressPercent = progressPercent,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            )
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.selected_desc),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
            )
        }
    }
}

@com.martinrevert.latorrentola.ui.theme.LightDarkPreviews
@Composable
private fun MovieItemPreview() {
    com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme {
        androidx.compose.material3.Surface {
            Box(modifier = Modifier.size(160.dp, 240.dp)) {
                MovieItem(
                    movie = com.martinrevert.latorrentola.model.YTS.Movie(
                        id = 1,
                        title = "Inception",
                        year = 2010,
                        rating = "8.8",
                        mediumCoverImage = "",
                        imdbCode = "tt1375666"
                    ),
                    onClick = {}
                )
            }
        }
    }
}
