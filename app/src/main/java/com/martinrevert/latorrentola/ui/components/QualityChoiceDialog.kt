package com.martinrevert.latorrentola.ui.components

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface as TvSurface
import androidx.compose.ui.tooling.preview.Preview
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.isTvDevice
import kotlinx.coroutines.delay

/**
 * Dialog displaying available EZTV torrent releases for the next episode.
 *
 * @param seriesName Parent series display name.
 * @param seasonNumber Next episode season number.
 * @param episodeNumber Next episode number within its season.
 * @param episodeName Next episode title.
 * @param releases List of matching EZTV torrents for the next episode.
 * @param isLoading Whether releases are currently being fetched.
 * @param error Optional error message if release lookup failed.
 * @param onTorrentSelected Callback when user chooses a specific release.
 * @param onDismiss Callback when user cancels or dismisses the dialog.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NextEpisodeTorrentDialog(
    seriesName: String,
    seasonNumber: Int,
    episodeNumber: Int,
    episodeName: String,
    releases: List<EztvTorrent>,
    isLoading: Boolean,
    error: String?,
    onTorrentSelected: (EztvTorrent) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val firstItemFocusRequester = remember { FocusRequester() }
    val closeButtonFocusRequester = remember { FocusRequester() }

    BackHandler(onBack = onDismiss)

    // Ensure deterministic TV D-pad focus in all scenarios (Loading, Error, Empty, or Populated)
    if (isTv) {
        LaunchedEffect(isLoading, error, releases) {
            delay(100)
            try {
                if (!isLoading && releases.isNotEmpty()) {
                    firstItemFocusRequester.requestFocus()
                } else {
                    closeButtonFocusRequester.requestFocus()
                }
            } catch (_: Exception) { }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            contentAlignment = Alignment.Center
        ) {
        val dialogContent: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header section
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (seriesName.isNotBlank()) {
                        Text(
                            text = seriesName,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    val episodeTitleFormatted = if (episodeName.isNotBlank()) {
                        "%s S%02dE%02d · %s".format(
                            stringResource(R.string.next_episode_dialog_title),
                            seasonNumber,
                            episodeNumber,
                            episodeName
                        )
                    } else {
                        "%s S%02dE%02d".format(
                            stringResource(R.string.next_episode_dialog_title),
                            seasonNumber,
                            episodeNumber
                        )
                    }

                    Text(
                        text = episodeTitleFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = stringResource(R.string.tv_available_torrents),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Content area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = if (isTv) 360.dp else 280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = stringResource(R.string.next_episode_searching),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        !error.isNullOrBlank() -> {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        releases.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.next_episode_no_torrents),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = if (isTv) 280.dp else 220.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(releases) { index, torrent ->
                                    val itemModifier = Modifier
                                        .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                                        .then(
                                            if (isTv) {
                                                Modifier.focusProperties {
                                                    if (index == releases.lastIndex) down = closeButtonFocusRequester
                                                    if (index == 0) up = closeButtonFocusRequester
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )

                                    TorrentOptionItem(
                                        torrent = torrent,
                                        isTv = isTv,
                                        onClick = { onTorrentSelected(torrent) },
                                        modifier = itemModifier
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom action row - clean, right-aligned Cancel button for TV & Mobile
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .focusRequester(closeButtonFocusRequester)
                            .then(
                                if (isTv && releases.isNotEmpty()) {
                                    Modifier.focusProperties {
                                        up = firstItemFocusRequester
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .focusHighlight(shape = MaterialTheme.shapes.small)
                    ) {
                        Text(
                            text = stringResource(android.R.string.cancel),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (isTv) {
            TvSurface(
                onClick = {},
                shape = ClickableSurfaceDefaults.shape(androidx.tv.material3.MaterialTheme.shapes.extraLarge),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = androidx.tv.material3.MaterialTheme.colorScheme.surface,
                    contentColor = androidx.tv.material3.MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = androidx.tv.material3.MaterialTheme.colorScheme.surface,
                    focusedContentColor = androidx.tv.material3.MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(min = 520.dp, max = 680.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                dialogContent()
            }
        } else {
            Surface(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(min = 320.dp, max = 560.dp)
                    .fillMaxWidth(0.92f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                dialogContent()
            }
        }
    }
    }
}

/**
 * Individual torrent card row within the next episode dialog.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TorrentOptionItem(
    torrent: EztvTorrent,
    isTv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardContent: @Composable (Color, Color, Color) -> Unit = { textColor, variantColor, primaryColor ->
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
                    color = textColor,
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
                            color = variantColor
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.tv_torrent_peers_info,
                            torrent.seeds,
                            torrent.peers
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = primaryColor
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = stringResource(R.string.tv_download_torrent_desc),
                tint = primaryColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    if (isTv) {
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        val tvColors = ClickableSurfaceDefaults.colors(
            containerColor = androidx.tv.material3.MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = androidx.tv.material3.MaterialTheme.colorScheme.primaryContainer,
            contentColor = androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContentColor = androidx.tv.material3.MaterialTheme.colorScheme.onPrimaryContainer
        )

        val textColor = if (isFocused) {
            androidx.tv.material3.MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            androidx.tv.material3.MaterialTheme.colorScheme.onSurface
        }

        val variantColor = if (isFocused) {
            androidx.tv.material3.MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        } else {
            androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant
        }

        val primaryColor = if (isFocused) {
            androidx.tv.material3.MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            androidx.tv.material3.MaterialTheme.colorScheme.primary
        }

        TvSurface(
            onClick = onClick,
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
            shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
            colors = tvColors,
            interactionSource = interactionSource,
            modifier = modifier.fillMaxWidth()
        ) {
            cardContent(textColor, variantColor, primaryColor)
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .focusHighlight(shape = MaterialTheme.shapes.medium),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            cardContent(
                MaterialTheme.colorScheme.onSurface,
                MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Backward compatibility wrapper for older quality choice callers.
 */
