@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.detail

import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.TMDB.TmdbCastCredit
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeason
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.user.DownloadedEpisode
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.ui.components.ActorDetailBottomSheet
import com.martinrevert.latorrentola.ui.components.AdaptiveChip
import com.martinrevert.latorrentola.ui.components.MovieDetailPlaceholder
import com.martinrevert.latorrentola.ui.components.MovieLogoHeader
import com.martinrevert.latorrentola.ui.components.WatchProgressBar
import com.martinrevert.latorrentola.ui.components.WatchProgressOptionDialog
import com.martinrevert.latorrentola.ui.components.adaptiveLongClickable
import com.martinrevert.latorrentola.ui.components.tvDpadLongClickable
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.LightDarkPreviews
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Presents TV series metadata, cast, seasons, and episodes with cinematic design parity
 * across Android TV, Foldables, Tablets, and Handheld devices.
 *
 * @param viewModel Series and season details state.
 * @param seriesId TMDB TV series identifier.
 * @param onEpisodeClick Triggers navigation to detail screen for a selected episode.
 * @param onBackClick Returns to the previous destination.
 * @param onFilmographyCreditClick Opens the selected movie or series from actor filmography.
 * @param themeMode User selected theme setting for mode propagation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvDetailScreen(
    viewModel: TvDetailViewModel,
    seriesId: Int,
    onEpisodeClick: (seriesName: String, episode: TmdbTvEpisode) -> Unit = { _, _ -> },
    onBackClick: () -> Unit,
    onFilmographyCreditClick: (TmdbCastCredit) -> Unit = {},
    themeMode: Int? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val seasonState by viewModel.seasonState.collectAsState()
    val downloadedEpisodes by viewModel.downloadedEpisodes.collectAsState()
    val watchHistoryMap by viewModel.watchHistoryMap.collectAsState()
    val favoriteTvSeriesIds by viewModel.favoriteTvSeriesIds.collectAsState()
    val favoriteActionError by viewModel.favoriteActionError.collectAsState()
    val selectedActorDetail by viewModel.selectedActorDetail.collectAsState()
    val isActorLoading by viewModel.isActorLoading.collectAsState()
    val logoUrl by viewModel.logoUrl.collectAsState()
    val isLogoLoading by viewModel.isLogoLoading.collectAsState()
    val trailerKey by viewModel.trailerKey.collectAsState()

    val context = LocalContext.current
    val resources = LocalResources.current
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }
    val isTv = remember(context) { context.isTvDevice() }
    val hazeState = if (!isTv) rememberHazeState() else null

    var showActorSheet by remember { mutableStateOf(false) }
    val actorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showTrailerDialog by remember { mutableStateOf(false) }
    var selectedSeasonForDialog by remember { mutableStateOf<TmdbTvSeason?>(null) }
    var selectedEpisodeForDialog by remember { mutableStateOf<TmdbTvEpisode?>(null) }
    val favoriteSnackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(seriesId) { viewModel.load(seriesId) }
    LaunchedEffect(favoriteActionError) {
        favoriteActionError?.let { message ->
            favoriteSnackbarHostState.showSnackbar(message)
            viewModel.clearFavoriteActionError()
        }
    }

    val content = @Composable {
        when (val state = uiState) {
            TvDetailUiState.Loading -> {
                val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val topPadding = if (isTv) 48.dp else statusBarHeight + 72.dp
                Box(modifier = Modifier.fillMaxSize()) {
                    MovieDetailPlaceholder(
                        contentPadding = PaddingValues(
                            top = topPadding,
                            start = if (isTv) 48.dp else 16.dp,
                            end = if (isTv) 48.dp else 16.dp,
                            bottom = 16.dp + navBarHeight
                        )
                    )
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_desc),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            is TvDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is TvDetailUiState.Success -> {
                val isFavorite = state.series.id in favoriteTvSeriesIds
                val onFavoriteToggle = { viewModel.toggleFavorite(state.series) }
                val onShareClick = {
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
                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            resources.getString(R.string.share_movie_chooser)
                        )
                    )
                }

                if (isTv) {
                    TvSeriesDetailTvContent(
                        series = state.series,
                        logoUrl = logoUrl,
                        isLogoLoading = isLogoLoading,
                        hasTrailer = !trailerKey.isNullOrBlank(),
                        isFavorite = isFavorite,
                        selectedSeasonNumber = selectedSeason?.seasonNumber,
                        seasonState = seasonState,
                        downloadedEpisodes = downloadedEpisodes,
                        watchHistoryMap = watchHistoryMap,
                        onWatchTrailer = { showTrailerDialog = true },
                        onSeasonSelected = viewModel::selectSeason,
                        onSeasonLongClick = { selectedSeasonForDialog = it },
                        onEpisodeClick = { episode -> onEpisodeClick(state.series.name.orEmpty(), episode) },
                        onEpisodeLongClick = { selectedEpisodeForDialog = it },
                        onBackClick = onBackClick,
                        onFavoriteToggle = onFavoriteToggle,
                        onShareClick = onShareClick,
                        onCastClick = { personId ->
                            showActorSheet = true
                            viewModel.fetchActorDetails(personId)
                        }
                    )
                } else {
                    HandheldTvSeriesDetailContent(
                        series = state.series,
                        logoUrl = logoUrl,
                        isLogoLoading = isLogoLoading,
                        hasTrailer = !trailerKey.isNullOrBlank(),
                        isFavorite = isFavorite,
                        selectedSeasonNumber = selectedSeason?.seasonNumber,
                        seasonState = seasonState,
                        downloadedEpisodes = downloadedEpisodes,
                        watchHistoryMap = watchHistoryMap,
                        hazeState = hazeState ?: rememberHazeState(),
                        onWatchTrailer = { showTrailerDialog = true },
                        onSeasonSelected = viewModel::selectSeason,
                        onSeasonLongClick = { selectedSeasonForDialog = it },
                        onEpisodeClick = { episode -> onEpisodeClick(state.series.name.orEmpty(), episode) },
                        onEpisodeLongClick = { selectedEpisodeForDialog = it },
                        onBackClick = onBackClick,
                        onFavoriteToggle = onFavoriteToggle,
                        onShareClick = onShareClick,
                        onCastClick = { personId ->
                            showActorSheet = true
                            viewModel.fetchActorDetails(personId)
                        }
                    )
                }
            }
        }
    }

    if (isTv) {
        TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()
                SnackbarHost(
                    hostState = favoriteSnackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
                )
            }
        }
    } else {
        LaTorrentolaTheme(themeMode = effectiveThemeMode) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()
                SnackbarHost(
                    hostState = favoriteSnackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
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

    selectedSeasonForDialog?.let { season ->
        WatchProgressOptionDialog(
            title = stringResource(R.string.mark_season_title),
            option1Text = stringResource(R.string.mark_season_completed),
            option2Text = stringResource(R.string.mark_season_uncompleted),
            onOption1Selected = { viewModel.markSeasonAsCompleted(season) },
            onOption2Selected = { viewModel.markSeasonAsUncompleted(season) },
            onDismissRequest = { selectedSeasonForDialog = null }
        )
    }

    selectedEpisodeForDialog?.let { episode ->
        WatchProgressOptionDialog(
            title = stringResource(R.string.mark_episode_title),
            option1Text = stringResource(R.string.mark_episode_completed),
            option2Text = stringResource(R.string.mark_episode_uncompleted),
            onOption1Selected = { viewModel.markEpisodeAsCompleted(episode) },
            onOption2Selected = { viewModel.markEpisodeAsUncompleted(episode) },
            onDismissRequest = { selectedEpisodeForDialog = null }
        )
    }

    if (showTrailerDialog && !trailerKey.isNullOrBlank()) {
        if (isTv) {
            TvTrailerDialog(
                youtubeVideoId = trailerKey!!,
                onDismiss = { showTrailerDialog = false },
                themeMode = effectiveThemeMode
            )
        } else {
            HandheldTrailerDialog(
                youtubeVideoId = trailerKey!!,
                onDismiss = { showTrailerDialog = false }
            )
        }
    }
}

/**
 * 10-foot TV series detail layout with edge-to-edge backdrop, cinematic hero, action buttons,
 * smooth D-pad navigation, cast row, and episode cards.
 */
@Composable
private fun TvSeriesDetailTvContent(
    series: TmdbTvSummary,
    logoUrl: String?,
    isLogoLoading: Boolean,
    hasTrailer: Boolean,
    isFavorite: Boolean,
    selectedSeasonNumber: Int?,
    seasonState: TvSeasonUiState,
    downloadedEpisodes: List<DownloadedEpisode>,
    watchHistoryMap: Map<String, PlaybackProgress>,
    onWatchTrailer: () -> Unit,
    onSeasonSelected: (TmdbTvSeason) -> Unit,
    onSeasonLongClick: (TmdbTvSeason) -> Unit,
    onEpisodeClick: (TmdbTvEpisode) -> Unit,
    onEpisodeLongClick: (TmdbTvEpisode) -> Unit,
    onBackClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onShareClick: () -> Unit,
    onCastClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val backButtonFocusRequester = remember { FocusRequester() }
    val firstActionFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val firstCastFocusRequester = remember { FocusRequester() }
    val firstEpisodeFocusRequester = remember { FocusRequester() }

    var isRevealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isRevealed = true
        try {
            withFrameNanos { }
            backButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val posterScale by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0.70f,
        animationSpec = tween(durationMillis = 650, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "tvPosterScale"
    )
    val posterAlpha by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "tvPosterAlpha"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 100),
        label = "tvContentAlpha"
    )
    val backdropAlpha by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = LinearOutSlowInEasing),
        label = "tvBackdropAlpha"
    )

    val backdropUrl = series.fullBackdropUrl
    val posterUrl = series.fullPosterUrl ?: series.fullBackdropUrl
    val cast = series.aggregateCredits?.cast.orEmpty()
    val orderedSeasons = remember(series.seasons) {
        series.seasons.orEmpty().sortedWith(
            compareBy<TmdbTvSeason> { it.seasonNumber == 0 }
                .thenByDescending { it.seasonNumber }
        )
    }
    val seriesDownloads = remember(downloadedEpisodes, series.id) {
        downloadedEpisodes.filter { it.seriesId == series.id }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 1. Edge-to-Edge Dynamic TMDB Backdrop
        if (!backdropUrl.isNullOrEmpty()) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .graphicsLayer { alpha = backdropAlpha }
            )
            // Top gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                Color.Transparent
                            )
                        )
                    )
            )
            // Left horizontal gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .fillMaxHeight(0.85f)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                Color.Transparent
                            )
                        )
                    )
            )
            // Bottom vertical gradient scrim fading seamlessly to solid surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
        }

        // 2. 10-foot Cinematic Scrollable Content
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            // Top Navigation Bar (Back button only; actions moved to hero section)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .focusRequester(backButtonFocusRequester)
                        .focusHighlight(shape = CircleShape)
                        .focusProperties { down = firstActionFocusRequester }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_desc),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hero Section: Morphing Poster Card on Left, Logo & Badges on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (!posterUrl.isNullOrEmpty()) {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .width(190.dp)
                            .aspectRatio(2 / 3f)
                            .graphicsLayer {
                                scaleX = posterScale
                                scaleY = posterScale
                                alpha = posterAlpha
                            }
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                shape = MaterialTheme.shapes.medium
                            )
                    ) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = series.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = contentAlpha }
                ) {
                    MovieLogoHeader(
                        title = series.name.orEmpty(),
                        logoUrl = logoUrl,
                        isLogoLoading = isLogoLoading,
                        textStyle = MaterialTheme.typography.headlineLarge,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        minHeight = 40.dp,
                        maxHeight = 76.dp,
                        maxWidth = 480.dp,
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TvSeriesMetadataBadges(series = series)

                    if (!series.genres.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = series.genres.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!series.overview.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = series.overview,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons Row (Watch Trailer, Favorite, Share)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup()
                    ) {
                        // Trailer Button
                        if (hasTrailer) {
                            TvButton(
                                onClick = onWatchTrailer,
                                modifier = Modifier
                                    .focusRequester(firstActionFocusRequester)
                                    .focusProperties {
                                        up = backButtonFocusRequester
                                        down = if (cast.isNotEmpty()) firstCastFocusRequester else contentFocusRequester
                                    },
                                colors = TvButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    focusedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = TvButtonDefaults.shape(shape = MaterialTheme.shapes.small)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.watch_trailer),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Favorite Button
                        TvButton(
                            onClick = onFavoriteToggle,
                            modifier = Modifier
                                .then(if (!hasTrailer) Modifier.focusRequester(firstActionFocusRequester) else Modifier)
                                .focusProperties {
                                    up = backButtonFocusRequester
                                    down = if (cast.isNotEmpty()) firstCastFocusRequester else contentFocusRequester
                                },
                            colors = TvButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                focusedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = TvButtonDefaults.shape(shape = MaterialTheme.shapes.small)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isFavorite) stringResource(R.string.remove_favorite_desc) else stringResource(R.string.add_favorite_desc),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Share Button
                        TvButton(
                            onClick = onShareClick,
                            modifier = Modifier
                                .focusProperties {
                                    up = backButtonFocusRequester
                                    down = if (cast.isNotEmpty()) firstCastFocusRequester else contentFocusRequester
                                },
                            colors = TvButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                focusedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = TvButtonDefaults.shape(shape = MaterialTheme.shapes.small)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.share_desc),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Cast Section
            if (cast.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.tv_cast),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    modifier = Modifier
                        .focusRestorer()
                        .focusGroup(),
                    contentPadding = PaddingValues(vertical = 4.dp),
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
                                    up = firstActionFocusRequester
                                    down = contentFocusRequester
                                }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Seasons Section
            if (orderedSeasons.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.tv_seasons),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
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
                        val totalCount = if (
                            seasonState is TvSeasonUiState.Success &&
                            seasonState.season.seasonNumber == season.seasonNumber
                        ) {
                            seasonState.season.episodes.orEmpty().size.coerceAtLeast(season.episodeCount ?: 0)
                        } else {
                            season.episodeCount ?: 0
                        }
                        val statusLabel = when {
                            downloadedCount > 0 && totalCount > 0 && downloadedCount >= totalCount ->
                                "$baseName · " + stringResource(R.string.tv_season_completed)
                            downloadedCount > 0 ->
                                if (totalCount > 0) {
                                    "$baseName · " + stringResource(R.string.tv_season_partial, downloadedCount, totalCount)
                                } else {
                                    "$baseName · " + stringResource(R.string.tv_season_partial, downloadedCount, downloadedCount)
                                }
                            else -> baseName
                        }

                        AdaptiveChip(
                            selected = selectedSeasonNumber == season.seasonNumber,
                            onClick = { onSeasonSelected(season) },
                            onLongClick = { onSeasonLongClick(season) },
                            label = { Text(statusLabel) },
                            modifier = if (
                                season.seasonNumber == selectedSeasonNumber ||
                                (selectedSeasonNumber == null && season == orderedSeasons.first())
                            ) {
                                Modifier
                                    .focusRequester(contentFocusRequester)
                                    .focusProperties {
                                        up = if (cast.isNotEmpty()) firstCastFocusRequester else firstActionFocusRequester
                                        if (seasonState is TvSeasonUiState.Success && seasonState.season.episodes.orEmpty().isNotEmpty()) {
                                            down = firstEpisodeFocusRequester
                                        }
                                    }
                            } else Modifier
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Episodes Section
            Text(
                text = stringResource(R.string.tv_episodes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (seasonState) {
                TvSeasonUiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is TvSeasonUiState.Error -> Text(seasonState.message, color = MaterialTheme.colorScheme.error)
                is TvSeasonUiState.Success -> {
                    val episodeList = seasonState.season.episodes.orEmpty()
                    if (episodeList.isEmpty()) {
                        Text(
                            text = stringResource(R.string.tv_no_episodes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val episodeFocusRequesters = remember(episodeList.map { it.id }) {
                            episodeList.indices.map { index ->
                                if (index == 0) firstEpisodeFocusRequester else FocusRequester()
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            episodeList.forEachIndexed { index, episode ->
                                val isDownloaded = seriesDownloads.any {
                                    it.seasonNumber == (selectedSeasonNumber ?: episode.seasonNumber) &&
                                            it.episodeNumber == episode.episodeNumber
                                }
                                val mediaId = "media_${series.id}_s${episode.seasonNumber}_e${episode.episodeNumber}"
                                val progress = watchHistoryMap[mediaId]
                                val progressPercent = progress?.let { if (it.isCompleted) 100 else it.progressPercent }
                                TvEpisodeCard(
                                    episode = episode,
                                    isDownloaded = isDownloaded,
                                    progressPercent = progressPercent,
                                    onClick = { onEpisodeClick(episode) },
                                    onLongClick = { onEpisodeLongClick(episode) },
                                    modifier = Modifier
                                        .focusRequester(episodeFocusRequesters[index])
                                        .focusProperties {
                                            // FIX: Only index 0 navigates UP to contentFocusRequester (season pills)
                                            // Subsequent indices navigate UP to the previous episode card
                                            up = if (index == 0) {
                                                contentFocusRequester
                                            } else {
                                                episodeFocusRequesters[index - 1]
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
    }
}

/**
 * Handheld TV series detail layout with responsive adaptations across Phones (<600dp),
 * Foldables (600–840dp), and Tablets (>840dp).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HandheldTvSeriesDetailContent(
    series: TmdbTvSummary,
    logoUrl: String?,
    isLogoLoading: Boolean,
    hasTrailer: Boolean,
    isFavorite: Boolean,
    selectedSeasonNumber: Int?,
    seasonState: TvSeasonUiState,
    downloadedEpisodes: List<DownloadedEpisode>,
    watchHistoryMap: Map<String, PlaybackProgress>,
    hazeState: HazeState,
    onWatchTrailer: () -> Unit,
    onSeasonSelected: (TmdbTvSeason) -> Unit,
    onSeasonLongClick: (TmdbTvSeason) -> Unit,
    onEpisodeClick: (TmdbTvEpisode) -> Unit,
    onEpisodeLongClick: (TmdbTvEpisode) -> Unit,
    onBackClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onShareClick: () -> Unit,
    onCastClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isTablet = screenWidthDp >= 840
    val isWideScreen = screenWidthDp >= 600

    var isRevealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isRevealed = true }

    val posterScale by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0.72f,
        animationSpec = tween(durationMillis = 600, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "handheldPosterScale"
    )
    val posterAlpha by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "handheldPosterAlpha"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(durationMillis = 450, delayMillis = 80),
        label = "handheldContentAlpha"
    )
    val backdropAlpha by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing),
        label = "handheldBackdropAlpha"
    )

    val backdropUrl = series.fullBackdropUrl
    val posterUrl = series.fullPosterUrl ?: series.fullBackdropUrl
    val cast = series.aggregateCredits?.cast.orEmpty()
    val orderedSeasons = remember(series.seasons) {
        series.seasons.orEmpty().sortedWith(
            compareBy<TmdbTvSeason> { it.seasonNumber == 0 }
                .thenByDescending { it.seasonNumber }
        )
    }
    val seriesDownloads = remember(downloadedEpisodes, series.id) {
        downloadedEpisodes.filter { it.seriesId == series.id }
    }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .hazeSource(state = hazeState)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 1. Dynamic TMDB Backdrop with Directional Scrims
        if (!backdropUrl.isNullOrEmpty()) {
            val backdropHeight = if (isWideScreen) 380.dp else 260.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(backdropHeight)
            ) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = backdropAlpha }
                )
                // Top scrim for status bar legibility
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                // Horizontal scrim on wide tablets / foldables
                if (isWideScreen) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
                // Bottom fade to solid background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                )
            }
        }

        // 2. Main Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    top = statusBarHeight + 56.dp,
                    bottom = navBarHeight + 24.dp,
                    start = if (isWideScreen) 28.dp else 16.dp,
                    end = if (isWideScreen) 28.dp else 16.dp
                )
        ) {
            if (isWideScreen) {
                // FOLDABLE UNPINNED / TABLET (>= 600dp): Wide Cinematic Split Layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (!posterUrl.isNullOrEmpty()) {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            modifier = Modifier
                                .width(180.dp)
                                .aspectRatio(2 / 3f)
                                .graphicsLayer {
                                    scaleX = posterScale
                                    scaleY = posterScale
                                    alpha = posterAlpha
                                }
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    shape = MaterialTheme.shapes.medium
                                )
                        ) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = series.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { alpha = contentAlpha }
                    ) {
                        MovieLogoHeader(
                            title = series.name.orEmpty(),
                            logoUrl = logoUrl,
                            isLogoLoading = isLogoLoading,
                            textStyle = MaterialTheme.typography.headlineMedium,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            minHeight = 36.dp,
                            maxHeight = 68.dp,
                            maxWidth = 420.dp,
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TvSeriesMetadataBadges(series = series)

                        if (!series.genres.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = series.genres.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        HandheldTvActionButtons(
                            hasTrailer = hasTrailer,
                            isFavorite = isFavorite,
                            onWatchTrailer = onWatchTrailer,
                            onFavoriteToggle = onFavoriteToggle,
                            onShareClick = onShareClick
                        )

                        if (!series.overview.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = series.overview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                // PHONE (< 600dp): Compact Two-Row Hero Layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (!posterUrl.isNullOrEmpty()) {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            modifier = Modifier
                                .width(115.dp)
                                .aspectRatio(2 / 3f)
                                .graphicsLayer {
                                    scaleX = posterScale
                                    scaleY = posterScale
                                    alpha = posterAlpha
                                }
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    shape = MaterialTheme.shapes.medium
                                )
                        ) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = series.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { alpha = contentAlpha }
                    ) {
                        MovieLogoHeader(
                            title = series.name.orEmpty(),
                            logoUrl = logoUrl,
                            isLogoLoading = isLogoLoading,
                            textStyle = MaterialTheme.typography.titleLarge,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            minHeight = 30.dp,
                            maxHeight = 54.dp,
                            maxWidth = 260.dp,
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TvSeriesMetadataBadges(series = series)

                        Spacer(modifier = Modifier.height(12.dp))

                        HandheldTvActionButtons(
                            hasTrailer = hasTrailer,
                            isFavorite = isFavorite,
                            onWatchTrailer = onWatchTrailer,
                            onFavoriteToggle = onFavoriteToggle,
                            onShareClick = onShareClick
                        )
                    }
                }

                if (!series.overview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = series.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Cast Section
            if (cast.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.tv_cast),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(cast, key = { it.id }) { actor ->
                        TvCastMember(
                            name = actor.name.orEmpty(),
                            character = actor.roles.orEmpty().firstOrNull()?.character.orEmpty(),
                            profileUrl = actor.fullProfileUrl,
                            onClick = { onCastClick(actor.id) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Seasons Section
            if (orderedSeasons.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.tv_seasons),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
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
                        val totalCount = if (
                            seasonState is TvSeasonUiState.Success &&
                            seasonState.season.seasonNumber == season.seasonNumber
                        ) {
                            seasonState.season.episodes.orEmpty().size.coerceAtLeast(season.episodeCount ?: 0)
                        } else {
                            season.episodeCount ?: 0
                        }
                        val statusLabel = when {
                            downloadedCount > 0 && totalCount > 0 && downloadedCount >= totalCount ->
                                "$baseName · " + stringResource(R.string.tv_season_completed)
                            downloadedCount > 0 ->
                                if (totalCount > 0) {
                                    "$baseName · " + stringResource(R.string.tv_season_partial, downloadedCount, totalCount)
                                } else {
                                    "$baseName · " + stringResource(R.string.tv_season_partial, downloadedCount, downloadedCount)
                                }
                            else -> baseName
                        }

                        AdaptiveChip(
                            selected = selectedSeasonNumber == season.seasonNumber,
                            onClick = { onSeasonSelected(season) },
                            onLongClick = { onSeasonLongClick(season) },
                            label = { Text(statusLabel) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Episodes Section
            Text(
                text = stringResource(R.string.tv_episodes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            when (seasonState) {
                TvSeasonUiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is TvSeasonUiState.Error -> Text(seasonState.message, color = MaterialTheme.colorScheme.error)
                is TvSeasonUiState.Success -> {
                    val episodeList = seasonState.season.episodes.orEmpty()
                    if (episodeList.isEmpty()) {
                        Text(
                            text = stringResource(R.string.tv_no_episodes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (isTablet) {
                        // Tablet 2-column episode cards layout
                        val chunkedEpisodes = remember(episodeList) { episodeList.chunked(2) }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            chunkedEpisodes.forEach { rowEpisodes ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowEpisodes.forEach { episode ->
                                        val isDownloaded = seriesDownloads.any {
                                            it.seasonNumber == (selectedSeasonNumber ?: episode.seasonNumber) &&
                                                    it.episodeNumber == episode.episodeNumber
                                        }
                                        val mediaId = "media_${series.id}_s${episode.seasonNumber}_e${episode.episodeNumber}"
                                        val progress = watchHistoryMap[mediaId]
                                        val progressPercent = progress?.let { if (it.isCompleted) 100 else it.progressPercent }
                                        Box(modifier = Modifier.weight(1f)) {
                                            TvEpisodeCard(
                                                episode = episode,
                                                isDownloaded = isDownloaded,
                                                progressPercent = progressPercent,
                                                onClick = { onEpisodeClick(episode) },
                                                onLongClick = { onEpisodeLongClick(episode) }
                                            )
                                        }
                                    }
                                    if (rowEpisodes.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        // Phone / Foldable 1-column episode cards layout
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            episodeList.forEach { episode ->
                                val isDownloaded = seriesDownloads.any {
                                    it.seasonNumber == (selectedSeasonNumber ?: episode.seasonNumber) &&
                                            it.episodeNumber == episode.episodeNumber
                                }
                                val mediaId = "media_${series.id}_s${episode.seasonNumber}_e${episode.episodeNumber}"
                                val progress = watchHistoryMap[mediaId]
                                val progressPercent = progress?.let { if (it.isCompleted) 100 else it.progressPercent }
                                TvEpisodeCard(
                                    episode = episode,
                                    isDownloaded = isDownloaded,
                                    progressPercent = progressPercent,
                                    onClick = { onEpisodeClick(episode) },
                                    onLongClick = { onEpisodeLongClick(episode) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Floating Glass Top Navigation Bar (Back button only; actions relocated to action buttons row)
        val isInspection = LocalInspectionMode.current
        val isPreAndroid12 = !isInspection && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_desc),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (isPreAndroid12) MaterialTheme.colorScheme.surface.copy(alpha = 0.85f) else Color.Transparent
            ),
            modifier = Modifier
                .statusBarsPadding()
                .hazeGlass(input = HazeInput.Sources(hazeState))
        )
    }
}

/**
 * Metadata badges row for TV series (Air year, rating, seasons, episodes, status).
 */
@Composable
private fun TvSeriesMetadataBadges(
    series: TmdbTvSummary,
    modifier: Modifier = Modifier
) {
    val items = mutableListOf<String>()
    series.firstAirYear.takeIf(String::isNotBlank)?.let { items.add(it) }
    series.voteAverage?.takeIf { it > 0.0 }?.let { items.add("★ %.1f".format(Locale.US, it)) }
    series.numberOfSeasons?.let { items.add(stringResource(R.string.tv_season_count, it)) }
    series.numberOfEpisodes?.let { items.add(stringResource(R.string.tv_episode_count, it)) }
    series.status?.let { items.add(it) }

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { badge ->
            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = SuggestionChipDefaults.suggestionChipBorder(
                    enabled = true,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    borderWidth = 1.dp
                )
            )
        }
    }
}

/**
 * Action buttons for handheld TV series detail (Watch Trailer, Favorite toggle, and Share).
 */
@Composable
private fun HandheldTvActionButtons(
    hasTrailer: Boolean,
    isFavorite: Boolean,
    onWatchTrailer: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        if (hasTrailer) {
            FilledTonalButton(
                onClick = onWatchTrailer,
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.watch_trailer),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        FilledTonalButton(
            onClick = onFavoriteToggle,
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isFavorite) stringResource(R.string.remove_favorite_desc) else stringResource(R.string.add_favorite_desc),
                style = MaterialTheme.typography.labelMedium
            )
        }

        FilledTonalButton(
            onClick = onShareClick,
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Displays a cast member using the same circular portrait and focus treatment as movie cast cards.
 */
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
                .width(88.dp)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = profileUrl,
                contentDescription = name,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = if (isFocused) 3.dp else 1.dp,
                        color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                placeholder = painterResource(R.drawable.ic_launcher_foreground),
                error = painterResource(R.drawable.ic_launcher_foreground),
                fallback = painterResource(R.drawable.ic_launcher_foreground),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isFocused) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (!isTv && character.isNotBlank()) {
                Text(
                    text = character,
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
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            interactionSource = interactionSource,
            modifier = modifier
                .width(105.dp)
                .padding(4.dp)
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
                text = name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (character.isNotBlank()) {
                Text(
                    text = character,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Displays an episode with available still art, metadata, and an optional download badge.
 */
@Composable
private fun TvEpisodeCard(
    episode: TmdbTvEpisode,
    isDownloaded: Boolean = false,
    progressPercent: Int? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
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
                Box(
                    modifier = Modifier
                        .width(if (isTv) 180.dp else 140.dp)
                        .aspectRatio(16 / 9f)
                        .clip(MaterialTheme.shapes.small)
                ) {
                    AsyncImage(
                        model = episode.fullStillUrl,
                        contentDescription = episode.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    WatchProgressBar(
                        progressPercent = progressPercent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tv_episode_number, episode.episodeNumber),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (isDownloaded) Icons.Default.CloudDone else Icons.Default.Download,
                        contentDescription = if (isDownloaded) stringResource(R.string.tv_episode_downloaded) else null,
                        tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = episode.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!episode.overview.isNullOrBlank()) {
                    Text(
                        text = episode.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                episode.runtime?.let {
                    Text(
                        text = stringResource(R.string.tv_episode_runtime, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = modifier
                .fillMaxWidth()
                .tvDpadLongClickable(onLongClick)
        ) {
            cardContent()
        }
    } else {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .focusHighlight(shape = MaterialTheme.shapes.medium)
                .adaptiveLongClickable(onClick = onClick, onLongClick = onLongClick),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            cardContent()
        }
    }
}

@LightDarkPreviews
@Composable
private fun TvEpisodeCardPreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        TvEpisodeCard(
            episode = TmdbTvEpisode(
                id = 1,
                name = "Pilot",
                overview = "A high school chemistry teacher discovers he has lung cancer.",
                seasonNumber = 1,
                episodeNumber = 1,
                runtime = 58,
                voteAverage = 8.9,
                airDate = "2008-01-20"
            ),
            isDownloaded = false,
            onClick = {}
        )
    }
}

/**
 * Handheld modal dialog for playing YouTube trailers on mobile and tablet form factors.
 */
@Composable
private fun HandheldTrailerDialog(
    youtubeVideoId: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.90f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(16 / 9f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.scrim)
            ) {
                YoutubePlayer(
                    youtubeVideoId = youtubeVideoId,
                    lifecycleOwner = LocalLifecycleOwner.current
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.70f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close_trailer),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Focus-isolated modal dialog for playing YouTube trailers on Android TV without corrupting D-pad navigation.
 */
@Composable
private fun TvTrailerDialog(
    youtubeVideoId: String,
    onDismiss: () -> Unit,
    themeMode: Int? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val context = LocalContext.current
        val effectiveThemeMode = remember(context, themeMode) {
            themeMode ?: PreferenceManager(context).getTheme()
        }
        TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
            val playFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                playFocusRequester.requestFocus()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim),
                contentAlignment = Alignment.Center
            ) {
                YoutubePlayer(
                    youtubeVideoId = youtubeVideoId,
                    lifecycleOwner = LocalLifecycleOwner.current,
                    focusRequester = playFocusRequester
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(32.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
                        .focusHighlight(shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.close_trailer),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
