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

    fun search(query: String) {
        if (query.isEmpty()) {
            resetSearch()
            return
        }
        if (query == lastQuery && !isShowingFavorites) return
        isShowingFavorites = false
        lastQuery = query
        lastGenre = null
        resetAndLoad { ytsRepository.searchMovies(query) }
    }

    fun resetSearch() {
        isShowingFavorites = false
        lastQuery = null
        lastGenre = null
        allResults.clear()
        _uiState.value = SearchUiState.Idle
    }

    fun searchByGenre(genre: String) {
        if (genre == lastGenre && !isShowingFavorites) return
        isShowingFavorites = true // We use the same state for genre/search lists
        lastGenre = genre
        lastQuery = null
        isShowingFavorites = false
        resetAndLoad { ytsRepository.searchByGenre(genre, 1) }
    }

    fun showFavorites() {
        isShowingFavorites = true
        lastQuery = null
        lastGenre = null
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
            // The Flow from getFavoriteMovies will automatically trigger UI update if we are in Favorites mode
        }
    }

    private fun resetAndLoad(loadTask: suspend () -> com.martinrevert.latorrentola.model.YTS.MovieDetails) {
        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            allResults.clear()
            currentPage = 1
            try {
                val result = loadTask()
                result.data?.movies?.let {
                    allResults.addAll(it)
                    _uiState.value = SearchUiState.Success(allResults.toList())
                    currentPage++
                } ?: run {
                    _uiState.value = SearchUiState.Empty
                }
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun loadMore() {
        // Implement pagination for search if needed
    }
}

sealed interface SearchUiState {
    object Idle : SearchUiState
    object Loading : SearchUiState
    object Empty : SearchUiState
    data class Success(val movies: List<Movie>, val isFavorites: Boolean = false) : SearchUiState
    data class Error(val message: String) : SearchUiState
}
