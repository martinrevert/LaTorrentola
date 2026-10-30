@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.ott

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieSummary
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.ui.components.MovieListPlaceholder
import com.martinrevert.latorrentola.ui.components.TvGenreSortChips
import com.martinrevert.latorrentola.ui.components.TvSeriesGrid
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.LightDarkPreviews
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Screen presenting movies or TV series available on an OTT streaming provider with sorting and pagination.
 *
 * @param viewModel Catalog state, discovery paging, and sorting.
 * @param providerId TMDB watch provider identifier.
 * @param providerName Display name of the streaming platform.
 * @param isTvMode Whether browsing TV series or Movies.
 * @param onMovieClick Triggered when user selects a movie.
 * @param onTvSeriesClick Triggered when user selects a TV series.
 * @param onBackClick Returns to the previous destination.
 * @param themeMode User selected theme setting for mode propagation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OttCatalogScreen(
    viewModel: OttCatalogViewModel,
    providerId: Int,
    providerName: String,
    isTvMode: Boolean,
    onMovieClick: (TmdbMovieSummary) -> Unit,
    onTvSeriesClick: (TmdbTvSummary) -> Unit,
    onBackClick: () -> Unit,
    themeMode: Int? = null
) {
    val context = LocalContext.current
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }
    val isTv = remember(context) { context.isTvDevice() }

    val content = @Composable {
        val uiState by viewModel.uiState.collectAsState()
        val selectedSort by viewModel.sort.collectAsState()
        val descending by viewModel.descending.collectAsState()
        val isLoadingMore by viewModel.isLoadingMore.collectAsState()
        val lastClickedId by viewModel.lastClickedId.collectAsState()
        val tvGenres by viewModel.tvGenres.collectAsState()

        val gridState = rememberLazyGridState()
        val sortFocusRequester = remember { FocusRequester() }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(providerId, isTvMode) {
            viewModel.setProvider(providerId, isTvMode)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = providerName,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
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
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_desc)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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

                Box(modifier = Modifier.weight(1f)) {
                    when (val state = uiState) {
                        OttCatalogUiState.Loading -> {
                            MovieListPlaceholder(contentPadding = PaddingValues(16.dp))
                        }

                        is OttCatalogUiState.SuccessTv -> {
                            if (state.series.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.ott_empty_catalog),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                TvSeriesGrid(
                                    series = state.series,
                                    genres = tvGenres,
                                    state = gridState,
                                    isLoadingMore = isLoadingMore,
                                    onSeriesClick = { series ->
                                        viewModel.setLastClickedId(series.id)
                                        onTvSeriesClick(series)
                                    },
                                    onLoadMore = viewModel::loadMore,
                                    initialFocusId = lastClickedId,
                                    onFocusRestored = { viewModel.clearLastClickedId() },
                                    contentPadding = PaddingValues(16.dp)
                                )
                            }
                        }

                        is OttCatalogUiState.SuccessMovies -> {
                            if (state.movies.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.ott_empty_catalog),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                OttMovieGrid(
                                    movies = state.movies,
                                    state = gridState,
                                    isLoadingMore = isLoadingMore,
                                    onMovieClick = { movie ->
                                        viewModel.setLastClickedId(movie.id)
                                        onMovieClick(movie)
                                    },
                                    onLoadMore = viewModel::loadMore,
                                    initialFocusId = lastClickedId,
                                    onFocusRestored = { viewModel.clearLastClickedId() },
                                    contentPadding = PaddingValues(16.dp)
                                )
                            }
                        }

                        is OttCatalogUiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        text = state.message.asString(context),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (isTv) {
                                        Surface(
                                            onClick = { viewModel.refresh() },
                                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                                            colors = ClickableSurfaceDefaults.colors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.retry),
                                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = { viewModel.refresh() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Text(text = stringResource(R.string.retry))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isTv) {
        TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
            content()
        }
    } else {
        LaTorrentolaTheme(themeMode = effectiveThemeMode) {
            content()
        }
    }
}

/**
 * Displays a responsive, focus-restorable grid of TMDB movies for the OTT catalog.
 */
