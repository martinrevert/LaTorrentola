package com.martinrevert.latorrentola.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.date.DateLastVisit
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieResult
import com.martinrevert.latorrentola.network.AuthRepository
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.network.YtsRepository
import com.martinrevert.latorrentola.utils.MovieFilter
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Loads and filters the home movie feed and exposes associated UI state.
 *
 * @property ytsRepository retrieves movie data and local statistics.
 * @property userLibraryRepository observes the user's download records.
 * @property preferenceManager supplies language and rating filters.
 * @property authRepository exposes account changes for remote-setting observation.
 * @property tmdbRepository resolves high-resolution backdrops and movie metadata from TMDB.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val ytsRepository: YtsRepository,
    private val userLibraryRepository: UserLibraryRepository,
    private val preferenceManager: PreferenceManager,
    private val authRepository: AuthRepository,
    private val tmdbRepository: TmdbRepository
) : ViewModel() {

    companion object {
        private const val TARGET_INITIAL_BATCH = 18
        private const val TARGET_LOAD_MORE_BATCH = 12
        private const val MAX_PAGES_PER_BATCH = 5
        private const val YTS_PAGE_SIZE = 20
    }

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

    /** User's in-progress watch history items mapped by media identifier or title. */
    val watchHistoryMap: StateFlow<Map<String, PlaybackProgress>> = userLibraryRepository.getWatchHistory()
        .map { list -> list.associateBy { it.mediaId } }
        .catch { emit(emptyMap()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    /** Quality labels offered in the home filter. */
    val qualityOptions = listOf("All", "2160p", "1080p.x265", "1080p", "720p", "3D")

    /** Number of favorite movies and TV series currently in the user's library. */
    val favoritesCount: StateFlow<Int> = combine(
        ytsRepository.getFavoriteMovies(),
        userLibraryRepository.getFavoriteTvSeries()
    ) { movies, series ->
        movies.size + series.size
    }
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

    private var movieFocusJob: Job? = null

    /** Currently focused movie in TV immersive browsing mode. */
    private val _focusedMovie = MutableStateFlow<Movie?>(null)
    val focusedMovie: StateFlow<Movie?> = _focusedMovie.asStateFlow()

    /** High-resolution TMDB or fallback backdrop URL for the currently focused movie. */
    private val _focusedMovieBackdrop = MutableStateFlow<String?>(null)
    val focusedMovieBackdrop: StateFlow<String?> = _focusedMovieBackdrop.asStateFlow()

    /** Enriched TMDB metadata for the currently focused movie. */
    private val _focusedMovieTmdb = MutableStateFlow<TmdbMovieResult?>(null)
    val focusedMovieTmdb: StateFlow<TmdbMovieResult?> = _focusedMovieTmdb.asStateFlow()

    /** TMDB transparent logo URL for the currently focused movie. */
    private val _focusedMovieLogo = MutableStateFlow<String?>(null)
    val focusedMovieLogo: StateFlow<String?> = _focusedMovieLogo.asStateFlow()

    /** Whether logo resolution for the focused movie is actively in flight. */
    private val _isFocusedMovieLogoLoading = MutableStateFlow(false)
    val isFocusedMovieLogoLoading: StateFlow<Boolean> = _isFocusedMovieLogoLoading.asStateFlow()

    /**
     * Updates the focused movie for the TV immersive layout and fetches its TMDB backdrop and logo.
     */
    fun onMovieFocused(movie: Movie) {
        if (_focusedMovie.value?.id == movie.id) return
        _focusedMovie.value = movie
        _focusedMovieBackdrop.value = movie.backgroundImageOriginal ?: movie.backgroundImage ?: movie.largeCoverImage
        _focusedMovieTmdb.value = null
        _focusedMovieLogo.value = null
        _isFocusedMovieLogoLoading.value = !movie.imdbCode.isNullOrBlank()

        movieFocusJob?.cancel()
        movieFocusJob = viewModelScope.launch {
            try {
                delay(120)
                val imdb = movie.imdbCode
                if (!imdb.isNullOrBlank()) {
                    val tmdb = tmdbRepository.findMovieByImdbId(imdb)
                    if (tmdb != null && _focusedMovie.value?.id == movie.id) {
                        _focusedMovieTmdb.value = tmdb
                        tmdb.fullBackdropUrl?.let { backdrop ->
                            _focusedMovieBackdrop.value = backdrop
                        }
                        val logo = tmdbRepository.getMovieLogo(tmdb.id)
                        if (_focusedMovie.value?.id == movie.id) {
                            _focusedMovieLogo.value = logo
                        }
                    } else if (_focusedMovie.value?.id == movie.id) {
                        val logo = tmdbRepository.getMovieLogoByImdbId(imdb)
                        if (_focusedMovie.value?.id == movie.id) {
                            _focusedMovieLogo.value = logo
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                if (_focusedMovie.value?.id == movie.id) {
                    _isFocusedMovieLogoLoading.value = false
                }
            }
        }
    }

    /** Accumulated movie results backing the paginated feed. */
    private val allMovies = mutableListOf<Movie>()
    /** O(1) lookup set of movie IDs currently in [allMovies]. */
    private val seenMovieIds = HashSet<Int>()
    /** Page number to request next from the YTS API. */
    private var currentPage = 1
    /** Whether a page request is currently running. */
    private var isFetching = false
    /** Whether additional pages may contain results. */
    private var canLoadMore = true
    /** Current feed request, canceled when filters trigger a refresh. */
    private var movieFetchJob: kotlinx.coroutines.Job? = null
    /** Monotonic generation counter to ignore stale/cancelled coroutine completions. */
    private var requestGeneration = 0L

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
        ) { langs, rating -> langs to rating }
            .distinctUntilChanged()
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
        refresh(force = true)
    }

    /** Loads the next batch of filtered, deduplicated movies. */
    fun loadMovies() {
        if (isFetching || !canLoadMore) return
        isFetching = true
        val generation = ++requestGeneration
        if (currentPage > 1) {
            _isLoadingMore.value = true
        }

        movieFetchJob = viewModelScope.launch {
            try {
                if (currentPage == 1) _uiState.value = HomeUiState.Loading

                val targetBatchSize = if (currentPage == 1) TARGET_INITIAL_BATCH else TARGET_LOAD_MORE_BATCH
                var addedInBatch = 0
                var pagesFetchedInBatch = 0
                var lastPageWasFull = true

                while (
                    canLoadMore &&
                    (addedInBatch == 0 || (lastPageWasFull && addedInBatch < targetBatchSize && pagesFetchedInBatch < MAX_PAGES_PER_BATCH))
                ) {
                    val result = ytsRepository.getMovies(
                        currentPage,
                        _selectedQuality.value,
                        preferenceManager.getMinimumRating()
                    )
                    if (generation != requestGeneration) return@launch
                    val moviesFromApi = result.data?.movies

                    if (moviesFromApi.isNullOrEmpty()) {
                        canLoadMore = false
                        break
                    }

                    lastPageWasFull = moviesFromApi.size >= YTS_PAGE_SIZE
                    pagesFetchedInBatch++
                    currentPage++

                    // Filter movies based on user settings
                    val excludedLangs = preferenceManager.getFilteredLanguages()
                    val filteredMovies = MovieFilter.filterMovies(
                        moviesFromApi,
                        excludedLangs,
                        minimumRating = preferenceManager.getMinimumRating()
                    )

                    for (movie in filteredMovies) {
                        if (seenMovieIds.add(movie.id)) {
                            allMovies.add(movie)
                            addedInBatch++
                        }
                    }
                }

                if (addedInBatch > 0) {
                    _uiState.value = HomeUiState.Success(allMovies.toList())
                    if (_focusedMovie.value == null && allMovies.isNotEmpty()) {
                        onMovieFocused(allMovies.first())
                    }
                } else if (allMovies.isEmpty() && !canLoadMore) {
                    _uiState.value = HomeUiState.Error(UiText.StringResource(R.string.error_no_movies_filtered))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (generation == requestGeneration && allMovies.isEmpty()) {
                    _uiState.value = HomeUiState.Error(UiText.DynamicString(e.localizedMessage ?: "Unknown error"))
                }
            } finally {
                if (generation == requestGeneration) {
                    isFetching = false
                    _isLoadingMore.value = false
                }
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
        val generation = ++requestGeneration
        isFetching = true
        _isLoadingMore.value = false
        _isRefreshing.value = showIndicator

        movieFetchJob = viewModelScope.launch {
            try {
                // reset pagination and current list
                currentPage = 1
                canLoadMore = true
                allMovies.clear()
                seenMovieIds.clear()
                _focusedMovie.value = null
                _focusedMovieBackdrop.value = null
                _focusedMovieTmdb.value = null
                _focusedMovieLogo.value = null
                _isFocusedMovieLogoLoading.value = false
                _uiState.value = HomeUiState.Loading

                var addedInBatch = 0
                var pagesFetchedInBatch = 0
                var lastPageWasFull = true

                while (
                    canLoadMore &&
                    (addedInBatch == 0 || (lastPageWasFull && addedInBatch < TARGET_INITIAL_BATCH && pagesFetchedInBatch < MAX_PAGES_PER_BATCH))
                ) {
                    val result = ytsRepository.getMovies(
                        currentPage,
                        _selectedQuality.value,
                        preferenceManager.getMinimumRating()
                    )
                    if (generation != requestGeneration) return@launch
                    val moviesFromApi = result.data?.movies

                    if (moviesFromApi.isNullOrEmpty()) {
                        canLoadMore = false
                        break
                    }

                    lastPageWasFull = moviesFromApi.size >= YTS_PAGE_SIZE
                    pagesFetchedInBatch++
                    currentPage++

                    // Filter movies based on user settings
                    val excludedLangs = preferenceManager.getFilteredLanguages()
                    val filteredMovies = MovieFilter.filterMovies(
                        moviesFromApi,
                        excludedLangs,
                        minimumRating = preferenceManager.getMinimumRating()
                    )

                    for (movie in filteredMovies) {
                        if (seenMovieIds.add(movie.id)) {
                            allMovies.add(movie)
                            addedInBatch++
                        }
                    }
                }

                if (addedInBatch > 0) {
                    _uiState.value = HomeUiState.Success(allMovies.toList())
                    if (_focusedMovie.value == null && allMovies.isNotEmpty()) {
                        onMovieFocused(allMovies.first())
                    }
                } else if (allMovies.isEmpty() && !canLoadMore) {
                    _uiState.value = HomeUiState.Error(UiText.StringResource(R.string.error_no_movies_filtered))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (generation == requestGeneration && allMovies.isEmpty()) {
                    _uiState.value = HomeUiState.Error(UiText.DynamicString(e.localizedMessage ?: "Unknown error"))
                }
            } finally {
                if (generation == requestGeneration) {
                    _isRefreshing.value = false
                    _isLoadingMore.value = false
                    isFetching = false
                }
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
        }
    }

    /** Marks [movie] as 100% completed in Room and Firestore. */
    fun markMovieAsCompleted(movie: Movie) {
        viewModelScope.launch {
            try {
                userLibraryRepository.markMovieAsCompleted(movie)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    /** Clears watch progress and downloads for [movie]. */
    fun markMovieAsUncompleted(movie: Movie) {
        viewModelScope.launch {
            try {
                userLibraryRepository.markMovieAsUncompleted(movie)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
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
