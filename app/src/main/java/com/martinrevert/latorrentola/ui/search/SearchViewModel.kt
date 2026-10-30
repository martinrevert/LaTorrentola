package com.martinrevert.latorrentola.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.model.TMDB.TmdbActorDetail
import com.martinrevert.latorrentola.model.TMDB.TmdbCastCredit
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieResult
import com.martinrevert.latorrentola.model.TMDB.TmdbPersonResult
import com.martinrevert.latorrentola.model.TMDB.TmdbTvGenre
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.TMDB.sortedByYearDescending
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.TmdbTvFeed
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.network.YtsRepository
import com.martinrevert.latorrentola.utils.MovieFilter
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** Filter options for the mixed Favorites collection. */
enum class FavoritesFilter {
    ALL,
    MOVIES,
    TV_SERIES,
    WATCHED,
    UNWATCHED,
    IN_PROGRESS
}

/**
 * Performs movie, TV series, and actor searches and exposes favorites, downloads, and new-release collections.
 *
 * @property ytsRepository searches movies and manages favorites.
 * @property tmdbRepository searches TV series and actors and fetches metadata.
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

    companion object {
        private const val TARGET_INITIAL_BATCH = 18
        private const val TARGET_LOAD_MORE_BATCH = 12
        private const val MAX_PAGES_PER_BATCH = 5
        private const val YTS_PAGE_SIZE = 20
    }

    /** Mutable backing state for the search input text, preserved across Back navigation. */
    private val _searchQuery = MutableStateFlow("")

    /** Current search input text. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Mutable backing state for the selected favorites filter. */
    private val _selectedFavoritesFilter = MutableStateFlow(FavoritesFilter.ALL)

    /** Currently selected filter for the favorites collection. */
    val selectedFavoritesFilter: StateFlow<FavoritesFilter> = _selectedFavoritesFilter.asStateFlow()

    /** Mutable backing state for current search results. */
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)

    /** Current search or collection state. */
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Mutable backing state for TV mode toggle. */
    private val _isTvMode = MutableStateFlow(false)

    /** Whether TV mode is currently selected. */
    val isTvMode: StateFlow<Boolean> = _isTvMode.asStateFlow()

    /** Mutable backing state for Actor search mode. */
    private val _isActorMode = MutableStateFlow(false)

    /** Whether Actor search mode is currently active. */
    val isActorMode: StateFlow<Boolean> = _isActorMode.asStateFlow()

    /** Mutable backing state for Actor search results and filmography. */
    private val _actorUiState = MutableStateFlow<SearchActorUiState>(SearchActorUiState.Idle)

    /** Current Actor search and filmography state. */
    val actorUiState: StateFlow<SearchActorUiState> = _actorUiState.asStateFlow()

    /** Mutable backing state for current TV search results. */
    private val _tvUiState = MutableStateFlow<SearchTvUiState>(SearchTvUiState.Idle)

    /** Current TV search or collection state. */
    val tvUiState: StateFlow<SearchTvUiState> = _tvUiState.asStateFlow()

    /** Mutable backing state for TV-card focus restoration. */
    private val _lastClickedSeriesId = MutableStateFlow<Int?>(null)

    /** TV series identifier to refocus when returning to results. */
    val lastClickedSeriesId: StateFlow<Int?> = _lastClickedSeriesId.asStateFlow()

    /** Mutable backing state for Actor credit-card focus restoration. */
    private val _lastClickedCreditKey = MutableStateFlow<String?>(null)

    /** Actor credit key (`"{mediaType}_{id}"`) to refocus when returning to Actor results. */
    val lastClickedCreditKey: StateFlow<String?> = _lastClickedCreditKey.asStateFlow()

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

    /** Shared watch-history flow to avoid duplicate Room/Firestore subscriptions. */
    private val watchHistoryFlow: StateFlow<List<PlaybackProgress>> = userLibraryRepository.getWatchHistory()
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** User's in-progress watch history items mapped by media identifier or title. */
    val watchHistoryMap: StateFlow<Map<String, PlaybackProgress>> = watchHistoryFlow
        .map { list -> list.associateBy { it.mediaId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    /** All TMDB TV genres, ordered by local usage. */
    val tvGenres: StateFlow<List<TmdbTvGenre>> = tmdbRepository.observeTvGenreUsage()
        .map { usage ->
            val catalog = try {
                tmdbRepository.getTvGenres()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
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

    private var movieFocusJob: Job? = null

    /** Currently focused movie in TV immersive search mode. */
    private val _focusedMovie = MutableStateFlow<Movie?>(null)
    val focusedMovie: StateFlow<Movie?> = _focusedMovie.asStateFlow()

    /** High-resolution TMDB or fallback backdrop URL for the currently focused movie. */
    private val _focusedMovieBackdrop = MutableStateFlow<String?>(null)
    val focusedMovieBackdrop: StateFlow<String?> = _focusedMovieBackdrop.asStateFlow()

    /** Enriched TMDB metadata for the currently focused movie. */
    private val _focusedMovieTmdb = MutableStateFlow<TmdbMovieResult?>(null)
    val focusedMovieTmdb: StateFlow<TmdbMovieResult?> = _focusedMovieTmdb.asStateFlow()

    /**
     * Updates the focused movie for the TV immersive layout and fetches its TMDB backdrop.
     */
    fun onMovieFocused(movie: Movie) {
        if (_focusedMovie.value?.id == movie.id) return
        _focusedMovie.value = movie
        _focusedMovieBackdrop.value = movie.backgroundImageOriginal ?: movie.backgroundImage ?: movie.largeCoverImage
        _focusedMovieTmdb.value = null

        movieFocusJob?.cancel()
        movieFocusJob = viewModelScope.launch {
            delay(120)
            val imdb = movie.imdbCode
            if (!imdb.isNullOrBlank()) {
                val tmdb = tmdbRepository.findMovieByImdbId(imdb)
                if (tmdb != null && _focusedMovie.value?.id == movie.id) {
                    _focusedMovieTmdb.value = tmdb
                    tmdb.fullBackdropUrl?.let { backdrop ->
                        _focusedMovieBackdrop.value = backdrop
                    }
                }
            }
        }
    }

    /**
     * Clears the currently focused movie, TMDB details, and backdrop.
     */
    fun clearFocusedMovie() {
        movieFocusJob?.cancel()
        _focusedMovie.value = null
        _focusedMovieBackdrop.value = null
        _focusedMovieTmdb.value = null
    }

    /** Accumulated results backing pagination and bulk selection. */
    private val allResults = mutableListOf<Movie>()
    /** O(1) lookup set of movie IDs currently in [allResults]. */
    private val seenMovieIds = HashSet<Int>()

    /** Accumulated TV results backing TV pagination. */
    private val allTvResults = mutableListOf<TmdbTvSummary>()
    /** O(1) lookup set of TV series IDs currently in [allTvResults]. */
    private val seenTvSeriesIds = HashSet<Int>()
    /** Next page to request for a TV series search or feed. */
    private var currentTvPage = 1
    /** Total TV pages reported by TMDB. */
    private var totalTvPages = 1
    /** Whether a TV page request is active. */
    private var isFetchingTv = false

    /** Next page to request for a catalog search. */
    private var currentPage = 1

    /** Current text search term, if any. */
    private var lastQuery: String? = null

    /** Last actor search query executed. */
    private var lastActorQuery: String? = null

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

    /** Whether initial route parameters have already been applied for this ViewModel instance. */
    private var hasInitializedRoute = false

    /** Monotonic generation counter for movie pagination requests. */
    private var requestGeneration = 0L

    /** Monotonic generation counter for TV pagination requests. */
    private var tvRequestGeneration = 0L

    /** Active favorites observation job. */
    private var favoritesJob: Job? = null

    /** Active downloads observation job. */
    private var downloadsJob: Job? = null

    /** Active recent-movies loading job. */
    private var newMoviesJob: Job? = null

    /** Active catalog search job. */
    private var searchJob: Job? = null

    /** Active TV search or pagination job. */
    private var tvSearchJob: Job? = null

    /** Active Actor search or details job. */
    private var actorSearchJob: Job? = null

    /** Debounce job for typing in the search bar. */
    private var debounceSearchJob: Job? = null

    /** Reactive cache of enriched TV series details used in Favorites without triggering Loading resets. */
    private val seriesDetailsCacheFlow = MutableStateFlow<Map<Int, TmdbTvSummary>>(emptyMap())
    private val attemptedSeriesDetailIds = HashSet<Int>()

    /** Observes local filter preferences to refresh the active result collection. */
    init {
        observeMovieFilters()
    }

    /**
     * Initializes the screen from navigation arguments once per ViewModel lifetime.
     * Returning to `SearchScreen` via Back navigation preserves existing search state and scroll/focus.
     */
    fun initializeIfNeeded(
        initialGenre: String?,
        initialQuery: String?,
        initialTvMode: Boolean,
        initialActorMode: Boolean = false
    ) {
        if (hasInitializedRoute) return
        hasInitializedRoute = true

        _isTvMode.value = initialTvMode
        _isActorMode.value = initialActorMode

        when {
            initialActorMode -> {
                if (!initialQuery.isNullOrBlank()) {
                    _searchQuery.value = initialQuery
                    searchActors(initialQuery)
                }
            }
            initialGenre == "milista" || initialGenre == "favorites" -> {
                showFavorites()
            }
            initialGenre != null -> {
                if (initialTvMode) {
                    when (initialGenre) {
                        "ya_vistas" -> showDownloadedTvSeries()
                        "nuevas" -> showNewTvSeries()
                    }
                } else {
                    searchByGenre(initialGenre)
                }
            }
            initialQuery != null -> {
                _searchQuery.value = initialQuery
                if (initialTvMode) {
                    searchTvSeries(initialQuery)
                } else {
                    search(initialQuery)
                }
            }
        }
    }

    /**
     * Updates the search input text and schedules a debounced search when the query changes.
     */
    fun onSearchQueryChange(newQuery: String) {
        if (_searchQuery.value == newQuery) return
        _searchQuery.value = newQuery
        debounceSearchJob?.cancel()
        if (newQuery.isEmpty()) {
            resetSearch()
        } else {
            debounceSearchJob = viewModelScope.launch {
                delay(300)
                search(newQuery)
            }
        }
    }

    /** Re-runs the currently displayed search or collection when filters change. */
    private fun observeMovieFilters() {
        combine(
            preferenceManager.filteredLanguagesFlow,
            preferenceManager.minimumRatingFlow
        ) { langs, rating -> langs to rating }
            .distinctUntilChanged()
            .onEach {
                if (isShowingFavorites) {
                    showFavorites(force = true)
                } else if (isShowingDownloads) {
                    showDownloadedMovies(force = true)
                } else if (isShowingNew && !_isTvMode.value) {
                    showNewMovies(force = true)
                } else if (!_isTvMode.value && !_isActorMode.value && (lastQuery != null || lastGenre != null)) {
                    currentPage = 1
                    allResults.clear()
                    seenMovieIds.clear()
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

    /** Applies [filter] to the favorites collection without resetting to Loading. */
    fun setFavoritesFilter(filter: FavoritesFilter) {
        if (_selectedFavoritesFilter.value == filter) return
        _selectedFavoritesFilter.value = filter
    }

    /** Switches Actor search mode on or off. */
    fun setActorMode(enabled: Boolean) {
        if (_isActorMode.value == enabled) return
        _isActorMode.value = enabled
        if (enabled) {
            val q = _searchQuery.value.ifBlank { lastQuery.orEmpty() }
            if (q.isNotBlank()) {
                searchActors(q, force = true)
            } else {
                _actorUiState.value = SearchActorUiState.Idle
            }
        }
    }

    /** Switches between Movies and TV Series mode. */
    fun setTvMode(isTv: Boolean) {
        if (_isTvMode.value == isTv && !_isActorMode.value) return
        _isActorMode.value = false
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

    /** Stores the Actor credit key (`"{mediaType}_{id}"`) used to restore card focus. */
    fun setLastClickedCreditKey(key: String?) {
        _lastClickedCreditKey.value = key
    }

    /** Clears the pending Actor credit focus-restoration key. */
    fun clearLastClickedCreditKey() {
        _lastClickedCreditKey.value = null
    }

    /** Performs an Actor search on TMDB and loads the first matching actor's year-descending filmography. */
    fun searchActors(query: String, force: Boolean = false) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            lastActorQuery = null
            _actorUiState.value = SearchActorUiState.Idle
            return
        }
        if (!force && trimmed == lastActorQuery && _actorUiState.value is SearchActorUiState.Success) return

        lastQuery = trimmed
        lastActorQuery = trimmed
        _searchQuery.value = query
        clearLastClickedCreditKey()
        actorSearchJob?.cancel()
        actorSearchJob = viewModelScope.launch {
            _actorUiState.value = SearchActorUiState.Loading
            try {
                val actors = tmdbRepository.searchActors(trimmed)
                if (actors.isEmpty()) {
                    _actorUiState.value = SearchActorUiState.Empty
                    return@launch
                }
                val firstActor = actors.first()
                _actorUiState.value = SearchActorUiState.Success(
                    actors = actors,
                    selectedActor = firstActor,
                    actorDetail = null,
                    credits = emptyList(),
                    isLoadingCredits = true
                )
                val detailResult = tmdbRepository.getActorDetails(firstActor.id)
                detailResult.fold(
                    onSuccess = { detail ->
                        val sortedCredits = detail.combinedCredits?.cast.orEmpty().sortedByYearDescending()
                        _actorUiState.value = SearchActorUiState.Success(
                            actors = actors,
                            selectedActor = firstActor,
                            actorDetail = detail,
                            credits = sortedCredits,
                            isLoadingCredits = false
                        )
                    },
                    onFailure = { e ->
                        _actorUiState.value = SearchActorUiState.Error(
                            UiText.DynamicString(e.localizedMessage ?: "Unknown error")
                        )
                    }
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _actorUiState.value = SearchActorUiState.Error(
                    UiText.DynamicString(e.localizedMessage ?: "Unknown error")
                )
            }
        }
    }

    /** Selects a specific actor from the horizontal Actor chips and loads their sorted filmography. */
    fun selectActor(actor: TmdbPersonResult) {
        val currentState = _actorUiState.value as? SearchActorUiState.Success ?: return
        if (currentState.selectedActor.id == actor.id && !currentState.isLoadingCredits) return

        clearLastClickedCreditKey()
        actorSearchJob?.cancel()
        actorSearchJob = viewModelScope.launch {
            _actorUiState.value = currentState.copy(
                selectedActor = actor,
                actorDetail = null,
                credits = emptyList(),
                isLoadingCredits = true
            )
            val detailResult = tmdbRepository.getActorDetails(actor.id)
            detailResult.fold(
                onSuccess = { detail ->
                    val sortedCredits = detail.combinedCredits?.cast.orEmpty().sortedByYearDescending()
                    _actorUiState.value = SearchActorUiState.Success(
                        actors = currentState.actors,
                        selectedActor = actor,
                        actorDetail = detail,
                        credits = sortedCredits,
                        isLoadingCredits = false
                    )
                },
                onFailure = { e ->
                    _actorUiState.value = SearchActorUiState.Error(
                        UiText.DynamicString(e.localizedMessage ?: "Unknown error")
                    )
                }
            )
        }
    }

    /** Performs a TV series search on TMDB, checking IMDb IDs first when provided. */
    fun searchTvSeries(query: String, force: Boolean = false) {
        if (query.isEmpty()) {
            _tvUiState.value = SearchTvUiState.Idle
            return
        }
        if (!force && query == lastQuery && _tvUiState.value is SearchTvUiState.Success) return

        isShowingFavorites = false
        isShowingDownloads = false
        isShowingNew = false
        lastQuery = query
        _searchQuery.value = query
        clearLastClickedSeriesId()
        tvSearchJob?.cancel()
        val generation = ++tvRequestGeneration
        isFetchingTv = true
        _isLoadingMore.value = false
        currentTvPage = 1
        totalTvPages = 1
        allTvResults.clear()
        seenTvSeriesIds.clear()

        tvSearchJob = viewModelScope.launch {
            _tvUiState.value = SearchTvUiState.Loading
            try {
                val imdbId = extractImdbId(query)
                if (imdbId != null) {
                    val tvSeries = tmdbRepository.findTvSeriesByImdbId(imdbId)
                    if (generation != tvRequestGeneration) return@launch
                    if (tvSeries != null) {
                        seenTvSeriesIds.add(tvSeries.id)
                        allTvResults.add(tvSeries)
                        totalTvPages = 1
                        currentTvPage = 2
                        _tvUiState.value = SearchTvUiState.Success(allTvResults.toList())
                        return@launch
                    }
                }

                val page = tmdbRepository.searchTvSeries(query, 1)
                if (generation != tvRequestGeneration) return@launch
                val results = page.results.orEmpty()
                val pageNum = if (page.page > 0) page.page else 1
                totalTvPages = page.totalPages.coerceAtLeast(pageNum)
                currentTvPage = pageNum + 1
                for (item in results) {
                    if (seenTvSeriesIds.add(item.id)) {
                        allTvResults.add(item)
                    }
                }
                if (allTvResults.isEmpty()) {
                    _tvUiState.value = SearchTvUiState.Empty
                } else {
                    _tvUiState.value = SearchTvUiState.Success(allTvResults.toList())
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (generation == tvRequestGeneration) {
                    _tvUiState.value = SearchTvUiState.Error(UiText.DynamicString(e.localizedMessage ?: "Unknown error"))
                }
            } finally {
                if (generation == tvRequestGeneration) {
                    isFetchingTv = false
                    _isLoadingMore.value = false
                }
            }
        }
    }

    /** Loads the next page of TMDB TV series for either text search or the popular/new TV feed. */
    fun loadMoreTvSeries() {
        if (isFetchingTv || currentTvPage > totalTvPages || isShowingDownloads) return
        val query = lastQuery
        if (!isShowingNew && query.isNullOrBlank()) return

        isFetchingTv = true
        _isLoadingMore.value = true
        val generation = tvRequestGeneration

        tvSearchJob = viewModelScope.launch {
            try {
                var addedInBatch = 0
                var pagesFetched = 0
                while (currentTvPage <= totalTvPages && addedInBatch == 0 && pagesFetched < 3) {
                    val requestedPage = currentTvPage
                    val page = if (isShowingNew) {
                        tmdbRepository.getHomeTvFeed(TmdbTvFeed.POPULAR, requestedPage)
                    } else {
                        tmdbRepository.searchTvSeries(query!!, requestedPage)
                    }
                    if (generation != tvRequestGeneration) return@launch
                    val pageNum = if (page.page > 0) page.page else requestedPage
                    totalTvPages = page.totalPages.coerceAtLeast(pageNum)
                    currentTvPage = pageNum + 1
                    for (item in page.results.orEmpty()) {
                        if (seenTvSeriesIds.add(item.id)) {
                            allTvResults.add(item)
                            addedInBatch++
                        }
                    }
                    pagesFetched++
                }
                if (allTvResults.isNotEmpty()) {
                    _tvUiState.value = SearchTvUiState.Success(allTvResults.toList())
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                if (generation == tvRequestGeneration) {
                    isFetchingTv = false
                    _isLoadingMore.value = false
                }
            }
        }
    }

    private fun extractImdbId(input: String): String? {
        val regex = Regex("tt\\d{7,10}", RegexOption.IGNORE_CASE)
        return regex.find(input)?.value
    }

    /** Loads TV series that have downloaded episodes for the user. */
    fun showDownloadedTvSeries(force: Boolean = false) {
        if (!force && isShowingDownloads && _tvUiState.value is SearchTvUiState.Success) return
        isShowingDownloads = true
        isShowingFavorites = false
        isShowingNew = false
        lastQuery = null
        tvSearchJob?.cancel()
        val generation = ++tvRequestGeneration
        isFetchingTv = false
        _isLoadingMore.value = false

        tvSearchJob = viewModelScope.launch {
            _tvUiState.value = SearchTvUiState.Loading
            userLibraryRepository.getDownloadedEpisodes().collect { downloads ->
                if (generation != tvRequestGeneration) return@collect
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
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
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
        if (!force && isShowingNew && _tvUiState.value is SearchTvUiState.Success) return
        isShowingNew = true
        isShowingDownloads = false
        isShowingFavorites = false
        lastQuery = null
        tvSearchJob?.cancel()
        val generation = ++tvRequestGeneration
        isFetchingTv = true
        _isLoadingMore.value = false
        currentTvPage = 1
        totalTvPages = 1
        allTvResults.clear()
        seenTvSeriesIds.clear()

        tvSearchJob = viewModelScope.launch {
            _tvUiState.value = SearchTvUiState.Loading
            try {
                val page = tmdbRepository.getHomeTvFeed(TmdbTvFeed.POPULAR, 1)
                if (generation != tvRequestGeneration) return@launch
                val results = page.results.orEmpty()
                val pageNum = if (page.page > 0) page.page else 1
                totalTvPages = page.totalPages.coerceAtLeast(pageNum)
                currentTvPage = pageNum + 1
                for (item in results) {
                    if (seenTvSeriesIds.add(item.id)) {
                        allTvResults.add(item)
                    }
                }
                if (allTvResults.isEmpty()) {
                    _tvUiState.value = SearchTvUiState.Empty
                } else {
                    _tvUiState.value = SearchTvUiState.Success(allTvResults.toList())
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (generation == tvRequestGeneration) {
                    _tvUiState.value = SearchTvUiState.Error(UiText.DynamicString(e.localizedMessage ?: "Unknown error"))
                }
            } finally {
                if (generation == tvRequestGeneration) {
                    isFetchingTv = false
                    _isLoadingMore.value = false
                }
            }
        }
    }

    /** Starts a text search, resetting the results when [query] is empty. */
    fun search(query: String) {
        debounceSearchJob?.cancel()
        _searchQuery.value = query
        if (query.isEmpty()) {
            resetSearch()
            return
        }
        if (_isActorMode.value) {
            searchActors(query)
            return
        }
        if (_isTvMode.value) {
            searchTvSeries(query)
            return
        }
        if (query == lastQuery && !isShowingFavorites && !isShowingDownloads && !isShowingNew && _uiState.value is SearchUiState.Success) return

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
        debounceSearchJob?.cancel()
        searchJob?.cancel()
        tvSearchJob?.cancel()
        actorSearchJob?.cancel()
        requestGeneration++
        tvRequestGeneration++
        isFetching = false
        isFetchingTv = false
        _isLoadingMore.value = false
        isShowingFavorites = false
        isShowingDownloads = false
        isShowingNew = false
        _searchQuery.value = ""
        lastQuery = null
        lastActorQuery = null
        lastGenre = null
        favoritesJob?.cancel()
        downloadsJob?.cancel()
        newMoviesJob?.cancel()
        allResults.clear()
        seenMovieIds.clear()
        allTvResults.clear()
        seenTvSeriesIds.clear()
        currentPage = 1
        currentTvPage = 1
        totalTvPages = 1
        canLoadMore = true
        clearLastClickedMovieId()
        clearLastClickedSeriesId()
        clearLastClickedCreditKey()
        clearFocusedMovie()
        _uiState.value = SearchUiState.Idle
        _tvUiState.value = SearchTvUiState.Idle
        _actorUiState.value = SearchActorUiState.Idle
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
        if (genre == lastGenre && !isShowingFavorites && !isShowingDownloads && !isShowingNew && _uiState.value is SearchUiState.Success) return

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

    /** Observes and filters the signed-in user's favorites without double-flicker or focus loss. */
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
            val currentSuccess = _uiState.value as? SearchUiState.Success
            if (currentSuccess?.isFavorites != true) {
                _uiState.value = SearchUiState.Loading
            }
            combine(
                ytsRepository.getFavoriteMovies(),
                userLibraryRepository.getFavoriteTvSeries(),
                watchHistoryFlow,
                _selectedFavoritesFilter,
                seriesDetailsCacheFlow
            ) { favorites, series, watchHistory, filter, detailsCache ->
                val watchMap = watchHistory.associateBy { it.mediaId }

                val missingSeriesIds = series.filter {
                    (it.numberOfEpisodes == null || it.numberOfEpisodes == 0) &&
                        !detailsCache.containsKey(it.id) &&
                        !attemptedSeriesDetailIds.contains(it.id)
                }.map { it.id }

                if (missingSeriesIds.isNotEmpty()) {
                    attemptedSeriesDetailIds.addAll(missingSeriesIds)
                    viewModelScope.launch {
                        val fetched = mutableMapOf<Int, TmdbTvSummary>()
                        missingSeriesIds.forEach { seriesId ->
                            try {
                                val details = tmdbRepository.getTvDetails(seriesId)
                                fetched[seriesId] = details
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                            }
                        }
                        if (fetched.isNotEmpty()) {
                            seriesDetailsCacheFlow.update { it + fetched }
                        }
                    }
                }

                val enrichedSeries = series.map { s ->
                    val cached = detailsCache[s.id]
                    if (cached != null) {
                        s.copy(
                            numberOfSeasons = cached.numberOfSeasons?.takeIf { it > 0 } ?: s.numberOfSeasons,
                            numberOfEpisodes = cached.numberOfEpisodes?.takeIf { it > 0 } ?: s.numberOfEpisodes
                        )
                    } else s
                }

                val (filteredMovies, filteredSeries) = when (filter) {
                    FavoritesFilter.ALL -> favorites to enrichedSeries
                    FavoritesFilter.MOVIES -> favorites to emptyList()
                    FavoritesFilter.TV_SERIES -> emptyList<Movie>() to enrichedSeries
                    FavoritesFilter.WATCHED -> {
                        favorites.filter { isMovieWatched(it, watchMap) } to enrichedSeries.filter {
                            isTvSeriesWatched(
                                it,
                                watchHistory,
                                detailsCache
                            )
                        }
                    }

                    FavoritesFilter.UNWATCHED -> {
                        favorites.filter {
                            isMovieUnwatched(
                                it,
                                watchMap
                            )
                        } to enrichedSeries.filter { isTvSeriesUnwatched(it, watchHistory) }
                    }

                    FavoritesFilter.IN_PROGRESS -> {
                        favorites.filter {
                            isMovieInProgress(
                                it,
                                watchMap
                            )
                        } to enrichedSeries.filter {
                            isTvSeriesInProgress(
                                it,
                                watchHistory,
                                detailsCache
                            )
                        }
                    }
                }

                val excludedLangs = preferenceManager.getFilteredLanguages()
                val finalMovies = MovieFilter.filterMovies(
                    filteredMovies,
                    excludedLangs,
                    _selectedQuality.value
                ).distinctBy { it.id }

                finalMovies to filteredSeries
            }.collect { (filteredMovies, filteredSeries) ->
                if (isShowingFavorites) {
                    _favoriteTvSeries.value = filteredSeries
                    allResults.clear()
                    seenMovieIds.clear()
                    for (movie in filteredMovies) {
                        if (seenMovieIds.add(movie.id)) {
                            allResults.add(movie)
                        }
                    }

                    if (allResults.isEmpty() && filteredSeries.isEmpty()) {
                        _uiState.value = SearchUiState.Empty
                    } else {
                        _uiState.value = SearchUiState.Success(
                            movies = allResults.toList(),
                            isFavorites = true,
                            favoriteSeries = filteredSeries
                        )
                    }
                }
            }
        }
    }

    private fun isMovieWatched(m: Movie, watchMap: Map<String, PlaybackProgress>): Boolean {
        val progress = watchMap[m.id.toString()] ?: watchMap[m.title]
        return progress != null && (progress.isCompleted || progress.progressPercent >= 95)
    }

    private fun isMovieInProgress(m: Movie, watchMap: Map<String, PlaybackProgress>): Boolean {
        val progress = watchMap[m.id.toString()] ?: watchMap[m.title]
        return progress != null && !progress.isCompleted && progress.progressPercent > 0 && progress.progressPercent < 95
    }

    private fun isMovieUnwatched(m: Movie, watchMap: Map<String, PlaybackProgress>): Boolean {
        val progress = watchMap[m.id.toString()] ?: watchMap[m.title]
        val percent = progress?.let { if (it.isCompleted) 100 else it.progressPercent } ?: 0
        return percent == 0
    }

    private fun getSeriesEpisodeProgresses(
        s: TmdbTvSummary,
        watchHistory: List<PlaybackProgress>
    ): List<PlaybackProgress> {
        val seriesIdStr = s.id.toString()
        return watchHistory.filter { item ->
            val isEpisodeMedia = item.isEpisode ||
                    item.mediaId.startsWith("media_") ||
                    (item.mediaId.contains("_s") && item.mediaId.contains("_e"))

            if (!isEpisodeMedia) return@filter false

            item.mediaId.startsWith("media_${seriesIdStr}_s") ||
                    item.mediaId.contains("_${seriesIdStr}_s") ||
                    item.mediaId.contains("_${seriesIdStr}_")
        }
    }

    private fun isTvSeriesInProgress(
        s: TmdbTvSummary,
        watchHistory: List<PlaybackProgress>,
        detailsCache: Map<Int, TmdbTvSummary>
    ): Boolean {
        val episodeProgresses = getSeriesEpisodeProgresses(s, watchHistory)
        val hasStartedEpisode = episodeProgresses.any {
            val pct = if (it.isCompleted) 100 else it.progressPercent
            pct > 0 || it.positionMs > 0
        }
        if (!hasStartedEpisode) return false

        val fullSeries = detailsCache[s.id] ?: s
        val totalEpisodes = fullSeries.numberOfEpisodes

        if (totalEpisodes == null || totalEpisodes <= 0) {
            return true
        }

        val completedCount = episodeProgresses.count { it.isCompleted || it.progressPercent >= 95 }
        return completedCount < totalEpisodes
    }

    private fun isTvSeriesWatched(
        s: TmdbTvSummary,
        watchHistory: List<PlaybackProgress>,
        detailsCache: Map<Int, TmdbTvSummary>
    ): Boolean {
        val episodeProgresses = getSeriesEpisodeProgresses(s, watchHistory)
        if (episodeProgresses.isEmpty()) return false

        val fullSeries = detailsCache[s.id] ?: s
        val totalEpisodes = fullSeries.numberOfEpisodes

        if (totalEpisodes == null || totalEpisodes <= 0) {
            return false
        }

        val completedCount = episodeProgresses.count { it.isCompleted || it.progressPercent >= 95 }
        return completedCount >= totalEpisodes
    }

    private fun isTvSeriesUnwatched(s: TmdbTvSummary, watchHistory: List<PlaybackProgress>): Boolean {
        val episodeProgresses = getSeriesEpisodeProgresses(s, watchHistory)
        if (episodeProgresses.isEmpty()) return true
        return episodeProgresses.none {
            val pct = if (it.isCompleted) 100 else it.progressPercent
            pct > 0 || it.positionMs > 0
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
                    seenMovieIds.clear()
                    for (movie in filtered) {
                        if (seenMovieIds.add(movie.id)) {
                            allResults.add(movie)
                        }
                    }

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
                        if (_focusedMovie.value == null && allResults.isNotEmpty()) {
                            onMovieFocused(allResults.first())
                        }
                    }
                }

                updateState()

                // Fetch missing metadata for older records in batches
                downloads.filter { it.movie == null }
                    .distinctBy { it.movieId }
                    .chunked(5)
                    .forEach { batch ->
                        coroutineScope {
                            batch.map { dl ->
                                launch {
                                    try {
                                        val details = ytsRepository.getMovieSummary(dl.movieId)
                                        details.data?.movie?.let { movie ->
                                            moviesMap[dl.movieId] = movie
                                        }
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                    }
                                }
                            }.joinAll()
                        }
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
                    seenMovieIds.clear()
                    for (movie in filtered) {
                        if (seenMovieIds.add(movie.id)) {
                            allResults.add(movie)
                        }
                    }

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
                        if (_focusedMovie.value == null && allResults.isNotEmpty()) {
                            onMovieFocused(allResults.first())
                        }
                    }
                }

                if (recentIds.isEmpty()) {
                    _uiState.value = SearchUiState.Empty
                    return@launch
                }

                // Optimization: Fetch all summaries in parallel but update state in batches
                recentIds.chunked(5).forEach { batchIds ->
                    coroutineScope {
                        batchIds.map { id ->
                            launch {
                                try {
                                    val details = ytsRepository.getMovieSummary(id)
                                    details.data?.movie?.let { movie ->
                                        moviesMap[id] = movie
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                }
                            }
                        }.joinAll()
                    }
                    updateState()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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

    /** Removes every selected movie or TV series from favorites and clears the selection. */
    fun deleteSelectedFavorites() {
        val idsToDelete = _selectedFavoriteIds.value
        if (idsToDelete.isEmpty()) return

        viewModelScope.launch {
            val moviesToDelete = allResults.filter { it.id in idsToDelete }
            moviesToDelete.forEach { movie ->
                ytsRepository.removeFavorite(movie)
            }
            val seriesToDelete = _favoriteTvSeries.value.filter { it.id in idsToDelete }
            seriesToDelete.forEach { series ->
                userLibraryRepository.removeFavoriteTvSeries(series)
            }
            clearSelection()
        }
    }

    /** Resets catalog pagination and starts loading from the first page. */
    private fun resetAndLoad() {
        allResults.clear()
        seenMovieIds.clear()
        currentPage = 1
        canLoadMore = true
        clearFocusedMovie()
        loadMore(force = true)
    }

    /** Loads catalog pages in batches until new filtered results are found or no pages remain. */
    fun loadMore(force: Boolean = false) {
        if (_isTvMode.value) {
            loadMoreTvSeries()
            return
        }
        if ((isFetching && !force) || !canLoadMore || isShowingFavorites || isShowingDownloads || isShowingNew) return

        val query = lastQuery
        val genre = lastGenre

        if (query == null && genre == null) return

        searchJob?.cancel()
        val generation = ++requestGeneration
        isFetching = true
        if (currentPage > 1) {
            _isLoadingMore.value = true
        } else {
            _isLoadingMore.value = false
        }
        searchJob = viewModelScope.launch {
            try {
                if (currentPage == 1) _uiState.value = SearchUiState.Loading

                val apiQuality = when (_selectedQuality.value) {
                    "1080p.x265" -> "1080p"
                    else -> _selectedQuality.value
                }

                val targetBatchSize = if (currentPage == 1) TARGET_INITIAL_BATCH else TARGET_LOAD_MORE_BATCH
                var addedInBatch = 0
                var pagesFetchedInBatch = 0
                var lastPageWasFull = true

                while (
                    canLoadMore &&
                    (addedInBatch == 0 || (lastPageWasFull && addedInBatch < targetBatchSize && pagesFetchedInBatch < MAX_PAGES_PER_BATCH))
                ) {
                    val result = when {
                        query != null -> ytsRepository.searchMovies(query, currentPage, apiQuality)
                        genre != null -> ytsRepository.searchByGenre(genre, currentPage, apiQuality)
                        else -> null
                    }
                    if (generation != requestGeneration) return@launch

                    val moviesFromApi = result?.data?.movies

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
                        _selectedQuality.value,
                        minimumRating = if (query == null) {
                            preferenceManager.getMinimumRating()
                        } else {
                            null
                        }
                    )

                    for (movie in filteredMovies) {
                        if (seenMovieIds.add(movie.id)) {
                            allResults.add(movie)
                            addedInBatch++
                        }
                    }
                }

                if (addedInBatch > 0) {
                    _uiState.value = SearchUiState.Success(allResults.toList(), genre = lastGenre)
                    if (_focusedMovie.value == null && allResults.isNotEmpty()) {
                        onMovieFocused(allResults.first())
                    }
                } else if (allResults.isEmpty() && !canLoadMore) {
                    _uiState.value = SearchUiState.Empty
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (generation == requestGeneration && allResults.isEmpty()) {
                    _uiState.value = SearchUiState.Error(UiText.DynamicString(e.localizedMessage ?: "Unknown error"))
                }
            } finally {
                if (generation == requestGeneration) {
                    isFetching = false
                    _isLoadingMore.value = false
                }
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
     * @property favoriteSeries Favorite TV series atomically paired with [movies] when [isFavorites] is true.
     */
    data class Success(
        val movies: List<Movie>,
        val isFavorites: Boolean = false,
        val isDownloads: Boolean = false,
        val isNew: Boolean = false,
        val genre: String? = null,
        val favoriteSeries: List<TmdbTvSummary> = emptyList()
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

/** States emitted while searching TMDB actors and displaying their year-descending filmography. */
sealed interface SearchActorUiState {
    /** No actor search is currently active. */
    data object Idle : SearchActorUiState

    /** Actor search results are being loaded. */
    data object Loading : SearchActorUiState

    /** No actors matched the query. */
    data object Empty : SearchActorUiState

    /**
     * Actor search succeeded and a selected actor's filmography is loaded or loading.
     *
     * @property actors Matching TMDB people for the query.
     * @property selectedActor Currently selected actor whose filmography is displayed.
     * @property actorDetail Full actor detail including biography and personal metadata, when loaded.
     * @property credits Combined movie and TV cast credits sorted by year descending.
     * @property isLoadingCredits Whether the selected actor's credits are currently loading.
     */
    data class Success(
        val actors: List<TmdbPersonResult>,
        val selectedActor: TmdbPersonResult,
        val actorDetail: TmdbActorDetail?,
        val credits: List<TmdbCastCredit>,
        val isLoadingCredits: Boolean = false
    ) : SearchActorUiState

    /**
     * Actor search or credit lookup failed.
     *
     * @property message User-facing failure description.
     */
    data class Error(val message: UiText) : SearchActorUiState
}
