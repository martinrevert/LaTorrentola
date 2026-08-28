package com.martinrevert.latorrentola.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.network.YtsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val ytsRepository: YtsRepository,
    private val userLibraryRepository: UserLibraryRepository
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
    private var isFetching = false
    private var canLoadMore = true

    fun setQuality(quality: String?) {
        val q = if (quality == "All") null else quality
        if (_selectedQuality.value == q) return
        _selectedQuality.value = q
        clearLastClickedMovieId()
        if (!isShowingFavorites) {
            resetAndLoad()
        }
    }

    fun search(query: String) {
        if (query.isEmpty()) {
            resetSearch()
            return
        }
        if (query == lastQuery && !isShowingFavorites) return
        
        isShowingFavorites = false
        lastQuery = query
        lastGenre = null
        clearLastClickedMovieId()
        
        resetAndLoad()
    }

    fun resetSearch() {
        isShowingFavorites = false
        lastQuery = null
        lastGenre = null
        allResults.clear()
        currentPage = 1
        canLoadMore = true
        clearLastClickedMovieId()
        _uiState.value = SearchUiState.Idle
    }

    fun searchByGenre(genre: String) {
        if (genre == lastGenre && !isShowingFavorites) return
        
        isShowingFavorites = false
        lastGenre = genre
        lastQuery = null
        clearLastClickedMovieId()
        
        resetAndLoad()
    }

    fun showFavorites() {
        isShowingFavorites = true
        lastQuery = null
        lastGenre = null
        canLoadMore = false
        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            ytsRepository.getFavoriteMovies().collect { favorites ->
                if (isShowingFavorites) {
                    allResults.clear()
                    allResults.addAll(favorites)
                    if (allResults.isEmpty()) {
                        _uiState.value = SearchUiState.Empty
                    } else {
                        _uiState.value = SearchUiState.Success(allResults.toList(), isFavorites = true)
                    }
                }
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
        loadMore()
    }

    fun loadMore() {
        if (isFetching || !canLoadMore || isShowingFavorites) return
        
        val query = lastQuery
        val genre = lastGenre
        
        if (query == null && genre == null) return

        isFetching = true
        viewModelScope.launch {
            try {
                if (currentPage == 1) _uiState.value = SearchUiState.Loading
                
                val result = when {
                    query != null -> ytsRepository.searchMovies(query, currentPage, _selectedQuality.value)
                    genre != null -> ytsRepository.searchByGenre(genre, currentPage, _selectedQuality.value)
                    else -> null
                }

                val moviesFromApi = result?.data?.movies
                
                if (moviesFromApi.isNullOrEmpty()) {
                    canLoadMore = false
                    if (allResults.isEmpty()) {
                        _uiState.value = SearchUiState.Empty
                    }
                } else {
                    // Deduplicate API response first
                    val distinctFromApi = moviesFromApi.distinctBy { it.id }
                    
                    // Filter duplicates against existing results
                    val newMovies = distinctFromApi.filter { newMovie ->
                        allResults.none { it.id == newMovie.id }
                    }
                    
                    if (newMovies.isNotEmpty()) {
                        allResults.addAll(newMovies)
                        _uiState.value = SearchUiState.Success(allResults.toList())
                        currentPage++
                    } else {
                        // If all movies were duplicates, try next page automatically
                        currentPage++
                        isFetching = false
                        loadMore()
                        return@launch
                    }
                }
            } catch (e: Exception) {
                if (allResults.isEmpty()) {
                    _uiState.value = SearchUiState.Error(e.localizedMessage ?: "Unknown error")
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
    data class Success(val movies: List<Movie>, val isFavorites: Boolean = false) : SearchUiState
    data class Error(val message: String) : SearchUiState
}
