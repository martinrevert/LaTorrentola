@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.detail

import com.martinrevert.latorrentola.model.user.PlaybackProgress
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.martinrevert.latorrentola.ui.components.EpisodeTorrentShimmerCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.combinedClickable
import com.martinrevert.latorrentola.ui.components.WatchProgressOptionDialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.EZTV.downloadInfoHash
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.ui.components.InsufficientStorageDialog
import com.martinrevert.latorrentola.ui.components.MovieDetailPlaceholder
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.TorrentLaunchHelper
import com.martinrevert.latorrentola.utils.TorrentLaunchResult
import com.martinrevert.latorrentola.utils.isTvDevice

/**
 * Screen displaying details and available EZTV torrent releases for an episode.
 *
 * @param viewModel ViewModel handling state and downloads for this episode.
 * @param seriesId TMDB TV series identifier.
 * @param seasonNumber Season number of the episode.
 * @param episodeNumber Episode number within season.
 * @param seriesName Series display name.
 * @param episodeJson Serialized [TmdbTvEpisode] payload when available.
 * @param onBackClick Navigation callback to return to previous screen with the shared circular focus indicator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvEpisodeDetailScreen(
    viewModel: TvEpisodeDetailViewModel,
    seriesId: Int,
    seasonNumber: Int,
    episodeNumber: Int,
    seriesName: String = "",
    episodeJson: String? = null,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val torrentHandlingMode by viewModel.torrentHandlingMode.collectAsState()
    val watchHistoryMap by viewModel.watchHistoryMap.collectAsState()
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val episodeSummaryFocusRequester = remember { FocusRequester() }
    val firstTorrentFocusRequester = remember { FocusRequester() }
    val insufficientStorageState = remember { mutableStateOf<Pair<Long, Long>?>(null) }

    LaunchedEffect(seriesId, seasonNumber, episodeNumber) {
        viewModel.loadEpisode(
            seriesId = seriesId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            seriesName = seriesName,
            episodeJson = episodeJson
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tv_episode_details_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .focusHighlight(shape = CircleShape)
                            .then(
                                if (isTv && uiState is TvEpisodeDetailUiState.Success) {
                                    Modifier.focusProperties { down = episodeSummaryFocusRequester }
                                } else {
                                    Modifier
                                }
                            )
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
        when (val state = uiState) {
            TvEpisodeDetailUiState.Loading -> MovieDetailPlaceholder(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                )
            )

            is TvEpisodeDetailUiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }

            is TvEpisodeDetailUiState.Success -> {
                var showOptionDialog by remember { mutableStateOf(false) }
                val mediaId = "media_${seriesId}_s${seasonNumber}_e${episodeNumber}"
                val progress = watchHistoryMap[mediaId]
                val progressPercent = progress?.let { if (it.isCompleted) 100 else it.progressPercent }
                EpisodeDetailContent(
                    seriesName = state.seriesName,
                    episode = state.episode,
                    torrents = state.torrents,
                    isDownloaded = state.isDownloaded,
                    progressPercent = progressPercent,
                    downloadedHashes = state.downloadedHashes,
                    topPadding = padding.calculateTopPadding(),
                    episodeSummaryFocusRequester = if (isTv) episodeSummaryFocusRequester else null,
                    firstTorrentFocusRequester = if (isTv) firstTorrentFocusRequester else null,
                    onHeaderLongClick = { showOptionDialog = true },
                    onTorrentClick = { torrent ->
                        val title = "%s S%02dE%02d %s".format(
                            state.seriesName,
                            state.episode.seasonNumber,
                            state.episode.episodeNumber,
                            torrent.title
                        )
                        val magnetUri = torrent.magnetUrl.ifBlank {
                            torrent.hash.takeIf(String::isNotBlank)?.let {
                                TorrentLaunchHelper.buildMagnetUri(it, title)
                            }.orEmpty()
                        }
                        if (magnetUri.isNotBlank()) {
                            val nextEpisodeInfo = viewModel.getNextEpisodeInfo()?.let { ArrayList(it.map { item -> item.toString() }) }
                            val sizeBytes = torrent.sizeBytes?.toLongOrNull()
                            when (
                                val result = TorrentLaunchHelper.launch(
                                    context,
                                    torrentHandlingMode,
                                    magnetUri,
                                    title,
                                    nextEpisodeInfo,
                                    seriesId = seriesId,
                                    seasonNumber = state.episode.seasonNumber,
                                    episodeNumber = state.episode.episodeNumber,
                                    requiredSizeBytes = sizeBytes
                                )
                            ) {
                                TorrentLaunchResult.Started -> {
                                    viewModel.markEpisodeAsDownloaded(torrent)
                                }
                                is TorrentLaunchResult.InsufficientStorage -> {
                                    insufficientStorageState.value = Pair(result.availableBytes, result.requiredBytes)
                                }
                                else -> {}
                            }
                        }
                    }
                )

                if (showOptionDialog) {
                    WatchProgressOptionDialog(
                        title = stringResource(R.string.mark_episode_title),
                        option1Text = stringResource(R.string.mark_episode_completed),
                        option2Text = stringResource(R.string.mark_episode_uncompleted),
                        onOption1Selected = {
                            viewModel.markEpisodeAsCompleted(state.seriesName, state.episode)
                        },
                        onOption2Selected = {
                            viewModel.markEpisodeAsUncompleted()
                        },
                        onDismissRequest = { showOptionDialog = false }
                    )
                }
            }
        }
    }

    val storageData = insufficientStorageState.value
    if (storageData != null) {
        InsufficientStorageDialog(
            availableBytes = storageData.first,
            requiredBytes = storageData.second,
            onDismiss = { insufficientStorageState.value = null }
        )
    }
}

/**
 * Renders the episode metadata hero and the list of available torrent releases.
 *
 * @param seriesName Parent series display name.
 * @param episode Episode metadata.
 * @param torrents Matching EZTV releases.
 * @param isDownloaded Whether this episode has been downloaded.
 * @param downloadedHashes Specific release infohashes recorded in user downloads.
 * @param topPadding Space reserved for the app bar and system insets.
 * @param episodeSummaryFocusRequester Optional TV focus target for the episode summary.
 * @param firstTorrentFocusRequester Optional TV focus target for the first release.
 * @param onTorrentClick Records and opens the selected release.
 */
