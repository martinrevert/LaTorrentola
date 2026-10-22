@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.downloads

import android.content.ComponentName
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.IconButton as TvIconButton
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.torrent.TorrentDownload
import com.martinrevert.latorrentola.service.PlaybackService
import com.martinrevert.latorrentola.service.TorrentDownloadService
import com.martinrevert.latorrentola.ui.components.CastBottomSheet
import com.martinrevert.latorrentola.ui.components.CastCodecWarningObserver
import com.martinrevert.latorrentola.ui.player.LocalPlayerActivity
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.isTvDevice

/**
 * Displays the app-managed torrent jobs and their playback/lifecycle actions.
 *
 * @param viewModel Room-backed torrent download state and control actions.
 * @param onBackClick Returns to the previous destination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentDownloadsScreen(
    viewModel: TorrentDownloadsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val downloads by viewModel.downloads.collectAsState()
    val context = LocalContext.current
    val isTv = context.isTvDevice()
    var showCastBottomSheet by remember { mutableStateOf(false) }

    // Connect to MediaController to track active playback
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            mediaController?.let {
                MediaController.releaseFuture(controllerFuture)
            }
        }
    }

    // Observe active playback item and playing state from MediaController
    var activeMediaId by remember { mutableStateOf("") }
    var activeMediaTitle by remember { mutableStateOf("") }
    var isMediaPlaying by remember { mutableStateOf(false) }

    DisposableEffect(mediaController) {
        val controller = mediaController ?: return@DisposableEffect onDispose {}
        fun update() {
            activeMediaId = controller.currentMediaItem?.mediaId.orEmpty()
            activeMediaTitle = controller.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
            isMediaPlaying = controller.isPlaying
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { update() }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { update() }
            override fun onPlaybackStateChanged(playbackState: Int) { update() }
        }
        controller.addListener(listener)
        update()
        onDispose {
            controller.removeListener(listener)
        }
    }

    // Observe Cast connection state via MediaRouter
    val mediaRouter = remember { MediaRouter.getInstance(context) }
    val selector = remember {
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
    }
    var selectedRoute by remember { mutableStateOf(mediaRouter.selectedRoute) }
    var isCasting by remember { mutableStateOf(!mediaRouter.selectedRoute.isDefault) }

    DisposableEffect(Unit) {
        val callback = object : MediaRouter.Callback() {
            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
                selectedRoute = route
                isCasting = !route.isDefault
            }
            override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
                selectedRoute = router.selectedRoute
                isCasting = !router.selectedRoute.isDefault
            }
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                selectedRoute = router.selectedRoute
                isCasting = !router.selectedRoute.isDefault
            }
        }
        mediaRouter.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        selectedRoute = mediaRouter.selectedRoute
        isCasting = !mediaRouter.selectedRoute.isDefault
        onDispose {
            mediaRouter.removeCallback(callback)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.torrent_downloads_title)) },
                actions = {
                    if (!isTv) {
                        IconButton(
                            onClick = { showCastBottomSheet = true }
                        ) {
                            Icon(
                                imageVector = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                                contentDescription = stringResource(R.string.cast_desc),
                                tint = if (isCasting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
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
        },
        bottomBar = {
            // Persistent Mini-Player Bar when playback or casting is active
            if (activeMediaTitle.isNotBlank() && (isMediaPlaying || isCasting)) {
                Surface(
                    onClick = {
                        val intent = Intent(context, LocalPlayerActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        }
                        context.startActivity(intent)
                    },
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCasting) Icons.Default.Tv else Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeMediaTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isCasting) {
                                    stringResource(R.string.mini_player_casting, selectedRoute.name)
                                } else {
                                    stringResource(R.string.mini_player_local)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                if (isMediaPlaying) mediaController?.pause() else mediaController?.play()
                            }
                        ) {
                            Icon(
                                imageVector = if (isMediaPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (isMediaPlaying) R.string.pause_desc else R.string.play_desc
                                )
                            )
                        }
                        IconButton(
                            onClick = {
                                mediaController?.stop()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_stop)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (downloads.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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
                        .padding(horizontal = if (isTv) 48.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(downloads, key = TorrentDownload::infoHash) { download ->
                        val isActive = activeMediaTitle.isNotBlank() && (
                            activeMediaId == download.infoHash ||
                            activeMediaId == download.movieId?.toString() ||
                            activeMediaId.contains(download.infoHash, ignoreCase = true) ||
                            activeMediaTitle.equals(download.title, ignoreCase = true)
                        )

                        TorrentDownloadItem(
                            download = download,
                            isTv = isTv,
                            isActive = isActive,
                            isPlaying = isMediaPlaying,
                            isCasting = isCasting,
                            activeRouteName = selectedRoute.name,
                            onToggleActivePlayback = {
                                if (isMediaPlaying) mediaController?.pause() else mediaController?.play()
                            },
                            onPauseResume = { viewModel.togglePause(context, download) },
                            onDelete = { viewModel.delete(context, download) },
                            onPlay = { cast ->
                                val intent = Intent(context, TorrentDownloadService::class.java).apply {
                                    action = if (download.state == TorrentDownloadService.STATE_READY || download.state == TorrentDownloadService.STATE_COMPLETED) {
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

                                    putExtra(TorrentDownloadService.EXTRA_TITLE, download.title)
                                    if (download.magnetUri.isNotBlank()) {
                                        putExtra(TorrentDownloadService.EXTRA_MAGNET_URI, download.magnetUri)
                                    }
                                }
                                context.startService(intent)
                            }
                        )
                    }
                }
            }

            CastCodecWarningObserver(
                player = mediaController,
                streamUrl = downloads.firstOrNull { it.infoHash == activeMediaId }?.mediaPath,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }

    if (showCastBottomSheet) {
        CastBottomSheet(
            onDismissRequest = { showCastBottomSheet = false }
        )
    }
}

/**
 * Renders progress and controls for one local torrent with separate Player and Torrent status rows.
 *
 * @param download Persisted transfer state.
 * @param isTv Whether to use native TV surfaces and actions.
 * @param isActive Whether this item is currently selected or playing in the player.
 * @param isPlaying Whether media is actively playing.
 * @param isCasting Whether media is currently casting.
 * @param activeRouteName Name of active cast route.
 * @param onToggleActivePlayback Toggles play/pause for active playback.
 * @param onPauseResume Toggles the current transfer state.
 * @param onDelete Deletes the job and its private media files.
 * @param onPlay Starts verified local playback through the app's range-checked media server.
 */