@Composable
private fun OttMovieGrid(
    movies: List<TmdbMovieSummary>,
    state: LazyGridState,
    isLoadingMore: Boolean,
    onMovieClick: (TmdbMovieSummary) -> Unit,
    onLoadMore: () -> Unit,
    initialFocusId: Int? = null,
    onFocusRestored: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(16.dp),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val currentMovies by rememberUpdatedState(movies)
    val currentIsLoadingMore by rememberUpdatedState(isLoadingMore)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    val columns = when {
        isTv -> GridCells.Fixed(6)
        screenWidth < 600.dp -> GridCells.Fixed(2)
        screenWidth < 900.dp -> GridCells.Adaptive(minSize = 160.dp)
        else -> GridCells.Adaptive(minSize = 200.dp)
    }

    // Prefetch before reaching bottom of the list
    LaunchedEffect(state) {
        snapshotFlow {
            val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = currentMovies.size
            val nearBottom = total > 0 && lastVisible >= (total - 6).coerceAtLeast(0)
            Triple(nearBottom, total, currentIsLoadingMore)
        }
            .distinctUntilChanged()
            .collect { (nearBottom, _, loading) ->
                if (nearBottom && !loading) {
                    currentOnLoadMore()
                }
            }
    }

    LaunchedEffect(initialFocusId) {
        if (initialFocusId != null) {
            val restoredList = snapshotFlow { currentMovies }.first { items ->
                items.any { it.id == initialFocusId }
            }
            val index = restoredList.indexOfFirst { it.id == initialFocusId }
            if (index >= 0) {
                val isVisible = state.layoutInfo.visibleItemsInfo.any { it.index == index }
                if (!isVisible) {
                    state.scrollToItem(index)
                }
            }
        }
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
            items = movies,
            key = { it.id },
            contentType = { "movie" }
        ) { movie ->
            OttMovieCard(
                movie = movie,
                isTv = isTv,
                shouldRequestFocus = movie.id == initialFocusId,
                onFocusRestored = onFocusRestored,
                onClick = { onMovieClick(movie) }
            )
        }
        if (isLoadingMore) {
            items(count = 6, key = { "ott-placeholder-$it" }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Individual movie poster card with poster image, title, rating, and year.
 */
@Composable
private fun OttMovieCard(
    movie: TmdbMovieSummary,
    isTv: Boolean,
    shouldRequestFocus: Boolean,
    onFocusRestored: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    if (shouldRequestFocus) {
        LaunchedEffect(Unit) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val posterContent = @Composable {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            AsyncImage(
                model = movie.fullPosterUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Gradient shadow on bottom
            val scrimColor = MaterialTheme.colorScheme.scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.4f to scrimColor.copy(alpha = 0f),
                            1.0f to scrimColor.copy(alpha = 0.85f)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = movie.title.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    movie.voteAverage?.let { rating ->
                        if (rating > 0.0) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "%.1f".format(Locale.US, rating),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (movie.releaseYear.isNotBlank()) {
                        Text(
                            text = movie.releaseYear,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (isTv) {
        Surface(
            onClick = onClick,
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused && shouldRequestFocus) {
                        onFocusRestored()
                    }
                }
        ) {
            posterContent()
        }
    } else {
        Card(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = modifier.fillMaxWidth()
        ) {
            posterContent()
        }
    }
}

@LightDarkPreviews
@Composable
private fun OttCatalogScreenPreview() {
    LaTorrentolaTheme {
        androidx.compose.material3.Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                OttMovieGrid(
                    movies = listOf(
                        TmdbMovieSummary(id = 1, title = "Inception", releaseDate = "2010-07-16", voteAverage = 8.8),
                        TmdbMovieSummary(id = 2, title = "Interstellar", releaseDate = "2014-11-07", voteAverage = 8.6)
                    ),
                    state = rememberLazyGridState(),
                    isLoadingMore = false,
                    onMovieClick = {},
                    onLoadMore = {}
                )
            }
        }
    }
}

@Preview(name = "TV Catalog", showBackground = true, device = "id:tv_720p")
@Composable
private fun OttCatalogScreenTvPreview() {
    TvLaTorrentolaTheme {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                OttMovieGrid(
                    movies = listOf(
                        TmdbMovieSummary(id = 1, title = "Inception", releaseDate = "2010-07-16", voteAverage = 8.8),
                        TmdbMovieSummary(id = 2, title = "Interstellar", releaseDate = "2014-11-07", voteAverage = 8.6)
                    ),
                    state = rememberLazyGridState(),
                    isLoadingMore = false,
                    onMovieClick = {},
                    onLoadMore = {}
                )
            }
        }
    }
}
