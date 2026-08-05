package com.martinrevert.latorrentola.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.network.YtsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val ytsRepository: YtsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val allResults = mutableListOf<Movie>()
    private var currentPage = 1
    private var lastQuery: String? = null
    private var lastGenre: String? = null
    private var isShowingFavorites = false
    private var isFetching = false
    private var canLoadMore = true

    fun search(query: String) {
        if (query.isEmpty()) {
            resetSearch()
            return
        }
        if (query == lastQuery && !isShowingFavorites) return
        
        isShowingFavorites = false
        lastQuery = query
        lastGenre = null
        
        resetAndLoad()
    }

    fun resetSearch() {
        isShowingFavorites = false
        lastQuery = null
        lastGenre = null
        allResults.clear()
        currentPage = 1
        canLoadMore = true
        _uiState.value = SearchUiState.Idle
    }

    fun searchByGenre(genre: String) {
        if (genre == lastGenre && !isShowingFavorites) return
        
        isShowingFavorites = false
        lastGenre = genre
        lastQuery = null
        
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

    fun removeFavorite(movie: Movie) {
        viewModelScope.launch {
            ytsRepository.removeFavorite(movie)
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
                    query != null -> ytsRepository.searchMovies(query, currentPage)
                    genre != null -> ytsRepository.searchByGenre(genre, currentPage)
                    else -> null
                }

                val moviesFromApi = result?.data?.movies
                
                if (moviesFromApi.isNullOrEmpty()) {
                    canLoadMore = false
                    if (allResults.isEmpty()) {
                        _uiState.value = SearchUiState.Empty
                    }
                } else {
                    // Filter duplicates
                    val newMovies = moviesFromApi.filter { newMovie ->
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