@Composable
fun QualityChoiceDialog(
    onQualitySelected: (String) -> Unit,
    onDismiss: () -> Unit,
    releases: List<EztvTorrent>,
    isLoading: Boolean,
    error: String?
) {
    LaTorrentolaTheme {
        NextEpisodeTorrentDialog(
            seriesName = "",
            seasonNumber = 0,
            episodeNumber = 0,
            episodeName = "",
            releases = releases,
            isLoading = isLoading,
            error = error,
            onTorrentSelected = { torrent ->
                onQualitySelected(torrent.title)
            },
            onDismiss = onDismiss
        )
    }
}

@Preview(name = "Light Phone", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true, widthDp = 360, heightDp = 640)
@Preview(name = "Dark Phone", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, widthDp = 360, heightDp = 640)
@Preview(name = "Light Tablet", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true, widthDp = 600, heightDp = 800)
@Preview(name = "Dark Tablet", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, widthDp = 600, heightDp = 800)
@Preview(name = "Light TV", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true, widthDp = 960, heightDp = 540)
@Preview(name = "Dark TV", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, widthDp = 960, heightDp = 540)
private annotation class LightDarkPreviews

@LightDarkPreviews
@Composable
private fun NextEpisodeTorrentDialogPreviewLoading() {
    LaTorrentolaTheme {
        NextEpisodeTorrentDialog(
            seriesName = "Breaking Bad",
            seasonNumber = 1,
            episodeNumber = 1,
            episodeName = "Pilot",
            releases = emptyList(),
            isLoading = true,
            error = null,
            onTorrentSelected = {},
            onDismiss = {}
        )
    }
}

@LightDarkPreviews
@Composable
private fun NextEpisodeTorrentDialogPreviewReleases() {
    LaTorrentolaTheme {
        NextEpisodeTorrentDialog(
            seriesName = "Breaking Bad",
            seasonNumber = 1,
            episodeNumber = 1,
            episodeName = "Pilot",
            releases = listOf(
                EztvTorrent(title = "Breaking.Bad.S01E01.1080p.WEB-DL.x264", seeds = 150, peers = 12, sizeBytes = "1258291200"),
                EztvTorrent(title = "Breaking.Bad.S01E01.720p.HDTV.x264", seeds = 45, peers = 5, sizeBytes = "524288000")
            ),
            isLoading = false,
            error = null,
            onTorrentSelected = {},
            onDismiss = {}
        )
    }
}

@LightDarkPreviews
@Composable
private fun NextEpisodeTorrentDialogPreviewError() {
    LaTorrentolaTheme {
        NextEpisodeTorrentDialog(
            seriesName = "Breaking Bad",
            seasonNumber = 1,
            episodeNumber = 1,
            episodeName = "Pilot",
            releases = emptyList(),
            isLoading = false,
            error = "Failed to fetch torrent releases from EZTV.",
            onTorrentSelected = {},
            onDismiss = {}
        )
    }
}

@LightDarkPreviews
@Composable
private fun NextEpisodeTorrentDialogPreviewEmpty() {
    LaTorrentolaTheme {
        NextEpisodeTorrentDialog(
            seriesName = "Breaking Bad",
            seasonNumber = 1,
            episodeNumber = 1,
            episodeName = "Pilot",
            releases = emptyList(),
            isLoading = false,
            error = null,
            onTorrentSelected = {},
            onDismiss = {}
        )
    }
}

@LightDarkPreviews
@Composable
private fun TorrentOptionItemPreviewHandheld() {
    LaTorrentolaTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            TorrentOptionItem(
                torrent = EztvTorrent(
                    title = "Breaking.Bad.S01E01.1080p.WEB-DL.x264",
                    seeds = 150,
                    peers = 12,
                    sizeBytes = "1258291200"
                ),
                isTv = false,
                onClick = {}
            )
        }
    }
}

@LightDarkPreviews
@Composable
private fun TorrentOptionItemPreviewTv() {
    LaTorrentolaTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            TorrentOptionItem(
                torrent = EztvTorrent(
                    title = "Breaking.Bad.S01E01.1080p.WEB-DL.x264",
                    seeds = 150,
                    peers = 12,
                    sizeBytes = "1258291200"
                ),
                isTv = true,
                onClick = {}
            )
        }
    }
}
