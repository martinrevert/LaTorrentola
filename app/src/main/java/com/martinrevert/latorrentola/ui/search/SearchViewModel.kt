package com.martinrevert.latorrentola.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.YTS.Movie
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

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val ytsRepository: YtsRepository,
    private val userLibraryRepository: UserLibraryRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _selectedQuality = MutableStateFlow<String?>(null)
    val selectedQuality: StateFlow<String?> = _selectedQuality.asStateFlow()

    private val _lastClickedMovieId = MutableStateFlow<Int?>(null)
    val lastClickedMovieId: StateFlow<Int?> = _lastClickedMovieId.asStateFlow()

    private val _selectedFavoriteIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedFavoriteIds: StateFlow<Set<Int>> = _selectedFavoriteIds.asStateFlow()

    val downloadedMovieIds: StateFlow<Set<Int>> = userLibraryRepository.getDownloadedMovies()
        .map { it.map { download -> download.movieId }.toSet() }
        .catch { emit(emptySet()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val qualityOptions = listOf("All", "2160p", "1080p.x265", "1080p", "720p", "3D")

    private val allResults = mutableListOf<Movie>()
    private var currentPage = 1
    private var lastQuery: String? = null
    private var lastGenre: String? = null
    private var isShowingFavorites = false
    private var isShowingDownloads = false
    private var isShowingNew = false
    private var isFetching = false
    private var canLoadMore = true
    private var favoritesJob: Job? = null
    private var downloadsJob: Job? = null
    private var newMoviesJob: Job? = null
    private var searchJob: Job? = null

    init {
        observeFilteredLanguages()
    }

    private fun observeFilteredLanguages() {
        preferenceManager.filteredLanguagesFlow
            .onEach { 
                if (isShowingFavorites) {
                    showFavorites()
                } else if (isShowingDownloads) {
                    showDownloadedMovies()
                } else if (isShowingNew) {
                    showNewMovies()
                } else if (lastQuery != null || lastGenre != null) {
                    currentPage = 1
                    allResults.clear()
                    loadMore(force = true) 
                }
            }
            .launchIn(viewModelScope)
    }

    fun setQuality(quality: String?) {
        val q = if (quality == "All") null else quality
        if (_selectedQuality.value == q) return
        _selectedQuality.value = q
        clearLastClickedMovieId()
        
        if (isShowingFavorites) {
            showFavorites()
        } else if (isShowingDownloads) {
            showDownloadedMovies()
        } else if (isShowingNew) {
            showNewMovies()
        } else {
            resetAndLoad()
        }
    }

    fun search(query: String) {
        if (query.isEmpty()) {
            resetSearch()
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
        _uiState.value = SearchUiState.Idle
    }

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

    fun showFavorites() {
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
            ytsRepository.getFavoriteMovies().collect { favorites ->
                if (isShowingFavorites) {
                    allResults.clear()
                    
                    val excludedLangs = preferenceManager.getFilteredLanguages()
                    val filteredFavorites = MovieFilter.filterMovies(
                        favorites,
                        excludedLangs,
                        _selectedQuality.value
                    ).distinctBy { it.id }
                    
                    allResults.addAll(filteredFavorites)
                    if (allResults.isEmpty()) {
                        _uiState.value = SearchUiState.Empty
                    } else {
                        _uiState.value = SearchUiState.Success(allResults.toList(), isFavorites = true)
                    }
                }
            }
        }
    }

    fun showDownloadedMovies() {
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

    fun showNewMovies() {
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

                fun updateState() {
                    val sortedMovies = recentIds.mapNotNull { moviesMap[it] }.distinctBy { it.id }
                    val filtered = MovieFilter.filterMovies(
                        sortedMovies,
                        excludedLangs,
                        _selectedQuality.value
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

    fun setLastClickedMovieId(id: Int?) {
        _lastClickedMovieId.value = id
    }

    fun clearLastClickedMovieId() {
        _lastClickedMovieId.value = null
    }

    fun removeFavorite(movie: Movie) {
        viewModelScope.launch {
            ytsRepository.removeFavorite(movie)
        }
    }

    fun toggleFavoriteSelection(movieId: Int) {
        val current = _selectedFavoriteIds.value
        _selectedFavoriteIds.value = if (current.contains(movieId)) {
            current - movieId
        } else {
            current + movieId
        }
    }

    fun clearSelection() {
        _selectedFavoriteIds.value = emptySet()
    }

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

    private fun resetAndLoad() {
        allResults.clear()
        currentPage = 1
        canLoadMore = true
        loadMore(force = true)
    }

    fun loadMore(force: Boolean = false) {
        if ((isFetching && !force) || !canLoadMore || isShowingFavorites) return
        
        val query = lastQuery
        val genre = lastGenre
        
        if (query == null && genre == null) return

        searchJob?.cancel()
        isFetching = true
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
                            _selectedQuality.value
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
                    val error = if (query != null) {
                        UiText.StringResource(R.string.movie_not_found_query, query)
                    } else if (genre != null) {
                        UiText.StringResource(R.string.movie_not_found_query, genre)
                    } else {
                        UiText.StringResource(R.string.no_results)
                    }
                    _uiState.value = SearchUiState.Error(error)
                }
            } catch (e: Exception) {
                if (e !is CancellationException && allResults.isEmpty()) {
                    _uiState.value = SearchUiState.Error(UiText.DynamicString(e.localizedMessage ?: "Unknown error"))
                }
            } finally {
                isFetching = false
            }
        }
    }
}

sealed interface SearchUiState {
    object Idle : SearchUiState
    object Loading : SearchUiState
    object Empty : SearchUiState
    data class Success(
        val movies: List<Movie>, 
        val isFavorites: Boolean = false,
        val isDownloads: Boolean = false,
        val isNew: Boolean = false,
        val genre: String? = null
    ) : SearchUiState
    data class Error(val message: UiText) : SearchUiState
}
