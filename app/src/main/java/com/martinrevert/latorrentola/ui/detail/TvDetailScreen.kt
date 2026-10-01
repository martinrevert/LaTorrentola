package com.martinrevert.latorrentola.ui.detail

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.hazeGlass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeason
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.TMDB.TmdbCastCredit
import com.martinrevert.latorrentola.model.user.DownloadedEpisode
import com.martinrevert.latorrentola.ui.components.AdaptiveChip
import com.martinrevert.latorrentola.ui.components.ActorDetailBottomSheet
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
 * @param onFilmographyCreditClick Opens the selected movie or series from actor filmography.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
fun TvDetailScreen(
    viewModel: TvDetailViewModel,
    seriesId: Int,
    onEpisodeClick: (seriesName: String, episode: TmdbTvEpisode) -> Unit = { _, _ -> },
    onBackClick: () -> Unit,
    onFilmographyCreditClick: (TmdbCastCredit) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val seasonState by viewModel.seasonState.collectAsState()
    val downloadedEpisodes by viewModel.downloadedEpisodes.collectAsState()
    val favoriteTvSeriesIds by viewModel.favoriteTvSeriesIds.collectAsState()
    val favoriteActionError by viewModel.favoriteActionError.collectAsState()
    val selectedActorDetail by viewModel.selectedActorDetail.collectAsState()
    val isActorLoading by viewModel.isActorLoading.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val isTv = remember(context) { context.isTvDevice() }
    val hazeState = if (!isTv) rememberHazeState() else null
    val isInspection = LocalInspectionMode.current
    val isPreAndroid12 = !isInspection && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
    val topBarContainerColor = if (isPreAndroid12) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    } else {
        Color.Transparent
    }

    val detailContentFocusRequester = remember { FocusRequester() }
    val seriesInformationFocusRequester = remember { FocusRequester() }
    val firstCastFocusRequester = remember { FocusRequester() }
    var showActorSheet by remember { mutableStateOf(false) }
    val actorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val favoriteSnackbarHostState = remember { SnackbarHostState() }
    val firstContentFocusRequester =
        if (uiState is TvDetailUiState.Success) seriesInformationFocusRequester else null

    LaunchedEffect(seriesId) { viewModel.load(seriesId) }
    LaunchedEffect(favoriteActionError) {
        favoriteActionError?.let { message ->
            favoriteSnackbarHostState.showSnackbar(message)
            viewModel.clearFavoriteActionError()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(favoriteSnackbarHostState) },
        topBar = {
            TopAppBar(
                modifier = if (hazeState != null) {
                    Modifier.hazeGlass(input = HazeInput.Sources(hazeState))
                } else Modifier,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (!isTv) topBarContainerColor else MaterialTheme.colorScheme.surface),
                title = { Text(stringResource(R.string.tv_details_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .focusHighlight(shape = CircleShape)
                            .then(
                                if (isTv && firstContentFocusRequester != null) {
                                    Modifier
                                        .focusProperties { down = firstContentFocusRequester }
                                        .onPreviewKeyEvent { event ->
                                            if (
                                                event.type == KeyEventType.KeyDown &&
                                                event.key == Key.DirectionDown
                                            ) {
                                                firstContentFocusRequester.requestFocus()
                                                true
                                            } else {
                                                false
                                            }
                                        }
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
                },
                actions = {
                    val state = uiState
                    if (state is TvDetailUiState.Success) {
                        IconButton(
                            onClick = { viewModel.toggleFavorite(state.series) },
                            modifier = Modifier
                                .focusHighlight(shape = CircleShape)
                                .then(
                                    if (isTv && firstContentFocusRequester != null) {
                                        Modifier.focusProperties { down = firstContentFocusRequester }
                                    } else Modifier
                                )
                        ) {
                            Icon(
                                imageVector = if (state.series.id in favoriteTvSeriesIds) {
                                    Icons.Default.Favorite
                                } else {
                                    Icons.Default.FavoriteBorder
                                },
                                contentDescription = stringResource(
                                    if (state.series.id in favoriteTvSeriesIds) {
                                        R.string.remove_favorite_desc
                                    } else {
                                        R.string.add_favorite_desc
                                    }
                                ),
                                tint = if (state.series.id in favoriteTvSeriesIds) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        IconButton(
                            onClick = {
                                val imdbCode = state.imdbId?.let { if (it.startsWith("tt")) it else "tt$it" }
                                val shareUrl = if (!imdbCode.isNullOrBlank()) {
                                    "https://www.imdb.com/title/$imdbCode"
                                } else {
                                    "https://www.themoviedb.org/tv/${state.series.id}"
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, state.series.name)
                                    val shareText = resources.getString(
                                        R.string.share_movie_text,
                                        state.series.name,
                                        shareUrl
                                    )
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, resources.getString(R.string.share_movie_chooser)))
                            },
                            modifier = Modifier
                                .focusHighlight(shape = CircleShape)
                                .then(
                                    if (isTv && firstContentFocusRequester != null) {
                                        Modifier.focusProperties { down = firstContentFocusRequester }
                                    } else Modifier
                                )
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.share_desc)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val topContentPadding = if (!isTv) {
            padding.calculateTopPadding() + 64.dp
        } else {
            padding.calculateTopPadding() + 16.dp
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (hazeState != null) Modifier.hazeSource(state = hazeState) else Modifier)
                .consumeWindowInsets(padding)
        ) {
            when (val state = uiState) {
                TvDetailUiState.Loading -> MovieDetailPlaceholder(
                    contentPadding = PaddingValues(
                        top = topContentPadding,
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
                    seriesInformationFocusRequester = seriesInformationFocusRequester.takeIf { isTv },
                    firstCastFocusRequester = firstCastFocusRequester,
                    onCastClick = { personId ->
                        showActorSheet = true
                        viewModel.fetchActorDetails(personId)
                    },
                    topPadding = topContentPadding
                )
            }
        }
    }

    if (showActorSheet) {
        ActorDetailBottomSheet(
            actorDetailState = selectedActorDetail,
            isLoading = isActorLoading,
            onDismiss = {
                showActorSheet = false
                viewModel.clearSelectedActor()
            },
            sheetState = actorSheetState,
            onCreditClick = { credit ->
                showActorSheet = false
                viewModel.clearSelectedActor()
                onFilmographyCreditClick(credit)
            }
        )
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
 * @param seriesInformationFocusRequester Series title and summary focus target.
 * @param firstCastFocusRequester First cast member focus target, when cast is available.
 * @param onCastClick Opens the shared actor detail sheet for the selected TMDB person.
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
    seriesInformationFocusRequester: FocusRequester?,
    firstCastFocusRequester: FocusRequester,
    onCastClick: (Int) -> Unit,
    topPadding: Dp
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val isWide = LocalConfiguration.current.screenWidthDp >= 600 || isTv
    val cast = series.aggregateCredits?.cast.orEmpty()
    val orderedSeasons = remember(series.seasons) {
        series.seasons.orEmpty().sortedWith(
            compareBy<TmdbTvSeason> { it.seasonNumber == 0 }
                .thenByDescending { it.seasonNumber }
        )
    }

    // Downloaded episodes for active series
    val seriesDownloads = remember(downloadedEpisodes, series.id) {
        downloadedEpisodes.filter { it.seriesId == series.id }
    }

    val firstEpisodeFocusRequester = remember { FocusRequester() }

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
        if (isWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                TvSeriesHero(series, Modifier.weight(0.6f))
                TvSeriesInformation(
                    series,
                    Modifier.weight(0.4f),
                    informationFocusRequester = seriesInformationFocusRequester.takeIf { isTv },
                    nextFocusRequester = firstCastFocusRequester.takeIf { isTv && cast.isNotEmpty() }
                )
            }
        } else {
            Column {
                TvSeriesHero(series, Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                TvSeriesInformation(
                    series,
                    Modifier.fillMaxWidth(),
                    informationFocusRequester = seriesInformationFocusRequester.takeIf { isTv },
                    nextFocusRequester = firstCastFocusRequester.takeIf { isTv && cast.isNotEmpty() }
                )
            }
        }
        if (cast.isNotEmpty()) {
            Text(
                stringResource(R.string.tv_cast),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            LazyRow(
                modifier = Modifier.focusGroup(),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(cast, key = { it.id }) { actor ->
                    TvCastMember(
                        name = actor.name.orEmpty(),
                        character = actor.roles.orEmpty().firstOrNull()?.character.orEmpty(),
                        profileUrl = actor.fullProfileUrl,
                        onClick = { onCastClick(actor.id) },
                        modifier = Modifier
                            .then(
                                if (actor == cast.firstOrNull()) {
                                    Modifier.focusRequester(firstCastFocusRequester)
                                } else Modifier
                            )
                            .focusProperties {
                                down = contentFocusRequester
                                if (isTv) seriesInformationFocusRequester?.let { up = it }
                            }
                    )
                }
            }
        }
        if (orderedSeasons.isNotEmpty()) {
            Text(
                stringResource(R.string.tv_seasons),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier
                    .focusGroup()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                orderedSeasons.forEach { season ->
                    val baseName = season.name ?: stringResource(
                        R.string.tv_season_number,
                        season.seasonNumber
                    )
                    val downloadedCount = seriesDownloads
                        .filter { it.seasonNumber == season.seasonNumber }
                        .distinctBy { it.episodeNumber }
                        .size
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
                        modifier = if (
                            season.seasonNumber == selectedSeasonNumber ||
                            (selectedSeasonNumber == null && season == orderedSeasons.first())
                        ) {
                            Modifier
                                .focusRequester(contentFocusRequester)
                                .focusProperties {
                                    if (
                                        seasonState is TvSeasonUiState.Success &&
                                        seasonState.season.episodes.orEmpty().isNotEmpty()
                                    ) {
                                        down = firstEpisodeFocusRequester
                                    }
                                }
                        } else Modifier
                    )
                }
            }
        }
        Text(
            stringResource(R.string.tv_episodes),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        when (val episodes = seasonState) {
            TvSeasonUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            is TvSeasonUiState.Error -> Text(episodes.message, color = MaterialTheme.colorScheme.error)
            is TvSeasonUiState.Success -> {
                val episodeList = episodes.season.episodes.orEmpty()
                if (episodeList.isEmpty()) {
                    Text(
                        stringResource(R.string.tv_no_episodes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val episodeFocusRequesters = remember(episodeList.map { it.id }) {
                        episodeList.indices.map { index ->
                            if (index == 0) {
                                if (series.seasons.isNullOrEmpty()) {
                                    contentFocusRequester
                                } else {
                                    firstEpisodeFocusRequester
                                }
                            } else {
                                FocusRequester()
                            }
                        }
                    }
                    episodeList.forEachIndexed { index, episode ->
                        val isDownloaded = seriesDownloads.any {
                            it.seasonNumber == selectedSeasonNumber && it.episodeNumber == episode.episodeNumber
                        }
                        TvEpisodeCard(
                            episode = episode,
                            isDownloaded = isDownloaded,
                            onClick = { onEpisodeClick(episode) },
                            modifier = Modifier
                                .focusRequester(episodeFocusRequesters[index])
                                .focusProperties {
                                    if (!series.seasons.isNullOrEmpty()) {
                                        up = contentFocusRequester
                                    }
                                    if (index < episodeFocusRequesters.lastIndex) {
                                        down = episodeFocusRequesters[index + 1]
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Displays a cast member using the same circular portrait and focus treatment as movie cast cards.
 *
 * @param name Cast member name.
 * @param character Character played by the cast member.
 * @param profileUrl TMDB profile image URL.
 * @param onClick Opens the cast member details.
 * @param modifier Focus and layout modifiers for this cast member.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvCastMember(
    name: String,
    character: String,
    profileUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .width(90.dp)
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = profileUrl,
                contentDescription = name,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                placeholder = painterResource(R.drawable.ic_launcher_foreground),
                error = painterResource(R.drawable.ic_launcher_foreground),
                fallback = painterResource(R.drawable.ic_launcher_foreground),
                contentScale = ContentScale.Crop
            )
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (!isTv) {
                Text(
                    character,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            interactionSource = interactionSource,
            modifier = modifier
                .width(90.dp)
                .padding(4.dp)
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = MaterialTheme.shapes.small
                )
        ) {
            content()
        }
    } else {
        Column(
            modifier = modifier
                .width(80.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = profileUrl,
                contentDescription = name,
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                placeholder = painterResource(R.drawable.ic_launcher_foreground),
                error = painterResource(R.drawable.ic_launcher_foreground),
                fallback = painterResource(R.drawable.ic_launcher_foreground),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(4.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                character,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
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
 * @param informationFocusRequester Optional focus target for the title and summary.
 * @param nextFocusRequester Optional next focus target below the series information.
 */
@Composable
private fun TvSeriesInformation(
    series: TmdbTvSummary,
    modifier: Modifier,
    informationFocusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null
) {
    Column(modifier = modifier) {
        Text(
            series.name.orEmpty(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = informationFocusRequester?.let { requester ->
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
        if (!series.genres.isNullOrEmpty()) {
            Text(
                series.genres.orEmpty().joinToString(", ") { it.name },
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
 * Displays an episode with available still art, metadata, and an optional download badge.
 *
 * @param episode Episode details from the selected season.
 * @param isDownloaded Whether the episode has been downloaded at least once.
 * @param onClick Triggered when user taps the episode card.
 * @param modifier Layout and focus modifiers for the episode card.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvEpisodeCard(
    episode: TmdbTvEpisode,
    isDownloaded: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val cardContent: @Composable () -> Unit = {
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

    if (isTv) {
        androidx.tv.material3.Surface(
            onClick = onClick,
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
            modifier = modifier.fillMaxWidth()
        ) {
            cardContent()
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier.focusHighlight(shape = MaterialTheme.shapes.medium)
        ) {
            cardContent()
        }
    }
}