@Composable
private fun TorrentDownloadItem(
    download: TorrentDownload,
    isTv: Boolean,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isCasting: Boolean = false,
    activeRouteName: String = "",
    onToggleActivePlayback: () -> Unit = {},
    onPauseResume: () -> Unit,
    onDelete: () -> Unit,
    onPlay: (Boolean) -> Unit
) {
    val completed = download.state == TorrentDownloadService.STATE_COMPLETED
    val playable = completed || download.state == TorrentDownloadService.STATE_READY
    val resumable = download.state == TorrentDownloadService.STATE_PAUSED ||
        download.state == TorrentDownloadService.STATE_FAILED

    // Evaluate Player Status (Row 1)
    val (playerIcon, playerText, playerColor) = when {
        isActive && isCasting -> Triple(
            Icons.Default.CastConnected,
            stringResource(R.string.player_status_label, stringResource(R.string.player_status_casting, activeRouteName.ifBlank { stringResource(R.string.cast_desc) })),
            MaterialTheme.colorScheme.primary
        )
        isActive && isPlaying -> Triple(
            Icons.Default.PlayCircle,
            stringResource(R.string.player_status_label, stringResource(R.string.player_status_playing_local)),
            MaterialTheme.colorScheme.primary
        )
        isActive && !isPlaying -> Triple(
            Icons.Default.PauseCircle,
            stringResource(R.string.player_status_label, stringResource(R.string.player_status_paused)),
            MaterialTheme.colorScheme.secondary
        )
        download.state == TorrentDownloadService.STATE_READY || download.state == TorrentDownloadService.STATE_COMPLETED -> Triple(
            Icons.Default.CheckCircleOutline,
            stringResource(R.string.player_status_label, stringResource(R.string.player_status_ready)),
            MaterialTheme.colorScheme.tertiary
        )
        download.state == TorrentDownloadService.STATE_BUFFERING || download.state == TorrentDownloadService.STATE_QUEUED || (download.state == TorrentDownloadService.STATE_DOWNLOADING && download.progressPercent < 10) -> Triple(
            Icons.Default.HourglassTop,
            stringResource(R.string.player_status_label, stringResource(R.string.player_status_waiting)),
            MaterialTheme.colorScheme.secondary
        )
        else -> Triple(
            Icons.Default.StopCircle,
            stringResource(R.string.player_status_label, stringResource(R.string.player_status_idle)),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Evaluate Torrent Download Status (Row 2)
    val (torrentIcon, torrentText, torrentColor) = when {
        download.state == TorrentDownloadService.STATE_PAUSED -> Triple(
            Icons.Default.PauseCircle,
            stringResource(R.string.torrent_status_label, stringResource(R.string.torrent_status_paused, download.progressPercent, download.numSeeds, download.numPeers)),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        download.state == TorrentDownloadService.STATE_FAILED -> Triple(
            Icons.Default.ErrorOutline,
            stringResource(R.string.torrent_status_label, stringResource(R.string.torrent_status_failed)),
            MaterialTheme.colorScheme.error
        )
        download.state == TorrentDownloadService.STATE_COMPLETED || download.progressPercent == 100 -> Triple(
            Icons.Default.TaskAlt,
            stringResource(R.string.torrent_status_label, stringResource(R.string.torrent_status_completed, download.numSeeds, download.numPeers)),
            MaterialTheme.colorScheme.primary
        )
        (download.state == TorrentDownloadService.STATE_DOWNLOADING || download.state == TorrentDownloadService.STATE_BUFFERING || download.state == TorrentDownloadService.STATE_QUEUED) && download.numSeeds == 0 && download.numPeers == 0 -> Triple(
            Icons.Default.CloudOff,
            stringResource(R.string.torrent_status_label, stringResource(R.string.torrent_status_stalled, 0, 0)),
            MaterialTheme.colorScheme.error
        )
        else -> Triple(
            Icons.Default.CloudDownload,
            stringResource(R.string.torrent_status_label, stringResource(R.string.torrent_status_downloading, download.progressPercent, download.numSeeds, download.numPeers)),
            MaterialTheme.colorScheme.primary
        )
    }

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

            // Line 1: Player Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = playerIcon,
                    contentDescription = null,
                    tint = playerColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = playerText,
                    style = MaterialTheme.typography.bodySmall,
                    color = playerColor,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Line 2: Torrent Download Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = torrentIcon,
                    contentDescription = null,
                    tint = torrentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = torrentText,
                    style = MaterialTheme.typography.bodySmall,
                    color = torrentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

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
                    val iconVector = if (isActive) {
                        if (isPlaying) Icons.Default.Pause else (if (isCasting) Icons.Default.Tv else Icons.Default.PlayArrow)
                    } else {
                        if (isCasting) Icons.Default.Tv else Icons.Default.PlayArrow
                    }
                    val iconTint = if (isActive || isCasting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

                    if (isTv) {
                        TvIconButton(
                            onClick = {
                                if (isActive) onToggleActivePlayback() else onPlay(isCasting)
                            },
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            TvIcon(
                                imageVector = iconVector,
                                contentDescription = stringResource(R.string.play_desc)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (isActive) onToggleActivePlayback() else onPlay(isCasting)
                            },
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = stringResource(R.string.play_desc),
                                tint = iconTint
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

    val activeBorder = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null

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
            border = activeBorder,
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
                download = TorrentDownload(
                    infoHash = "HASH1",
                    magnetUri = "magnet:?",
                    title = "Inception (2010)",
                    state = "DOWNLOADING",
                    progressPercent = 50,
                    mediaPath = null,
                    castWhenReady = false,
                    updatedAtMillis = System.currentTimeMillis(),
                    numSeeds = 12,
                    numPeers = 5
                ),
                isTv = false,
                onPauseResume = {},
                onDelete = {},
                onPlay = {}
            )
        }
    }
}
