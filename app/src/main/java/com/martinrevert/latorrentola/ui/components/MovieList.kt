package com.martinrevert.latorrentola.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.utils.isTvDevice
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val PREFETCH_ITEM_THRESHOLD = 12

/** Displays a responsive, focus-restorable grid of movie cards with pagination placeholders. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MovieList(
    movies: List<Movie>,
    state: LazyGridState,
    modifier: Modifier = Modifier,
    lastVisitDate: Long? = null,
    downloadedMovieIds: Set<Int> = emptySet(),
    watchHistoryMap: Map<String, PlaybackProgress> = emptyMap(),
    selectedIds: Set<Int> = emptySet(),
    isLoadingMore: Boolean = false,
    onMovieClick: (Movie) -> Unit,
    onLoadMore: () -> Unit,
    onLongClick: ((Int) -> Unit)? = null,
    onToggleSelection: ((Int) -> Unit)? = null,
    initialFocusId: Int? = null,
    onFocusRestored: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onMovieFocus: ((Movie) -> Unit)? = null,
    activeHeroMovieId: Int? = null
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val currentMovies by rememberUpdatedState(movies)
    val currentIsLoadingMore by rememberUpdatedState(isLoadingMore)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    val columns = when {
        isTv -> GridCells.Fixed(6) // Enforce exactly 6 columns on TV devices
        screenWidth < 600.dp -> GridCells.Fixed(2)
        screenWidth < 840.dp -> GridCells.Adaptive(minSize = 160.dp)
        else -> GridCells.Adaptive(minSize = 175.dp)
    }

    // Proactive prefetch before reaching the bottom of the grid
    LaunchedEffect(state) {
        snapshotFlow {
            val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalMovies = currentMovies.size
            val nearBottom = totalMovies > 0 && lastVisible >= (totalMovies - PREFETCH_ITEM_THRESHOLD).coerceAtLeast(0)
            Triple(nearBottom, totalMovies, currentIsLoadingMore)
        }
            .distinctUntilChanged()
            .collect { (nearBottom, _, loading) ->
                if (nearBottom && !loading) {
                    currentOnLoadMore()
                }
            }
    }

    var hasRequestedInitialTvFocus by remember { mutableStateOf(false) }
    val effectiveFocusId = remember(initialFocusId, movies, isTv, hasRequestedInitialTvFocus) {
        when {
            initialFocusId != null -> initialFocusId
            isTv && !hasRequestedInitialTvFocus && movies.isNotEmpty() -> movies.first().id
            else -> null
        }
    }

    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

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
            key = { _, movie -> movie.id },
            contentType = { _, _ -> "movie" }
        ) { index, movie ->
            val isTarget = movie.id == effectiveFocusId
            val progress = watchHistoryMap[movie.id.toString()] ?: watchHistoryMap[movie.title]
            val progressPercent = progress?.let { if (it.isCompleted) 100 else it.progressPercent }
            MovieItem(
                movie = movie,
                modifier = Modifier.tvGridRowDpadNavigation(
                    isTv = isTv,
                    index = index,
                    totalItems = movies.size,
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
                lastVisitDate = lastVisitDate,
                isDownloaded = downloadedMovieIds.contains(movie.id),
                isSelected = selectedIds.contains(movie.id),
                isHeroSelected = activeHeroMovieId == movie.id,
                progressPercent = progressPercent,
                onClick = { onMovieClick(movie) },
                onLongClick = onLongClick?.let { { it(movie.id) } },
                onToggleSelection = onToggleSelection?.let { { it(movie.id) } },
                shouldRequestFocus = isTarget,
                onFocusRestored = {
                    hasRequestedInitialTvFocus = true
                    onFocusRestored()
                },
                isTv = isTv,
                onFocus = onMovieFocus?.let { { it(movie) } }
            )
        }
        if (isLoadingMore) {
            items(
                count = 6,
                key = { "placeholder_$it" },
                contentType = { "placeholder" }
            ) {
                MovieItemPlaceholder(isTv = isTv)
            }
        }
        item(key = "load_more_indicator", contentType = "load_more") {
            Box(modifier = Modifier.size(48.dp)) {
                LaunchedEffect(movies.size, isLoadingMore) {
                    if (!isLoadingMore) {
                        currentOnLoadMore()
                    }
                }
            }
        }
    }

    // Focus restoration scroll: only run when initialFocusId changes
    LaunchedEffect(initialFocusId) {
        if (initialFocusId != null) {
            val restoredMovies = snapshotFlow { currentMovies }.first { list ->
                list.any { it.id == initialFocusId }
            }
            val index = restoredMovies.indexOfFirst { it.id == initialFocusId }
            if (index != -1) {
                val isVisible = state.layoutInfo.visibleItemsInfo.any { it.index == index }
                if (!isVisible) {
                    state.scrollToItem(index)
                }
            }
        }
    }
}

@com.martinrevert.latorrentola.ui.theme.LightDarkPreviews
@Composable
private fun MovieListPreview() {
    com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme {
        androidx.compose.material3.Surface {
            MovieList(
                movies = listOf(
                    Movie(id = 1, title = "Movie 1", year = 2024, rating = "8.5"),
                    Movie(id = 2, title = "Movie 2", year = 2023, rating = "7.9")
                ),
                state = androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
                onMovieClick = {},
                onLoadMore = {}
            )
        }
    }
}
