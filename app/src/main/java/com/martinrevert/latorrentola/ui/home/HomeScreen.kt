package com.martinrevert.latorrentola.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.layout.onSizeChanged
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.TMDB.TmdbTvGenre
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.ui.components.MovieListPlaceholder
import com.martinrevert.latorrentola.ui.components.QualityChips
import com.martinrevert.latorrentola.ui.components.GenreChips
import com.martinrevert.latorrentola.ui.components.GenreBottomSheet
import com.martinrevert.latorrentola.ui.components.MovieList
import com.martinrevert.latorrentola.ui.components.TvFeedChips
import com.martinrevert.latorrentola.ui.components.TvGenreBottomSheet
import com.martinrevert.latorrentola.ui.components.TvGenreChips
import com.martinrevert.latorrentola.ui.components.TvSeriesGrid
import com.martinrevert.latorrentola.network.TmdbTvFeed
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.isTvDevice
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.hazeGlass

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalTvMaterial3Api::class)
/**
 * Connects movie and TV home state to the adaptive home layout.
 *
 * @param viewModel Existing movie-feed state and actions.
 * @param tvHomeViewModel TMDB TV feeds, genres, and focus state.
 * @param userPhotoUrl Signed-in user's avatar URL.
 * @param onMovieClick Opens a movie detail destination.
 * @param onTvSeriesClick Opens a TV series detail destination.
 * @param onTvGenreClick Opens results for a selected TMDB TV genre.
 * @param onSettingsClick Opens settings.
 * @param onSearchClick Opens search in the selected catalog mode.
 * @param onFavoritesClick Opens the user's movie favorites.
 * @param onGenreClick Opens the selected movie genre.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    tvHomeViewModel: TvHomeViewModel,
    userPhotoUrl: String?,
    onMovieClick: (Movie) -> Unit,
    onTvSeriesClick: (TmdbTvSummary) -> Unit,
    onTvGenreClick: (TmdbTvGenre) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: (isTvMode: Boolean) -> Unit,
    onFavoritesClick: () -> Unit,
    onGenreClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val topGenres by viewModel.topGenres.collectAsState()
    val lastVisitDate by viewModel.lastVisitDate.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val favoritesCount by viewModel.favoritesCount.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()
    val lastClickedMovieId by viewModel.lastClickedMovieId.collectAsState()
    val downloadedMovieIds by viewModel.downloadedMovieIds.collectAsState()
    val watchHistoryMap by viewModel.watchHistoryMap.collectAsState()
    val qualityOptions = viewModel.qualityOptions
    val allGenres = viewModel.allGenres
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }

    HomeScreenContent(
        uiState = uiState,
        topGenres = topGenres,
        allGenres = allGenres,
        lastVisitDate = lastVisitDate,
        isRefreshing = isRefreshing,
        isLoadingMore = isLoadingMore,
        favoritesCount = favoritesCount,
        selectedQuality = selectedQuality,
        lastClickedMovieId = lastClickedMovieId,
        downloadedMovieIds = downloadedMovieIds,
        watchHistoryMap = watchHistoryMap,
        qualityOptions = qualityOptions,
        userPhotoUrl = userPhotoUrl,
        isTv = isTv,
        onMovieClick = onMovieClick,
        onSettingsClick = onSettingsClick,
        onSearchClick = onSearchClick,
        onFavoritesClick = onFavoritesClick,
        onGenreClick = onGenreClick,
        onQualityClick = { viewModel.setQuality(it) },
        onLoadMore = { viewModel.loadMovies() },
        onRefresh = { showIndicator -> viewModel.refresh(showIndicator = showIndicator) },
        onSetLastClickedMovieId = { viewModel.setLastClickedMovieId(it) },
        onFocusRestored = { viewModel.clearLastClickedMovieId() },
        tvHomeViewModel = tvHomeViewModel,
        onTvSeriesClick = onTvSeriesClick,
        onTvGenreClick = onTvGenreClick
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalTvMaterial3Api::class)
/**
 * Renders home feed content and controls, sizing handheld list padding to the measured header.
 */
