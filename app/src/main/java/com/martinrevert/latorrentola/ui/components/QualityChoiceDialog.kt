package com.martinrevert.latorrentola.ui.components

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface as TvSurface
import androidx.compose.ui.tooling.preview.Preview
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.isTvDevice

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .then(
                    if (isTv) Modifier.widthIn(min = 480.dp, max = 640.dp)
                    else Modifier.fillMaxWidth(0.92f)
                )
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
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                if (seriesName.isNotBlank()) {
                    Text(
                        text = seriesName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
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
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.tv_available_torrents),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = stringResource(R.string.next_episode_searching),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (!error.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else if (releases.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.next_episode_no_torrents),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (isTv) 380.dp else 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(releases) { index, torrent ->
                            val itemModifier = if (index == 0) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            }

                            TorrentOptionItem(
                                torrent = torrent,
                                isTv = isTv,
                                onClick = { onTorrentSelected(torrent) },
                                modifier = itemModifier
                            )
                        }
                    }

                    if (isTv) {
                        LaunchedEffect(releases) {
                            if (releases.isNotEmpty()) {
                                try {
                                    firstItemFocusRequester.requestFocus()
                                } catch (_: Exception) { }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .focusRequester(closeButtonFocusRequester)
                            .focusHighlight(shape = MaterialTheme.shapes.small)
                    ) {
                        Text(
                            text = stringResource(android.R.string.cancel),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = torrent.title,
                    style = MaterialTheme.typography.bodyMedium,
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
                    Text(
                        text = stringResource(
                            R.string.tv_torrent_peers_info,
                            torrent.seeds,
                            torrent.peers
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = stringResource(R.string.tv_download_torrent_desc),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    if (isTv) {
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        TvSurface(
            onClick = onClick,
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
            shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContentColor = MaterialTheme.colorScheme.onSurface
            ),
            interactionSource = interactionSource,
            modifier = modifier.fillMaxWidth()
        ) {
            cardContent()
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
            cardContent()
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

@Preview(name = "Light Theme", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "Dark Theme", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
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
