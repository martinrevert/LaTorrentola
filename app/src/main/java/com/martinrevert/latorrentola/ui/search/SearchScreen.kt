@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.search

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.TMDB.TmdbCastCredit
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieResult
import com.martinrevert.latorrentola.model.TMDB.TmdbPersonResult
import com.martinrevert.latorrentola.model.TMDB.TmdbTvGenre
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.ui.components.ActorDetailBottomSheet
import com.martinrevert.latorrentola.ui.components.AdaptiveChip
import com.martinrevert.latorrentola.ui.components.FavoritesChips
import com.martinrevert.latorrentola.ui.components.FilmographyMetadata
import com.martinrevert.latorrentola.ui.components.MovieItem
import com.martinrevert.latorrentola.ui.components.MovieList
import com.martinrevert.latorrentola.ui.components.MovieListPlaceholder
import com.martinrevert.latorrentola.ui.components.QualityChips
import com.martinrevert.latorrentola.ui.components.TvMovieImmersiveContainer
import com.martinrevert.latorrentola.ui.components.TvSeriesCard
import com.martinrevert.latorrentola.ui.components.TvSeriesGrid
import com.martinrevert.latorrentola.ui.components.WatchProgressOptionDialog
import com.martinrevert.latorrentola.ui.components.tvGridRowDpadNavigation
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.GenreTranslation
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Connects search fields, collection modes, and result actions to the search view model.
 *
 * @param viewModel Search state and catalog actions.
 * @param initialGenre Optional movie collection or genre to display.
 * @param initialQuery Optional initial text query.
 * @param initialTvMode Whether search should initially use the TV series catalog.
 * @param initialActorMode Whether search should initially use TMDB Actor search mode.
 * @param onMovieClick Opens a selected movie.
 * @param onTvSeriesClick Opens a selected TV series.
 * @param onActorCreditClick Opens a selected TMDB movie or TV credit from actor filmography.
 * @param onBackClick Returns to the previous screen.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    initialGenre: String? = null,
    initialQuery: String? = null,
    initialTvMode: Boolean = false,
    initialActorMode: Boolean = false,
    onMovieClick: (Movie) -> Unit,
    onTvSeriesClick: (TmdbTvSummary) -> Unit = {},
    onActorCreditClick: (TmdbCastCredit) -> Unit = {},
    onBackClick: () -> Unit,
    themeMode: Int? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val isTvMode by viewModel.isTvMode.collectAsState()
    val isActorMode by viewModel.isActorMode.collectAsState()
    val actorUiState by viewModel.actorUiState.collectAsState()
    val tvUiState by viewModel.tvUiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val downloadedSeriesIds by viewModel.downloadedSeriesIds.collectAsState()
    val favoriteTvSeries by viewModel.favoriteTvSeries.collectAsState()
    val tvGenres by viewModel.tvGenres.collectAsState()
    val lastClickedSeriesId by viewModel.lastClickedSeriesId.collectAsState()
    val lastClickedCreditKey by viewModel.lastClickedCreditKey.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()
    val selectedFavoritesFilter by viewModel.selectedFavoritesFilter.collectAsState()
    val lastClickedMovieId by viewModel.lastClickedMovieId.collectAsState()
    val downloadedMovieIds by viewModel.downloadedMovieIds.collectAsState()
    val watchHistoryMap by viewModel.watchHistoryMap.collectAsState()
    val selectedFavoriteIds by viewModel.selectedFavoriteIds.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val qualityOptions = viewModel.qualityOptions

    val isShowingFavorites = initialGenre == "milista" || initialGenre == "favorites"
    val isShowingDownloads = initialGenre == "ya_vistas"
    val isShowingNew = initialGenre == "nuevas"
    val isShowingGenre = initialGenre != null && !isShowingFavorites && !isShowingDownloads && !isShowingNew

    val context = LocalContext.current
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }
    val resources = LocalResources.current
    val isTv = remember(context) { context.isTvDevice() }
    val focusRequester = remember { FocusRequester() }
    var selectedMovieForDialog by remember { mutableStateOf<Movie?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            spokenText?.let {
                viewModel.search(it)
            }
        }
    }

    LaunchedEffect(initialGenre, initialQuery, initialTvMode, initialActorMode) {
        viewModel.initializeIfNeeded(
            initialGenre = initialGenre,
            initialQuery = initialQuery,
            initialTvMode = initialTvMode && initialGenre == null,
            initialActorMode = initialActorMode
        )

        val hasNoPendingItemFocus =
            lastClickedMovieId == null && lastClickedSeriesId == null && lastClickedCreditKey == null
        val isIdle = when {
            initialActorMode -> actorUiState is SearchActorUiState.Idle
            initialTvMode -> tvUiState is SearchTvUiState.Idle
            else -> uiState is SearchUiState.Idle
        }
        if (isTv && !isShowingFavorites && !isShowingDownloads && !isShowingGenre && !isShowingNew && isIdle && hasNoPendingItemFocus) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
                // Ignore if not attached
            }
        }
    }

    val focusedMovie by viewModel.focusedMovie.collectAsState()
    val focusedMovieBackdrop by viewModel.focusedMovieBackdrop.collectAsState()
    val focusedMovieLogo by viewModel.focusedMovieLogo.collectAsState()
    val isFocusedMovieLogoLoading by viewModel.isFocusedMovieLogoLoading.collectAsState()
    val focusedMovieTmdb by viewModel.focusedMovieTmdb.collectAsState()

    SearchScreenContent(
        uiState = uiState,
        isTvMode = isTvMode,
        isActorMode = isActorMode,
        actorUiState = actorUiState,
        tvUiState = tvUiState,
        downloadedSeriesIds = downloadedSeriesIds,
        favoriteTvSeries = favoriteTvSeries,
        tvGenres = tvGenres,
        lastClickedSeriesId = lastClickedSeriesId,
        lastClickedCreditKey = lastClickedCreditKey,
        selectedQuality = selectedQuality,
        lastClickedMovieId = lastClickedMovieId,
        downloadedMovieIds = downloadedMovieIds,
        watchHistoryMap = watchHistoryMap,
        selectedFavoriteIds = selectedFavoriteIds,
        qualityOptions = qualityOptions,
        searchQuery = searchQuery,
        isShowingFavorites = isShowingFavorites,
        isShowingDownloads = isShowingDownloads,
        isShowingNew = isShowingNew,
        isShowingGenre = isShowingGenre,
        initialGenre = initialGenre,
        isTv = isTv,
        focusedMovie = focusedMovie,
        focusedMovieBackdrop = focusedMovieBackdrop,
        focusedMovieLogo = focusedMovieLogo,
        isFocusedMovieLogoLoading = isFocusedMovieLogoLoading,
        focusedMovieTmdb = focusedMovieTmdb,
        isLoadingMore = isLoadingMore,
        focusRequester = focusRequester,
        onMovieFocus = { viewModel.onMovieFocused(it) },
        onSearchQueryChange = { newQuery ->
            viewModel.onSearchQueryChange(newQuery)
        },
        onVoiceSearchClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, resources.getString(R.string.voice_search_prompt))
            }
            try {
                speechLauncher.launch(intent)
            } catch (_: Exception) {
            }
        },
        onQualityClick = { viewModel.setQuality(it) },
        selectedFavoritesFilter = selectedFavoritesFilter,
        onFavoritesFilterClick = { viewModel.setFavoritesFilter(it) },
        onActorSelect = { viewModel.selectActor(it) },
        onActorCreditClick = { credit ->
            val creditKey = "${credit.mediaType ?: "movie"}_${credit.id}"
            viewModel.setLastClickedCreditKey(creditKey)
            onActorCreditClick(credit)
        },
        onMovieClick = {
            if (selectedFavoriteIds.isNotEmpty()) {
                viewModel.toggleFavoriteSelection(it.id)
            } else {
                viewModel.clearLastClickedSeriesId()
                viewModel.setLastClickedMovieId(it.id)
                onMovieClick(it)
            }
        },
        onMovieProgressLongClick = { movie -> selectedMovieForDialog = movie },
        onTvSeriesClick = {
            if (selectedFavoriteIds.isNotEmpty()) {
                viewModel.toggleFavoriteSelection(it.id)
            } else {
                viewModel.clearLastClickedMovieId()
                viewModel.setLastClickedSeriesId(it.id)
                onTvSeriesClick(it)
            }
        },
        onLongClick = { viewModel.toggleFavoriteSelection(it) },
        onLoadMore = { viewModel.loadMore() },
        onBackClick = onBackClick,
        onClearSelection = { viewModel.clearSelection() },
        onDeleteSelectedFavorites = { viewModel.deleteSelectedFavorites() },
        onFocusRestored = { viewModel.clearLastClickedMovieId() },
        onTvFocusRestored = { viewModel.clearLastClickedSeriesId() },
        onActorCreditFocusRestored = { viewModel.clearLastClickedCreditKey() },
        themeMode = effectiveThemeMode
    )

    selectedMovieForDialog?.let { movie ->
        WatchProgressOptionDialog(
            title = stringResource(R.string.mark_movie_title),
            option1Text = stringResource(R.string.mark_movie_completed),
            option2Text = stringResource(R.string.mark_movie_uncompleted),
            onOption1Selected = { viewModel.markMovieAsCompleted(movie) },
            onOption2Selected = { viewModel.markMovieAsUncompleted(movie) },
            onDismissRequest = { selectedMovieForDialog = null }
        )
    }
}

