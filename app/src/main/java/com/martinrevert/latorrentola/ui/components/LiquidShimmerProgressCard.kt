@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.EZTV.downloadInfoHash
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.Torrent
import com.martinrevert.latorrentola.service.TorrentDownloadService
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice
import java.util.Locale

/** Formats bytes per second into human-readable transfer rate (KB/s or MB/s). */
fun formatTransferRate(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return "0 KB/s"
    val kb = bytesPerSec / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB/s", mb)
    } else {
        String.format(Locale.US, "%.0f KB/s", kb)
    }
}

/**
 * Reusable surface container providing a real-time "Liquid Shimmer Fill & Glowing Edge"
 * visual progress indicator. Retains cards and interactive elements visually responsive
 * while background buffering or torrent pieces are acquired.
 */
@Composable
fun LiquidShimmerProgressSurface(
    progress: Float,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    themeMode: Int? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = remember(configuration, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val waveWidth = remember(screenWidthPx) {
        (screenWidthPx * 0.35f).coerceIn(400f, 900f)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isActive) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "liquidProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "shimmerTransition")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -waveWidth,
        targetValue = screenWidthPx + waveWidth,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer

    val internalContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
        ) {
            // Liquid shimmer background fill
            if (isActive && animatedProgress > 0.005f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shape)
                ) {
                    // Base liquid fill (animates left-to-right with high-contrast gradient)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        primaryColor.copy(alpha = 0.28f),
                                        primaryContainerColor.copy(alpha = 0.36f),
                                        primaryColor.copy(alpha = 0.28f)
                                    )
                                )
                            )
                    )

                    // Infinite wave shimmer overlay sweeping across the full card width
                    val shimmerBrush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            primaryColor.copy(alpha = 0.40f),
                            primaryContainerColor.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        start = Offset(shimmerTranslate, 0f),
                        end = Offset(shimmerTranslate + waveWidth, waveWidth)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .background(shimmerBrush)
                    )

                    // Glowing leading edge while actively filling
                    if (animatedProgress in 0.01f..0.99f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                primaryColor.copy(alpha = 0.2f),
                                                primaryColor.copy(alpha = 0.95f)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
            }

            // Foreground content
            content()
        }
    }

    if (isTv) {
        TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
            val interactionSource = remember { MutableInteractionSource() }
            TvSurface(
                onClick = onClick,
                onLongClick = onLongClick,
                enabled = enabled,
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
                shape = ClickableSurfaceDefaults.shape(shape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = surfaceContainerColor,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    focusedContentColor = MaterialTheme.colorScheme.onSurface
                ),
                interactionSource = interactionSource,
                modifier = modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            ) {
                internalContent()
            }
        }
    } else {
        LaTorrentolaTheme(themeMode = effectiveThemeMode) {
            Card(
                shape = shape,
                colors = CardDefaults.cardColors(
                    containerColor = surfaceContainerColor,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .adaptiveLongClickable(onClick = onClick, onLongClick = onLongClick, enabled = enabled)
                    .focusHighlight(shape = shape)
            ) {
                internalContent()
            }
        }
    }
}

/**
 * Redesigned movie release card displaying sanitized release metadata and real-time
 * liquid shimmer progress until the startup buffer is 100% verified.
 */