@Composable
private fun EpisodeDetailContent(
    seriesName: String,
    episode: TmdbTvEpisode,
    torrents: List<EztvTorrent>,
    isDownloaded: Boolean = false,
    progressPercent: Int? = null,
    downloadedHashes: Set<String> = emptySet(),
    topPadding: Dp,
    episodeSummaryFocusRequester: FocusRequester?,
    firstTorrentFocusRequester: FocusRequester?,
    onHeaderLongClick: (() -> Unit)? = null,
    onTorrentClick: (EztvTorrent) -> Unit
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(
                top = topPadding + 16.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 24.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EpisodeHeader(
            seriesName = seriesName,
            episode = episode,
            isDownloaded = isDownloaded,
            progressPercent = progressPercent,
            onLongClick = onHeaderLongClick,
            modifier = Modifier.fillMaxWidth(),
            summaryFocusRequester = episodeSummaryFocusRequester,
            nextFocusRequester = firstTorrentFocusRequester.takeIf { torrents.isNotEmpty() }
        )

        Text(
            text = stringResource(R.string.tv_available_torrents),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp)
        )

        if (torrents.isEmpty()) {
            Text(
                text = stringResource(R.string.tv_no_torrents_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .then(
                        if (isTv) Modifier.focusable() else Modifier
                    )
            )
        } else {
            torrents.forEachIndexed { index, torrent ->
                val isTorrentDownloaded =
                    torrent.downloadInfoHash()?.let(downloadedHashes::contains) == true
                EpisodeTorrentShimmerCard(
                    torrent = torrent,
                    isDownloaded = isTorrentDownloaded,
                    onClick = { onTorrentClick(torrent) },
                    modifier = if (
                        index == 0 &&
                        firstTorrentFocusRequester != null &&
                        episodeSummaryFocusRequester != null
                    ) {
                        Modifier
                            .focusRequester(firstTorrentFocusRequester)
                            .focusProperties { up = episodeSummaryFocusRequester }
                    } else Modifier
                )
            }
        }
    }
}

/**
 * Displays episode artwork, season/episode tags, title, air date, runtime, overview, and download badge.
 *
 * @param seriesName Parent series display name.
 * @param episode Episode metadata to display.
 * @param isDownloaded Whether this episode has been downloaded.
 * @param modifier Layout modifier for the episode header.
 * @param summaryFocusRequester Optional TV focus target for the episode title.
 * @param nextFocusRequester Optional next target below the episode summary.
 */
@Composable
private fun EpisodeHeader(
    seriesName: String,
    episode: TmdbTvEpisode,
    isDownloaded: Boolean = false,
    progressPercent: Int? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    summaryFocusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null
) {
    Column(
        modifier = modifier.then(
            if (onLongClick != null) {
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
            } else {
                Modifier
            }
        )
    ) {
        if (episode.fullStillUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
            ) {
                AsyncImage(
                    model = episode.fullStillUrl,
                    contentDescription = episode.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (progressPercent != null && progressPercent > 0) {
                    LinearProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        strokeCap = StrokeCap.Butt
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (seriesName.isNotBlank()) {
                Text(
                    text = seriesName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (isDownloaded) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = stringResource(R.string.tv_episode_downloaded),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Text(
            text = "S%02dE%02d · %s".format(
                episode.seasonNumber,
                episode.episodeNumber,
                episode.name.orEmpty()
            ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = summaryFocusRequester?.let { requester ->
                Modifier
                    .focusRequester(requester)
                    .focusable()
                    .then(
                        nextFocusRequester?.let { next ->
                            Modifier.focusProperties { down = next }
                        } ?: Modifier
                    )
            } ?: Modifier
        )

        val metaList = listOfNotNull(
            episode.airDate?.takeIf(String::isNotBlank),
            episode.runtime?.let { stringResource(R.string.tv_episode_runtime, it) }
        )
        if (metaList.isNotEmpty()) {
            Text(
                text = metaList.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (!episode.overview.isNullOrBlank()) {
            Text(
                text = episode.overview,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

/** Displays a torrent release with real-time liquid shimmer fill and native focus behavior. */
@Composable
private fun TorrentReleaseCard(
    torrent: EztvTorrent,
    isDownloaded: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    EpisodeTorrentShimmerCard(
        torrent = torrent,
        isDownloaded = isDownloaded,
        onClick = onClick,
        modifier = modifier
    )
}

@com.martinrevert.latorrentola.ui.theme.LightDarkPreviews
@Composable
private fun TorrentReleaseCardPreview() {
    com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme {
        androidx.compose.material3.Surface {
            EpisodeTorrentShimmerCard(
                torrent = com.martinrevert.latorrentola.model.EZTV.EztvTorrent(
                    title = "Breaking.Bad.S01E01.1080p.WEB-DL.x264",
                    seeds = 120,
                    peers = 15,
                    sizeBytes = "1258291200"
                ),
                isDownloaded = false,
                onClick = {}
            )
        }
    }
}