/**
 * Renders the adaptive search interface and its current result collection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreenContent(
    uiState: SearchUiState,
    isTvMode: Boolean = false,
    isActorMode: Boolean = false,
    actorUiState: SearchActorUiState = SearchActorUiState.Idle,
    tvUiState: SearchTvUiState = SearchTvUiState.Idle,
    downloadedSeriesIds: Set<Int> = emptySet(),
    favoriteTvSeries: List<TmdbTvSummary> = emptyList(),
    tvGenres: List<TmdbTvGenre> = emptyList(),
    lastClickedSeriesId: Int? = null,
    lastClickedCreditKey: String? = null,
    selectedQuality: String?,
    lastClickedMovieId: Int?,
    downloadedMovieIds: Set<Int>,
    watchHistoryMap: Map<String, com.martinrevert.latorrentola.model.user.PlaybackProgress> = emptyMap(),
    selectedFavoriteIds: Set<Int>,
    qualityOptions: List<String>,
    searchQuery: String,
    isShowingFavorites: Boolean,
    isShowingDownloads: Boolean,
    isShowingNew: Boolean,
    isShowingGenre: Boolean,
    initialGenre: String?,
    isTv: Boolean,
    focusedMovie: Movie? = null,
    focusedMovieBackdrop: String? = null,
    focusedMovieLogo: String? = null,
    isFocusedMovieLogoLoading: Boolean = false,
    focusedMovieTmdb: TmdbMovieResult? = null,
    isLoadingMore: Boolean = false,
    focusRequester: FocusRequester,
    onMovieFocus: ((Movie) -> Unit)? = null,
    onSearchQueryChange: (String) -> Unit,
    onVoiceSearchClick: () -> Unit,
    onQualityClick: (String) -> Unit,
    selectedFavoritesFilter: FavoritesFilter = FavoritesFilter.ALL,
    onFavoritesFilterClick: (FavoritesFilter) -> Unit = {},
    onActorSelect: (TmdbPersonResult) -> Unit = {},
    onActorCreditClick: (TmdbCastCredit) -> Unit = {},
    onMovieClick: (Movie) -> Unit,
    onMovieProgressLongClick: (Movie) -> Unit = {},
    onTvSeriesClick: (TmdbTvSummary) -> Unit = {},
    onLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onBackClick: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelectedFavorites: () -> Unit,
    onFocusRestored: () -> Unit,
    onTvFocusRestored: () -> Unit = {},
    onActorCreditFocusRestored: () -> Unit = {},
    themeMode: Int? = null
) {
    val focusManager = LocalFocusManager.current
    val gridState = rememberLazyGridState()
    val qualityChipsFocusRequester = remember { FocusRequester() }
    val resultsFocusRequester = remember { FocusRequester() }
    val hazeState = if (!isTv) rememberHazeState() else null
    var showActorBioSheet by remember { mutableStateOf(false) }
    val actorBioSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isInspection = LocalInspectionMode.current
    val isPreAndroid12 = !isInspection && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
    val topBarContainerColor = if (isPreAndroid12) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    } else {
        Color.Transparent
    }

    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 10
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

    val hasTopChips = isShowingFavorites || (!isTvMode && !isActorMode) ||
        (isActorMode && actorUiState is SearchActorUiState.Success)
    val isTvMoviesSearch = isTv && !isTvMode && !isActorMode && !isShowingFavorites

    Scaffold(
        contentWindowInsets = if (isTvMoviesSearch) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
        topBar = {
            if (!isTvMoviesSearch) {
                TopAppBar(
                    modifier = Modifier
                        .focusProperties {
                            down = if (hasTopChips) qualityChipsFocusRequester else resultsFocusRequester
                        }
                        .then(
                            if (hazeState != null) {
                                Modifier
                                    .graphicsLayer { alpha = hazeAlpha }
                                    .hazeGlass(input = HazeInput.Sources(hazeState))
                            } else Modifier
                        ),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarContainerColor),
                    title = {
                        if (isShowingFavorites) {
                            if (selectedFavoriteIds.isNotEmpty()) {
                                Text(stringResource(R.string.selected_count, selectedFavoriteIds.size))
                            } else {
                                Text(stringResource(R.string.my_favorites))
                            }
                        } else if (isShowingDownloads) {
                            Text(stringResource(R.string.already_seen))
                        } else if (isShowingNew) {
                            Text(stringResource(R.string.new_movies))
                        } else if (isShowingGenre && initialGenre != null) {
                            Text(GenreTranslation.getGenreText(initialGenre).asString())
                        } else {
                            SearchTextField(
                                searchQuery = searchQuery,
                                onSearchQueryChange = onSearchQueryChange,
                                onVoiceSearchClick = onVoiceSearchClick,
                                showVoiceSearch = !isTv,
                                isTvMode = isTvMode,
                                isActorMode = isActorMode,
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .then(if (isTv) Modifier.focusHighlight() else Modifier)
                                    .onPreviewKeyEvent { event ->
                                        if (isTv && (event.key == Key.Back || event.key == Key.Escape)) {
                                            if (event.type == KeyEventType.KeyUp) {
                                                focusManager.moveFocus(FocusDirection.Exit)
                                            }
                                            true
                                        } else false
                                    }
                            )
                        }
                    },
                    navigationIcon = {
                        if (isShowingFavorites && selectedFavoriteIds.isNotEmpty()) {
                            IconButton(
                                onClick = onClearSelection,
                                modifier = Modifier.focusHighlight(shape = CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection_desc))
                            }
                        } else {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier.focusHighlight(shape = CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_desc))
                            }
                        }
                    },
                    actions = {
                        if (isShowingFavorites && selectedFavoriteIds.isNotEmpty()) {
                            IconButton(
                                onClick = onDeleteSelectedFavorites,
                                modifier = Modifier.focusHighlight(shape = CircleShape)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected_desc))
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (isTv) {
            if (isTvMoviesSearch) {
                // TV Movies Search: Full edge-to-edge immersive backdrop behind search bar and chips
                TvMovieImmersiveContainer(
                    focusedMovie = focusedMovie,
                    focusedMovieTmdb = focusedMovieTmdb,
                    backdropUrl = focusedMovieBackdrop,
                    logoUrl = focusedMovieLogo,
                    isLogoLoading = isFocusedMovieLogoLoading,
                    themeMode = themeMode,
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            modifier = Modifier.focusProperties { down = qualityChipsFocusRequester },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                            title = {
                                if (isShowingDownloads) {
                                    Text(stringResource(R.string.already_seen))
                                } else if (isShowingNew) {
                                    Text(stringResource(R.string.new_movies))
                                } else if (isShowingGenre && initialGenre != null) {
                                    Text(GenreTranslation.getGenreText(initialGenre).asString())
                                } else {
                                    SearchTextField(
                                        searchQuery = searchQuery,
                                        onSearchQueryChange = onSearchQueryChange,
                                        onVoiceSearchClick = onVoiceSearchClick,
                                        showVoiceSearch = false,
                                        isTvMode = false,
                                        isActorMode = false,
                                        modifier = Modifier
                                            .focusRequester(focusRequester)
                                            .focusHighlight()
                                            .onPreviewKeyEvent { event ->
                                                if (event.key == Key.Back || event.key == Key.Escape) {
                                                    if (event.type == KeyEventType.KeyUp) {
                                                        focusManager.moveFocus(FocusDirection.Exit)
                                                    }
                                                    true
                                                } else false
                                            }
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = onBackClick,
                                    modifier = Modifier.focusHighlight(shape = CircleShape)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_desc))
                                }
                            }
                        )
                    },
                    chips = {
                        QualityChips(
                            options = qualityOptions,
                            selectedQuality = selectedQuality ?: "All",
                            onQualityClick = onQualityClick,
                            modifier = Modifier
                                .focusRequester(qualityChipsFocusRequester)
                                .focusProperties { down = resultsFocusRequester }
                        )
                    }
                ) {
                    when (uiState) {
                        is SearchUiState.Idle -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = stringResource(R.string.start_searching))
                        }
                        is SearchUiState.Loading -> MovieListPlaceholder(contentPadding = PaddingValues(16.dp))
                        is SearchUiState.Success -> {
                            MovieList(
                                movies = uiState.movies,
                                state = gridState,
                                downloadedMovieIds = downloadedMovieIds,
                                watchHistoryMap = watchHistoryMap,
                                selectedIds = selectedFavoriteIds,
                                isLoadingMore = isLoadingMore,
                                onMovieClick = onMovieClick,
                                onLongClick = { id -> uiState.movies.firstOrNull { it.id == id }?.let(onMovieProgressLongClick) },
                                onLoadMore = { if (!uiState.isDownloads && !uiState.isNew) onLoadMore() },
                                initialFocusId = lastClickedMovieId,
                                onFocusRestored = onFocusRestored,
                                onMovieFocus = onMovieFocus,
                                contentPadding = PaddingValues(16.dp),
                                modifier = Modifier.focusRequester(resultsFocusRequester)
                            )
                        }
                        is SearchUiState.Empty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = stringResource(R.string.no_results))
                        }
                        is SearchUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = uiState.message.asString())
                        }
                    }
                }
            } else {
                // TV Layout: Single vertical column for unbroken D-pad focus traversal
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding)
                ) {
                if (isShowingFavorites) {
                    FavoritesChips(
                        selectedFilter = selectedFavoritesFilter,
                        onFilterClick = onFavoritesFilterClick,
                        modifier = Modifier
                            .focusRequester(qualityChipsFocusRequester)
                            .focusProperties { down = resultsFocusRequester }
                    )
                } else if (isActorMode) {
                    if (actorUiState is SearchActorUiState.Success) {
                        ActorChipsRow(
                            actors = actorUiState.actors,
                            selectedActor = actorUiState.selectedActor,
                            hasBiography = actorUiState.actorDetail?.biography?.isNotBlank() ?: false,
                            onActorSelect = onActorSelect,
                            onShowBioClick = { showActorBioSheet = true },
                            modifier = Modifier
                                .focusRequester(qualityChipsFocusRequester)
                                .focusProperties { down = resultsFocusRequester }
                        )
                    }
                } else if (!isTvMode) {
                    QualityChips(
                        options = qualityOptions,
                        selectedQuality = selectedQuality ?: "All",
                        onQualityClick = onQualityClick,
                        modifier = Modifier
                            .focusRequester(qualityChipsFocusRequester)
                            .focusProperties { down = resultsFocusRequester }
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(top = 4.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        isActorMode -> {
                            when (actorUiState) {
                                SearchActorUiState.Idle -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = stringResource(R.string.start_searching))
                                }
                                SearchActorUiState.Loading -> MovieListPlaceholder(contentPadding = PaddingValues(16.dp))
                                SearchActorUiState.Empty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = stringResource(R.string.no_results))
                                }
                                is SearchActorUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = actorUiState.message.asString())
                                }
                                is SearchActorUiState.Success -> {
                                    when {
                                        actorUiState.isLoadingCredits -> MovieListPlaceholder(contentPadding = PaddingValues(16.dp))
                                        actorUiState.credits.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(text = stringResource(R.string.no_results))
                                        }
                                        else -> ActorCreditsGrid(
                                            credits = actorUiState.credits,
                                            state = gridState,
                                            initialFocusKey = lastClickedCreditKey,
                                            onCreditClick = onActorCreditClick,
                                            onFocusRestored = onActorCreditFocusRestored,
                                            contentPadding = PaddingValues(16.dp),
                                            modifier = Modifier.focusRequester(resultsFocusRequester)
                                        )
                                    }
                                }
                            }
                        }
                        isTvMode -> {
                            when (tvUiState) {
                                is SearchTvUiState.Idle -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = stringResource(R.string.start_searching))
                                }
                                is SearchTvUiState.Loading -> MovieListPlaceholder(contentPadding = PaddingValues(16.dp))
                                is SearchTvUiState.Success -> TvSeriesGrid(
                                    series = tvUiState.series,
                                    genres = tvGenres,
                                    downloadedSeriesIds = downloadedSeriesIds,
                                    state = gridState,
                                    isLoadingMore = isLoadingMore,
                                    onSeriesClick = onTvSeriesClick,
                                    onLoadMore = onLoadMore,
                                    initialFocusId = lastClickedSeriesId,
                                    onFocusRestored = onTvFocusRestored,
                                    contentPadding = PaddingValues(16.dp),
                                    modifier = Modifier.focusRequester(resultsFocusRequester)
                                )
                                is SearchTvUiState.Empty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = stringResource(R.string.no_results))
                                }
                                is SearchTvUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = tvUiState.message.asString())
                                }
                            }
                        }
                        else -> {
                            when (uiState) {
                                is SearchUiState.Idle -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = stringResource(R.string.start_searching))
                                }
                                is SearchUiState.Loading -> MovieListPlaceholder(contentPadding = PaddingValues(16.dp))
                                is SearchUiState.Success -> if (uiState.isFavorites) {
                                    FavoriteItemsGrid(
                                        movies = uiState.movies,
                                        series = uiState.favoriteSeries.ifEmpty { favoriteTvSeries },
                                        tvGenres = tvGenres,
                                        state = gridState,
                                        downloadedMovieIds = downloadedMovieIds,
                                        downloadedSeriesIds = downloadedSeriesIds,
                                        watchHistoryMap = watchHistoryMap,
                                        selectedMovieIds = selectedFavoriteIds,
                                        lastClickedMovieId = lastClickedMovieId,
                                        lastClickedSeriesId = lastClickedSeriesId,
                                        onMovieClick = onMovieClick,
                                        onTvSeriesClick = onTvSeriesClick,
                                        onMovieLongClick = onLongClick,
                                        onMovieFocusRestored = onFocusRestored,
                                        onTvFocusRestored = onTvFocusRestored,
                                        contentPadding = PaddingValues(16.dp),
                                        modifier = Modifier.focusRequester(resultsFocusRequester)
                                    )
                                } else {
                                    MovieList(
                                        movies = uiState.movies,
                                        state = gridState,
                                        downloadedMovieIds = downloadedMovieIds,
                                        watchHistoryMap = watchHistoryMap,
                                        selectedIds = selectedFavoriteIds,
                                        isLoadingMore = isLoadingMore,
                                        onMovieClick = onMovieClick,
                                        onLongClick = { id -> uiState.movies.firstOrNull { it.id == id }?.let(onMovieProgressLongClick) },
                                        onLoadMore = { if (!uiState.isDownloads && !uiState.isNew) onLoadMore() },
                                        initialFocusId = lastClickedMovieId,
                                        onFocusRestored = onFocusRestored,
                                        onMovieFocus = onMovieFocus,
                                        contentPadding = PaddingValues(16.dp),
                                        modifier = Modifier.focusRequester(resultsFocusRequester)
                                    )
                                }
                                is SearchUiState.Empty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = stringResource(R.string.no_results))
                                }
                                is SearchUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = uiState.message.asString())
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
            // Handheld Layout: Edge-to-edge with Haze top blur and translucent bottom bar
            val topExtraOffset = if (hasTopChips) 64.dp else 16.dp
            val searchContentPadding = PaddingValues(
                top = padding.calculateTopPadding() + topExtraOffset,
                bottom = padding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (hazeState != null) Modifier.hazeSource(state = hazeState) else Modifier)
            ) {
                when {
                    isActorMode -> {
                        when (actorUiState) {
                            SearchActorUiState.Idle -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = stringResource(R.string.start_searching))
                            }
                            SearchActorUiState.Loading -> MovieListPlaceholder(contentPadding = searchContentPadding)
                            SearchActorUiState.Empty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = stringResource(R.string.no_results))
                            }
                            is SearchActorUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = actorUiState.message.asString())
                            }
                            is SearchActorUiState.Success -> {
                                when {
                                    actorUiState.isLoadingCredits -> MovieListPlaceholder(contentPadding = searchContentPadding)
                                    actorUiState.credits.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(text = stringResource(R.string.no_results))
                                    }
                                    else -> ActorCreditsGrid(
                                        credits = actorUiState.credits,
                                        state = gridState,
                                        initialFocusKey = lastClickedCreditKey,
                                        onCreditClick = onActorCreditClick,
                                        onFocusRestored = onActorCreditFocusRestored,
                                        contentPadding = searchContentPadding
                                    )
                                }
                            }
                        }
                    }
                    isTvMode -> {
                        when (tvUiState) {
                            is SearchTvUiState.Idle -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = stringResource(R.string.start_searching))
                            }
                            is SearchTvUiState.Loading -> MovieListPlaceholder(contentPadding = searchContentPadding)
                            is SearchTvUiState.Success -> TvSeriesGrid(
                                series = tvUiState.series,
                                genres = tvGenres,
                                downloadedSeriesIds = downloadedSeriesIds,
                                state = gridState,
                                isLoadingMore = isLoadingMore,
                                onSeriesClick = onTvSeriesClick,
                                onLoadMore = onLoadMore,
                                initialFocusId = lastClickedSeriesId,
                                onFocusRestored = onTvFocusRestored,
                                contentPadding = searchContentPadding
                            )
                            is SearchTvUiState.Empty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = stringResource(R.string.no_results))
                            }
                            is SearchTvUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = tvUiState.message.asString())
                            }
                        }
                    }
                    else -> {
                        when (uiState) {
                            is SearchUiState.Idle -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = stringResource(R.string.start_searching))
                            }
                            is SearchUiState.Loading -> MovieListPlaceholder(contentPadding = searchContentPadding)
                            is SearchUiState.Success -> if (uiState.isFavorites) {
                                FavoriteItemsGrid(
                                    movies = uiState.movies,
                                    series = uiState.favoriteSeries.ifEmpty { favoriteTvSeries },
                                    tvGenres = tvGenres,
                                    state = gridState,
                                    downloadedMovieIds = downloadedMovieIds,
                                    downloadedSeriesIds = downloadedSeriesIds,
                                    watchHistoryMap = watchHistoryMap,
                                    selectedMovieIds = selectedFavoriteIds,
                                    lastClickedMovieId = lastClickedMovieId,
                                    lastClickedSeriesId = lastClickedSeriesId,
                                    onMovieClick = onMovieClick,
                                    onTvSeriesClick = onTvSeriesClick,
                                    onMovieLongClick = onLongClick,
                                    onMovieFocusRestored = onFocusRestored,
                                    onTvFocusRestored = onTvFocusRestored,
                                    contentPadding = searchContentPadding
                                )
                            } else MovieList(
                                movies = uiState.movies,
                                state = gridState,
                                downloadedMovieIds = downloadedMovieIds,
                                watchHistoryMap = watchHistoryMap,
                                selectedIds = selectedFavoriteIds,
                                isLoadingMore = isLoadingMore,
                                onMovieClick = onMovieClick,
                                onLongClick = { id -> uiState.movies.firstOrNull { it.id == id }?.let(onMovieProgressLongClick) },
                                onLoadMore = { if (!uiState.isDownloads && !uiState.isNew) onLoadMore() },
                                initialFocusId = lastClickedMovieId,
                                onFocusRestored = onFocusRestored,
                                contentPadding = searchContentPadding
                            )
                            is SearchUiState.Empty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = stringResource(R.string.no_results))
                            }
                            is SearchUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = uiState.message.asString())
                            }
                        }
                    }
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
                ) {
                    if (isShowingFavorites) {
                        FavoritesChips(
                            selectedFilter = selectedFavoritesFilter,
                            onFilterClick = onFavoritesFilterClick,
                            modifier = Modifier.focusRequester(qualityChipsFocusRequester)
                        )
                    } else if (isActorMode) {
                        if (actorUiState is SearchActorUiState.Success) {
                            ActorChipsRow(
                                actors = actorUiState.actors,
                                selectedActor = actorUiState.selectedActor,
                                hasBiography = actorUiState.actorDetail?.biography?.isNotBlank() ?: false,
                                onActorSelect = onActorSelect,
                                onShowBioClick = { showActorBioSheet = true },
                                modifier = Modifier.focusRequester(qualityChipsFocusRequester)
                            )
                        }
                    } else if (!isTvMode) {
                        QualityChips(
                            options = qualityOptions,
                            selectedQuality = selectedQuality ?: "All",
                            onQualityClick = onQualityClick,
                            modifier = Modifier.focusRequester(qualityChipsFocusRequester)
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 4.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }

    LaunchedEffect(actorUiState) {
        if (actorUiState !is SearchActorUiState.Success) {
            showActorBioSheet = false
        }
    }

    if (showActorBioSheet && actorUiState is SearchActorUiState.Success) {
        val detail = actorUiState.actorDetail
        ActorDetailBottomSheet(
            actorDetailState = detail?.let { Result.success(it) },
            isLoading = actorUiState.isLoadingCredits && detail == null,
            onDismiss = { showActorBioSheet = false },
            sheetState = actorBioSheetState,
            hazeState = hazeState,
            onCreditClick = { credit ->
                showActorBioSheet = false
                onActorCreditClick(credit)
            }
        )
    }
}

internal sealed interface ActorChipItem {
    data object Bio : ActorChipItem
    data class Actor(val person: TmdbPersonResult) : ActorChipItem
}

internal fun buildActorChipItems(
    actors: List<TmdbPersonResult>,
    selectedActor: TmdbPersonResult,
    hasBiography: Boolean = true
): List<ActorChipItem> {
    if (actors.isEmpty()) {
        return emptyList()
    }
    val items = mutableListOf<ActorChipItem>()
    val selectedIndex = actors.indexOfFirst { it.id == selectedActor.id }
    if (hasBiography && selectedIndex == -1) {
        items.add(ActorChipItem.Bio)
    }
    actors.forEachIndexed { index, actor ->
        if (hasBiography && index == selectedIndex) {
            items.add(ActorChipItem.Bio)
        }
        items.add(ActorChipItem.Actor(actor))
    }
    return items
}

/**
 * Horizontal row of matching TMDB actor chips and an optional Biography chip.
 *
 * The Biography chip is always positioned immediately to the left of [selectedActor].
 * Changing [selectedActor] animates the Biography chip and surrounding actor chips
 * horizontally using `Modifier.animateItem`.
 */
