package com.martinrevert.latorrentola.ui.downloads

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.IconButton as TvIconButton
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.torrent.TorrentDownload
import com.martinrevert.latorrentola.service.TorrentDownloadService
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.isTvDevice

/**
 * Displays the app-managed torrent jobs and their playback/lifecycle actions.
 *
 * @param viewModel Room-backed torrent download state and control actions.
 * @param onBackClick Returns to the previous destination.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
fun TorrentDownloadsScreen(
    viewModel: TorrentDownloadsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val downloads by viewModel.downloads.collectAsState()
    val context = LocalContext.current
    val isTv = context.isTvDevice()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.torrent_downloads_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.focusHighlight(shape = MaterialTheme.shapes.extraLarge)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_desc)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.torrent_downloads_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = if (isTv) 48.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloads, key = TorrentDownload::infoHash) { download ->
                    TorrentDownloadItem(
                        download = download,
                        isTv = isTv,
                        onPauseResume = { viewModel.togglePause(context, download) },
                        onDelete = { viewModel.delete(context, download) },
                        onPlay = { cast ->
                            val intent = Intent(context, TorrentDownloadService::class.java).apply {
                                action = if (download.state == TorrentDownloadService.STATE_READY) {
                                    TorrentDownloadService.ACTION_PLAY_STREAM
                                } else {
                                    TorrentDownloadService.ACTION_DOWNLOAD
                                }
                                putExtra(TorrentDownloadService.EXTRA_INFO_HASH, download.infoHash)
                                putExtra(TorrentDownloadService.EXTRA_CAST_WHEN_READY, cast)
                                if (download.seriesId != null) putExtra(TorrentDownloadService.EXTRA_SERIES_ID, download.seriesId)
                                if (download.seasonNumber != null) putExtra(TorrentDownloadService.EXTRA_SEASON_NUMBER, download.seasonNumber)
                                if (download.episodeNumber != null) putExtra(TorrentDownloadService.EXTRA_EPISODE_NUMBER, download.episodeNumber)
                                if (download.movieId != null) putExtra(TorrentDownloadService.EXTRA_MOVIE_ID, download.movieId)

                                if (download.state != TorrentDownloadService.STATE_READY) {
                                    putExtra(TorrentDownloadService.EXTRA_MAGNET_URI, download.magnetUri)
                                    putExtra(TorrentDownloadService.EXTRA_TITLE, download.title)
                                }
                            }
                            ContextCompat.startForegroundService(context, intent)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Renders progress and controls for one local torrent.
 *
 * @param download Persisted transfer state.
 * @param isTv Whether to use native TV surfaces and actions.
 * @param onPauseResume Toggles the current transfer state.
 * @param onDelete Deletes the job and its private media files.
 * @param onPlay Starts verified local playback through the app's range-checked media server.
 */
@Composable
private fun TorrentDownloadItem(
    download: TorrentDownload,
    isTv: Boolean,
    onPauseResume: () -> Unit,
    onDelete: () -> Unit,
    onPlay: (Boolean) -> Unit
) {
    val completed = download.state == TorrentDownloadService.STATE_COMPLETED
    val playable = completed || download.state == TorrentDownloadService.STATE_READY
    val resumable = download.state == TorrentDownloadService.STATE_PAUSED ||
        download.state == TorrentDownloadService.STATE_FAILED
    val cardContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = download.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(
                    R.string.torrent_download_status,
                    when (download.state) {
                        TorrentDownloadService.STATE_COMPLETED -> stringResource(R.string.torrent_download_state_complete)
                        TorrentDownloadService.STATE_PAUSED -> stringResource(R.string.torrent_download_state_paused)
                        TorrentDownloadService.STATE_FAILED -> stringResource(R.string.torrent_download_state_failed)
                        TorrentDownloadService.STATE_READY -> stringResource(R.string.torrent_download_state_ready)
                        else -> stringResource(R.string.torrent_download_state_active)
                    },
                    download.progressPercent
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!completed) {
                LinearProgressIndicator(
                    progress = { download.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (playable) {
                    if (isTv) {
                        TvIconButton(
                            onClick = { onPlay(false) },
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            TvIcon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.play_desc)
                            )
                        }
                        TvIconButton(
                            onClick = { onPlay(true) },
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            TvIcon(
                                Icons.Default.CastConnected,
                                contentDescription = "Cast"
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { onPlay(false) },
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.play_desc)
                            )
                        }
                        IconButton(
                            onClick = { onPlay(true) },
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            Icon(
                                Icons.Default.CastConnected,
                                contentDescription = "Cast"
                            )
                        }
                    }
                }
                if (!completed) {
                    if (isTv) {
                        TvIconButton(
                            onClick = onPauseResume,
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            TvIcon(
                                if (resumable) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = stringResource(
                                    if (resumable) R.string.torrent_resume else R.string.pause_desc
                                )
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onPauseResume,
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            Icon(
                                if (resumable) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = stringResource(
                                    if (resumable) R.string.torrent_resume else R.string.pause_desc
                                )
                            )
                        }
                    }
                }
                if (isTv) {
                    TvIconButton(
                        onClick = onDelete,
                        modifier = Modifier.focusHighlight(shape = CircleShape)
                    ) {
                        TvIcon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.torrent_delete)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.focusHighlight(shape = CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.torrent_delete)
                        )
                    }
                }
            }
        }
    }

    if (isTv) {
        TvSurface(
            shape = MaterialTheme.shapes.medium,
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            cardContent()
        }
    } else {
        Card(
            modifier = Modifier.focusHighlight(shape = MaterialTheme.shapes.medium),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            cardContent()
        }
    }
}

@com.martinrevert.latorrentola.ui.theme.LightDarkPreviews
@Composable
private fun TorrentDownloadItemPreview() {
    com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme {
        androidx.compose.material3.Surface {
            TorrentDownloadItem(
                download = com.martinrevert.latorrentola.model.torrent.TorrentDownload(
                    infoHash = "HASH1",
                    magnetUri = "magnet:?",
                    title = "Inception (2010)",
                    state = "DOWNLOADING",
                    progressPercent = 50,
                    mediaPath = null,
                    castWhenReady = false,
                    updatedAtMillis = System.currentTimeMillis()
                ),
                isTv = false,
                onPauseResume = {},
                onDelete = {},
                onPlay = {}
            )
        }
    }
}
