package com.martinrevert.latorrentola.ui.home

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
class HomeViewModel @Inject constructor(
    private val ytsRepository: YtsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val allMovies = mutableListOf<Movie>()
    private var currentPage = 1
    private var isFetching = false
    private var canLoadMore = true

    init {
        loadMovies()
    }

    fun loadMovies() {
        if (isFetching || !canLoadMore) return
        isFetching = true
        
        viewModelScope.launch {
            try {
                if (currentPage == 1) _uiState.value = HomeUiState.Loading
                
                val result = ytsRepository.getMovies(currentPage)
                result.data?.movies?.let { newMovies ->
                    if (newMovies.isNotEmpty()) {
                        // 1. Add only new movies (by id) to avoid duplicates
                        val filteredNewMovies = newMovies.filter { newMovie ->
                            allMovies.none { it.id == newMovie.id }
                        }
                        
                        if (filteredNewMovies.isNotEmpty()) {
                            allMovies.addAll(filteredNewMovies)
                            _uiState.value = HomeUiState.Success(allMovies.toList())
                        }
                        currentPage++
                    } else {
                        canLoadMore = false
                        if (allMovies.isEmpty()) _uiState.value = HomeUiState.Error("No movies found")
                    }
                } ?: run {
                    canLoadMore = false
                    if (allMovies.isEmpty()) _uiState.value = HomeUiState.Error("No movies found")
                }
            } catch (e: Exception) {
                if (allMovies.isEmpty()) _uiState.value = HomeUiState.Error(e.localizedMessage ?: "Unknown error")
            } finally {
                isFetching = false
            }
        }
    }

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

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(val movies: List<Movie>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
