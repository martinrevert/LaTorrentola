package com.martinrevert.latorrentola.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.utils.isTvDevice
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MovieList(
    movies: List<Movie>,
    state: LazyGridState,
    modifier: Modifier = Modifier,
    lastVisitDate: Long? = null,
    downloadedMovieIds: Set<Int> = emptySet(),
    selectedIds: Set<Int> = emptySet(),
    isLoadingMore: Boolean = false,
    onMovieClick: (Movie) -> Unit,
    onLoadMore: () -> Unit,
    onLongClick: ((Int) -> Unit)? = null,
    onToggleSelection: ((Int) -> Unit)? = null,
    initialFocusId: Int? = null,
    onFocusRestored: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(16.dp)
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
        modifier = modifier
            .fillMaxSize()
            .focusRestorer(),
        contentPadding = contentPadding,
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
            snapshotFlow { movies }.first { list -> list.any { it.id == initialFocusId } }
            val index = movies.indexOfFirst { it.id == initialFocusId }
            if (index != -1) {
                state.scrollToItem(index)
            }
        }
    }
}
