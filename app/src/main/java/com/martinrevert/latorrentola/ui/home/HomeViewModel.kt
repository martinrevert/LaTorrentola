package com.martinrevert.latorrentola.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.date.DateLastVisit
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.network.YtsRepository
import com.martinrevert.latorrentola.network.AuthRepository
import com.martinrevert.latorrentola.utils.MovieFilter
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Loads and filters the home movie feed and exposes associated UI state.
 *
 * @property ytsRepository retrieves movie data and local statistics.
 * @property userLibraryRepository observes the user's download records.
 * @property preferenceManager supplies language and rating filters.
 * @property authRepository exposes account changes for remote-setting observation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val ytsRepository: YtsRepository,
    private val userLibraryRepository: UserLibraryRepository,
    private val preferenceManager: PreferenceManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    /** Mutable backing state for the home feed result. */
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    /** Current home feed state. */
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Mutable backing state for the most visited genres. */
    private val _topGenres = MutableStateFlow<List<String>>(emptyList())
    /** Most visited genres, or defaults before any visits are recorded. */
    val topGenres: StateFlow<List<String>> = _topGenres.asStateFlow()

    /** Mutable backing state for the previous session's visit timestamp. */
    private val _lastVisitDate = MutableStateFlow<Long?>(null)
    /** Previous session's visit time in milliseconds, when recorded. */
    val lastVisitDate: StateFlow<Long?> = _lastVisitDate.asStateFlow()

    // Expose refresh state for UI pull-to-refresh
    /** Mutable backing state for pull-to-refresh progress. */
    private val _isRefreshing = MutableStateFlow(false)
    /** Whether a user-visible refresh is in progress. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Mutable backing state for pagination progress. */
    private val _isLoadingMore = MutableStateFlow(false)
    /** Whether another page is currently loading. */
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    /** Mutable backing state for the selected torrent quality filter. */
    private val _selectedQuality = MutableStateFlow<String?>(null)
    /** Selected torrent quality, or `null` for all qualities. */
    val selectedQuality: StateFlow<String?> = _selectedQuality.asStateFlow()

    /** Mutable backing state for focus restoration after returning from details. */
    private val _lastClickedMovieId = MutableStateFlow<Int?>(null)
    /** Movie identifier to refocus when the home feed is restored. */
    val lastClickedMovieId: StateFlow<Int?> = _lastClickedMovieId.asStateFlow()

    /** IDs of movies recorded in the user's download library. */
    val downloadedMovieIds: StateFlow<Set<Int>> = userLibraryRepository.getDownloadedMovies()
        .map { it.map { download -> download.movieId }.toSet() }
        .catch { emit(emptySet()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    /** Quality labels offered in the home filter. */
    val qualityOptions = listOf("All", "2160p", "1080p.x265", "1080p", "720p", "3D")

    /** Number of favorite movies currently in the user's library. */
    val favoritesCount: StateFlow<Int> = ytsRepository.getFavoriteMovies()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    /** Genre labels available in the genre browser. */
    val allGenres = listOf(
        "Action", "Adventure", "Animation", "Biography", "Comedy", "Crime",
        "Documentary", "Drama", "Family", "Fantasy", "Film-Noir", "Game-Show",
        "History", "Horror", "Music", "Musical", "Mystery", "News", "Reality-TV",
        "Romance", "Sci-Fi", "Short", "Sport", "Talk-Show", "Thriller", "War", "Western"
    )

    /** Accumulated movie results backing the paginated feed. */
    private val allMovies = mutableListOf<Movie>()
    /** Page number to request next from the YTS API. */
    private var currentPage = 1
    /** Whether a page request is currently running. */
    private var isFetching = false
    /** Whether additional pages may contain results. */
    private var canLoadMore = true
    /** Current feed request, canceled when filters trigger a refresh. */
    private var movieFetchJob: kotlinx.coroutines.Job? = null

    /** Starts observing visit metadata, genres, and preference synchronization. */
    init {
        initVisitDate()
        observeTopGenres()
        observeMovieFilters()
        syncRemoteSettings()
    }

    /** Observes authenticated user's remote filters and applies changed values locally. */
    private fun syncRemoteSettings() {
        viewModelScope.launch {
            authRepository.authStateFlow
                .filterNotNull()
                .flatMapLatest { user ->
                    combine(
                        userLibraryRepository.observeRemoteFilteredLanguages(user.uid),
                        userLibraryRepository.observeRemoteMinimumRating(user.uid)
                    ) { languages, rating -> languages to rating }
                }
                .collect { (remoteLanguages, remoteRating) ->
                    if (remoteLanguages != null &&
                        remoteLanguages != preferenceManager.getFilteredLanguages()
                    ) {
                        preferenceManager.setFilteredLanguages(remoteLanguages)
                    }
                    if (remoteRating != preferenceManager.getMinimumRating()) {
                        preferenceManager.setMinimumRating(remoteRating)
                    }
                }
        }
    }

    /** Refreshes the feed whenever language or rating filters change. */
    private fun observeMovieFilters() {
        combine(
            preferenceManager.filteredLanguagesFlow,
            preferenceManager.minimumRatingFlow
        ) { _, _ -> Unit }
            .onEach { refresh(force = true) }
            .launchIn(viewModelScope)
    }

    /** Loads the previous visit timestamp and stores the current time for the next session. */
    private fun initVisitDate() {
        viewModelScope.launch {
            val visit = ytsRepository.getLastVisitDate()
            _lastVisitDate.value = visit?.date?.time
            
            // Immediately update to current time for NEXT session
            ytsRepository.setLastVisitDate(DateLastVisit(id = 1, date = Date()))
        }
    }

    /** Observes genre statistics and provides defaults when there is no history. */
    private fun observeTopGenres() {
        ytsRepository.getAllGenresWithCount()
            .onEach { stats ->
                val topList = stats.map { it.genre }
                
                // If no history, show some defaults so the row isn't completely empty
                if (topList.isEmpty()) {
                    _topGenres.value = listOf("Action", "Comedy", "Drama", "Horror", "Sci-Fi")
                } else {
                    _topGenres.value = topList
                }
            }
            .launchIn(viewModelScope)
    }

    /** Applies [quality] and reloads the home feed when the selection changes. */
    fun setQuality(quality: String?) {
        val q = if (quality == "All") null else quality
        if (_selectedQuality.value == q) return
        _selectedQuality.value = q
        clearLastClickedMovieId()
        refresh()
    }

    /** Loads the next page of filtered, deduplicated movies. */
    fun loadMovies() {
        if (isFetching || !canLoadMore) return
        isFetching = true
        if (currentPage > 1) {
            _isLoadingMore.value = true
        }
        
        movieFetchJob = viewModelScope.launch {
            try {
                if (currentPage == 1) _uiState.value = HomeUiState.Loading
                
                var foundNewMovies = false
                while (canLoadMore && !foundNewMovies) {
                    val result = ytsRepository.getMovies(
                        currentPage,
                        _selectedQuality.value,
                        preferenceManager.getMinimumRating()
                    )
                    val moviesFromApi = result.data?.movies
                    
                    // Filter movies based on user settings
                    val excludedLangs = preferenceManager.getFilteredLanguages()
                    val filteredMovies = MovieFilter.filterMovies(
                        moviesFromApi,
                        excludedLangs,
                        minimumRating = preferenceManager.getMinimumRating()
                    )
                        .distinctBy { it.id }
                    
                    if (filteredMovies.isNotEmpty()) {
                        // Add only new movies (by id) to avoid duplicates
                        val filteredNewMovies = filteredMovies.filter { newMovie ->
                            allMovies.none { it.id == newMovie.id }
                        }
                        
                        if (filteredNewMovies.isNotEmpty()) {
                            allMovies.addAll(filteredNewMovies)
                            _uiState.value = HomeUiState.Success(allMovies.toList())
                            foundNewMovies = true
                        }
                    }
                    
                    currentPage++
                }

                if (allMovies.isEmpty() && !canLoadMore) {
                    _uiState.value = HomeUiState.Error(UiText.StringResource(R.string.error_no_movies_filtered))
                }
            } catch (e: Exception) {
                if (e !is CancellationException && allMovies.isEmpty()) {
                    _uiState.value = HomeUiState.Error(UiText.DynamicString(e.localizedMessage ?: "Unknown error"))
                }
            } finally {
                isFetching = false
                _isLoadingMore.value = false
            }
        }
    }

    /**
     * Refresh the movie list: clear cached items and reload page 1.
     * @param force whether to override the current fetching status.
     * @param showIndicator whether to show the UI pull-to-refresh indicator.
     */
    fun refresh(force: Boolean = false, showIndicator: Boolean = false) {
        if (isFetching && !force) return
        
        movieFetchJob?.cancel()
        
        movieFetchJob = viewModelScope.launch {
            isFetching = true
            _isRefreshing.value = showIndicator
            try {
                // reset pagination and current list
                currentPage = 1
                canLoadMore = true
                allMovies.clear()
                _uiState.value = HomeUiState.Loading

                var foundNewMovies = false
                while (canLoadMore && !foundNewMovies) {
                    val result = ytsRepository.getMovies(
                        currentPage,
                        _selectedQuality.value,
                        preferenceManager.getMinimumRating()
                    )
                    val moviesFromApi = result.data?.movies

                    if (moviesFromApi.isNullOrEmpty()) {
                        canLoadMore = false
                        break
                    }

                    // Filter movies based on user settings
                    val excludedLangs = preferenceManager.getFilteredLanguages()
                    val filteredMovies = MovieFilter.filterMovies(
                        moviesFromApi,
                        excludedLangs,
                        minimumRating = preferenceManager.getMinimumRating()
                    )
                        .distinctBy { it.id }

                    if (filteredMovies.isNotEmpty()) {
                        // Add only new movies (by id) to avoid duplicates across pages
                        val filteredNewMovies = filteredMovies.filter { newMovie ->
                            allMovies.none { it.id == newMovie.id }
                        }

                        if (filteredNewMovies.isNotEmpty()) {
                            allMovies.addAll(filteredNewMovies)
                            _uiState.value = HomeUiState.Success(allMovies.toList())
                            foundNewMovies = true
                        }
                    }
                    currentPage++
                }

                if (allMovies.isEmpty() && !canLoadMore) {
                    _uiState.value = HomeUiState.Error(UiText.StringResource(R.string.error_no_movies_filtered))
                }
            } catch (e: Exception) {
                if (e !is CancellationException && allMovies.isEmpty()) {
                    _uiState.value = HomeUiState.Error(UiText.DynamicString(e.localizedMessage ?: "Unknown error"))
                }
            } finally {
                _isRefreshing.value = false
                isFetching = false
            }
        }
    }
    /** Stores the movie ID used to restore card focus. */
    fun setLastClickedMovieId(id: Int?) {
        _lastClickedMovieId.value = id
    }

    /** Clears the pending movie focus-restoration ID. */
    fun clearLastClickedMovieId() {
        _lastClickedMovieId.value = null
    }

    /** Adds [movie] to favorites or removes it if it is already saved. */
    fun toggleFavorite(movie: Movie) {
        viewModelScope.launch {
            if (ytsRepository.isFavorite(movie.id)) {
                ytsRepository.removeFavorite(movie)
            } else {
                ytsRepository.addFavorite(movie)
            }
            // Trigger UI update if needed, though Flow from Room would be better for this
        }
    }
}

/** Possible loading, success, and failure states of the home feed. */
sealed interface HomeUiState {
    /** Feed results are being loaded. */
    object Loading : HomeUiState
    /**
     * Feed loaded with movie results.
     *
     * @property movies Filtered movie entries.
     */
    data class Success(val movies: List<Movie>) : HomeUiState
    /**
     * Feed loading failed or no results satisfy the filters.
     *
     * @property message User-facing failure description.
     */
    data class Error(val message: UiText) : HomeUiState
}
