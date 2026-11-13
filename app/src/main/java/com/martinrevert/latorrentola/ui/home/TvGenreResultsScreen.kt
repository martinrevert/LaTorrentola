@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.ui.components.ApiErrorView
import com.martinrevert.latorrentola.ui.components.MovieListPlaceholder
import com.martinrevert.latorrentola.ui.components.TvGenreSortChips
import com.martinrevert.latorrentola.ui.components.TvSeriesGrid
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.ui.theme.hazeAppBarContainerColor
import com.martinrevert.latorrentola.ui.theme.hazeAppBarGlass
import com.martinrevert.latorrentola.ui.theme.rememberHandheldHazeState
import com.martinrevert.latorrentola.utils.isTvDevice
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

/**
 * Presents paginated TMDB series results for one selected TV genre.
 *
 * @param viewModel Genre results, sort, and paging state.
 * @param genreId TMDB TV genre identifier.
 * @param genreName Localized TV genre label.
 * @param onSeriesClick Opens TV series details.
 * @param onBackClick Returns to the previous destination using the shared circular focus indicator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvGenreResultsScreen(
    viewModel: TvGenreResultsViewModel,
    genreId: Int,
    genreName: String,
    onSeriesClick: (TmdbTvSummary) -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedSort by viewModel.sort.collectAsState()
    val descending by viewModel.descending.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val genreCatalogError by viewModel.genreCatalogError.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val lastClickedSeriesId by viewModel.lastClickedSeriesId.collectAsState()
    val gridState = rememberLazyGridState()
    val sortFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val hazeState = rememberHandheldHazeState(isTv)

    LaunchedEffect(genreId) { viewModel.setGenre(genreId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.tv_genre_results_title, genreName),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .focusHighlight(shape = CircleShape)
                            .focusProperties { down = sortFocusRequester }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_desc)
                        )
                    }
                },
                colors = if (isTv) {
                    TopAppBarDefaults.topAppBarColors()
                } else {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = hazeAppBarContainerColor(hazeState != null, 0.90f)
                    )
                },
                modifier = Modifier.hazeAppBarGlass(hazeState)
            )
        }
    ) { padding ->
        if (isTv) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Top
            ) {
                if (uiState !is TvGenreResultsUiState.Error) {
                    Column {
                        Text(
                            text = stringResource(R.string.tv_sort_by),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                        )
                        TvGenreSortChips(
                            selectedSort = selectedSort,
                            descending = descending,
                            onSortClick = { sort ->
                                viewModel.setSort(sort)
                                coroutineScope.launch { gridState.scrollToItem(0) }
                            },
                            onDirectionClick = {
                                viewModel.toggleDirection()
                                coroutineScope.launch { gridState.scrollToItem(0) }
                            },
                            firstFocusRequester = sortFocusRequester
                        )
                    }
                }
                genreCatalogError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                val listContentPadding = PaddingValues(16.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when (val state = uiState) {
                        TvGenreResultsUiState.Loading -> MovieListPlaceholder(
                            contentPadding = listContentPadding
                        )

                        is TvGenreResultsUiState.Success -> TvSeriesGrid(
                            series = state.series,
                            genres = genres,
                            state = gridState,
                            isLoadingMore = isLoadingMore,
                            onSeriesClick = { series ->
                                viewModel.setLastClickedSeriesId(series.id)
                                onSeriesClick(series)
                            },
                            onLoadMore = viewModel::loadMore,
                            initialFocusId = lastClickedSeriesId,
                            onFocusRestored = { viewModel.clearLastClickedSeriesId() },
                            contentPadding = listContentPadding
                        )

                        is TvGenreResultsUiState.Error -> ApiErrorView(
                            error = state.message,
                            onRetry = { viewModel.refresh() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } else {
            SubcomposeLayout(modifier = Modifier.fillMaxSize()) { constraints ->
                val headerPlaceables = if (uiState !is TvGenreResultsUiState.Error || genreCatalogError != null) {
                    subcompose("header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(hazeAppBarContainerColor(hazeState != null, 0.90f))
                                .hazeAppBarGlass(hazeState)
                        ) {
                            if (uiState !is TvGenreResultsUiState.Error) {
                                Text(
                                    text = stringResource(R.string.tv_sort_by),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                                )
                                TvGenreSortChips(
                                    selectedSort = selectedSort,
                                    descending = descending,
                                    onSortClick = { sort ->
                                        viewModel.setSort(sort)
                                        coroutineScope.launch { gridState.scrollToItem(0) }
                                    },
                                    onDirectionClick = {
                                        viewModel.toggleDirection()
                                        coroutineScope.launch { gridState.scrollToItem(0) }
                                    },
                                    firstFocusRequester = sortFocusRequester
                                )
                            }
                            genreCatalogError?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
                } else {
                    emptyList()
                }
                val topBarOffsetPx = padding.calculateTopPadding().roundToPx()
                val headerHeightDp = (headerPlaceables.maxOfOrNull { it.height } ?: 0).toDp()
                val listContentPadding = PaddingValues(
                    start = 16.dp,
                    top = padding.calculateTopPadding() + headerHeightDp + 16.dp,
                    end = 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp
                )
                val contentPlaceables = subcompose("content") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (hazeState != null) Modifier.hazeSource(state = hazeState) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        when (val state = uiState) {
                            TvGenreResultsUiState.Loading -> MovieListPlaceholder(
                                contentPadding = listContentPadding
                            )

                            is TvGenreResultsUiState.Success -> TvSeriesGrid(
                                series = state.series,
                                genres = genres,
                                state = gridState,
                                isLoadingMore = isLoadingMore,
                                onSeriesClick = { series ->
                                    viewModel.setLastClickedSeriesId(series.id)
                                    onSeriesClick(series)
                                },
                                onLoadMore = viewModel::loadMore,
                                initialFocusId = lastClickedSeriesId,
                                onFocusRestored = { viewModel.clearLastClickedSeriesId() },
                                contentPadding = listContentPadding
                            )

                            is TvGenreResultsUiState.Error -> ApiErrorView(
                                error = state.message,
                                onRetry = { viewModel.refresh() },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        top = padding.calculateTopPadding() + headerHeightDp,
                                        bottom = padding.calculateBottomPadding()
                                    )
                            )
                        }
                    }
                }.map { it.measure(constraints) }

                layout(constraints.maxWidth, constraints.maxHeight) {
                    contentPlaceables.forEach { it.placeRelative(0, 0) }
                    headerPlaceables.forEach { it.placeRelative(0, topBarOffsetPx) }
                }
            }
        }
    }
}

@com.martinrevert.latorrentola.ui.theme.LightDarkPreviews
@Composable
private fun TvGenreResultsScreenPreview() {
    com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme {
        androidx.compose.material3.Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                TvSeriesGrid(
                    series = listOf(
                        TmdbTvSummary(id = 1, name = "Breaking Bad", voteAverage = 9.5),
                        TmdbTvSummary(id = 2, name = "Better Call Saul", voteAverage = 8.9)
                    ),
                    genres = emptyList(),
                    state = rememberLazyGridState(),
                    isLoadingMore = false,
                    onSeriesClick = {},
                    onLoadMore = {}
                )
            }
        }
    }
}
