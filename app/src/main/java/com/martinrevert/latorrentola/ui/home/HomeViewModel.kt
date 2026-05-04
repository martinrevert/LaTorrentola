package com.martinrevert.latorrentola.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.date.DateLastVisit
import com.martinrevert.latorrentola.network.YtsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val ytsRepository: YtsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _topGenres = MutableStateFlow<List<String>>(emptyList())
    val topGenres: StateFlow<List<String>> = _topGenres.asStateFlow()

    private val _lastVisitDate = MutableStateFlow<Long?>(null)
    val lastVisitDate: StateFlow<Long?> = _lastVisitDate.asStateFlow()

    val allGenres = listOf(
        "Action", "Adventure", "Animation", "Biography", "Comedy", "Crime",
        "Documentary", "Drama", "Family", "Fantasy", "Film-Noir", "History",
        "Horror", "Music", "Musical", "Mystery", "Romance", "Sci-Fi",
        "Short", "Sport", "Thriller", "War", "Western"
    )

    private val allMovies = mutableListOf<Movie>()
    private var currentPage = 1
    private var isFetching = false
    private var canLoadMore = true

    init {
        initVisitDate()
        loadMovies()
        observeTopGenres()
    }

    private fun initVisitDate() {
        viewModelScope.launch {
            val visit = ytsRepository.getLastVisitDate()
            _lastVisitDate.value = visit?.date?.time
            
            // Immediately update to current time for NEXT session
            ytsRepository.setLastVisitDate(DateLastVisit(id = 1, date = Date()))
        }
    }

    private fun observeTopGenres() {
        ytsRepository.getTopGenres(limit = 7)
            .onEach { stats ->
                val topList = stats.map { it.genre }.toMutableList()
                
                // Fill with defaults if not enough history
                val defaults = listOf("Action", "Comedy", "Drama", "Horror", "Sci-Fi")
                for (default in defaults) {
                    if (topList.size >= 7) break
                    if (!topList.contains(default)) {
                        topList.add(default)
                    }
                }
                _topGenres.value = topList
            }
            .launchIn(viewModelScope)
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
