package com.martinrevert.latorrentola.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.TMDB.TmdbTvGenre
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.TmdbTvFeed
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Loads TMDB TV feeds and orders TV genres using TV-only visit statistics.
 *
 * @property tmdbRepository TV catalog and genre usage access.
 * @property userLibraryRepository TV episode download history access.
 */
@HiltViewModel
class TvHomeViewModel @Inject constructor(
    private val tmdbRepository: TmdbRepository,
    private val userLibraryRepository: UserLibraryRepository
) : ViewModel() {
    /** Mutable backing state for the selected TV feed. */
    private val _uiState = MutableStateFlow<TvHomeUiState>(TvHomeUiState.Loading)
    /** Current TV feed state. */
    val uiState: StateFlow<TvHomeUiState> = _uiState.asStateFlow()

    /** IDs of TV series containing downloaded episodes. */
    val downloadedSeriesIds: StateFlow<Set<Int>> = userLibraryRepository.getDownloadedEpisodes()
        .map { episodes -> episodes.map { it.seriesId }.toSet() }
        .catch { emit(emptySet()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )

    /** Mutable backing state for the selected feed endpoint. */
    private val _selectedFeed = MutableStateFlow(TmdbTvFeed.ON_THE_AIR)
    /** Currently selected TV feed. */
    val selectedFeed: StateFlow<TmdbTvFeed> = _selectedFeed.asStateFlow()

    /** Mutable backing state for subsequent-page loading. */
    private val _isLoadingMore = MutableStateFlow(false)
    /** Whether a subsequent TV feed page is loading. */
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    /** Mutable backing state for explicit refresh progress. */
    private val _isRefreshing = MutableStateFlow(false)
    /** Whether an explicit TV feed refresh is in progress. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Mutable backing state for series focus restoration. */
    private val _lastClickedSeriesId = MutableStateFlow<Int?>(null)
    /** Series ID to refocus when returning from the TV detail screen. */
    val lastClickedSeriesId: StateFlow<Int?> = _lastClickedSeriesId.asStateFlow()

    /** Mutable backing cache of TMDB TV genre metadata. */
    private val _genreCatalog = MutableStateFlow<List<TmdbTvGenre>>(emptyList())
    /** Mutable backing error state for genre-catalog requests. */
    private val _genreError = MutableStateFlow<UiText?>(null)
    /** Error encountered while loading the TV genre catalog, when present. */
    val genreError: StateFlow<UiText?> = _genreError.asStateFlow()

    /** All TMDB TV genres, ordered by local usage and then by name. */
    val tvGenres: StateFlow<List<TmdbTvGenre>> = combine(
        _genreCatalog,
        tmdbRepository.observeTvGenreUsage()
    ) { genres, usage ->
        genres.sortedWith(
            compareByDescending<TmdbTvGenre> { usage[it.id] ?: 0 }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    /** Accumulated series backing the selected paginated feed. */
    private val allSeries = mutableListOf<TmdbTvSummary>()
    /** Page number to request next. */
    private var currentPage = 1
    /** Last available page reported by TMDB. */
    private var totalPages = 1
    /** Whether a TV page request is running. */
    private var isFetching = false
    /** Active page request, canceled when the selected feed changes. */
    private var feedJob: Job? = null
    /** Whether the TV catalogs have been requested during this ViewModel lifetime. */
    private var hasLoaded = false
    /** Invalidates responses from canceled feed and refresh requests. */
    private var requestGeneration = 0L

    /** Loads the TV feed and genre catalog when the user enters TV mode for the first time. */
    fun activate() {
        if (hasLoaded) return
        hasLoaded = true
        loadTvGenres()
        refresh()
    }

    /** Selects a TV feed and reloads it from the first page. */
    fun selectFeed(feed: TmdbTvFeed) {
        if (_selectedFeed.value == feed) return
        _selectedFeed.value = feed
        clearLastClickedSeriesId()
        refresh()
    }

    /** Reloads the selected TV feed from its first page. */
    fun refresh(showIndicator: Boolean = false) {
        feedJob?.cancel()
        requestGeneration++
        val generation = requestGeneration
        isFetching = true
        currentPage = 1
        totalPages = 1
        allSeries.clear()
        _uiState.value = TvHomeUiState.Loading
        feedJob = viewModelScope.launch {
            _isRefreshing.value = showIndicator
            try {
                loadNextPage(generation)
            } finally {
                if (generation == requestGeneration) {
                    _isRefreshing.value = false
                }
            }
        }
    }

    /** Loads another page when the currently selected TV feed has more results. */
    fun loadMore() {
        if (isFetching || currentPage > totalPages) return
        isFetching = true
        feedJob = viewModelScope.launch { loadNextPage(requestGeneration) }
    }

    /** Records a genre visit before opening its TV results. */
    fun recordGenreVisit(genreId: Int) {
        viewModelScope.launch {
            tmdbRepository.recordTvGenreVisit(genreId)
        }
    }

    /** Stores the series identifier used to restore poster focus. */
    fun setLastClickedSeriesId(id: Int?) {
        _lastClickedSeriesId.value = id
    }

    /** Clears the pending series focus-restoration identifier. */
    fun clearLastClickedSeriesId() {
        _lastClickedSeriesId.value = null
    }

    /** Fetches TMDB's TV genre catalog and records request failures for display. */
    private fun loadTvGenres() {
        viewModelScope.launch {
            try {
                _genreCatalog.value = tmdbRepository.getTvGenres()
                _genreError.value = null
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _genreError.value = UiText.DynamicString(e.localizedMessage ?: "Unable to load TV genres")
                }
            }
        }
    }

    /** Appends the next page for the currently selected TV feed. */
    private suspend fun loadNextPage(generation: Long) {
        if (generation != requestGeneration) return
        val requestedPage = currentPage
        if (requestedPage > 1) _isLoadingMore.value = true
        try {
            val page = tmdbRepository.getHomeTvFeed(_selectedFeed.value, requestedPage)
            if (generation != requestGeneration) return
            totalPages = page.totalPages.coerceAtLeast(page.page)
            allSeries.addAll(page.results.filterNot { candidate ->
                allSeries.any { it.id == candidate.id }
            })
            currentPage = page.page + 1
            if (allSeries.isEmpty()) {
                _uiState.value = TvHomeUiState.Error(UiText.StringResource(R.string.tv_no_results))
            } else {
                _uiState.value = TvHomeUiState.Success(allSeries.toList())
            }
        } catch (e: Exception) {
            if (generation == requestGeneration && e !is CancellationException && allSeries.isEmpty()) {
                _uiState.value = TvHomeUiState.Error(
                    UiText.DynamicString(e.localizedMessage ?: "Unable to load TV series")
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

/** Possible loading, success, and failure states of a TMDB TV feed. */
sealed interface TvHomeUiState {
    /** Feed results are loading. */
    data object Loading : TvHomeUiState

    /**
     * TV feed loaded.
     *
     * @property series TV series returned from the selected feed.
     */
    data class Success(val series: List<TmdbTvSummary>) : TvHomeUiState

    /**
     * TV feed loading failed or returned no results.
     *
     * @property message User-facing error description.
     */
    data class Error(val message: UiText) : TvHomeUiState
}
