@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.components

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.EZTV.downloadInfoHash
import com.martinrevert.latorrentola.model.torrent.TorrentRelease
import com.martinrevert.latorrentola.model.torrent.VideoQualityGroup
import com.martinrevert.latorrentola.network.NextEpisodeEvaluation
import com.martinrevert.latorrentola.network.SeriesContinuationState
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Dialog displaying available torrent releases for the next episode or clear series/season
 * continuation status when no more torrents are available.
 *
 * @param seriesName Parent series display name.
 * @param seasonNumber Next episode season number.
 * @param episodeNumber Next episode number within its season.
 * @param episodeName Next episode title.
 * @param releases List of matching unified torrent releases for the next episode.
 * @param isLoading Whether releases are currently being fetched.
 * @param error Optional error message if release lookup failed.
 * @param onTorrentSelected Callback when user chooses a specific release.
 * @param onDismiss Callback when user cancels or dismisses the dialog.
 * @param evaluation Optional [NextEpisodeEvaluation] with TMDB series continuation status and upcoming air dates.
 * @param themeMode Optional explicit theme mode override.
 */
@Composable
fun NextEpisodeTorrentDialog(
    seriesName: String,
    seasonNumber: Int,
    episodeNumber: Int,
    episodeName: String,
    releases: List<TorrentRelease>,
    isLoading: Boolean,
    error: String?,
    onTorrentSelected: (TorrentRelease) -> Unit,
    onDismiss: () -> Unit,
    evaluation: NextEpisodeEvaluation? = null,
    themeMode: Int? = null
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }
    val firstItemFocusRequester = remember { FocusRequester() }
    val closeButtonFocusRequester = remember { FocusRequester() }

    BackHandler(onBack = onDismiss)

    // Ensure deterministic TV D-pad focus in all scenarios (Loading, Error, Empty, or Populated)
    if (isTv) {
        LaunchedEffect(isLoading, error, releases, evaluation) {
            delay(100.milliseconds)
            try {
                if (!isLoading && releases.isNotEmpty()) {
                    firstItemFocusRequester.requestFocus()
                } else {
                    closeButtonFocusRequester.requestFocus()
                }
            } catch (_: Exception) { }
        }
    }

    val effectiveSeriesName = evaluation?.seriesName?.takeIf { it.isNotBlank() } ?: seriesName
    val isStatusOnlyMode = !isLoading && releases.isEmpty() && evaluation != null &&
        evaluation.state != SeriesContinuationState.NEXT_EPISODE_READY

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val themedDialogBody = @Composable {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f))
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
                            if (effectiveSeriesName.isNotBlank()) {
                                Text(
                                    text = effectiveSeriesName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            val headerTitle = when (evaluation?.state) {
                                SeriesContinuationState.SERIES_ENDED ->
                                    stringResource(R.string.series_status_ended_title)
                                SeriesContinuationState.SEASON_COMPLETE_SERIES_CONTINUES ->
                                    stringResource(R.string.series_status_returning_title)
                                else -> {
                                    val sNum = evaluation?.nextSeasonNumber?.takeIf { it > 0 } ?: seasonNumber
                                    val eNum = evaluation?.nextEpisodeNumber?.takeIf { it > 0 } ?: episodeNumber
                                    val epTitle = evaluation?.nextEpisodeName?.takeIf { it.isNotBlank() } ?: episodeName
                                    if (sNum > 0 && eNum > 0) {
                                        if (epTitle.isNotBlank()) {
                                            "%s S%02dE%02d · %s".format(
                                                stringResource(R.string.next_episode_dialog_title),
                                                sNum,
                                                eNum,
                                                epTitle
                                            )
                                        } else {
                                            "%s S%02dE%02d".format(
                                                stringResource(R.string.next_episode_dialog_title),
                                                sNum,
                                                eNum
                                            )
                                        }
                                    } else {
                                        stringResource(R.string.next_episode_dialog_title)
                                    }
                                }
                            }

                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface
                            )

                            if (!isStatusOnlyMode) {
                                Spacer(Modifier.height(2.dp))

                                Text(
                                    text = stringResource(R.string.tv_available_torrents),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                                    val statusMessage = when (evaluation?.state) {
                                        SeriesContinuationState.SERIES_ENDED ->
                                            stringResource(R.string.series_status_ended_message)
                                        SeriesContinuationState.SEASON_COMPLETE_SERIES_CONTINUES -> {
                                            val nextDate = evaluation.nextEpisodeAirDate?.takeIf { it.isNotBlank() }
                                            if (nextDate != null) {
                                                stringResource(R.string.series_status_returning_with_date_message, nextDate)
                                            } else {
                                                stringResource(R.string.series_status_returning_message)
                                            }
                                        }
                                        SeriesContinuationState.REMAINING_EPISODES_NO_TORRENTS -> {
                                            val sNum = evaluation.nextSeasonNumber.takeIf { it > 0 } ?: seasonNumber
                                            val eNum = evaluation.nextEpisodeNumber.takeIf { it > 0 } ?: episodeNumber
                                            val airDate = evaluation.nextEpisodeAirDate?.takeIf { it.isNotBlank() }
                                            if (evaluation.isNextEpisodeUnaired && airDate != null) {
                                                stringResource(
                                                    R.string.series_status_unaired_episode_message,
                                                    sNum,
                                                    eNum,
                                                    airDate
                                                )
                                            } else if (evaluation.isSeriesContinuing) {
                                                stringResource(
                                                    R.string.series_status_remaining_continuing_message,
                                                    maxOf(1, evaluation.remainingEpisodesCount),
                                                    sNum,
                                                    eNum
                                                )
                                            } else {
                                                stringResource(
                                                    R.string.series_status_remaining_ended_message,
                                                    maxOf(1, evaluation.remainingEpisodesCount),
                                                    sNum,
                                                    eNum
                                                )
                                            }
                                        }
                                        else -> stringResource(R.string.next_episode_no_torrents)
                                    }

                                    Text(
                                        text = statusMessage,
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
                                        itemsIndexed(releases) { index, release ->
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

                                            TorrentReleaseShimmerCard(
                                                release = release,
                                                isDownloaded = false,
                                                onClick = { onTorrentSelected(release) },
                                                modifier = itemModifier,
                                                themeMode = effectiveThemeMode
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom action row - clean, right-aligned button for TV & Mobile
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
                                                up = FocusRequester.Default
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .focusHighlight(shape = MaterialTheme.shapes.small)
                            ) {
                                Text(
                                    text = if (isStatusOnlyMode) {
                                        stringResource(R.string.series_status_understood)
                                    } else {
                                        stringResource(android.R.string.cancel)
                                    },
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

        if (isTv) {
            TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
                themedDialogBody()
            }
        } else {
            LaTorrentolaTheme(themeMode = effectiveThemeMode) {
                themedDialogBody()
            }
        }
    }
}

/**
 * Backward compatibility overload for callers passing [EztvTorrent] releases.
 */
@JvmName("NextEpisodeTorrentDialogEztv")
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
    onDismiss: () -> Unit,
    themeMode: Int? = null
) {
    val mappedReleases = remember(releases) {
        releases.map { eztv ->
            TorrentRelease(
                hash = eztv.hash.ifBlank { eztv.downloadInfoHash() ?: "" },
                title = eztv.title,
                qualityGroup = when {
                    eztv.title.contains("2160p", true) || eztv.title.contains("4k", true) -> VideoQualityGroup.UHD_4K
                    eztv.title.contains("1080p", true) -> VideoQualityGroup.FHD_1080P
                    eztv.title.contains("720p", true) -> VideoQualityGroup.HD_720P
                    else -> VideoQualityGroup.OTHER
                },
                resolutionLabel = when {
                    eztv.title.contains("2160p", true) || eztv.title.contains("4k", true) -> "4K"
                    eztv.title.contains("1080p", true) -> "1080p"
                    eztv.title.contains("720p", true) -> "720p"
                    else -> "SD"
                },
                audioCodec = "AAC",
                sizeBytes = eztv.sizeBytes?.toLongOrNull(),
                formattedSize = eztv.formattedSize,
                seeds = eztv.seeds,
                peers = eztv.peers,
                provider = "EZTV",
                magnetUrl = eztv.magnetUrl.takeIf { it.isNotBlank() }
            )
        }
    }

    NextEpisodeTorrentDialog(
        seriesName = seriesName,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeName = episodeName,
        releases = mappedReleases,
        isLoading = isLoading,
        error = error,
        onTorrentSelected = { release ->
            val match = releases.firstOrNull { it.hash.equals(release.hash, ignoreCase = true) || it.title == release.title }
                ?: EztvTorrent(
                    title = release.title,
                    hash = release.hash,
                    magnetUrl = release.magnetUrl.orEmpty(),
                    seeds = release.seeds,
                    peers = release.peers,
                    sizeBytes = release.sizeBytes?.toString()
                )
            onTorrentSelected(match)
        },
        onDismiss = onDismiss,
        themeMode = themeMode
    )
}

/**
 * Individual torrent card row within the next episode dialog.
 */
@Composable
private fun TorrentOptionItem(
    torrent: EztvTorrent,
    isTv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NextEpisodeTorrentShimmerCard(
        torrent = torrent,
        onClick = onClick,
        modifier = modifier
    )
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
    error: String?,
    themeMode: Int? = null
) {
    val context = LocalContext.current
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }
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
        onDismiss = onDismiss,
        themeMode = effectiveThemeMode
    )
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
            releases = emptyList<TorrentRelease>(),
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
                TorrentRelease(
                    hash = "hash1080",
                    title = "Breaking.Bad.S01E01.1080p.WEB-DL.x264",
                    qualityGroup = VideoQualityGroup.FHD_1080P,
                    resolutionLabel = "1080p",
                    audioCodec = "AAC 5.1",
                    videoCodec = "x264",
                    sizeBytes = 1258291200L,
                    formattedSize = "1.17 GB",
                    seeds = 150,
                    peers = 12,
                    provider = "Torrentio"
                ),
                TorrentRelease(
                    hash = "hash720",
                    title = "Breaking.Bad.S01E01.720p.HDTV.x264",
                    qualityGroup = VideoQualityGroup.HD_720P,
                    resolutionLabel = "720p",
                    audioCodec = "AAC",
                    videoCodec = "x264",
                    sizeBytes = 524288000L,
                    formattedSize = "500 MB",
                    seeds = 45,
                    peers = 5,
                    provider = "EZTV"
                )
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
            releases = emptyList<TorrentRelease>(),
            isLoading = false,
            error = "Failed to fetch torrent releases.",
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
            releases = emptyList<TorrentRelease>(),
            isLoading = false,
            error = null,
            onTorrentSelected = {},
            onDismiss = {}
        )
    }
}