@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    topGenres: List<String>,
    allGenres: List<String>,
    lastVisitDate: Long?,
    isRefreshing: Boolean,
    isLoadingMore: Boolean = false,
    favoritesCount: Int,
    selectedQuality: String?,
    lastClickedMovieId: Int?,
    downloadedMovieIds: Set<Int>,
    watchHistoryMap: Map<String, PlaybackProgress>,
    qualityOptions: List<String>,
    userPhotoUrl: String?,
    isTv: Boolean,
    onMovieClick: (Movie) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: (isTvMode: Boolean) -> Unit,
    onFavoritesClick: () -> Unit,
    onGenreClick: (String) -> Unit,
    onQualityClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: (Boolean) -> Unit,
    onSetLastClickedMovieId: (Int?) -> Unit,
    onFocusRestored: () -> Unit,
    tvHomeViewModel: TvHomeViewModel? = null,
    onTvSeriesClick: (TmdbTvSummary) -> Unit = {},
    onTvGenreClick: (TmdbTvGenre) -> Unit = {}
) {
    var showGenreSheet by remember { mutableStateOf(false) }
    var showTvGenreSheet by remember { mutableStateOf(false) }
    var isTvMode by rememberSaveable { mutableStateOf(false) }
    val homeControlsFocusRequester = remember { FocusRequester() }
    var homeControlsHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tvGenreSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 1. Properly save and restore scroll state across configuration changes (rotation)
    val gridState = rememberLazyGridState()
    val tvGridState = rememberLazyGridState()
    val tvUiState = tvHomeViewModel?.uiState?.collectAsState()?.value ?: TvHomeUiState.Loading
    val tvGenres = tvHomeViewModel?.tvGenres?.collectAsState()?.value.orEmpty()
    val tvGenreError = tvHomeViewModel?.genreError?.collectAsState()?.value
    val selectedTvFeed = tvHomeViewModel?.selectedFeed?.collectAsState()?.value ?: TmdbTvFeed.ON_THE_AIR
    val tvIsRefreshing = tvHomeViewModel?.isRefreshing?.collectAsState()?.value ?: false
    val tvIsLoadingMore = tvHomeViewModel?.isLoadingMore?.collectAsState()?.value ?: false
    val lastClickedSeriesId = tvHomeViewModel?.lastClickedSeriesId?.collectAsState()?.value
    val downloadedSeriesIds = tvHomeViewModel?.downloadedSeriesIds?.collectAsState()?.value.orEmpty()
    val scope = rememberCoroutineScope()
    /** Switches catalogs and starts the selected catalog at its first poster. */
    fun selectMediaMode(tvMode: Boolean) {
        if (isTvMode == tvMode) return
        isTvMode = tvMode
        scope.launch {
            (if (tvMode) tvGridState else gridState).scrollToItem(0)
        }
    }

    LaunchedEffect(isTvMode) {
        if (isTvMode) tvHomeViewModel?.activate()
    }

    val isInspection = LocalInspectionMode.current
    val isPreAndroid12 = !isInspection && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
    val topBarContainerColor = if (isPreAndroid12) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    } else {
        Color.Transparent
    }

    val hazeState = if (!isTv) rememberHazeState() else null

    val isScrolled by remember {
        derivedStateOf {
            val activeGrid = if (isTvMode) tvGridState else gridState
            activeGrid.firstVisibleItemIndex > 0 || activeGrid.firstVisibleItemScrollOffset > 10
        }
    }

    val hazeAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 0.15f else 1f,
        animationSpec = tween(
            durationMillis = 600,
            easing = EaseInOutCubic
        ),
        label = "hazeAlpha"
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (!isTv) {
                HomeTopAppBar(
                    userPhotoUrl = userPhotoUrl,
                    favoritesCount = favoritesCount,
                    onSearchClick = onSearchClick,
                    onFavoritesClick = onFavoritesClick,
                    onSettingsClick = onSettingsClick,
                    isTvMode = isTvMode,
                    onMoviesClick = { selectMediaMode(false) },
                    onTvClick = { selectMediaMode(true) },
                    focusDownRequester = homeControlsFocusRequester,
                    containerColor = topBarContainerColor,
                    modifier = if (hazeState != null) {
                        Modifier
                            .graphicsLayer { alpha = hazeAlpha }
                            .hazeGlass(input = HazeInput.Sources(hazeState))
                    } else Modifier
                )
            }
        }
    ) { padding ->
        val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (hazeState != null) Modifier.hazeSource(state = hazeState) else Modifier)
        ) {
            if (isTv) {
                // TV Layout: Single vertical column for unbroken D-pad focus traversal
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding)
                ) {
                    HomeTopAppBar(
                        userPhotoUrl = userPhotoUrl,
                        favoritesCount = favoritesCount,
                        onSearchClick = onSearchClick,
                        onFavoritesClick = onFavoritesClick,
                        onSettingsClick = onSettingsClick,
                        isTvMode = isTvMode,
                        onMoviesClick = { selectMediaMode(false) },
                        onTvClick = { selectMediaMode(true) },
                        focusDownRequester = homeControlsFocusRequester,
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                    if (isTvMode) {
                        TvFeedChips(
                            selectedFeed = selectedTvFeed,
                            onFeedClick = {
                                tvHomeViewModel?.selectFeed(it)
                                scope.launch { tvGridState.scrollToItem(0) }
                            },
                            firstFocusRequester = homeControlsFocusRequester
                        )
                        tvGenreError?.let {
                            Text(
                                text = it.asString(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        TvGenreChips(
                            genres = tvGenres,
                            onGenreClick = {
                                tvHomeViewModel?.recordGenreVisit(it.id)
                                onTvGenreClick(it)
                            },
                            onAllGenresClick = { showTvGenreSheet = true }
                        )
                    } else {
                        GenreChips(
                            genres = topGenres,
                            onGenreClick = onGenreClick,
                            onAllGenresClick = { showGenreSheet = true },
                            firstFocusRequester = homeControlsFocusRequester
                        )
                        QualityChips(
                            options = qualityOptions,
                            selectedQuality = selectedQuality ?: "All",
                            onQualityClick = onQualityClick
                        )
                    }

                    if (isTvMode) {
                        TvHomeContent(
                            uiState = tvUiState,
                            genres = tvGenres,
                            gridState = tvGridState,
                            isLoadingMore = tvIsLoadingMore,
                            downloadedSeriesIds = downloadedSeriesIds,
                            onSeriesClick = {
                                tvHomeViewModel?.setLastClickedSeriesId(it.id)
                                onTvSeriesClick(it)
                            },
                            onLoadMore = { tvHomeViewModel?.loadMore() },
                            lastClickedSeriesId = lastClickedSeriesId,
                            onFocusRestored = { tvHomeViewModel?.clearLastClickedSeriesId() },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        HomeContent(
                            uiState = uiState,
                            gridState = gridState,
                            lastVisitDate = lastVisitDate,
                            downloadedMovieIds = downloadedMovieIds,
                            watchHistoryMap = watchHistoryMap,
                            isLoadingMore = isLoadingMore,
                            onMovieClick = {
                                onSetLastClickedMovieId(it.id)
                                onMovieClick(it)
                            },
                            onLoadMore = onLoadMore,
                            lastClickedMovieId = lastClickedMovieId,
                            onFocusRestored = onFocusRestored,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // Handheld Layout: Full-screen edge-to-edge with Haze top blur and translucent bottom bar
                val pullRefreshState = rememberPullRefreshState(
                    if (isTvMode) tvIsRefreshing else isRefreshing,
                    onRefresh = {
                    onSetLastClickedMovieId(null)
                    if (isTvMode) tvHomeViewModel?.refresh(showIndicator = true) else onRefresh(true)
                    scope.launch {
                        (if (isTvMode) tvGridState else gridState).scrollToItem(0)
                    }
                })
                val measuredControlsOffset = if (homeControlsHeightPx > 0) {
                    with(density) { homeControlsHeightPx.toDp() } + 16.dp
                } else {
                    if (isTvMode) 208.dp else 160.dp
                }
                val topContentOffset = padding.calculateTopPadding() + measuredControlsOffset

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState)
                ) {
                    val contentPadding = PaddingValues(
                        top = topContentOffset,
                        bottom = navBarHeight + 16.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
                    if (isTvMode) {
                        TvHomeContent(
                            uiState = tvUiState,
                            genres = tvGenres,
                            gridState = tvGridState,
                            isLoadingMore = tvIsLoadingMore,
                            downloadedSeriesIds = downloadedSeriesIds,
                            onSeriesClick = {
                                tvHomeViewModel?.setLastClickedSeriesId(it.id)
                                onTvSeriesClick(it)
                            },
                            onLoadMore = { tvHomeViewModel?.loadMore() },
                            lastClickedSeriesId = lastClickedSeriesId,
                            onFocusRestored = { tvHomeViewModel?.clearLastClickedSeriesId() },
                            contentPadding = contentPadding
                        )
                    } else {
                        HomeContent(
                            uiState = uiState,
                            gridState = gridState,
                            lastVisitDate = lastVisitDate,
                            downloadedMovieIds = downloadedMovieIds,
                            watchHistoryMap = watchHistoryMap,
                            isLoadingMore = isLoadingMore,
                            onMovieClick = {
                                onSetLastClickedMovieId(it.id)
                                onMovieClick(it)
                            },
                            onLoadMore = onLoadMore,
                            lastClickedMovieId = lastClickedMovieId,
                            onFocusRestored = onFocusRestored,
                            contentPadding = contentPadding
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = padding.calculateTopPadding())
                            .then(
                                if (hazeState != null) {
                                    Modifier
                                        .graphicsLayer { alpha = hazeAlpha }
                                        .hazeGlass(input = HazeInput.Sources(hazeState))
                                } else Modifier
                            )
                            .onSizeChanged { homeControlsHeightPx = it.height }
                    ) {
                        if (isTvMode) {
                            TvFeedChips(
                                selectedFeed = selectedTvFeed,
                                onFeedClick = {
                                    tvHomeViewModel?.selectFeed(it)
                                    scope.launch { tvGridState.scrollToItem(0) }
                                },
                                firstFocusRequester = homeControlsFocusRequester
                            )
                            tvGenreError?.let {
                                Text(
                                    text = it.asString(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            TvGenreChips(
                                genres = tvGenres,
                                onGenreClick = {
                                    tvHomeViewModel?.recordGenreVisit(it.id)
                                    onTvGenreClick(it)
                                },
                                onAllGenresClick = { showTvGenreSheet = true }
                            )
                        } else {
                            GenreChips(
                                genres = topGenres,
                                onGenreClick = onGenreClick,
                                onAllGenresClick = { showGenreSheet = true },
                                firstFocusRequester = homeControlsFocusRequester
                            )
                            QualityChips(
                                options = qualityOptions,
                                selectedQuality = selectedQuality ?: "All",
                                onQualityClick = onQualityClick
                            )
                        }
                    }

                    PullRefreshIndicator(
                        refreshing = if (isTvMode) tvIsRefreshing else isRefreshing,
                        state = pullRefreshState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topContentOffset)
                    )
                }
            }
        }
    }

    if (showTvGenreSheet) {
        TvGenreBottomSheet(
            genres = tvGenres,
            onGenreClick = {
                tvHomeViewModel?.recordGenreVisit(it.id)
                onTvGenreClick(it)
                showTvGenreSheet = false
            },
            onDismiss = { showTvGenreSheet = false },
            sheetState = tvGenreSheetState
        )
    }
    if (showGenreSheet) {
        GenreBottomSheet(
            genres = allGenres,
            onGenreClick = {
                onGenreClick(it)
                showGenreSheet = false
            },
            onDismiss = { showGenreSheet = false },
            sheetState = sheetState,
            hazeState = hazeState
        )
    }

}

/** Displays the selected TMDB TV feed with matching loading, error, and poster states. */
@Composable
private fun TvHomeContent(
    uiState: TvHomeUiState,
    genres: List<TmdbTvGenre>,
    gridState: LazyGridState,
    isLoadingMore: Boolean,
    downloadedSeriesIds: Set<Int> = emptySet(),
    onSeriesClick: (TmdbTvSummary) -> Unit,
    onLoadMore: () -> Unit,
    lastClickedSeriesId: Int?,
    onFocusRestored: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    modifier: Modifier = Modifier
) {
    when (uiState) {
        TvHomeUiState.Loading -> MovieListPlaceholder(
            contentPadding = contentPadding,
            modifier = modifier
        )
        is TvHomeUiState.Success -> TvSeriesGrid(
            series = uiState.series,
            genres = genres,
            state = gridState,
            isLoadingMore = isLoadingMore,
            downloadedSeriesIds = downloadedSeriesIds,
            onSeriesClick = onSeriesClick,
            onLoadMore = onLoadMore,
            initialFocusId = lastClickedSeriesId,
            onFocusRestored = onFocusRestored,
            contentPadding = contentPadding,
            modifier = modifier
        )
        is TvHomeUiState.Error -> androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .then(modifier)
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = uiState.message.asString(),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Renders the shared Home application bar with theme-aware actions and TV focus links.
 *
 * @param userPhotoUrl Signed-in user's avatar image URL.
 * @param favoritesCount Number of favorite movies.
 * @param isTvMode Whether the TV catalog is active.
 * @param onMoviesClick Selects the YTS movie catalog.
 * @param onTvClick Selects the TMDB TV catalog.
 * @param onSearchClick Opens search in the selected catalog mode.
 * @param onFavoritesClick Opens movie favorites.
 * @param onSettingsClick Opens settings.
 * @param focusDownRequester First Home filter/mode focus target on TV.
 * @param containerColor Semantic app-bar surface color.
 * @param modifier Modifier for blur and layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopAppBar(
    userPhotoUrl: String?,
    favoritesCount: Int,
    isTvMode: Boolean,
    onMoviesClick: () -> Unit,
    onTvClick: () -> Unit,
    onSearchClick: (isTvMode: Boolean) -> Unit,
    onFavoritesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    focusDownRequester: FocusRequester,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            IconButton(
                onClick = onMoviesClick,
                modifier = Modifier
                    .focusHighlight(shape = CircleShape)
                    .focusProperties { down = focusDownRequester }
            ) {
                Icon(
                    Icons.Default.Movie,
                    contentDescription = stringResource(R.string.home_mode_movies),
                    tint = if (isTvMode) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            IconButton(
                onClick = onTvClick,
                modifier = Modifier
                    .focusHighlight(shape = CircleShape)
                    .focusProperties { down = focusDownRequester }
            ) {
                Icon(
                    Icons.Default.Tv,
                    contentDescription = stringResource(R.string.home_mode_tv),
                    tint = if (isTvMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(
                onClick = { onSearchClick(isTvMode) },
                modifier = Modifier
                    .focusHighlight(shape = CircleShape)
                    .focusProperties { down = focusDownRequester }
            ) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_desc))
            }
            IconButton(
                onClick = onFavoritesClick,
                modifier = Modifier
                    .focusHighlight(shape = CircleShape)
                    .focusProperties { down = focusDownRequester }
            ) {
                BadgedBox(
                    badge = {
                        if (favoritesCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Text(
                                    text = if (favoritesCount > 99) "99+" else favoritesCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.favorites_desc))
                }
            }
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .focusHighlight(shape = CircleShape)
                    .focusProperties { down = focusDownRequester }
            ) {
                if (userPhotoUrl != null) {
                    AsyncImage(
                        model = userPhotoUrl,
                        contentDescription = stringResource(R.string.user_profile_desc),
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_desc))
                }
            }
        }
    )
}

/** Hosts the home content and coordinates refresh and genre-sheet presentation. */
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    gridState: LazyGridState,
    lastVisitDate: Long?,
    downloadedMovieIds: Set<Int>,
    watchHistoryMap: Map<String, PlaybackProgress>,
    isLoadingMore: Boolean = false,
    onMovieClick: (Movie) -> Unit,
    onLoadMore: () -> Unit,
    lastClickedMovieId: Int? = null,
    onFocusRestored: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(16.dp),
    modifier: Modifier = Modifier
) {
    when (uiState) {
        is HomeUiState.Loading -> {
            MovieListPlaceholder(contentPadding = contentPadding, modifier = modifier)
        }
        is HomeUiState.Success -> {
            MovieList(
                movies = uiState.movies,
                state = gridState,
                lastVisitDate = lastVisitDate,
                downloadedMovieIds = downloadedMovieIds,
                watchHistoryMap = watchHistoryMap,
                isLoadingMore = isLoadingMore,
                onMovieClick = onMovieClick,
                onLoadMore = onLoadMore,
                initialFocusId = lastClickedMovieId,
                onFocusRestored = onFocusRestored,
                contentPadding = contentPadding,
                modifier = modifier
            )
        }
        is HomeUiState.Error -> {
            Text(
                text = uiState.message.asString(),
                color = MaterialTheme.colorScheme.error,
                modifier = modifier.fillMaxSize()
            )
        }
    }
}

@Preview(name = "TV Light", showBackground = true, device = "id:tv_720p", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "TV Dark", showBackground = true, device = "id:tv_720p", uiMode = Configuration.UI_MODE_NIGHT_YES)
/** TV preview of the home movie feed. */
@Composable
fun HomeScreenTvPreview() {
    val sampleMovies = listOf(
        Movie(
            id = 1,
            title = "Inception",
            year = 2010,
            rating = "8.8",
            mediumCoverImage = "https://yts.mx/assets/images/movies/inception_2010/medium-cover.jpg",
            genres = listOf("Action", "Sci-Fi")
        ),
        Movie(
            id = 2,
            title = "The Dark Knight",
            year = 2008,
            rating = "9.0",
            mediumCoverImage = "https://yts.mx/assets/images/movies/the_dark_knight_2008/medium-cover.jpg",
            genres = listOf("Action", "Crime")
        )
    )

    LaTorrentolaTheme(dynamicColor = false) {
        HomeScreenContent(
            uiState = HomeUiState.Success(sampleMovies),
            topGenres = listOf("Action", "Adventure", "Animation"),
            allGenres = listOf("Action", "Adventure", "Animation", "Biography", "Comedy"),
            lastVisitDate = System.currentTimeMillis(),
            isRefreshing = false,
            favoritesCount = 5,
            selectedQuality = "All",
            lastClickedMovieId = null,
            downloadedMovieIds = setOf(1),
            watchHistoryMap = emptyMap(),
            qualityOptions = listOf("All", "2160p", "1080p", "720p"),
            userPhotoUrl = null,
            isTv = true,
            onMovieClick = {},
            onSettingsClick = {},
            onSearchClick = {},
            onFavoritesClick = {},
            onGenreClick = {},
            onQualityClick = {},
            onLoadMore = {},
            onRefresh = { _ -> },
            onSetLastClickedMovieId = { _ -> },
            onFocusRestored = {}
        )
    }
}

@PreviewLightDark
/** Light and dark previews of the home movie feed. */
@Composable
fun HomeScreenPreview() {
    val sampleMovies = listOf(
        Movie(
            id = 1,
            title = "Inception",
            year = 2010,
            rating = "8.8",
            mediumCoverImage = "https://yts.mx/assets/images/movies/inception_2010/medium-cover.jpg",
            genres = listOf("Action", "Sci-Fi")
        ),
        Movie(
            id = 2,
            title = "The Dark Knight",
            year = 2008,
            rating = "9.0",
            mediumCoverImage = "https://yts.mx/assets/images/movies/the_dark_knight_2008/medium-cover.jpg",
            genres = listOf("Action", "Crime")
        ),
        Movie(
            id = 3,
            title = "Interstellar",
            year = 2014,
            rating = "8.6",
            mediumCoverImage = "https://yts.mx/assets/images/movies/interstellar_2014/medium-cover.jpg",
            genres = listOf("Adventure", "Drama")
        )
    )

    LaTorrentolaTheme(dynamicColor = false) {
        HomeScreenContent(
            uiState = HomeUiState.Success(sampleMovies),
            topGenres = listOf("Action", "Adventure", "Animation"),
            allGenres = listOf("Action", "Adventure", "Animation", "Biography", "Comedy"),
            lastVisitDate = System.currentTimeMillis(),
            isRefreshing = false,
            favoritesCount = 5,
            selectedQuality = "All",
            lastClickedMovieId = null,
            downloadedMovieIds = setOf(1),
            watchHistoryMap = emptyMap(),
            qualityOptions = listOf("All", "2160p", "1080p", "720p"),
            userPhotoUrl = null,
            isTv = false,
            onMovieClick = {},
            onSettingsClick = {},
            onSearchClick = {},
            onFavoritesClick = {},
            onGenreClick = {},
            onQualityClick = {},
            onLoadMore = {},
            onRefresh = { _ -> },
            onSetLastClickedMovieId = { _ -> },
            onFocusRestored = {}
        )
    }
}
