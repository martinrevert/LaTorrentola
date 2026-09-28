package com.martinrevert.latorrentola.ui.detail

import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.IconButton as TvIconButton
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeason
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.user.DownloadedEpisode
import com.martinrevert.latorrentola.ui.components.AdaptiveChip
import com.martinrevert.latorrentola.ui.components.MovieDetailPlaceholder
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.isTvDevice
import java.util.Locale

/**
 * Presents TV series metadata, cast, seasons, and episodes.
 *
 * @param viewModel Series and season details state.
 * @param seriesId TMDB TV series identifier.
 * @param onEpisodeClick Triggers navigation to detail screen for a selected episode.
 * @param onBackClick Returns to the previous destination.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
fun TvDetailScreen(
    viewModel: TvDetailViewModel,
    seriesId: Int,
    onEpisodeClick: (seriesName: String, episode: TmdbTvEpisode) -> Unit = { _, _ -> },
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val seasonState by viewModel.seasonState.collectAsState()
    val downloadedEpisodes by viewModel.downloadedEpisodes.collectAsState()
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val detailContentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(seriesId) { viewModel.load(seriesId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tv_details_title)) },
                navigationIcon = {
                    if (isTv) {
                        TvIconButton(
                            onClick = onBackClick,
                            modifier = Modifier.focusProperties { down = detailContentFocusRequester }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_desc)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.focusProperties { down = detailContentFocusRequester }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_desc)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            TvDetailUiState.Loading -> MovieDetailPlaceholder(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                )
            )
            is TvDetailUiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            is TvDetailUiState.Success -> TvDetailContent(
                series = state.series,
                selectedSeasonNumber = selectedSeason?.seasonNumber,
                seasonState = seasonState,
                downloadedEpisodes = downloadedEpisodes,
                onSeasonSelected = viewModel::selectSeason,
                onEpisodeClick = { episode -> onEpisodeClick(state.series.name.orEmpty(), episode) },
                contentFocusRequester = detailContentFocusRequester,
                topPadding = padding.calculateTopPadding()
            )
        }
    }
}

/**
 * Renders the responsive series hero, metadata, cast, and season content.
 *
 * @param series TMDB series details.
 * @param selectedSeasonNumber Selected season's TMDB season number.
 * @param seasonState Selected season's episode state.
 * @param downloadedEpisodes List of TV episode downloads recorded for user.
 * @param onSeasonSelected Loads episodes for a selected season.
 * @param onEpisodeClick Navigates to episode detail view when tapped.
 * @param contentFocusRequester First focusable control in the detail content.
 * @param topPadding Space reserved for the app bar and system insets.
 */
