package com.martinrevert.latorrentola.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.model.TMDB.TmdbTvGenre
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.TmdbTvFeed
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.network.YtsRepository
import com.martinrevert.latorrentola.utils.MovieFilter
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Performs movie and TV series searches and exposes favorites, downloads, and new-release collections.
 *
 * @property ytsRepository searches movies and manages favorites.
 * @property tmdbRepository searches TV series and fetches metadata.
 * @property userLibraryRepository observes downloads and favorites.
 * @property preferenceManager supplies filtering preferences.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val ytsRepository: YtsRepository,
    private val tmdbRepository: TmdbRepository,
    private val userLibraryRepository: UserLibraryRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    /** Mutable backing state for current search results. */
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    /** Current search or collection state. */
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Mutable backing state for TV mode toggle. */
    private val _isTvMode = MutableStateFlow(false)
    /** Whether TV mode is currently selected. */
    val isTvMode: StateFlow<Boolean> = _isTvMode.asStateFlow()

    /** Mutable backing state for current TV search results. */
    private val _tvUiState = MutableStateFlow<SearchTvUiState>(SearchTvUiState.Idle)
    /** Current TV search or collection state. */
    val tvUiState: StateFlow<SearchTvUiState> = _tvUiState.asStateFlow()

    /** Mutable backing state for TV-card focus restoration. */
    private val _lastClickedSeriesId = MutableStateFlow<Int?>(null)
    /** TV series identifier to refocus when returning to results. */
    val lastClickedSeriesId: StateFlow<Int?> = _lastClickedSeriesId.asStateFlow()

    /** Mutable backing state for the selected torrent quality. */
    private val _selectedQuality = MutableStateFlow<String?>(null)
    /** Selected torrent quality, or `null` for all qualities. */
    val selectedQuality: StateFlow<String?> = _selectedQuality.asStateFlow()

    /** Mutable backing state for pagination progress. */
    private val _isLoadingMore = MutableStateFlow(false)
    /** Whether another page is loading. */
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    /** Mutable backing state for movie-card focus restoration. */
    private val _lastClickedMovieId = MutableStateFlow<Int?>(null)
    /** Movie identifier to refocus when returning to results. */
    val lastClickedMovieId: StateFlow<Int?> = _lastClickedMovieId.asStateFlow()

    /** Mutable backing state for multi-selected favorite movie IDs. */
    private val _selectedFavoriteIds = MutableStateFlow<Set<Int>>(emptySet())
    /** IDs selected for bulk favorite removal. */
    val selectedFavoriteIds: StateFlow<Set<Int>> = _selectedFavoriteIds.asStateFlow()

    /** Mutable backing state for favorite TV series displayed alongside movies. */
    private val _favoriteTvSeries = MutableStateFlow<List<TmdbTvSummary>>(emptyList())
    /** Favorite TV series displayed in the mixed favorites collection. */
    val favoriteTvSeries: StateFlow<List<TmdbTvSummary>> = _favoriteTvSeries.asStateFlow()

    /** IDs of movies recorded in the user's download library. */
    val downloadedMovieIds: StateFlow<Set<Int>> = userLibraryRepository.getDownloadedMovies()
        .map { it.map { download -> download.movieId }.toSet() }
        .catch { emit(emptySet()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    /** IDs of TV series containing downloaded episodes. */
    val downloadedSeriesIds: StateFlow<Set<Int>> = userLibraryRepository.getDownloadedEpisodes()
        .map { episodes -> episodes.map { it.seriesId }.toSet() }
        .catch { emit(emptySet()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    /** All TMDB TV genres, ordered by local usage. */
    val tvGenres: StateFlow<List<TmdbTvGenre>> = tmdbRepository.observeTvGenreUsage()
        .map { usage ->
            val catalog = try { tmdbRepository.getTvGenres() } catch (_: Exception) { emptyList() }
            catalog.sortedByDescending { usage[it.id] ?: 0 }
        }
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Quality labels available to search results. */
    val qualityOptions = listOf("All", "2160p", "1080p.x265", "1080p", "720p", "3D")

    /** Accumulated results backing pagination and bulk selection. */
    private val allResults = mutableListOf<Movie>()
    /** Next page to request for a catalog search. */
    private var currentPage = 1
    /** Current text search term, if any. */
    private var lastQuery: String? = null
    /** Current genre search term, if any. */
    private var lastGenre: String? = null
    /** Whether the favorites collection is currently displayed. */
    private var isShowingFavorites = false
    /** Whether the downloads collection is currently displayed. */
    private var isShowingDownloads = false
    /** Whether the recent releases collection is currently displayed. */
    private var isShowingNew = false
    /** Whether a catalog page request is active. */
    private var isFetching = false
    /** Whether additional catalog pages may contain results. */
    private var canLoadMore = true
    /** Active favorites observation job. */
    private var favoritesJob: Job? = null
    /** Active downloads observation job. */
    private var downloadsJob: Job? = null
    /** Active recent-movies loading job. */
    private var newMoviesJob: Job? = null
    /** Active catalog search job. */
    private var searchJob: Job? = null

    /** Observes local filter preferences to refresh the active result collection. */
    init {
        observeMovieFilters()
    }

    /** Re-runs the currently displayed search or collection when filters change. */
    private fun observeMovieFilters() {
        combine(
            preferenceManager.filteredLanguagesFlow,
            preferenceManager.minimumRatingFlow
        ) { _, _ -> Unit }
            .onEach {
                if (isShowingFavorites) {
                    showFavorites(force = true)
                } else if (isShowingDownloads) {
                    showDownloadedMovies(force = true)
                } else if (isShowingNew) {
                    showNewMovies(force = true)
                } else if (lastQuery != null || lastGenre != null) {
                    currentPage = 1
                    allResults.clear()
                    loadMore(force = true) 
                }
            }
            .launchIn(viewModelScope)
    }

    /** Applies [quality] and reloads the current results when it changes. */
    fun setQuality(quality: String?) {
        val q = if (quality == "All") null else quality
        if (_selectedQuality.value == q) return
        _selectedQuality.value = q
        clearLastClickedMovieId()
        
        if (isShowingFavorites) {
            showFavorites(force = true)
        } else if (isShowingDownloads) {
            showDownloadedMovies(force = true)
        } else if (isShowingNew) {
            showNewMovies(force = true)
        } else {
            resetAndLoad()
        }
    }

    /** Switches between Movies and TV Series mode. */
    fun setTvMode(isTv: Boolean) {
        if (_isTvMode.value == isTv) return
        _isTvMode.value = isTv
        if (isTv) {
            when {
                isShowingDownloads -> showDownloadedTvSeries(force = true)
                isShowingNew -> showNewTvSeries(force = true)
                lastQuery != null -> searchTvSeries(lastQuery!!, force = true)
                else -> _tvUiState.value = SearchTvUiState.Idle
            }
        } else {
            when {
                isShowingFavorites -> showFavorites(force = true)
                isShowingDownloads -> showDownloadedMovies(force = true)
                isShowingNew -> showNewMovies(force = true)
                lastQuery != null -> search(lastQuery!!)
                else -> _uiState.value = SearchUiState.Idle
            }
        }
    }

    /** Stores the TV series ID used to restore result-card focus. */
    fun setLastClickedSeriesId(id: Int?) {
        _lastClickedSeriesId.value = id
    }

    /** Clears the pending TV series focus-restoration ID. */
    fun clearLastClickedSeriesId() {
        _lastClickedSeriesId.value = null
    }

    /** Performs a TV series search on TMDB. */
    fun searchTvSeries(query: String, force: Boolean = false) {
        if (query.isEmpty()) {
            _tvUiState.value = SearchTvUiState.Idle
            return
        }
        if (!force && query == lastQuery && _tvUiState.value is SearchTvUiState.Success) return

        lastQuery = query
        viewModelScope.launch {
            _tvUiState.value = SearchTvUiState.Loading
            try {
                val page = tmdbRepository.searchTvSeries(query)
                val results = page.results.orEmpty()
                if (results.isEmpty()) {
                    _tvUiState.value = SearchTvUiState.Empty
                } else {
                    _tvUiState.value = SearchTvUiState.Success(results)
                }
            } catch (e: Exception) {
                _tvUiState.value = SearchTvUiState.Error(UiText.DynamicString(e.localizedMessage ?: "Unknown error"))
            }
        }
    }

    /** Loads TV series that have downloaded episodes for the user. */
    fun showDownloadedTvSeries(force: Boolean = false) {
        viewModelScope.launch {
            _tvUiState.value = SearchTvUiState.Loading
            userLibraryRepository.getDownloadedEpisodes().collect { downloads ->
                val uniqueSeriesIds = downloads.map { it.seriesId }.distinct()
                if (uniqueSeriesIds.isEmpty()) {
                    _tvUiState.value = SearchTvUiState.Empty
                    return@collect
                }
                val seriesList = mutableListOf<TmdbTvSummary>()
                uniqueSeriesIds.forEach { seriesId ->
                    try {
                        val series = tmdbRepository.getTvDetails(seriesId)
                        seriesList.add(series)
                    } catch (_: Exception) {}
                }
                if (seriesList.isEmpty()) {
                    _tvUiState.value = SearchTvUiState.Empty
                } else {
                    _tvUiState.value = SearchTvUiState.Success(seriesList)
                }
            }
        }
    }

    /** Loads trending/popular TV series on TMDB. */
    fun showNewTvSeries(force: Boolean = false) {
        viewModelScope.launch {
            _tvUiState.value = SearchTvUiState.Loading
            try {
                val page = tmdbRepository.getHomeTvFeed(TmdbTvFeed.POPULAR, 1)
                val results = page.results.orEmpty()
                if (results.isEmpty()) {
                    _tvUiState.value = SearchTvUiState.Empty
                } else {
                    _tvUiState.value = SearchTvUiState.Success(results)
                }
            } catch (e: Exception) {
                _tvUiState.value = SearchTvUiState.Error(UiText.DynamicString(e.localizedMessage ?: "Unknown error"))
            }
        }
    }

    /** Starts a text search, resetting the results when [query] is empty. */
    fun search(query: String) {
        if (query.isEmpty()) {
            resetSearch()
            return
        }
        if (_isTvMode.value) {
            searchTvSeries(query)
            return
        }
        if (query == lastQuery && !isShowingFavorites && !isShowingDownloads && !isShowingNew) return
        
        isShowingFavorites = false
        isShowingDownloads = false
        isShowingNew = false
        lastQuery = query
        lastGenre = null
        favoritesJob?.cancel()
        downloadsJob?.cancel()
        newMoviesJob?.cancel()
        clearLastClickedMovieId()
        
        resetAndLoad()
    }

    /** Clears the active search and returns the result state to idle. */
    fun resetSearch() {
        isShowingFavorites = false
        isShowingDownloads = false
        isShowingNew = false
        lastQuery = null
        lastGenre = null
        favoritesJob?.cancel()
        downloadsJob?.cancel()
        newMoviesJob?.cancel()
        allResults.clear()
        currentPage = 1
        canLoadMore = true
        clearLastClickedMovieId()
        clearLastClickedSeriesId()
        _uiState.value = SearchUiState.Idle
        _tvUiState.value = SearchTvUiState.Idle
    }

    /** Searches by [genre], or opens a special downloads/recent collection. */
    fun searchByGenre(genre: String) {
        if (genre == "ya_vistas") {
            showDownloadedMovies()
            return
        }
        if (genre == "nuevas") {
            showNewMovies()
            return
        }
        if (genre == lastGenre && !isShowingFavorites && !isShowingDownloads && !isShowingNew) return
        
        isShowingFavorites = false
        isShowingDownloads = false
        isShowingNew = false
        lastGenre = genre
        lastQuery = null
        favoritesJob?.cancel()
        downloadsJob?.cancel()
        newMoviesJob?.cancel()
        clearLastClickedMovieId()
        
        resetAndLoad()
    }

    /** Observes and filters the signed-in user's favorites. */
    fun showFavorites(force: Boolean = false) {
        if (!force && isShowingFavorites && (favoritesJob?.isActive == true || _uiState.value is SearchUiState.Success)) return

        isShowingFavorites = true
        isShowingDownloads = false
        isShowingNew = false
        lastQuery = null
        lastGenre = null
        canLoadMore = false
        downloadsJob?.cancel()
        newMoviesJob?.cancel()
        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            combine(
                ytsRepository.getFavoriteMovies(),
                userLibraryRepository.getFavoriteTvSeries()
            ) { movies, series -> movies to series }.collect { (favorites, series) ->
                if (isShowingFavorites) {
                    _favoriteTvSeries.value = series
                    allResults.clear()
                    
                    val excludedLangs = preferenceManager.getFilteredLanguages()
                    val filteredFavorites = MovieFilter.filterMovies(
                        favorites,
                        excludedLangs,
                        _selectedQuality.value
                    ).distinctBy { it.id }
                    
                    allResults.addAll(filteredFavorites)
                    if (allResults.isEmpty() && series.isEmpty()) {
                        _uiState.value = SearchUiState.Empty
                    } else {
                        _uiState.value = SearchUiState.Success(allResults.toList(), isFavorites = true)
                    }
                }
            }
        }
    }

    /** Observes downloads and fetches missing movie metadata as needed. */
    fun showDownloadedMovies(force: Boolean = false) {
        if (!force && isShowingDownloads && (downloadsJob?.isActive == true || _uiState.value is SearchUiState.Success)) return

        isShowingDownloads = true
        isShowingFavorites = false
        isShowingNew = false
        lastQuery = null
        lastGenre = "ya_vistas"
        canLoadMore = false
        favoritesJob?.cancel()
        newMoviesJob?.cancel()
        downloadsJob?.cancel()
        downloadsJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            userLibraryRepository.getDownloadedMovies().collect { downloads ->
                if (!isShowingDownloads) return@collect
                
                val excludedLangs = preferenceManager.getFilteredLanguages()
                
                // Keep track of movies we have metadata for
                val moviesMap = mutableMapOf<Int, Movie>()
                downloads.forEach { dl ->
                    dl.movie?.let { moviesMap[dl.movieId] = it }
                }

                /** Rebuilds the displayed download results from available movie metadata. */
                fun updateState() {
                    val sortedMovies = downloads.mapNotNull { moviesMap[it.movieId] }.distinctBy { it.id }
                    val filtered = MovieFilter.filterMovies(
                        sortedMovies,
                        excludedLangs,
                        _selectedQuality.value
                    ).distinctBy { it.id }
                    allResults.clear()
                    allResults.addAll(filtered)
                    
                    if (allResults.isEmpty()) {
                        if (downloads.isEmpty()) {
                            _uiState.value = SearchUiState.Empty
                        } else if (moviesMap.size == downloads.distinctBy { it.movieId }.size) {
                            // We fetched everything and it was all filtered out by language
                            _uiState.value = SearchUiState.Empty
                        } else {
                            // Still fetching or loading
                            _uiState.value = SearchUiState.Loading
                        }
                    } else {
                        _uiState.value = SearchUiState.Success(allResults.toList(), isDownloads = true)
                    }
                }

                updateState()

                // Fetch missing metadata for older records in batches
                downloads.filter { it.movie == null }
                    .distinctBy { it.movieId }
                    .chunked(5)
                    .forEach { batch ->
                        batch.map { dl ->
                            launch {
                                try {
                                    val details = ytsRepository.getMovieSummary(dl.movieId)
                                    details.data?.movie?.let { movie ->
                                        moviesMap[dl.movieId] = movie
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }.joinAll()
                        updateState()
                    }
            }
        }
    }

    /** Loads recent movie IDs and fetches their summaries in batches. */
    fun showNewMovies(force: Boolean = false) {
        if (!force && isShowingNew && (newMoviesJob?.isActive == true || _uiState.value is SearchUiState.Success)) return

        isShowingNew = true
        isShowingFavorites = false
        isShowingDownloads = false
        lastQuery = null
        lastGenre = "nuevas"
        canLoadMore = false
        favoritesJob?.cancel()
        downloadsJob?.cancel()
        newMoviesJob?.cancel()
        newMoviesJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            try {
                val recentIds = ytsRepository.getRecentMovieIds()
                val excludedLangs = preferenceManager.getFilteredLanguages()
                val moviesMap = mutableMapOf<Int, Movie>()

                /** Rebuilds the displayed recent-release results from fetched summaries. */
                fun updateState() {
                    val sortedMovies = recentIds.mapNotNull { moviesMap[it] }.distinctBy { it.id }
                    val filtered = MovieFilter.filterMovies(
                        sortedMovies,
                        excludedLangs,
                        _selectedQuality.value,
                        preferenceManager.getMinimumRating()
                    ).distinctBy { it.id }
                    allResults.clear()
                    allResults.addAll(filtered)
                    
                    if (allResults.isEmpty()) {
                        if (recentIds.isEmpty()) {
                            _uiState.value = SearchUiState.Empty
                        } else if (moviesMap.size == recentIds.distinct().size) {
                            _uiState.value = SearchUiState.Empty
                        } else {
                            _uiState.value = SearchUiState.Loading
                        }
                    } else {
                        _uiState.value = SearchUiState.Success(allResults.toList(), isNew = true)
                    }
                }

                if (recentIds.isEmpty()) {
                    _uiState.value = SearchUiState.Empty
                    return@launch
                }

                // Optimization: Fetch all summaries in parallel but update state in batches
                recentIds.chunked(5).forEach { batchIds ->
                    batchIds.map { id ->
                        launch {
                            try {
                                val details = ytsRepository.getMovieSummary(id)
                                details.data?.movie?.let { movie ->
                                    moviesMap[id] = movie
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }.joinAll()
                    updateState()
                }
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(UiText.DynamicString(e.localizedMessage ?: "Unknown error"))
            }
        }
    }

    /** Stores the movie ID used to restore result-card focus. */
    fun setLastClickedMovieId(id: Int?) {
        _lastClickedMovieId.value = id
    }

    /** Clears the pending result-card focus-restoration ID. */
    fun clearLastClickedMovieId() {
        _lastClickedMovieId.value = null
    }

    /** Removes [movie] from the favorites collection. */
    fun removeFavorite(movie: Movie) {
        viewModelScope.launch {
            ytsRepository.removeFavorite(movie)
        }
    }

    /** Adds [movieId] to or removes it from the multi-selection. */
    fun toggleFavoriteSelection(movieId: Int) {
        val current = _selectedFavoriteIds.value
        _selectedFavoriteIds.value = if (current.contains(movieId)) {
            current - movieId
        } else {
            current + movieId
        }
    }

    /** Clears all selected favorite IDs. */
    fun clearSelection() {
        _selectedFavoriteIds.value = emptySet()
    }

    /** Removes every selected movie from favorites and clears the selection. */
    fun deleteSelectedFavorites() {
        val idsToDelete = _selectedFavoriteIds.value
        if (idsToDelete.isEmpty()) return

        viewModelScope.launch {
            // Find movies in allResults that match the ids to delete
            val moviesToDelete = allResults.filter { it.id in idsToDelete }
            moviesToDelete.forEach { movie ->
                ytsRepository.removeFavorite(movie)
            }
            clearSelection()
        }
    }

    /** Resets catalog pagination and starts loading from the first page. */
    private fun resetAndLoad() {
        allResults.clear()
        currentPage = 1
        canLoadMore = true
        loadMore(force = true)
    }

    /** Loads catalog pages until new filtered results are found or no pages remain. */
    fun loadMore(force: Boolean = false) {
        if ((isFetching && !force) || !canLoadMore || isShowingFavorites) return
        
        val query = lastQuery
        val genre = lastGenre
        
        if (query == null && genre == null) return

        searchJob?.cancel()
        isFetching = true
        if (currentPage > 1) {
            _isLoadingMore.value = true
        }
        searchJob = viewModelScope.launch {
            try {
                if (currentPage == 1) _uiState.value = SearchUiState.Loading
                
                val apiQuality = when (_selectedQuality.value) {
                    "1080p.x265" -> "1080p"
                    else -> _selectedQuality.value
                }

                var foundNewMovies = false
                while (canLoadMore && !foundNewMovies) {
                    val result = when {
                        query != null -> ytsRepository.searchMovies(query, currentPage, apiQuality)
                        genre != null -> ytsRepository.searchByGenre(genre, currentPage, apiQuality)
                        else -> null
                    }

                    val moviesFromApi = result?.data?.movies
                    
                    if (moviesFromApi.isNullOrEmpty()) {
                        canLoadMore = false
                    } else {
                        // Deduplicate API response first
                        val distinctFromApi = moviesFromApi.distinctBy { it.id }
                        
                        // Filter movies based on user settings
                        val excludedLangs = preferenceManager.getFilteredLanguages()
                        val filteredMovies = MovieFilter.filterMovies(
                            distinctFromApi,
                            excludedLangs,
                            _selectedQuality.value,
                            minimumRating = if (query == null) {
                                preferenceManager.getMinimumRating()
                            } else {
                                null
                            }
                        )
                        
                        // Filter duplicates against existing results
                        val newMovies = filteredMovies.filter { newMovie ->
                            allResults.none { it.id == newMovie.id }
                        }
                        
                        if (newMovies.isNotEmpty()) {
                            allResults.addAll(newMovies)
                            _uiState.value = SearchUiState.Success(allResults.toList(), genre = lastGenre)
                            foundNewMovies = true
                        }
                    }
                    
                    currentPage++
                }

                if (allResults.isEmpty() && !canLoadMore) {
                    _uiState.value = SearchUiState.Empty
                }
            } catch (e: Exception) {
                if (e !is CancellationException && allResults.isEmpty()) {
                    _uiState.value = SearchUiState.Error(UiText.DynamicString(e.localizedMessage ?: "Unknown error"))
                }
            } finally {
                isFetching = false
                _isLoadingMore.value = false
            }
        }
    }
}

/** States emitted while searching or displaying movie collections. */
sealed interface SearchUiState {
    /** No search or collection is currently active. */
    object Idle : SearchUiState
    /** Results are being loaded. */
    object Loading : SearchUiState
    /** The active search or collection has no matching results. */
    object Empty : SearchUiState
    /**
     * Successfully loaded movies and the collection context for displaying them.
     *
     * @property movies Matching movie entries.
     * @property isFavorites Whether the results are saved favorites.
     * @property isDownloads Whether the results are downloaded movies.
     * @property isNew Whether the results are recent releases.
     * @property genre Genre associated with a catalog search, if applicable.
     */
    data class Success(
        val movies: List<Movie>, 
        val isFavorites: Boolean = false,
        val isDownloads: Boolean = false,
        val isNew: Boolean = false,
        val genre: String? = null
    ) : SearchUiState
    /**
     * Search or collection loading failed.
     *
     * @property message User-facing failure description.
     */
    data class Error(val message: UiText) : SearchUiState
}

/** States emitted while searching or displaying TV series collections. */
sealed interface SearchTvUiState {
    /** No search or collection is currently active. */
    object Idle : SearchTvUiState
    /** Results are being loaded. */
    object Loading : SearchTvUiState
    /** The active search or collection has no matching results. */
    object Empty : SearchTvUiState
    /**
     * Successfully loaded TV series.
     *
     * @property series Matching TV series entries.
     */
    data class Success(val series: List<TmdbTvSummary>) : SearchTvUiState
    /**
     * Search or collection loading failed.
     *
     * @property message User-facing failure description.
     */
    data class Error(val message: UiText) : SearchTvUiState
}
