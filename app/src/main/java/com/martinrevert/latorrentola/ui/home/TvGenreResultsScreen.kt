package com.martinrevert.latorrentola.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.IconButton as TvIconButton
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.ui.components.MovieListPlaceholder
import com.martinrevert.latorrentola.ui.components.TvGenreSortChips
import com.martinrevert.latorrentola.ui.components.TvSeriesGrid
import com.martinrevert.latorrentola.utils.isTvDevice
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember

/**
 * Presents paginated TMDB series results for one selected TV genre.
 *
 * @param viewModel Genre results, sort, and paging state.
 * @param genreId TMDB TV genre identifier.
 * @param genreName Localized TV genre label.
 * @param onSeriesClick Opens TV series details.
 * @param onBackClick Returns to the previous destination.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
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
    val gridState = rememberLazyGridState()
    val sortFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }

    LaunchedEffect(genreId) { viewModel.setGenre(genreId) }
    LaunchedEffect(genreId, selectedSort, descending) {
        gridState.scrollToItem(0)
    }

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
                    if (isTv) {
                        TvIconButton(
                            onClick = onBackClick,
                            modifier = Modifier.focusProperties { down = sortFocusRequester }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_desc)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.focusProperties { down = sortFocusRequester }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_desc)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.Top
        ) {
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
                    onSortClick = viewModel::setSort,
                    onDirectionClick = viewModel::toggleDirection,
                    firstFocusRequester = sortFocusRequester
                )
            }
            genreCatalogError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                when (val state = uiState) {
                    TvGenreResultsUiState.Loading -> MovieListPlaceholder(
                        contentPadding = PaddingValues(16.dp)
                    )
                    is TvGenreResultsUiState.Success -> TvSeriesGrid(
                        series = state.series,
                        genres = genres,
                        state = gridState,
                        isLoadingMore = isLoadingMore,
                        onSeriesClick = onSeriesClick,
                        onLoadMore = viewModel::loadMore,
                        contentPadding = PaddingValues(16.dp)
                    )
                    is TvGenreResultsUiState.Error -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(state.message.asString(), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