@Composable
private fun TvDetailContent(
    series: TmdbTvSummary,
    selectedSeasonNumber: Int?,
    seasonState: TvSeasonUiState,
    downloadedEpisodes: List<DownloadedEpisode>,
    onSeasonSelected: (TmdbTvSeason) -> Unit,
    onEpisodeClick: (TmdbTvEpisode) -> Unit,
    contentFocusRequester: FocusRequester,
    topPadding: Dp
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val isWide = LocalConfiguration.current.screenWidthDp >= 600 || isTv
    val cast = series.aggregateCredits?.cast.orEmpty()

    // Downloaded episodes for active series
    val seriesDownloads = remember(downloadedEpisodes, series.id) {
        downloadedEpisodes.filter { it.seriesId == series.id }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = topPadding + 16.dp,
            start = 16.dp,
            end = 16.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    TvSeriesHero(series, Modifier.weight(0.6f))
                    TvSeriesInformation(series, Modifier.weight(0.4f))
                }
            } else {
                Column {
                    TvSeriesHero(series, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    TvSeriesInformation(series, Modifier.fillMaxWidth())
                }
            }
        }
        if (cast.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.tv_cast),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LazyRow(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(cast, key = { it.id }) { actor ->
                        Column(
                            modifier = Modifier.width(112.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = actor.fullProfileUrl,
                                contentDescription = actor.name,
                                modifier = Modifier
                                    .size(88.dp)
                                    .aspectRatio(1f),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                actor.name.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2
                            )
                            Text(
                                actor.roles.firstOrNull()?.character.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
        if (series.seasons.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.tv_seasons),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(series.seasons, key = { it.id }) { season ->
                        val baseName = season.name ?: stringResource(
                            R.string.tv_season_number,
                            season.seasonNumber
                        )

                        // Calculate season completion / partial status
                        val distinctDownloadedEpisodesInSeason = seriesDownloads
                            .filter { it.seasonNumber == season.seasonNumber }
                            .distinctBy { it.episodeNumber }
                        val downloadedCount = distinctDownloadedEpisodesInSeason.size
                        val totalCount = season.episodeCount ?: 0

                        val statusLabel = when {
                            downloadedCount > 0 && totalCount > 0 && downloadedCount >= totalCount ->
                                "$baseName · " + stringResource(R.string.tv_season_completed)
                            downloadedCount > 0 ->
                                "$baseName · " + stringResource(R.string.tv_season_partial, downloadedCount, totalCount)
                            else -> baseName
                        }

                        AdaptiveChip(
                            selected = selectedSeasonNumber == season.seasonNumber,
                            onClick = { onSeasonSelected(season) },
                            label = { Text(statusLabel) },
                            modifier = if (season == series.seasons.first()) {
                                Modifier.focusRequester(contentFocusRequester)
                            } else Modifier
                        )
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.tv_episodes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        when (val episodes = seasonState) {
            TvSeasonUiState.Loading -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            is TvSeasonUiState.Error -> item {
                Text(episodes.message, color = MaterialTheme.colorScheme.error)
            }
            is TvSeasonUiState.Success -> {
                if (episodes.season.episodes.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.tv_no_episodes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(episodes.season.episodes, key = TmdbTvEpisode::id) { episode ->
                        val isDownloaded = seriesDownloads.any {
                            it.seasonNumber == selectedSeasonNumber && it.episodeNumber == episode.episodeNumber
                        }
                        TvEpisodeCard(
                            episode = episode,
                            isDownloaded = isDownloaded,
                            onClick = { onEpisodeClick(episode) },
                            modifier = if (series.seasons.isEmpty() &&
                                episode == episodes.season.episodes.firstOrNull()
                            ) {
                                Modifier.focusRequester(contentFocusRequester)
                            } else Modifier
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shows series artwork, preferring the wide backdrop over the poster.
 *
 * @param series TV series metadata and image paths.
 * @param modifier Layout constraints for the artwork.
 */
@Composable
private fun TvSeriesHero(series: TmdbTvSummary, modifier: Modifier) {
    AsyncImage(
        model = series.fullBackdropUrl ?: series.fullPosterUrl,
        contentDescription = series.name,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (series.fullBackdropUrl != null) 16 / 9f else 0.67f),
        contentScale = ContentScale.Crop
    )
}

/**
 * Displays the title, rating, genres, status, and overview.
 *
 * @param series TV series metadata.
 * @param modifier Layout constraints for the metadata.
 */
@Composable
private fun TvSeriesInformation(series: TmdbTvSummary, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            series.name.orEmpty(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            listOfNotNull(
                series.firstAirYear.takeIf(String::isNotBlank),
                series.voteAverage?.let { "★ ${"%.1f".format(Locale.US, it)}" },
                series.numberOfSeasons?.let { stringResource(R.string.tv_season_count, it) },
                series.numberOfEpisodes?.let { stringResource(R.string.tv_episode_count, it) }
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        series.status?.let {
            Text(
                stringResource(R.string.tv_series_status, it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (series.genres.isNotEmpty()) {
            Text(
                series.genres.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (!series.overview.isNullOrBlank()) {
            Text(
                series.overview,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

/**
 * Displays one episode using available still art, metadata, and optional downloaded badge.
 *
 * @param episode Episode details from the selected season.
 * @param isDownloaded Whether the episode has been downloaded at least once.
 * @param onClick Triggered when user taps the episode card.
 * @param modifier Layout and focus modifiers for the episode card.
 */
@Composable
private fun TvEpisodeCard(
    episode: TmdbTvEpisode,
    isDownloaded: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .focusable()
            .focusHighlight(shape = MaterialTheme.shapes.medium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (episode.fullStillUrl != null) {
                AsyncImage(
                    model = episode.fullStillUrl,
                    contentDescription = episode.name,
                    modifier = Modifier
                        .width(180.dp)
                        .aspectRatio(16 / 9f),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.tv_episode_number, episode.episodeNumber),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isDownloaded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.tv_episode_downloaded),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                stringResource(R.string.tv_episode_downloaded),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Text(
                    episode.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!episode.overview.isNullOrBlank()) {
                    Text(
                        episode.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4
                    )
                }
                episode.runtime?.let {
                    Text(
                        stringResource(R.string.tv_episode_runtime, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
