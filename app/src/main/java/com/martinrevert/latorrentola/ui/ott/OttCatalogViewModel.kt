package com.martinrevert.latorrentola.ui.ott

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieSummary
import com.martinrevert.latorrentola.model.TMDB.TmdbTvGenre
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.ui.home.TvGenreSort
import com.martinrevert.latorrentola.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state for the OTT provider catalog screen. */
sealed interface OttCatalogUiState {
    data object Loading : OttCatalogUiState
    data class SuccessMovies(val movies: List<TmdbMovieSummary>) : OttCatalogUiState
    data class SuccessTv(val series: List<TmdbTvSummary>) : OttCatalogUiState
    data class Error(val message: UiText) : OttCatalogUiState
}

/**
 * Loads, sorts, and paginates movies or TV series available on an OTT streaming provider.
 *
 * @property tmdbRepository TMDB discover API coordinator.
 */
@HiltViewModel
class OttCatalogViewModel @Inject constructor(
    private val tmdbRepository: TmdbRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OttCatalogUiState>(OttCatalogUiState.Loading)
    val uiState: StateFlow<OttCatalogUiState> = _uiState.asStateFlow()

    private val _sort = MutableStateFlow(TvGenreSort.POPULARITY)
    val sort: StateFlow<TvGenreSort> = _sort.asStateFlow()

    private val _descending = MutableStateFlow(true)
    val descending: StateFlow<Boolean> = _descending.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _lastClickedId = MutableStateFlow<Int?>(null)
    val lastClickedId: StateFlow<Int?> = _lastClickedId.asStateFlow()

    private val _tvGenres = MutableStateFlow<List<TmdbTvGenre>>(emptyList())
    val tvGenres: StateFlow<List<TmdbTvGenre>> = _tvGenres.asStateFlow()

    private var currentProviderId: Int? = null
    private var isTvMode: Boolean = false
    private var currentPage = 1
    private var totalPages = 1
    private var isFetching = false
    private var requestJob: Job? = null
    private var requestGeneration = 0L

    private val accumulatedMovies = mutableListOf<TmdbMovieSummary>()
    private val seenMovieIds = HashSet<Int>()

    private val accumulatedSeries = mutableListOf<TmdbTvSummary>()
    private val seenSeriesIds = HashSet<Int>()

    init {
        viewModelScope.launch {
            try {
                _tvGenres.value = tmdbRepository.getTvGenres()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun setProvider(providerId: Int, isTv: Boolean) {
        if (currentProviderId == providerId && isTvMode == isTv) return
        currentProviderId = providerId
        isTvMode = isTv
        clearLastClickedId()
        refresh()
    }

    fun setSort(value: TvGenreSort) {
        if (_sort.value == value) return
        _sort.value = value
        clearLastClickedId()
        refresh()
    }

    fun toggleDirection() {
        _descending.value = !_descending.value
        clearLastClickedId()
        refresh()
    }

    fun setLastClickedId(id: Int?) {
        _lastClickedId.value = id
    }

    fun clearLastClickedId() {
        _lastClickedId.value = null
    }

    fun loadMore() {
        if (isFetching || currentPage > totalPages || currentProviderId == null) return
        isFetching = true
        requestJob = viewModelScope.launch {
            loadNextPage(currentProviderId!!, isTvMode, requestGeneration)
        }
    }

    fun refresh() {
        val providerId = currentProviderId ?: return
        requestJob?.cancel()
        requestGeneration++
        val generation = requestGeneration
        isFetching = true
        _isLoadingMore.value = false
        currentPage = 1
        totalPages = 1
        accumulatedMovies.clear()
        seenMovieIds.clear()
        accumulatedSeries.clear()
        seenSeriesIds.clear()
        _uiState.value = OttCatalogUiState.Loading

        requestJob = viewModelScope.launch {
            loadNextPage(providerId, isTvMode, generation)
        }
    }

    private suspend fun loadNextPage(providerId: Int, isTv: Boolean, generation: Long) {
        if (currentPage > 1) {
            _isLoadingMore.value = true
        }

        val direction = if (_descending.value) "desc" else "asc"
        val sortKey = resolveSortKey(_sort.value, isTv, direction)
        val region = Locale.getDefault().country.takeIf(String::isNotBlank) ?: "US"

        try {
            if (isTv) {
                val page = tmdbRepository.discoverTvByProvider(
                    providerId = providerId,
                    region = region,
                    sortBy = sortKey,
                    page = currentPage
                )
                if (generation != requestGeneration) return
                totalPages = page.totalPages

                val newSeries = page.results.orEmpty().filter { series ->
                    seenSeriesIds.add(series.id)
                }
                accumulatedSeries.addAll(newSeries)
                currentPage++
                _uiState.value = OttCatalogUiState.SuccessTv(accumulatedSeries.toList())
            } else {
                val page = tmdbRepository.discoverMoviesByProvider(
                    providerId = providerId,
                    region = region,
                    sortBy = sortKey,
                    page = currentPage
                )
                if (generation != requestGeneration) return
                totalPages = page.totalPages

                val newMovies = page.results.orEmpty().filter { movie ->
                    seenMovieIds.add(movie.id)
                }
                accumulatedMovies.addAll(newMovies)
                currentPage++
                _uiState.value = OttCatalogUiState.SuccessMovies(accumulatedMovies.toList())
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (generation != requestGeneration) return
            val hasContent = if (isTv) accumulatedSeries.isNotEmpty() else accumulatedMovies.isNotEmpty()
            if (!hasContent) {
                _uiState.value = OttCatalogUiState.Error(
                    e.localizedMessage?.takeIf(String::isNotBlank)?.let { UiText.DynamicString(it) }
                        ?: UiText.StringResource(R.string.network_error)
                )
            }
        } finally {
            if (generation == requestGeneration) {
                isFetching = false
                _isLoadingMore.value = false
            }
        }
    }

    private fun resolveSortKey(sort: TvGenreSort, isTv: Boolean, direction: String): String {
        return when (sort) {
            TvGenreSort.FIRST_AIR_DATE -> if (isTv) "first_air_date.$direction" else "primary_release_date.$direction"
            TvGenreSort.POPULARITY -> "popularity.$direction"
            TvGenreSort.RATING -> "vote_average.$direction"
            TvGenreSort.VOTE_COUNT -> "vote_count.$direction"
            TvGenreSort.NAME -> if (isTv) "name.$direction" else "original_title.$direction"
        }
    }
}
