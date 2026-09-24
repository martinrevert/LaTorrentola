package com.martinrevert.latorrentola.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.GenreTranslation
import com.martinrevert.latorrentola.utils.isTvDevice
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieItem(
    movie: Movie,
    lastVisitDate: Long? = null,
    isDownloaded: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onToggleSelection: (() -> Unit)? = null,
    shouldRequestFocus: Boolean = false,
    onFocusRestored: () -> Unit = {}
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val focusRequester = remember { FocusRequester() }

    val isPreview = LocalInspectionMode.current
    var isImageLoading by remember { mutableStateOf(!isPreview) }

    LaunchedEffect(shouldRequestFocus) {
        if (shouldRequestFocus) {
            delay(300)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus request might fail if not attached
            }
        }
    }

    Box {
        if (isTv) {
            Surface(
                onClick = onToggleSelection ?: onClick,
                onLongClick = onLongClick,
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused && shouldRequestFocus) {
                            onFocusRestored()
                        }
                    }
            ) {
                Column {
                    Box {
                        AsyncImage(
                            model = movie.mediumCoverImage,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.67f),
                            contentScale = ContentScale.Crop,
                            onState = { state ->
                                isImageLoading = state is AsyncImagePainter.State.Loading
                            }
                        )

                        val movieUploadTime = (movie.dateUploadedUnix ?: 0L) * 1000
                        val fifteenDaysInMs = 15L * 24 * 60 * 60 * 1000
                        val isRecent = movieUploadTime > (System.currentTimeMillis() - fifteenDaysInMs)

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
                    }
                    Column(
                        modifier = Modifier
                            .background(androidx.tv.material3.MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = movie.title ?: "",
                            style = androidx.tv.material3.MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!movie.genres.isNullOrEmpty()) {
                            val translatedGenres = movie.genres.map { GenreTranslation.getGenreText(it).asString() }
                            Text(
                                text = translatedGenres.joinToString(", "),
                                style = androidx.tv.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.tv.material3.MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${movie.year}",
                                style = androidx.tv.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.tv.material3.MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "⭐ ${movie.rating}",
                                style = androidx.tv.material3.MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = androidx.tv.material3.MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
                    .focusHighlight(shape = MaterialTheme.shapes.medium)
                    .semantics(mergeDescendants = true) { }
                    .combinedClickable(
                        onClick = onToggleSelection ?: onClick,
                        onLongClick = onLongClick
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Box {
                    Column {
                        Box {
                            AsyncImage(
                                model = movie.mediumCoverImage,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.67f),
                                contentScale = ContentScale.Crop,
                                onState = { state ->
                                    isImageLoading = state is AsyncImagePainter.State.Loading
                                }
                            )

                            val movieUploadTime = (movie.dateUploadedUnix ?: 0L) * 1000
                            val fifteenDaysInMs = 15L * 24 * 60 * 60 * 1000
                            val isRecent = movieUploadTime > (System.currentTimeMillis() - fifteenDaysInMs)

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
                        }
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Text(
                                text = movie.title ?: "",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (!movie.genres.isNullOrEmpty()) {
                                val translatedGenres = movie.genres.map { GenreTranslation.getGenreText(it).asString() }
                                Text(
                                    text = translatedGenres.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${movie.year}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = {
                                            val imdbUrl = "https://www.imdb.com/title/${movie.imdbCode}"
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, movie.title)
                                                val shareText = context.getString(
                                                    R.string.share_movie_text,
                                                    movie.title,
                                                    imdbUrl
                                                )
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, context.getString(
                                                R.string.share_movie_chooser)))
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = "${stringResource(R.string.share_desc)} ${movie.title ?: ""}",
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "⭐ ${movie.rating}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
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
                                .background(Color.White, CircleShape)
                        )
                    }
                }
            }
        }
        if (isImageLoading) {
            MovieItemPlaceholder(isTv = isTv)
        }
    }
}