@Composable
fun MovieTorrentShimmerCard(
    movie: Movie,
    torrent: Torrent,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    themeMode: Int? = null
) {
    val activeBuffer by TorrentDownloadService.activeBufferState.collectAsState()
    val torrentHash = torrent.hash.orEmpty()
    val isActive = remember(activeBuffer, torrentHash) {
        torrentHash.isNotBlank() && activeBuffer?.infoHash.equals(torrentHash, ignoreCase = true)
    }

    val readyProgress = when {
        isActive -> activeBuffer?.readyProgress ?: 0f
        isDownloaded -> 1.0f
        else -> 0f
    }
    val downloadSpeed = if (isActive) activeBuffer?.downloadSpeedBytes ?: 0L else 0L
    val seeds = if (isActive && (activeBuffer?.numSeeds ?: 0) > 0) activeBuffer?.numSeeds ?: torrent.seeds ?: 0 else torrent.seeds ?: 0
    val peers = if (isActive && (activeBuffer?.numPeers ?: 0) > 0) activeBuffer?.numPeers ?: torrent.peers ?: 0 else torrent.peers ?: 0

    val codecLabel = listOfNotNull(
        torrent.videoCodec?.uppercase()?.takeIf { it.isNotBlank() },
        torrent.bitDepth?.let { if (it == "10") "10-BIT" else null },
        torrent.audioChannels?.let { if (it.contains("5.1")) "5.1" else null }
    ).joinToString(" ")

    val releaseDetails = buildString {
        if (!torrent.quality.isNullOrBlank()) append(torrent.quality)
        if (!torrent.type.isNullOrBlank()) {
            if (isNotEmpty()) append(" • ")
            append(torrent.type.uppercase())
        }
        if (codecLabel.isNotBlank()) {
            if (isNotEmpty()) append(" • ")
            append(codecLabel)
        }
    }

    LiquidShimmerProgressSurface(
        progress = readyProgress,
        isActive = isActive,
        onClick = onClick,
        onLongClick = onLongClick,
        focusRequester = focusRequester,
        themeMode = themeMode,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title
                Text(
                    text = movie.title ?: stringResource(R.string.torrent_download_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Quality & codec line
                Text(
                    text = releaseDetails.ifBlank { torrent.quality ?: "" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Status line: size, seeds/peers or live transfer progress
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!torrent.size.isNullOrBlank()) {
                        Text(
                            text = torrent.size,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isActive) {
                        Text(
                            text = "${(readyProgress * 100).toInt()}% · ${formatTransferRate(downloadSpeed)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.tv_torrent_peers_info, seeds, peers),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Action / download state badge
            Icon(
                imageVector = when {
                    isActive -> Icons.Default.Download
                    isDownloaded -> Icons.Default.CloudDone
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = if (isActive) {
                    stringResource(R.string.tv_download_torrent_desc)
                } else if (isDownloaded) {
                    stringResource(R.string.downloaded_desc)
                } else {
                    stringResource(R.string.play_desc)
                },
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * TV Episode release card with real-time liquid shimmer fill during startup buffering.
 */
@Composable
fun EpisodeTorrentShimmerCard(
    torrent: EztvTorrent,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    themeMode: Int? = null
) {
    val activeBuffer by TorrentDownloadService.activeBufferState.collectAsState()
    val torrentHash = torrent.downloadInfoHash().orEmpty()
    val isActive = remember(activeBuffer, torrentHash) {
        torrentHash.isNotBlank() && activeBuffer?.infoHash.equals(torrentHash, ignoreCase = true)
    }

    val readyProgress = when {
        isActive -> activeBuffer?.readyProgress ?: 0f
        isDownloaded -> 1.0f
        else -> 0f
    }
    val downloadSpeed = if (isActive) activeBuffer?.downloadSpeedBytes ?: 0L else 0L
    val seeds = if (isActive && (activeBuffer?.numSeeds ?: 0) > 0) activeBuffer?.numSeeds ?: torrent.seeds else torrent.seeds
    val peers = if (isActive && (activeBuffer?.numPeers ?: 0) > 0) activeBuffer?.numPeers ?: torrent.peers else torrent.peers

    LiquidShimmerProgressSurface(
        progress = readyProgress,
        isActive = isActive,
        onClick = onClick,
        onLongClick = onLongClick,
        focusRequester = focusRequester,
        themeMode = themeMode,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = torrent.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (torrent.formattedSize.isNotBlank()) {
                        Text(
                            text = torrent.formattedSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isActive) {
                        Text(
                            text = "${(readyProgress * 100).toInt()}% · ${formatTransferRate(downloadSpeed)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.tv_torrent_peers_info, seeds, peers),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Icon(
                imageVector = when {
                    isActive -> Icons.Default.Download
                    isDownloaded -> Icons.Default.CloudDone
                    else -> Icons.Default.Download
                },
                contentDescription = if (isDownloaded && !isActive) {
                    stringResource(R.string.tv_episode_downloaded)
                } else {
                    stringResource(R.string.tv_download_torrent_desc)
                },
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Next Episode dialog release item with real-time liquid shimmer fill.
 */
@Composable
fun NextEpisodeTorrentShimmerCard(
    torrent: EztvTorrent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    themeMode: Int? = null
) {
    val activeBuffer by TorrentDownloadService.activeBufferState.collectAsState()
    val torrentHash = torrent.downloadInfoHash().orEmpty()
    val isActive = remember(activeBuffer, torrentHash) {
        torrentHash.isNotBlank() && activeBuffer?.infoHash.equals(torrentHash, ignoreCase = true)
    }

    val readyProgress = if (isActive) activeBuffer?.readyProgress ?: 0f else 0f
    val downloadSpeed = if (isActive) activeBuffer?.downloadSpeedBytes ?: 0L else 0L
    val seeds = if (isActive && (activeBuffer?.numSeeds ?: 0) > 0) activeBuffer?.numSeeds ?: torrent.seeds else torrent.seeds
    val peers = if (isActive && (activeBuffer?.numPeers ?: 0) > 0) activeBuffer?.numPeers ?: torrent.peers else torrent.peers

    LiquidShimmerProgressSurface(
        progress = readyProgress,
        isActive = isActive,
        onClick = onClick,
        onLongClick = onLongClick,
        focusRequester = focusRequester,
        themeMode = themeMode,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = torrent.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (torrent.formattedSize.isNotBlank()) {
                        Text(
                            text = torrent.formattedSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isActive) {
                        Text(
                            text = "${(readyProgress * 100).toInt()}% · ${formatTransferRate(downloadSpeed)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.tv_torrent_peers_info, seeds, peers),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = stringResource(R.string.tv_download_torrent_desc),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
