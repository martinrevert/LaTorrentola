package com.martinrevert.latorrentola.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.TMDB.TmdbTvGenre
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.network.TmdbTvFeed
import com.martinrevert.latorrentola.ui.home.TvGenreSort
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.isTvDevice
import kotlinx.coroutines.flow.first
import java.util.Locale

/** Shows the Movies and TV mode controls using the app's adaptive chip behavior. */
@Composable
fun HomeMediaModeChips(
    isTvMode: Boolean,
    onMoviesClick: () -> Unit,
    onTvClick: () -> Unit,
    firstFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AdaptiveChip(
            selected = !isTvMode,
            onClick = onMoviesClick,
            label = { Text(stringResource(R.string.home_mode_movies)) },
            leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Movie, contentDescription = null) },
            modifier = firstFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
        )
        AdaptiveChip(
            selected = isTvMode,
            onClick = onTvClick,
            label = { Text(stringResource(R.string.home_mode_tv)) },
            leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Tv, contentDescription = null) }
        )
    }
}

/** Shows TMDB TV feed selectors. */
@Composable
fun TvFeedChips(
    selectedFeed: TmdbTvFeed,
    onFeedClick: (TmdbTvFeed) -> Unit,
    modifier: Modifier = Modifier
) {
    val feeds = listOf(
        TmdbTvFeed.ON_THE_AIR to R.string.tv_feed_on_the_air,
        TmdbTvFeed.AIRING_TODAY to R.string.tv_feed_airing_today,
        TmdbTvFeed.POPULAR to R.string.tv_feed_popular,
        TmdbTvFeed.TOP_RATED to R.string.tv_feed_top_rated
    )
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .focusRestorer()
            .padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(feeds, key = { it.first.name }) { (feed, labelId) ->
            AdaptiveChip(
                selected = selectedFeed == feed,
                onClick = { onFeedClick(feed) },
                label = { Text(stringResource(labelId)) }
            )
        }
    }
}

/** Shows TMDB sorting criteria and ascending or descending order controls. */
@Composable
fun TvGenreSortChips(
    selectedSort: TvGenreSort,
    descending: Boolean,
    onSortClick: (TvGenreSort) -> Unit,
    onDirectionClick: () -> Unit,
    firstFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .focusRestorer()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(TvGenreSort.entries, key = { it.name }) { sort ->
            AdaptiveChip(
                selected = sort == selectedSort,
                onClick = { onSortClick(sort) },
                label = { Text(stringResource(sort.labelResource)) },
                modifier = if (sort == TvGenreSort.entries.firstOrNull()) {
                    firstFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
                } else Modifier
            )
        }
        item(key = "sort-direction") {
            AdaptiveChip(
                selected = false,
                onClick = onDirectionClick,
                label = {
                    Text(
                        stringResource(
                            if (descending) R.string.tv_sort_descending else R.string.tv_sort_ascending
                        )
                    )
                }
            )
        }
    }
}

/** Shows the complete TMDB TV genre catalog ordered by local genre usage. */
@Composable
fun TvGenreChips(
    genres: List<TmdbTvGenre>,
    onGenreClick: (TmdbTvGenre) -> Unit,
    onAllGenresClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .focusRestorer()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all-tv-genres") {
            AdaptiveChip(
                selected = false,
                onClick = onAllGenresClick,
                label = { Text(stringResource(R.string.all_genres)) }
            )
        }
        items(genres, key = { it.id }) { genre ->
            AdaptiveChip(
                selected = false,
                onClick = { onGenreClick(genre) },
                label = { Text(genre.name) }
            )
        }
    }
}

/** Presents every TMDB TV genre in an opaque, themed modal sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvGenreBottomSheet(
    genres: List<TmdbTvGenre>,
    onGenreClick: (TmdbTvGenre) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
    val sortedGenres = remember(genres) {
        genres.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.browse_by_genre),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sortedGenres.forEach { genre ->
                    AdaptiveChip(
                        selected = false,
                        onClick = { onGenreClick(genre) },
                        label = { Text(genre.name) }
                    )
                }
            }
        }
    }
}

/** Displays a responsive, focus-restorable grid of TMDB TV posters. */
@Composable
fun TvSeriesGrid(
    series: List<TmdbTvSummary>,
    genres: List<TmdbTvGenre>,
    state: LazyGridState,
    isLoadingMore: Boolean,
    onSeriesClick: (TmdbTvSummary) -> Unit,
    onLoadMore: () -> Unit,
    initialFocusId: Int? = null,
    onFocusRestored: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(16.dp),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val genreNames = remember(genres) { genres.associate { it.id to it.name } }
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
        items(series, key = { it.id }) { item ->
            TvSeriesCard(
                series = item,
                genres = item.genreIds.mapNotNull(genreNames::get),
                onClick = { onSeriesClick(item) },
                shouldRequestFocus = item.id == initialFocusId,
                onFocusRestored = onFocusRestored
            )
        }
        if (isLoadingMore) {
            items(6, key = { "tv-placeholder-$it" }) {
                MovieItemPlaceholder(isTv = isTv)
            }
        }
        item(key = "tv-load-more") {
            Box(modifier = Modifier.size(48.dp)) {
                LaunchedEffect(series.size) { onLoadMore() }
            }
        }
    }

    LaunchedEffect(initialFocusId, series) {
        if (initialFocusId != null) {
            snapshotFlow { series }.first { items -> items.any { it.id == initialFocusId } }
            val index = series.indexOfFirst { it.id == initialFocusId }
            if (index >= 0) state.scrollToItem(index)
        }
    }
}

/** Displays one TMDB TV poster card with phone and native TV focus behavior. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSeriesCard(
    series: TmdbTvSummary,
    genres: List<String>,
    onClick: () -> Unit,
    shouldRequestFocus: Boolean = false,
    onFocusRestored: () -> Unit = {}
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val focusRequester = remember { FocusRequester() }
    val title = series.name.orEmpty()
    val genreText = genres.joinToString(", ")

    LaunchedEffect(shouldRequestFocus) {
        if (shouldRequestFocus) focusRequester.requestFocus()
    }

    val cardContent: @Composable () -> Unit = {
        Column {
            AsyncImage(
                model = series.fullPosterUrl,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isTv) 12.dp else 8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (genreText.isNotEmpty()) {
                    Text(
                        text = genreText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = series.firstAirYear,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = series.voteAverage?.let { "★ ${"%.1f".format(Locale.US, it)}" }.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    Box {
        if (isTv) {
            Surface(
                onClick = onClick,
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused && shouldRequestFocus) onFocusRestored() }
            ) {
                cardContent()
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
                cardContent()
            }
        }
    }
}
