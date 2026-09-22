package com.martinrevert.latorrentola.ui.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.clickable
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.ui.components.MovieItemPlaceholder
import com.martinrevert.latorrentola.ui.components.MovieListPlaceholder
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.GenreTranslation
import com.martinrevert.latorrentola.utils.isTvDevice

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    userPhotoUrl: String?,
    onMovieClick: (Movie) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
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
        onFocusRestored = { viewModel.clearLastClickedMovieId() }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalTvMaterial3Api::class)
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
    qualityOptions: List<String>,
    userPhotoUrl: String?,
    isTv: Boolean,
    onMovieClick: (Movie) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onGenreClick: (String) -> Unit,
    onQualityClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: (Boolean) -> Unit,
    onSetLastClickedMovieId: (Int?) -> Unit,
    onFocusRestored: () -> Unit
) {
    var showGenreSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 1. Properly save and restore scroll state across configuration changes (rotation)
    val gridState = rememberLazyGridState()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color.White else Color.Black
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
                        onClick = onSearchClick,
                        modifier = Modifier.focusHighlight(shape = CircleShape)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_desc))
                    }
                    BadgedBox(
                        badge = {
                            if (favoritesCount > 0) {
                                Badge(
                                    containerColor = Color(0xFFB3261E), // Use same vibrant red in both modes
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = if (favoritesCount > 99) "99+" else favoritesCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 4.dp, top = 4.dp)
                    ) {
                        IconButton(
                            onClick = onFavoritesClick,
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.favorites_desc))
                        }
                    }
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.focusHighlight(shape = CircleShape)
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
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            GenreChips(
                genres = topGenres,
                onGenreClick = onGenreClick,
                onAllGenresClick = { showGenreSheet = true }
            )

            QualityChips(
                options = qualityOptions,
                selectedQuality = selectedQuality ?: "All",
                onQualityClick = onQualityClick
            )

            if (isTv) {
                // TV Layout: No pull-to-refresh
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    HomeContent(
                        uiState = uiState,
                        gridState = gridState,
                        lastVisitDate = lastVisitDate,
                        downloadedMovieIds = downloadedMovieIds,
                        isLoadingMore = isLoadingMore,
                        onMovieClick = {
                            onSetLastClickedMovieId(it.id)
                            onMovieClick(it)
                        },
                        onLoadMore = onLoadMore,
                        lastClickedMovieId = lastClickedMovieId,
                        onFocusRestored = onFocusRestored
                    )
                }
            } else {
                // Handheld Layout: With pull-to-refresh
                val scope = rememberCoroutineScope()
                val pullRefreshState = rememberPullRefreshState(isRefreshing, onRefresh = {
                    onSetLastClickedMovieId(null)
                    onRefresh(true)
                    scope.launch { gridState.scrollToItem(0) }
                })

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState),
                    contentAlignment = Alignment.Center
                ) {
                    HomeContent(
                        uiState = uiState,
                        gridState = gridState,
                        lastVisitDate = lastVisitDate,
                        downloadedMovieIds = downloadedMovieIds,
                        isLoadingMore = isLoadingMore,
                        onMovieClick = {
                            onSetLastClickedMovieId(it.id)
                            onMovieClick(it)
                        },
                        onLoadMore = onLoadMore,
                        lastClickedMovieId = lastClickedMovieId,
                        onFocusRestored = onFocusRestored
                    )
                    // Pull-to-refresh indicator (official Compose implementation)
                    PullRefreshIndicator(isRefreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }

    if (showGenreSheet) {
        GenreBottomSheet(
            genres = allGenres,
            onGenreClick = {
                onGenreClick(it)
                showGenreSheet = false
            },
            onDismiss = { showGenreSheet = false },
            sheetState = sheetState
        )
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    gridState: LazyGridState,
    lastVisitDate: Long?,
    downloadedMovieIds: Set<Int>,
    isLoadingMore: Boolean = false,
    onMovieClick: (Movie) -> Unit,
    onLoadMore: () -> Unit,
    lastClickedMovieId: Int? = null,
    onFocusRestored: () -> Unit = {}
) {
    when (uiState) {
        is HomeUiState.Loading -> {
            MovieListPlaceholder()
        }
        is HomeUiState.Success -> {
            MovieList(
                movies = uiState.movies,
                state = gridState,
                lastVisitDate = lastVisitDate,
                downloadedMovieIds = downloadedMovieIds,
                isLoadingMore = isLoadingMore,
                onMovieClick = onMovieClick,
                onLoadMore = onLoadMore,
                initialFocusId = lastClickedMovieId,
                onFocusRestored = onFocusRestored
            )
        }
        is HomeUiState.Error -> {
            Text(
                text = uiState.message.asString(),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QualityChips(
    options: List<String>,
    selectedQuality: String,
    onQualityClick: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .focusRestorer()
            .padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        lazyItems(options) { quality ->
            val isSelected = selectedQuality == quality
            FilterChip(
                selected = isSelected,
                onClick = { onQualityClick(quality) },
                label = { 
                    Text(
                        text = if (quality == "All") stringResource(R.string.quality_all) else quality,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                },
                modifier = Modifier.focusHighlight(shape = MaterialTheme.shapes.small)
            )
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun GenreChips(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    onAllGenresClick: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .focusRestorer()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = false,
                onClick = onAllGenresClick,
                label = { 
                    Text(
                        text = stringResource(R.string.all_genres),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                leadingIcon = { 
                    Icon(
                        Icons.Default.FilterList, 
                        contentDescription = null, 
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                },
                modifier = Modifier.focusHighlight(shape = MaterialTheme.shapes.small)
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = { onGenreClick("nuevas") },
                label = { 
                    Text(
                        text = stringResource(R.string.new_movies),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                leadingIcon = { 
                    Icon(
                        Icons.Default.NewReleases, 
                        contentDescription = null, 
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                },
                modifier = Modifier.focusHighlight(shape = MaterialTheme.shapes.small)
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = { onGenreClick("ya_vistas") },
                label = { 
                    Text(
                        text = stringResource(R.string.already_seen),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                leadingIcon = { 
                    Icon(
                        Icons.Default.History, 
                        contentDescription = null, 
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                },
                modifier = Modifier.focusHighlight(shape = MaterialTheme.shapes.small)
            )
        }
        lazyItems(genres) { genre ->
            SuggestionChip(
                onClick = { onGenreClick(genre) },
                label = { 
                    Text(
                        text = GenreTranslation.getGenreText(genre).asString(),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                modifier = Modifier.focusHighlight(shape = MaterialTheme.shapes.small)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun GenreBottomSheet(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
    val context = LocalContext.current
    val sortedGenres = remember(genres) {
        genres.sortedBy { GenreTranslation.getGenreText(it).asString(context) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.browse_by_genre),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Grid of genres
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sortedGenres.forEach { genre ->
                    InputChip(
                        selected = false,
                        onClick = { onGenreClick(genre) },
                        label = { 
                            Text(
                                text = GenreTranslation.getGenreText(genre).asString(),
                                color = MaterialTheme.colorScheme.onSurface
                            ) 
                        },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun MovieList(
    movies: List<Movie>,
    state: LazyGridState,
    lastVisitDate: Long? = null,
    downloadedMovieIds: Set<Int> = emptySet(),
    selectedIds: Set<Int> = emptySet(),
    isLoadingMore: Boolean = false,
    onMovieClick: (Movie) -> Unit,
    onLoadMore: () -> Unit,
    onLongClick: ((Int) -> Unit)? = null,
    onToggleSelection: ((Int) -> Unit)? = null,
    initialFocusId: Int? = null,
    onFocusRestored: () -> Unit = {}
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    val columns = when {
        isTv -> GridCells.Fixed(6) // Enforce exactly 6 columns on TV devices
        screenWidth < 600.dp -> GridCells.Fixed(2)
        screenWidth < 900.dp -> GridCells.Adaptive(minSize = 160.dp)
        else -> GridCells.Adaptive(minSize = 200.dp) 
    }

    LazyVerticalGrid(
        columns = columns,
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(movies, key = { it.id }) { movie ->
            MovieItem(
                movie = movie, 
                lastVisitDate = lastVisitDate,
                isDownloaded = downloadedMovieIds.contains(movie.id),
                isSelected = selectedIds.contains(movie.id),
                onClick = { onMovieClick(movie) },
                onLongClick = onLongClick?.let { { it(movie.id) } },
                onToggleSelection = onToggleSelection?.let { { it(movie.id) } },
                shouldRequestFocus = movie.id == initialFocusId,
                onFocusRestored = onFocusRestored
            )
        }
        if (isLoadingMore) {
            items(6, key = { "placeholder_$it" }) {
                MovieItemPlaceholder(isTv = isTv)
            }
        }
        item(key = "load_more_indicator") {
            Box(modifier = Modifier.size(48.dp)) {
                LaunchedEffect(Unit) {
                    onLoadMore()
                }
            }
        }
    }

    // Focus restoration scroll: only run when initialFocusId changes
    LaunchedEffect(initialFocusId) {
        if (initialFocusId != null) {
            // Give time for list to be fully populated and measured
            snapshotFlow { movies }.first { list -> list.any { it.id == initialFocusId } }
            val index = movies.indexOfFirst { it.id == initialFocusId }
            if (index != -1) {
                state.scrollToItem(index)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieItem(
    movie: Movie,
    lastVisitDate: Long? = null,
    isDownloaded: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onToggleSelection: (() -> Unit)? = null,
    shouldRequestFocus: Boolean = false,
    onFocusRestored: () -> Unit = {}
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val focusRequester = remember { FocusRequester() }

    val isPreview = LocalInspectionMode.current
    var isImageLoading by remember { mutableStateOf(!isPreview) }

    LaunchedEffect(shouldRequestFocus) {
        if (shouldRequestFocus) {
            // Wait for composition and layout to settle
            delay(300)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus request might fail if not attached
            }
        }
    }

    Box {
        if (isTv) {
            Surface(
                onClick = onToggleSelection ?: onClick,
                onLongClick = onLongClick,
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused && shouldRequestFocus) {
                            onFocusRestored()
                        }
                    }
            ) {
                Column {
                    Box {
                        AsyncImage(
                            model = movie.mediumCoverImage,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.67f),
                            contentScale = ContentScale.Crop,
                            onState = { state ->
                                isImageLoading = state is AsyncImagePainter.State.Loading
                            }
                        )
                        
                        val movieUploadTime = (movie.dateUploadedUnix ?: 0L) * 1000
                        val fifteenDaysInMs = 15L * 24 * 60 * 60 * 1000
                        val isRecent = movieUploadTime > (System.currentTimeMillis() - fifteenDaysInMs)
                        
                        if (isRecent) {
                            Icon(
                                painter = painterResource(com.martinrevert.latorrentola.R.drawable.new_badge),
                                contentDescription = stringResource(R.string.new_desc),
                                tint = Color.Yellow,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .size(32.dp)
                                    .rotate(-45f)
                            )
                        }

                        if (isDownloaded) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = stringResource(R.string.downloaded_desc),
                                tint = Color.Yellow,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .size(24.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        CircleShape
                                    )
                                    .padding(2.dp)
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .background(androidx.tv.material3.MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                            .fillMaxWidth()
                    ) {
                        androidx.tv.material3.Text(
                            text = movie.title ?: "",
                            style = androidx.tv.material3.MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        if (!movie.genres.isNullOrEmpty()) {
                            val translatedGenres = movie.genres.map { GenreTranslation.getGenreText(it).asString() }
                            androidx.tv.material3.Text(
                                text = translatedGenres.joinToString(", "),
                                style = androidx.tv.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.tv.material3.MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${movie.year}",
                                style = androidx.tv.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.tv.material3.MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            androidx.tv.material3.Text(
                                text = "⭐ ${movie.rating}",
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
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused && shouldRequestFocus) {
                            onFocusRestored()
                        }
                    }
                    .focusHighlight(shape = MaterialTheme.shapes.medium)
                    .semantics(mergeDescendants = true) { }
                    .combinedClickable(
                        onClick = onToggleSelection ?: onClick,
                        onLongClick = onLongClick
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Box {
                    Column {
                        Box {
                            AsyncImage(
                                model = movie.mediumCoverImage,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.67f),
                                contentScale = ContentScale.Crop,
                                onState = { state ->
                                    isImageLoading = state is AsyncImagePainter.State.Loading
                                }
                            )
                            
                            // NEW BADGE logic: Show if uploaded in the last 15 days
                            val movieUploadTime = (movie.dateUploadedUnix ?: 0L) * 1000
                            val fifteenDaysInMs = 15L * 24 * 60 * 60 * 1000
                            val isRecent = movieUploadTime > (System.currentTimeMillis() - fifteenDaysInMs)
                            
                            if (isRecent) {
                                Icon(
                                    painter = painterResource(R.drawable.new_badge),
                                    contentDescription = stringResource(R.string.new_desc),
                                    tint = Color.Yellow,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .size(32.dp)
                                        .rotate(-45f)
                                )
                            }

                            if (isDownloaded) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = stringResource(R.string.downloaded_desc),
                                    tint = Color.Yellow,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .size(24.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                            CircleShape
                                        )
                                        .padding(2.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Text(
                                text = movie.title ?: "",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            // RESTORED: Movie Genres
                            if (!movie.genres.isNullOrEmpty()) {
                                val translatedGenres = movie.genres.map { GenreTranslation.getGenreText(it).asString() }
                                Text(
                                    text = translatedGenres.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                              ) {
                                Text(
                                    text = "${movie.year}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = {
                                            val imdbUrl = "https://www.imdb.com/title/${movie.imdbCode}"
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, movie.title)
                                                val shareText = context.getString(
                                                    R.string.share_movie_text,
                                                    movie.title,
                                                    imdbUrl
                                                )
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, context.getString(
                                                R.string.share_movie_chooser)))
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Share, 
                                            contentDescription = "${stringResource(R.string.share_desc)} ${movie.title ?: ""}",
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "⭐ ${movie.rating}",
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
                    
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        )
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.selected_desc),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                }
            }
        }
        if (isImageLoading) {
            com.martinrevert.latorrentola.ui.components.MovieItemPlaceholder(isTv = isTv)
        }
    }
}

@Preview(name = "TV Light", showBackground = true, device = "id:tv_720p", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "TV Dark", showBackground = true, device = "id:tv_720p", uiMode = Configuration.UI_MODE_NIGHT_YES)
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

