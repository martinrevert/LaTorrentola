package com.martinrevert.latorrentola.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.model.TMDB.TmdbActorDetail
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeason
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeasonDetails
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.user.DownloadedEpisode
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Loads TMDB series details, selected season's episodes, and tracks downloaded episodes.
 *
 * @property tmdbRepository TMDB series and season endpoint access.
 * @property userLibraryRepository Access to the user's episode downloads and TV favorites in Firestore.
 */
@HiltViewModel
class TvDetailViewModel @Inject constructor(
    private val tmdbRepository: TmdbRepository,
    private val userLibraryRepository: UserLibraryRepository
) : ViewModel() {
    /** Mutable backing state for series details. */
    private val _uiState = MutableStateFlow<TvDetailUiState>(TvDetailUiState.Loading)
    /** Current series detail state. */
    val uiState: StateFlow<TvDetailUiState> = _uiState.asStateFlow()

    /** Mutable backing state for season selection. */
    private val _selectedSeason = MutableStateFlow<TmdbTvSeason?>(null)
    /** Season currently selected for episode display. */
    val selectedSeason: StateFlow<TmdbTvSeason?> = _selectedSeason.asStateFlow()

    /** Mutable backing state for season episode loading. */
    private val _seasonState = MutableStateFlow<TvSeasonUiState>(TvSeasonUiState.Loading)
    /** Current episode listing state. */
    val seasonState: StateFlow<TvSeasonUiState> = _seasonState.asStateFlow()

    /** Flow of all downloaded episodes for the authenticated user. */
    val downloadedEpisodes: StateFlow<List<DownloadedEpisode>> = userLibraryRepository
        .getDownloadedEpisodes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** IDs of TV series currently saved in the user's favorites. */
    val favoriteTvSeriesIds: StateFlow<Set<Int>> = userLibraryRepository
        .getFavoriteTvSeries()
        .map { series -> series.map { it.id }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    /** Mutable backing state for favorite write errors. */
    private val _favoriteActionError = MutableStateFlow<String?>(null)
    /** User-facing error from the latest favorite update. */
    val favoriteActionError: StateFlow<String?> = _favoriteActionError.asStateFlow()

    /** Mutable backing state for the selected cast member's TMDB detail result. */
    private val _selectedActorDetail = MutableStateFlow<Result<TmdbActorDetail>?>(null)
    /** Selected cast member details displayed in the shared actor sheet. */
    val selectedActorDetail: StateFlow<Result<TmdbActorDetail>?> = _selectedActorDetail.asStateFlow()

    /** Mutable backing state for cast member detail loading. */
    private val _isActorLoading = MutableStateFlow(false)
    /** Whether TMDB actor details are currently loading. */
    val isActorLoading: StateFlow<Boolean> = _isActorLoading.asStateFlow()

    /** Series ID whose details have been requested. */
    private var requestedSeriesId: Int? = null
    /** Active season request, canceled when a different season is selected. */
    private var seasonJob: Job? = null

    /** Loads detail for [seriesId] unless the same series is already displayed. */
    fun load(seriesId: Int) {
        if (requestedSeriesId == seriesId) return
        requestedSeriesId = seriesId
        viewModelScope.launch {
            _uiState.value = TvDetailUiState.Loading
            try {
                val series = tmdbRepository.getTvDetails(seriesId)
                _uiState.value = TvDetailUiState.Success(series)
                val initialSeason = series.seasons
                    .filter { it.seasonNumber > 0 }
                    .maxByOrNull { it.seasonNumber }
                    ?: series.seasons.firstOrNull()
                if (initialSeason != null) selectSeason(initialSeason)
                else _seasonState.value = TvSeasonUiState.Error("No seasons available")
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _uiState.value = TvDetailUiState.Error(e.localizedMessage ?: "Unable to load series")
                }
            }
        }
    }

    /** Loads episode details for [season]. */
    fun selectSeason(season: TmdbTvSeason) {
        val seriesId = requestedSeriesId ?: return
        _selectedSeason.value = season
        seasonJob?.cancel()
        seasonJob = viewModelScope.launch {
            _seasonState.value = TvSeasonUiState.Loading
            try {
                _seasonState.value = TvSeasonUiState.Success(
                    tmdbRepository.getTvSeasonDetails(seriesId, season.seasonNumber)
                )
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _seasonState.value = TvSeasonUiState.Error(
                        e.localizedMessage ?: "Unable to load episodes"
                    )
                }
            }
        }
    }

    /** Adds [series] to favorites, or removes it when it is already saved. */
    fun toggleFavorite(series: TmdbTvSummary) {
        viewModelScope.launch {
            _favoriteActionError.value = null
            try {
                if (series.id in favoriteTvSeriesIds.value) {
                    userLibraryRepository.removeFavoriteTvSeries(series)
                } else {
                    userLibraryRepository.addFavoriteTvSeries(series)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _favoriteActionError.value = e.localizedMessage
                        ?: "Unable to update TV favorites"
                }
            }
        }
    }

    /** Clears the current favorite write error. */
    fun clearFavoriteActionError() {
        _favoriteActionError.value = null
    }

    /** Loads TMDB actor details for the selected cast member. */
    fun fetchActorDetails(personId: Int) {
        viewModelScope.launch {
            _isActorLoading.value = true
            _selectedActorDetail.value = null
            _selectedActorDetail.value = tmdbRepository.getActorDetails(personId)
            _isActorLoading.value = false
        }
    }

    /** Clears the selected cast member and any completed loading state. */
    fun clearSelectedActor() {
        _selectedActorDetail.value = null
        _isActorLoading.value = false
    }
}

/** Possible states of TV series details. */
sealed interface TvDetailUiState {
    /** Series details are loading. */
    data object Loading : TvDetailUiState

    /**
     * Series details loaded.
     *
     * @property series TMDB series metadata.
     */
    data class Success(val series: TmdbTvSummary) : TvDetailUiState

    /**
     * Series detail request failed.
     *
     * @property message User-facing error description.
     */
    data class Error(val message: String) : TvDetailUiState
}

/** Possible states of the selected TV season. */
sealed interface TvSeasonUiState {
    /** Season episodes are loading. */
    data object Loading : TvSeasonUiState

    /**
     * Season episode listing loaded.
     *
     * @property season Season metadata and episodes.
     */
    data class Success(val season: TmdbTvSeasonDetails) : TvSeasonUiState

    /**
     * Season request failed.
     *
     * @property message User-facing error description.
     */
    data class Error(val message: String) : TvSeasonUiState
}