@Composable
private fun ActorChipsRow(
    actors: List<TmdbPersonResult>,
    selectedActor: TmdbPersonResult,
    onActorSelect: (TmdbPersonResult) -> Unit,
    onShowBioClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasBiography: Boolean = true
) {
    val chipItems = remember(actors, selectedActor.id, hasBiography) {
        buildActorChipItems(actors, selectedActor, hasBiography)
    }

    val listState = rememberLazyListState()

    LaunchedEffect(selectedActor.id) {
        val bioIndex = chipItems.indexOfFirst { it is ActorChipItem.Bio }
        val actorIndex = chipItems.indexOfFirst { it is ActorChipItem.Actor && it.person.id == selectedActor.id }
        val targetIndex = bioIndex.takeIf { it >= 0 } ?: actorIndex.takeIf { it >= 0 } ?: return@LaunchedEffect

        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        val bioItem = bioIndex.takeIf { it >= 0 }?.let { idx -> visibleItems.find { it.index == idx } }
        val actorItem = actorIndex.takeIf { it >= 0 }?.let { idx -> visibleItems.find { it.index == idx } }
        val isBioVisible = bioItem != null && bioItem.offset >= layoutInfo.viewportStartOffset &&
            bioItem.offset + bioItem.size <= layoutInfo.viewportEndOffset
        val isActorVisible = actorItem != null && actorItem.offset >= layoutInfo.viewportStartOffset &&
            actorItem.offset + actorItem.size <= layoutInfo.viewportEndOffset

        if ((bioIndex >= 0 && !isBioVisible) || (actorIndex >= 0 && !isActorVisible)) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .focusRestorer()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = chipItems,
            key = { item ->
                when (item) {
                    is ActorChipItem.Bio -> "actor_bio_chip"
                    is ActorChipItem.Actor -> item.person.id
                }
            },
            contentType = { item ->
                when (item) {
                    is ActorChipItem.Bio -> "bio_chip"
                    is ActorChipItem.Actor -> "actor_chip"
                }
            }
        ) { item ->
            when (item) {
                is ActorChipItem.Bio -> {
                    AdaptiveChip(
                        selected = false,
                        onClick = onShowBioClick,
                        label = { Text(stringResource(R.string.actor_bio_chip)) },
                        modifier = Modifier.animateItem(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                is ActorChipItem.Actor -> {
                    val actor = item.person
                    AdaptiveChip(
                        selected = actor.id == selectedActor.id,
                        onClick = { onActorSelect(actor) },
                        label = { Text(actor.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.animateItem(),
                        leadingIcon = {
                            val profileUrl = actor.fullProfileUrl
                            if (profileUrl != null) {
                                AsyncImage(
                                    model = profileUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Displays an actor's combined movie and TV appearances sorted by year descending,
 * with focus restoration when returning from a selected movie or TV detail screen.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ActorCreditsGrid(
    credits: List<TmdbCastCredit>,
    state: LazyGridState,
    initialFocusKey: String?,
    onCreditClick: (TmdbCastCredit) -> Unit,
    onFocusRestored: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val currentCredits by rememberUpdatedState(credits)
    val columns = when {
        isTv -> GridCells.Fixed(6)
        screenWidth < 600.dp -> GridCells.Fixed(2)
        screenWidth < 900.dp -> GridCells.Adaptive(minSize = 160.dp)
        else -> GridCells.Adaptive(minSize = 200.dp)
    }

    LazyVerticalGrid(
        columns = columns,
        state = state,
        modifier = modifier
            .fillMaxSize()
            .focusRestorer(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = credits,
            key = { "${it.mediaType ?: "movie"}_${it.id}" },
            contentType = { "actor_credit" }
        ) { credit ->
            val creditKey = "${credit.mediaType ?: "movie"}_${credit.id}"
            ActorCreditCard(
                credit = credit,
                isTv = isTv,
                shouldRequestFocus = creditKey == initialFocusKey,
                onFocusRestored = onFocusRestored,
                onClick = { onCreditClick(credit) }
            )
        }
    }

    LaunchedEffect(initialFocusKey) {
        if (initialFocusKey != null) {
            val restoredList = snapshotFlow { currentCredits }.first { list ->
                list.any { "${it.mediaType ?: "movie"}_${it.id}" == initialFocusKey }
            }
            val index = restoredList.indexOfFirst {
                "${it.mediaType ?: "movie"}_${it.id}" == initialFocusKey
            }
            if (index >= 0) {
                val isVisible = state.layoutInfo.visibleItemsInfo.any { it.index == index }
                if (!isVisible) {
                    state.scrollToItem(index)
                }
            }
        }
    }
}

/**
 * Renders one movie or TV appearance card inside [ActorCreditsGrid], reusing [FilmographyMetadata].
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActorCreditCard(
    credit: TmdbCastCredit,
    isTv: Boolean,
    shouldRequestFocus: Boolean,
    onFocusRestored: () -> Unit,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val ratingText = remember(credit.voteAverage) {
        credit.voteAverage?.takeIf { it > 0.0 }?.let { "⭐ ${"%.1f".format(Locale.US, it)}" }.orEmpty()
    }

    if (shouldRequestFocus) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
            delay(150)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    if (isTv) {
        Surface(
            onClick = onClick,
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused && shouldRequestFocus) onFocusRestored() }
        ) {
            Column {
                AsyncImage(
                    model = credit.fullPosterUrl,
                    contentDescription = credit.displayTitle,
                    placeholder = painterResource(R.drawable.ic_launcher_foreground),
                    error = painterResource(R.drawable.ic_launcher_foreground),
                    fallback = painterResource(R.drawable.ic_launcher_foreground),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.67f),
                    contentScale = ContentScale.Crop
                )
                Column(
                    modifier = Modifier
                        .background(androidx.tv.material3.MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = credit.displayTitle,
                        style = androidx.tv.material3.MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = androidx.tv.material3.MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    FilmographyMetadata(credit = credit, isTv = true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = credit.character.orEmpty(),
                            style = androidx.tv.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (ratingText.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = ratingText,
                                style = androidx.tv.material3.MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = androidx.tv.material3.MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    } else {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused && shouldRequestFocus) onFocusRestored() }
                .focusHighlight(shape = MaterialTheme.shapes.medium),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column {
                AsyncImage(
                    model = credit.fullPosterUrl,
                    contentDescription = credit.displayTitle,
                    placeholder = painterResource(R.drawable.ic_launcher_foreground),
                    error = painterResource(R.drawable.ic_launcher_foreground),
                    fallback = painterResource(R.drawable.ic_launcher_foreground),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.67f),
                    contentScale = ContentScale.Crop
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(
                        text = credit.displayTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    FilmographyMetadata(credit = credit, isTv = false)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = credit.character.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (ratingText.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = ratingText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Displays movie and TV favorites together in the same responsive poster grid.
 */
@Composable
private fun FavoriteItemsGrid(
    movies: List<Movie>,
    series: List<TmdbTvSummary>,
    tvGenres: List<TmdbTvGenre>,
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    downloadedMovieIds: Set<Int>,
    downloadedSeriesIds: Set<Int>,
    watchHistoryMap: Map<String, com.martinrevert.latorrentola.model.user.PlaybackProgress> = emptyMap(),
    selectedMovieIds: Set<Int>,
    lastClickedMovieId: Int?,
    lastClickedSeriesId: Int?,
    onMovieClick: (Movie) -> Unit,
    onTvSeriesClick: (TmdbTvSummary) -> Unit,
    onMovieLongClick: (Int) -> Unit,
    onMovieFocusRestored: () -> Unit,
    onTvFocusRestored: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val currentMovies by rememberUpdatedState(movies)
    val currentSeries by rememberUpdatedState(series)
    val columns = when {
        isTv -> GridCells.Fixed(6)
        screenWidth < 600.dp -> GridCells.Fixed(2)
        screenWidth < 900.dp -> GridCells.Adaptive(minSize = 160.dp)
        else -> GridCells.Adaptive(minSize = 200.dp)
    }
    val genreNames = remember(tvGenres) { tvGenres.associate { it.id to it.name } }

    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val totalGridItems = movies.size + series.size

    LazyVerticalGrid(
        columns = columns,
        state = state,
        modifier = modifier
            .fillMaxSize()
            .focusRestorer(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = movies,
            key = { _, it -> "movie_${it.id}" },
            contentType = { _, _ -> "movie" }
        ) { index, movie ->
            val progress = watchHistoryMap[movie.id.toString()] ?: watchHistoryMap[movie.title]
            val progressPercent = progress?.let { if (it.isCompleted) 100 else it.progressPercent }
            MovieItem(
                movie = movie,
                modifier = Modifier.tvGridRowDpadNavigation(
                    isTv = isTv,
                    index = index,
                    totalItems = totalGridItems,
                    columns = 6,
                    focusManager = focusManager,
                    onFallbackNext = {
                        coroutineScope.launch {
                            state.scrollToItem(index + 1)
                            withFrameNanos { }
                            focusManager.moveFocus(FocusDirection.Next)
                        }
                    }
                ),
                isDownloaded = movie.id in downloadedMovieIds,
                isSelected = movie.id in selectedMovieIds,
                progressPercent = progressPercent,
                onClick = { onMovieClick(movie) },
                onLongClick = { onMovieLongClick(movie.id) },
                shouldRequestFocus = movie.id == lastClickedMovieId,
                onFocusRestored = onMovieFocusRestored,
                isTv = isTv
            )
        }
        itemsIndexed(
            items = series,
            key = { _, it -> "tv_${it.id}" },
            contentType = { _, _ -> "tv_series" }
        ) { seriesIndex, item ->
            val itemGenres = remember(item.genres, item.genreIds, genreNames) {
                item.genres.orEmpty().map { it.name }.ifEmpty {
                    item.genreIds.orEmpty().mapNotNull(genreNames::get)
                }
            }
            val absoluteIndex = movies.size + seriesIndex
            TvSeriesCard(
                series = item,
                genres = itemGenres,
                modifier = Modifier.tvGridRowDpadNavigation(
                    isTv = isTv,
                    index = absoluteIndex,
                    totalItems = totalGridItems,
                    columns = 6,
                    focusManager = focusManager,
                    onFallbackNext = {
                        coroutineScope.launch {
                            state.scrollToItem(absoluteIndex + 1)
                            withFrameNanos { }
                            focusManager.moveFocus(FocusDirection.Next)
                        }
                    }
                ),
                hasDownloads = item.id in downloadedSeriesIds,
                isSelected = item.id in selectedMovieIds,
                onClick = { onTvSeriesClick(item) },
                onLongClick = { onMovieLongClick(item.id) },
                shouldRequestFocus = item.id == lastClickedSeriesId,
                onFocusRestored = onTvFocusRestored,
                isTv = isTv
            )
        }
    }

    LaunchedEffect(lastClickedMovieId, lastClickedSeriesId) {
        when {
            lastClickedMovieId != null -> {
                val restoredMovies = snapshotFlow { currentMovies }.first { list ->
                    list.any { it.id == lastClickedMovieId }
                }
                val index = restoredMovies.indexOfFirst { it.id == lastClickedMovieId }
                if (index >= 0) {
                    val isVisible = state.layoutInfo.visibleItemsInfo.any { it.index == index }
                    if (!isVisible) {
                        state.scrollToItem(index)
                    }
                }
            }
            lastClickedSeriesId != null -> {
                val (_, restoredSeries) = snapshotFlow { currentMovies to currentSeries }.first { (_, sList) ->
                    sList.any { it.id == lastClickedSeriesId }
                }
                val seriesIndex = restoredSeries.indexOfFirst { it.id == lastClickedSeriesId }
                if (seriesIndex >= 0) {
                    val targetIndex = currentMovies.size + seriesIndex
                    val isVisible = state.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
                    if (!isVisible) {
                        state.scrollToItem(targetIndex)
                    }
                }
            }
        }
    }
}

/**
 * Provides an accessible search field with a catalog-specific hint and optional voice input.
 */
@Composable
private fun SearchTextField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onVoiceSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    showVoiceSearch: Boolean = true,
    isTvMode: Boolean = false,
    isActorMode: Boolean = false
) {
    TextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = {
            Text(
                stringResource(
                    when {
                        isActorMode -> R.string.actor_search_placeholder
                        isTvMode -> R.string.tv_search_placeholder
                        else -> R.string.search_placeholder
                    }
                )
            )
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        trailingIcon = if (showVoiceSearch) {
            {
                IconButton(
                    onClick = onVoiceSearchClick,
                    modifier = Modifier.focusHighlight(shape = CircleShape)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = stringResource(com.martinrevert.latorrentola.R.string.voice_search_desc))
                }
            }
        } else null,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
        )
    )
}

/** TV preview of the movie search screen. */
@Preview(name = "TV Light", showBackground = true, device = "id:tv_720p", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "TV Dark", showBackground = true, device = "id:tv_720p", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SearchScreenTvPreview() {
    val sampleMovies = listOf(
        Movie(id = 1, title = "Inception", year = 2010, rating = "8.8"),
        Movie(id = 2, title = "Interstellar", year = 2014, rating = "8.6")
    )

    LaTorrentolaTheme {
        SearchScreenContent(
            uiState = SearchUiState.Success(sampleMovies),
            selectedQuality = "All",
            lastClickedMovieId = null,
            downloadedMovieIds = emptySet(),
            selectedFavoriteIds = emptySet(),
            qualityOptions = listOf("All", "1080p", "720p"),
            searchQuery = "Inception",
            isShowingFavorites = false,
            isShowingDownloads = false,
            isShowingNew = false,
            isShowingGenre = false,
            initialGenre = null,
            isTv = true,
            focusRequester = remember { FocusRequester() },
            onSearchQueryChange = {},
            onVoiceSearchClick = {},
            onQualityClick = {},
            onMovieClick = {},
            onLongClick = {},
            onLoadMore = {},
            onBackClick = {},
            onClearSelection = {},
            onDeleteSelectedFavorites = {},
            onFocusRestored = {}
        )
    }
}

/** Light and dark previews of the movie search screen across form factors. */
@com.martinrevert.latorrentola.ui.theme.LightDarkPreviews
@Composable
fun SearchScreenPreview() {
    val sampleMovies = listOf(
        Movie(id = 1, title = "Inception", year = 2010, rating = "8.8"),
        Movie(id = 2, title = "Interstellar", year = 2014, rating = "8.6")
    )

    LaTorrentolaTheme {
        SearchScreenContent(
            uiState = SearchUiState.Success(sampleMovies),
            selectedQuality = "All",
            lastClickedMovieId = null,
            downloadedMovieIds = emptySet(),
            selectedFavoriteIds = emptySet(),
            qualityOptions = listOf("All", "1080p", "720p"),
            searchQuery = "Inception",
            isShowingFavorites = false,
            isShowingDownloads = false,
            isShowingNew = false,
            isShowingGenre = false,
            initialGenre = null,
            isTv = false,
            focusRequester = remember { FocusRequester() },
            onSearchQueryChange = {},
            onVoiceSearchClick = {},
            onQualityClick = {},
            onMovieClick = {},
            onLongClick = {},
            onLoadMore = {},
            onBackClick = {},
            onClearSelection = {},
            onDeleteSelectedFavorites = {},
            onFocusRestored = {}
        )
    }
}
