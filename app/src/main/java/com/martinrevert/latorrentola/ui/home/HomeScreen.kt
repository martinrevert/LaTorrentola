package com.martinrevert.latorrentola.ui.home

import androidx.compose.foundation.clickable
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.model.YTS.Movie

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
    
    var showGenreSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // 1. Properly save and restore scroll state across configuration changes (rotation)
    val gridState = rememberLazyStaggeredGridState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("La Torrentola") },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onFavoritesClick) {
                        Icon(Icons.Default.Favorite, contentDescription = "Favorites")
                    }
                    IconButton(onClick = onSettingsClick) {
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

                    val pullRefreshState = rememberPullRefreshState(isRefreshing, onRefresh = { viewModel.refresh() })

                    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
                when (val state = uiState) {
                    is HomeUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is HomeUiState.Success -> {
                        MovieList(
                            movies = state.movies,
                            state = gridState,
                            lastVisitDate = lastVisitDate,
                            onMovieClick = onMovieClick,
                            onLoadMore = { viewModel.loadMovies() }
                        )
                    }
                    is HomeUiState.Error -> {
                        Text(
                            text = state.message,
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                // Pull-to-refresh indicator (official Compose implementation)
                PullRefreshIndicator(isRefreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
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
                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        lazyItems(genres) { genre ->
            SuggestionChip(
                onClick = { onGenreClick(genre) },
                label = { Text(genre) }
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
    state: LazyStaggeredGridState,
    lastVisitDate: Long? = null,
    onMovieClick: (Movie) -> Unit,
    onLoadMore: () -> Unit,
    onDeleteClick: ((Movie) -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    val columns = if (screenWidth < 600.dp) {
        StaggeredGridCells.Fixed(2)
    } else {
        StaggeredGridCells.Adaptive(minSize = 160.dp)
    }

    LazyVerticalStaggeredGrid(
        columns = columns,
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp
    ) {
        items(movies, key = { it.id }) { movie ->
            MovieItem(
                movie = movie, 
                lastVisitDate = lastVisitDate,
                onClick = { onMovieClick(movie) },
                onDeleteClick = onDeleteClick
            )
        }
        item {
            LaunchedEffect(Unit) {
                onLoadMore()
            }
        }
    }
}

@Composable
fun MovieItem(
    movie: Movie,
    lastVisitDate: Long? = null,
    onClick: () -> Unit,
    onDeleteClick: ((Movie) -> Unit)? = null
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
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
                    
                    // NEW BADGE logic
                    val movieUploadTime = (movie.dateUploadedUnix ?: 0L) * 1000
                    if (lastVisitDate != null && movieUploadTime > lastVisitDate) {
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
