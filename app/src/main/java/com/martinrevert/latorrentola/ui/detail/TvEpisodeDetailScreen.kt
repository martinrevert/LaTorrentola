package com.martinrevert.latorrentola.ui.detail

import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.EZTV.downloadInfoHash
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
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
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val episodeSummaryFocusRequester = remember { FocusRequester() }
    val firstTorrentFocusRequester = remember { FocusRequester() }

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
                val downloadedText = stringResource(R.string.tv_episode_downloaded)
                EpisodeDetailContent(
                    seriesName = state.seriesName,
                    episode = state.episode,
                    torrents = state.torrents,
                    isDownloaded = state.isDownloaded,
                    downloadedHashes = state.downloadedHashes,
                    topPadding = padding.calculateTopPadding(),
                    episodeSummaryFocusRequester = if (isTv) episodeSummaryFocusRequester else null,
                    firstTorrentFocusRequester = if (isTv) firstTorrentFocusRequester else null,
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
                        if (magnetUri.isBlank()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_magnet_error),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val nextEpisodeInfo = viewModel.getNextEpisodeInfo()?.let { ArrayList(it.map { item -> item.toString() }) }
                            when (
                                TorrentLaunchHelper.launch(
                                    context,
                                    torrentHandlingMode,
                                    magnetUri,
                                    title,
                                    nextEpisodeInfo,
                                    seriesId = seriesId,
                                    seasonNumber = state.episode.seasonNumber,
                                    episodeNumber = state.episode.episodeNumber
                                )
                            ) {
                                TorrentLaunchResult.Started -> {
                                    viewModel.markEpisodeAsDownloaded(torrent)
                                    if (torrentHandlingMode == TorrentHandlingMode.EXTERNAL_CLIENT) {
                                        Toast.makeText(
                                            context,
                                            downloadedText,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.torrent_download_queued),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                TorrentLaunchResult.NoExternalClient -> Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_no_torrent_client),
                                    Toast.LENGTH_LONG
                                ).show()
                                TorrentLaunchResult.CastUnavailable -> Toast.makeText(
                                    context,
                                    context.getString(R.string.torrent_cast_unavailable),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
            }
        }
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
    downloadedHashes: Set<String> = emptySet(),
    topPadding: Dp,
    episodeSummaryFocusRequester: FocusRequester?,
    firstTorrentFocusRequester: FocusRequester?,
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
                TorrentReleaseCard(
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
    modifier: Modifier = Modifier,
    summaryFocusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null
) {
    Column(modifier = modifier) {
        if (episode.fullStillUrl != null) {
            AsyncImage(
                model = episode.fullStillUrl,
                contentDescription = episode.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f),
                contentScale = ContentScale.Crop
            )
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

/** Displays a torrent release with native TV focus behavior, release info, and download status. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TorrentReleaseCard(
    torrent: EztvTorrent,
    isDownloaded: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = torrent.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
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
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = if (isDownloaded) Icons.Default.CloudDone else Icons.Default.Download,
                contentDescription = if (isDownloaded) stringResource(R.string.tv_episode_downloaded) else stringResource(R.string.tv_download_torrent_desc),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }

    if (isTv) {
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        TvSurface(
            onClick = onClick,
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            cardContent()
        }
    }
}
