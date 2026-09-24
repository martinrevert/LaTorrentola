package com.martinrevert.latorrentola.ui.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import android.os.Build
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
import com.martinrevert.latorrentola.ui.components.TvChip
import com.martinrevert.latorrentola.ui.components.QualityChips
import com.martinrevert.latorrentola.ui.components.GenreChips
import com.martinrevert.latorrentola.ui.components.MovieItem
import com.martinrevert.latorrentola.ui.components.MovieList
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.GenreTranslation
import com.martinrevert.latorrentola.utils.isTvDevice
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.hazeGlass

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

    val isInspection = LocalInspectionMode.current
    val isPreAndroid12 = !isInspection && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
    val topBarContainerColor = if (isPreAndroid12) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    } else {
        Color.Transparent
    }

    val hazeState = if (!isTv) rememberHazeState() else null

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                modifier = if (hazeState != null) Modifier.hazeGlass(input = HazeInput.Sources(hazeState)) else Modifier,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarContainerColor),
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

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
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
                }
            } else {
                // Handheld Layout: Full-screen edge-to-edge with Haze top blur and translucent bottom bar
                val scope = rememberCoroutineScope()
                val pullRefreshState = rememberPullRefreshState(isRefreshing, onRefresh = {
                    onSetLastClickedMovieId(null)
                    onRefresh(true)
                    scope.launch { gridState.scrollToItem(0) }
                })

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState)
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
                        onFocusRestored = onFocusRestored,
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding() + 96.dp,
                            bottom = navBarHeight + 16.dp,
                            start = 16.dp,
                            end = 16.dp
                        )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = padding.calculateTopPadding())
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
                    }

                    PullRefreshIndicator(
                        refreshing = isRefreshing,
                        state = pullRefreshState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = padding.calculateTopPadding() + 96.dp)
                    )
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
            sheetState = sheetState,
            hazeState = hazeState
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
    onFocusRestored: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(16.dp),
    modifier: Modifier = Modifier
) {
    when (uiState) {
        is HomeUiState.Loading -> {
            MovieListPlaceholder(contentPadding = contentPadding)
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
                onFocusRestored = onFocusRestored,
                contentPadding = contentPadding,
                modifier = modifier
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun GenreBottomSheet(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    hazeState: HazeState?
) {
    val context = LocalContext.current
    val sortedGenres = remember(genres) {
        genres.sortedBy { GenreTranslation.getGenreText(it).asString(context) }
    }
    val isInspection = LocalInspectionMode.current
    val isPreAndroid12 = !isInspection && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
    val sheetContainerColor = if (isPreAndroid12) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    } else {
        Color.Transparent
    }

    val sheetModifier = if (hazeState != null) {
        Modifier.hazeGlass(input = HazeInput.Sources(hazeState))
    } else {
        Modifier
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetContainerColor,
        modifier = sheetModifier
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
                    TvChip(
                        selected = false,
                        onClick = { onGenreClick(genre) },
                        label = { Text(GenreTranslation.getGenreText(genre).asString()) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
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

