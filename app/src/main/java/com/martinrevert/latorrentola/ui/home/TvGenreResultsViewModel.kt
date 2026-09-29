package com.martinrevert.latorrentola.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.TMDB.TmdbTvGenre
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Supported TMDB discovery sort fields for television genre results.
 *
 * @property apiField TMDB's sort field without its direction suffix.
 * @property labelResource Localized label shown to the user.
 */
enum class TvGenreSort(val apiField: String, val labelResource: Int) {
    /** Sort by popularity score. */
    POPULARITY("popularity", R.string.tv_sort_popularity),
    /** Sort by average rating. */
    RATING("vote_average", R.string.tv_sort_rating),
    /** Sort by vote count. */
    VOTE_COUNT("vote_count", R.string.tv_sort_vote_count),
    /** Sort by first broadcast date. */
    FIRST_AIR_DATE("first_air_date", R.string.tv_sort_first_air_date),
    /** Sort alphabetically by localized series name. */
    NAME("name", R.string.tv_sort_name)
}

/**
 * Loads and sorts paginated TMDB TV genre results.
 *
 * @property tmdbRepository TV discovery and genre catalog access.
 */
@HiltViewModel
class TvGenreResultsViewModel @Inject constructor(
    private val tmdbRepository: TmdbRepository
) : ViewModel() {
    /** Mutable backing state for genre-results loading and content. */
    private val _uiState = MutableStateFlow<TvGenreResultsUiState>(TvGenreResultsUiState.Loading)
    /** Current TV genre results state. */
    val uiState: StateFlow<TvGenreResultsUiState> = _uiState.asStateFlow()

    /** Mutable backing state for TMDB result sort field. */
    private val _sort = MutableStateFlow(TvGenreSort.POPULARITY)
    /** Active TMDB sort field. */
    val sort: StateFlow<TvGenreSort> = _sort.asStateFlow()

    /** Mutable backing state for sort direction. */
    private val _descending = MutableStateFlow(true)
    /** Whether the active TMDB sort order is descending. */
    val descending: StateFlow<Boolean> = _descending.asStateFlow()

    /** Mutable backing state for next-page loading. */
    private val _isLoadingMore = MutableStateFlow(false)
    /** Whether an additional genre-results page is loading. */
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    /** Mutable backing state for genre metadata used to label result posters. */
    private val _genres = MutableStateFlow<List<TmdbTvGenre>>(emptyList())
    /** Complete TV genre catalog for poster labels. */
    val genres: StateFlow<List<TmdbTvGenre>> = _genres.asStateFlow()

    /** Error loading TV genre metadata used to label results, when present. */
    private val _genreCatalogError = MutableStateFlow<String?>(null)
    /** User-readable genre metadata error, when present. */
    val genreCatalogError: StateFlow<String?> = _genreCatalogError.asStateFlow()

    /** TMDB genre ID associated with this destination. */
    private var genreId: Int? = null
    /** Page number to request next. */
    private var currentPage = 1
    /** Last available page reported by TMDB. */
    private var totalPages = 1
    /** Whether a genre-results page request is running. */
    private var isFetching = false
    /** Accumulated deduplicated TV results. */
    private val allSeries = mutableListOf<TmdbTvSummary>()
    /** Current request, canceled when genre or sort changes. */
    private var requestJob: Job? = null
    /** Invalidates results from canceled requests. */
    private var requestGeneration = 0L

    init {
        viewModelScope.launch {
            try {
                _genres.value = tmdbRepository.getTvGenres()
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _genreCatalogError.value = e.localizedMessage ?: "Unable to load TV genres"
                }
            }
        }
    }

    /** Loads results for [id] when the destination genre changes. */
    fun setGenre(id: Int) {
        if (genreId == id) return
        genreId = id
        refresh()
    }

    /** Selects a TMDB sort field and reloads results. */
    fun setSort(value: TvGenreSort) {
        if (_sort.value == value) return
        _sort.value = value
        refresh()
    }

    /** Toggles ascending or descending order and reloads results. */
    fun toggleDirection() {
        _descending.value = !_descending.value
        refresh()
    }

    /** Requests another page of the selected genre and sort. */
    fun loadMore() {
        if (isFetching || currentPage > totalPages || genreId == null) return
        isFetching = true
        requestJob = viewModelScope.launch {
            loadNextPage(genreId, requestGeneration)
        }
    }

    /** Cancels the previous query and reloads page one with the current filters. */
    private fun refresh() {
        val selectedGenre = genreId ?: return
        requestJob?.cancel()
        requestGeneration++
        val generation = requestGeneration
        isFetching = true
        currentPage = 1
        totalPages = 1
        allSeries.clear()
        _uiState.value = TvGenreResultsUiState.Loading
        requestJob = viewModelScope.launch { loadNextPage(selectedGenre, generation) }
    }

    /**
     * Appends the next discovery page for [selectedGenre] if [generation] is still current.
     *
     * @param selectedGenre TMDB genre ID whose results are being loaded.
     * @param generation Request generation used to reject stale responses.
     */
    private suspend fun loadNextPage(selectedGenre: Int?, generation: Long) {
        val requestedGenreId = selectedGenre ?: return
        if (generation != requestGeneration) return
        val requestedPage = currentPage
        if (requestedPage > 1) _isLoadingMore.value = true
        try {
            val sortBy = "${_sort.value.apiField}.${if (_descending.value) "desc" else "asc"}"
            val page = tmdbRepository.discoverTvByGenre(requestedGenreId, sortBy, requestedPage)
            if (generation != requestGeneration) return
            totalPages = page.totalPages.coerceAtLeast(page.page)
            allSeries.addAll(page.results.filterNot { candidate ->
                allSeries.any { it.id == candidate.id }
            })
            currentPage = page.page + 1
            _uiState.value = if (allSeries.isEmpty()) {
                TvGenreResultsUiState.Error(UiText.StringResource(R.string.tv_no_results))
            } else {
                TvGenreResultsUiState.Success(allSeries.toList())
            }
        } catch (e: Exception) {
            if (generation == requestGeneration && e !is CancellationException && allSeries.isEmpty()) {
                _uiState.value = TvGenreResultsUiState.Error(
                    UiText.DynamicString(e.localizedMessage ?: "Unable to load TV genre results")
                )
            }
        } finally {
            if (generation == requestGeneration) {
                isFetching = false
                _isLoadingMore.value = false
            }
        }
    }
}

/** Possible loading, success, and failure states of TV genre discovery. */
sealed interface TvGenreResultsUiState {
    /** Results are loading. */
    data object Loading : TvGenreResultsUiState

    /**
     * Genre results loaded.
     *
     * @property series Matching TV series.
     */
    data class Success(val series: List<TmdbTvSummary>) : TvGenreResultsUiState

    /**
     * Loading failed or no matching TV series were found.
     *
     * @property message User-facing error description.
     */
    data class Error(val message: UiText) : TvGenreResultsUiState
}
