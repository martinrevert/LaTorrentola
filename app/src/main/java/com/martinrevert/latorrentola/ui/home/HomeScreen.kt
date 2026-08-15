package com.martinrevert.latorrentola.ui.home

import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.clickable
import android.content.Intent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.isTvDevice

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
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
    val favoritesCount by viewModel.favoritesCount.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()
    val lastClickedMovieId by viewModel.lastClickedMovieId.collectAsState()
    val qualityOptions = viewModel.qualityOptions
    
    var showGenreSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }

    // 1. Properly save and restore scroll state across configuration changes (rotation)
    val gridState = rememberLazyGridState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(com.martinrevert.latorrentola.R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color.White else Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("La Torrentola")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier.focusHighlight(shape = CircleShape)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(
                        onClick = onFavoritesClick,
                        modifier = Modifier.focusHighlight(shape = CircleShape)
                    ) {
                        BadgedBox(
                            badge = {
                                if (favoritesCount > 0) {
                                    Badge {
                                        Text(favoritesCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = "Favorites")
                        }
                    }
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.focusHighlight(shape = CircleShape)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            GenreChips(
                genres = topGenres,
                onGenreClick = onGenreClick,
                onAllGenresClick = { showGenreSheet = true }
            )

            QualityChips(
                options = qualityOptions,
                selectedQuality = selectedQuality ?: "All",
                onQualityClick = { viewModel.setQuality(it) }
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
                        onMovieClick = {
                            viewModel.setLastClickedMovieId(it.id)
                            onMovieClick(it)
                        },
                        onLoadMore = { viewModel.loadMovies() },
                        lastClickedMovieId = lastClickedMovieId,
                        onFocusRestored = { viewModel.clearLastClickedMovieId() }
                    )
                }
            } else {
                // Handheld Layout: With pull-to-refresh
                val pullRefreshState = rememberPullRefreshState(isRefreshing, onRefresh = { viewModel.refresh() })

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
                        onMovieClick = {
                            viewModel.setLastClickedMovieId(it.id)
                            onMovieClick(it)
                        },
                        onLoadMore = { viewModel.loadMovies() },
                        lastClickedMovieId = lastClickedMovieId,
                        onFocusRestored = { viewModel.clearLastClickedMovieId() }
                    )
                    // Pull-to-refresh indicator (official Compose implementation)
                    PullRefreshIndicator(isRefreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }

    if (showGenreSheet) {
        GenreBottomSheet(
            genres = viewModel.allGenres,
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
    onMovieClick: (Movie) -> Unit,
    onLoadMore: () -> Unit,
    lastClickedMovieId: Int? = null,
    onFocusRestored: () -> Unit = {}
) {
    when (uiState) {
        is HomeUiState.Loading -> {
            CircularProgressIndicator()
        }
        is HomeUiState.Success -> {
            MovieList(
                movies = uiState.movies,
                state = gridState,
                lastVisitDate = lastVisitDate,
                onMovieClick = onMovieClick,
                onLoadMore = onLoadMore,
                initialFocusId = lastClickedMovieId,
                onFocusRestored = onFocusRestored
            )
        }
        is HomeUiState.Error -> {
            Text(
                text = uiState.message,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun QualityChips(
    options: List<String>,
    selectedQuality: String,
    onQualityClick: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        lazyItems(options) { quality ->
            FilterChip(
                selected = selectedQuality == quality,
                onClick = { onQualityClick(quality) },
                label = { Text(quality) },
                modifier = Modifier.focusHighlight(shape = MaterialTheme.shapes.small)
            )
        }
    }
}

@Composable
fun GenreChips(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    onAllGenresClick: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = false,
                onClick = onAllGenresClick,
                label = { Text("All Genres") },
                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.focusHighlight(shape = MaterialTheme.shapes.small)
            )
        }
        lazyItems(genres) { genre ->
            SuggestionChip(
                onClick = { onGenreClick(genre) },
                label = { Text(genre) },
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
                text = "Browse by Genre",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Grid of genres
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                genres.forEach { genre ->
                    InputChip(
                        selected = false,
                        onClick = { onGenreClick(genre) },
                        label = { Text(genre) },
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
    onMovieClick: (Movie) -> Unit,
    onLoadMore: () -> Unit,
    onDeleteClick: ((Movie) -> Unit)? = null,
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
                onClick = { onMovieClick(movie) },
                onDeleteClick = onDeleteClick,
                shouldRequestFocus = movie.id == initialFocusId,
                onFocusRestored = onFocusRestored
            )
        }
        item {
            LaunchedEffect(Unit) {
                onLoadMore()
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

@Composable
fun MovieItem(
    movie: Movie,
    lastVisitDate: Long? = null,
    onClick: () -> Unit,
    onDeleteClick: ((Movie) -> Unit)? = null,
    shouldRequestFocus: Boolean = false,
    onFocusRestored: () -> Unit = {}
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val focusRequester = remember { FocusRequester() }

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
            .clickable(onClick = onClick),
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
                        contentScale = ContentScale.Crop
                    )
                    
                    // NEW BADGE logic: Show if uploaded in the last 15 days
                    val movieUploadTime = (movie.dateUploadedUnix ?: 0L) * 1000
                    val fifteenDaysInMs = 15L * 24 * 60 * 60 * 1000
                    val isRecent = movieUploadTime > (System.currentTimeMillis() - fifteenDaysInMs)
                    
                    if (isRecent) {
                        Icon(
                            painter = painterResource(com.martinrevert.latorrentola.R.drawable.new_badge),
                            contentDescription = "New",
                            tint = Color.Yellow,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .size(32.dp)
                                .rotate(-45f)
                        )
                    }
                }
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = movie.title ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // RESTORED: Movie Genres
                    if (!movie.genres.isNullOrEmpty()) {
                        Text(
                            text = movie.genres.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isTv) {
                                IconButton(
                                    onClick = {
                                        val imdbUrl = "https://www.imdb.com/title/${movie.imdbCode}"
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, movie.title)
                                            putExtra(Intent.EXTRA_TEXT, "Check out this movie: ${movie.title}\n$imdbUrl")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share movie"))
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Share, 
                                        contentDescription = "Share",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = "⭐ ${movie.rating}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
            
            if (onDeleteClick != null) {
                IconButton(
                    onClick = { onDeleteClick(movie) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                        .size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = "Delete",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
