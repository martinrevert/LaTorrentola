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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val ytsRepository: YtsRepository,
    private val userLibraryRepository: UserLibraryRepository,
    private val preferenceManager: PreferenceManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _topGenres = MutableStateFlow<List<String>>(emptyList())
    val topGenres: StateFlow<List<String>> = _topGenres.asStateFlow()

    private val _lastVisitDate = MutableStateFlow<Long?>(null)
    val lastVisitDate: StateFlow<Long?> = _lastVisitDate.asStateFlow()

    // Expose refresh state for UI pull-to-refresh
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _selectedQuality = MutableStateFlow<String?>(null)
    val selectedQuality: StateFlow<String?> = _selectedQuality.asStateFlow()

    private val _lastClickedMovieId = MutableStateFlow<Int?>(null)
    val lastClickedMovieId: StateFlow<Int?> = _lastClickedMovieId.asStateFlow()

    val downloadedMovieIds: StateFlow<Set<Int>> = userLibraryRepository.getDownloadedMovies()
        .map { it.map { download -> download.movieId }.toSet() }
        .catch { emit(emptySet()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val qualityOptions = listOf("All", "2160p", "1080p.x265", "1080p", "720p", "3D")

    val favoritesCount: StateFlow<Int> = ytsRepository.getFavoriteMovies()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

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
    private var movieFetchJob: kotlinx.coroutines.Job? = null

    init {
        initVisitDate()
        observeTopGenres()
        observeFilteredLanguages()
        syncRemoteSettings()
    }

    private fun syncRemoteSettings() {
        viewModelScope.launch {
            authRepository.authStateFlow
                .filterNotNull()
                .flatMapLatest { user ->
                    userLibraryRepository.observeRemoteFilteredLanguages(user.uid)
                }
                .collect { remoteFiltered ->
                    if (remoteFiltered != null && remoteFiltered != preferenceManager.getFilteredLanguages()) {
                        preferenceManager.setFilteredLanguages(remoteFiltered)
                        // Local flow observer will trigger refresh()
                    }
                }
        }
    }

    private fun observeFilteredLanguages() {
        preferenceManager.filteredLanguagesFlow
            .onEach { refresh(force = true) }
            .launchIn(viewModelScope)
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

    fun setQuality(quality: String?) {
        val q = if (quality == "All") null else quality
        if (_selectedQuality.value == q) return
        _selectedQuality.value = q
        clearLastClickedMovieId()
        refresh()
    }

    fun loadMovies() {
        if (isFetching || !canLoadMore) return
        isFetching = true
        
        movieFetchJob = viewModelScope.launch {
            try {
                if (currentPage == 1) _uiState.value = HomeUiState.Loading
                
                var foundNewMovies = false
                while (canLoadMore && !foundNewMovies) {
                    val result = ytsRepository.getMovies(currentPage, _selectedQuality.value)
                    val moviesFromApi = result.data?.movies
                    
                    // Filter movies based on user settings
                    val excludedLangs = preferenceManager.getFilteredLanguages()
                    val filteredMovies = MovieFilter.filterMovies(moviesFromApi, excludedLangs)
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
            }
        }
    }

    /**
     * Refresh the movie list: clear cached items and reload page 1.
     * This method sets [_isRefreshing] while the network call is in progress
     * so UI pull-to-refresh indicators can react.
     */
    fun refresh(force: Boolean = false) {
        if (isFetching && !force) return
        
        movieFetchJob?.cancel()
        
        movieFetchJob = viewModelScope.launch {
            isFetching = true
            _isRefreshing.value = true
            try {
                // reset pagination and current list
                currentPage = 1
                canLoadMore = true
                allMovies.clear()
                _uiState.value = HomeUiState.Loading

                var foundNewMovies = false
                while (canLoadMore && !foundNewMovies) {
                    val result = ytsRepository.getMovies(currentPage, _selectedQuality.value)
                    val moviesFromApi = result.data?.movies

                    if (moviesFromApi.isNullOrEmpty()) {
                        canLoadMore = false
                        break
                    }

                    // Filter movies based on user settings
                    val excludedLangs = preferenceManager.getFilteredLanguages()
                    val filteredMovies = MovieFilter.filterMovies(moviesFromApi, excludedLangs)
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
    fun setLastClickedMovieId(id: Int?) {
        _lastClickedMovieId.value = id
    }

    fun clearLastClickedMovieId() {
        _lastClickedMovieId.value = null
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
    data class Error(val message: UiText) : HomeUiState
}
