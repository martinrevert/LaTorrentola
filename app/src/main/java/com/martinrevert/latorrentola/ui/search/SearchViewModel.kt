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

    fun search(query: String) {
        if (query == lastQuery) return
        lastQuery = query
        lastGenre = null
        resetAndLoad { ytsRepository.searchMovies(query) }
    }

    fun searchByGenre(genre: String) {
        if (genre == lastGenre) return
        lastGenre = genre
        lastQuery = null
        resetAndLoad { ytsRepository.searchByGenre(genre, 1) }
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
    data class Success(val movies: List<Movie>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}
