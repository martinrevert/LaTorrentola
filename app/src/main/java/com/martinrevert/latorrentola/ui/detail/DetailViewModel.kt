package com.martinrevert.latorrentola.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.model.user.DownloadedMovie
import com.martinrevert.latorrentola.model.TMDB.TmdbActorDetail
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.network.YtsRepository
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.utils.TranslationManager
import com.martinrevert.latorrentola.utils.UiText
import com.martinrevert.latorrentola.utils.VoiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Loads movie and actor details and coordinates favorites, downloads, and spoken summaries.
 *
 * @property ytsRepository retrieves YTS movie details and records genre visits.
 * @property userLibraryRepository observes downloads and stores new download records.
 * @property tmdbRepository retrieves actor information and movie IMDb IDs.
 * @property voiceManager speaks movie titles and summaries.
 * @property translationManager translates summaries before speech when requested.
 * @property preferenceManager supplies voice and language-filter settings.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val ytsRepository: YtsRepository,
    private val userLibraryRepository: UserLibraryRepository,
    private val tmdbRepository: TmdbRepository,
    private val voiceManager: VoiceManager,
    private val translationManager: TranslationManager,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    /** Mutable backing state for movie detail content. */
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    /** Current movie detail state. */
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    /** Torrent handling mode selected in Settings. */
    val torrentHandlingMode: StateFlow<TorrentHandlingMode> =
        preferenceManager.torrentHandlingModeFlow

    /** Mutable backing state for the selected TMDB actor lookup. */
    private val _selectedActorDetail = MutableStateFlow<Result<TmdbActorDetail>?>(null)
    /** Result for the selected actor, or `null` when no actor is selected. */
    val selectedActorDetail: StateFlow<Result<TmdbActorDetail>?> = _selectedActorDetail.asStateFlow()

    /** Mutable backing state for actor lookup progress. */
    private val _isActorLoading = MutableStateFlow(false)
    /** Whether actor details are currently being fetched. */
    val isActorLoading: StateFlow<Boolean> = _isActorLoading.asStateFlow()

    /** Looks up [actorName] and publishes the resulting actor detail or failure. */
    fun fetchActorDetails(actorName: String) {
        viewModelScope.launch {
            _isActorLoading.value = true
            _selectedActorDetail.value = null
            val result = tmdbRepository.getActorDetails(actorName)
            _selectedActorDetail.value = result
            _isActorLoading.value = false
        }
    }

    /** Clears the selected actor result and ends actor loading. */
    fun clearSelectedActor() {
        _selectedActorDetail.value = null
        _isActorLoading.value = false
    }

    /** Torrent hashes already recorded as downloaded by the user. */
    val downloadedHashes: StateFlow<Set<String>> = userLibraryRepository.getDownloadedMovies()
        .map { it.map { download -> download.hash }.toSet() }
        .catch { emit(emptySet()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    /** User's in-progress watch history items mapped by media ID. */
    val watchHistoryMap: StateFlow<Map<String, PlaybackProgress>> = userLibraryRepository.getWatchHistory()
        .map { list -> list.associateBy { it.mediaId } }
        .catch { emit(emptyMap()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    /** Current speech/summary job, canceled when another movie is selected. */
    private var voiceJob: Job? = null

    /** Shows [movie] immediately, then refreshes its details and records genre visits. */
    fun setMovie(movie: Movie) {
        viewModelScope.launch {
            // Optimization: Show passed movie immediately to avoid blank loading screen
            // We check favorite status quickly and show what we have
            val isFavorite = ytsRepository.isFavorite(movie.id)
            _uiState.value = DetailUiState.Success(movie, isFavorite)

            try {
                // Fetch full details (cast, images, etc.) in the background
                val fullDetailsResponse = ytsRepository.getMovieFullDetails(movie.id)
                var fullMovie = fullDetailsResponse.data?.movie ?: movie

                // Fetch richer TMDB cast if IMDb code is available
                val imdbId = fullMovie.imdbCode ?: movie.imdbCode
                if (!imdbId.isNullOrBlank()) {
                    val tmdbCast = tmdbRepository.getMovieCastByImdbId(imdbId)
                    if (tmdbCast.isNotEmpty()) {
                        fullMovie = fullMovie.copy(cast = tmdbCast)
                    }
                }

                _uiState.value = DetailUiState.Success(fullMovie, isFavorite)
                
                // Clear any existing voice job before starting a new one
                voiceJob?.cancel()
                voiceJob = viewModelScope.launch {
                    handleVoice(fullMovie)
                }
                
                // Record genre visits for personalization
                fullMovie.genres?.forEach { genre ->
                    ytsRepository.recordGenreVisit(genre)
                }
            } catch (e: Exception) {
                val imdbId = movie.imdbCode
                val movieWithCast = if (!imdbId.isNullOrBlank()) {
                    val tmdbCast = tmdbRepository.getMovieCastByImdbId(imdbId)
                    if (tmdbCast.isNotEmpty()) movie.copy(cast = tmdbCast) else movie
                } else movie

                val isFavorite = ytsRepository.isFavorite(movie.id)
                _uiState.value = DetailUiState.Success(movieWithCast, isFavorite)
                
                voiceJob?.cancel()
                voiceJob = viewModelScope.launch {
                    handleVoice(movieWithCast)
                }
            }
        }
    }

    /** Loads a movie by its YTS identifier and publishes loading, success, or error state. */
    fun setMovieById(movieId: Int) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                val isFavorite = ytsRepository.isFavorite(movieId)
                val fullDetailsResponse = ytsRepository.getMovieFullDetails(movieId)
                fullDetailsResponse.data?.movie?.let { movie ->
                    var fullMovie = movie
                    val imdbId = fullMovie.imdbCode
                    if (!imdbId.isNullOrBlank()) {
                        val tmdbCast = tmdbRepository.getMovieCastByImdbId(imdbId)
                        if (tmdbCast.isNotEmpty()) {
                            fullMovie = fullMovie.copy(cast = tmdbCast)
                        }
                    }

                    _uiState.value = DetailUiState.Success(fullMovie, isFavorite)
                    
                    voiceJob?.cancel()
                    voiceJob = viewModelScope.launch {
                        handleVoice(fullMovie)
                    }
                    
                    fullMovie.genres?.forEach { genre ->
                        ytsRepository.recordGenreVisit(genre)
                    }
                } ?: run {
                    _uiState.value = DetailUiState.Error(UiText.StringResource(R.string.movie_not_found))
                }
            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error(UiText.DynamicString(e.localizedMessage ?: "Unknown error"))
            }
        }
    }

    /** Searches for [query] and opens the first matching movie. */
    fun setMovieByQuery(query: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                val searchResponse = ytsRepository.searchMovies(query, 1)
                val movie = searchResponse.data?.movies?.firstOrNull()
                if (movie != null) {
                    setMovie(movie)
                } else {
                    _uiState.value = DetailUiState.Error(UiText.StringResource(R.string.movie_not_found_query, query))
                }
            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error(UiText.DynamicString(e.localizedMessage ?: "Unknown error"))
            }
        }
    }

    /** Resolves a TMDB movie to an IMDb ID when possible and searches YTS using that ID. */
    fun fetchAndOpenMovieByTmdbId(tmdbMovieId: Int, fallbackTitle: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            val imdbId = tmdbRepository.getMovieImdbId(tmdbMovieId)
            val searchQuery = imdbId ?: fallbackTitle
            setMovieByQuery(searchQuery)
        }
    }

    /** Speaks the movie title and optionally a translated or original summary. */
    private suspend fun handleVoice(movie: Movie) {
        if (!preferenceManager.getVoiceSystem()) return

        val title = movie.title ?: ""
        val summary = movie.summary?.ifEmpty { movie.descriptionFull } ?: movie.descriptionFull ?: ""
        val useTranslation = preferenceManager.getVoiceTranslation()
        
        // Use Locale.US for a clearer English accent
        val englishLocale = Locale.US
        val spanishLocale = Locale.forLanguageTag("es-ES")

        // Always read the title in English (NEVER translated)
        if (title.isNotEmpty()) {
            voiceManager.speak(title, englishLocale)
        }

        // Wait 3 seconds before reading the summary
        delay(3.seconds)

        // Read summary if enabled
        if (preferenceManager.getVoiceSummary() && summary.isNotEmpty()) {
            if (useTranslation) {
                translationManager.translate(
                    text = summary,
                    onSuccess = { translatedText ->
                        voiceManager.speak(translatedText, spanishLocale)
                    },
                    onError = {
                        // Fallback to English accent if translation fails
                        voiceManager.speak(summary, englishLocale)
                    }
                )
            } else {
                // EXPLICITLY use English Locale for the original summary
                voiceManager.speak(summary, englishLocale)
            }
        }
    }

    /** Adds [movie] to favorites or removes it if already saved. */
    fun toggleFavorite(movie: Movie) {
        viewModelScope.launch {
            if (ytsRepository.isFavorite(movie.id)) {
                ytsRepository.removeFavorite(movie)
                _uiState.value = (uiState.value as? DetailUiState.Success)?.copy(isFavorite = false) ?: uiState.value
            } else {
                ytsRepository.addFavorite(movie)
                _uiState.value = (uiState.value as? DetailUiState.Success)?.copy(isFavorite = true) ?: uiState.value
            }
        }
    }

    /** Cancels pending speech and stops the speech engine. */
    fun stopVoice() {
        voiceJob?.cancel()
        voiceManager.stop()
    }

    /** Saves [movie] as downloaded using the selected torrent hash and quality. */
    fun markAsDownloaded(movie: Movie, torrentHash: String, quality: String) {
        viewModelScope.launch {
            userLibraryRepository.markAsDownloaded(
                DownloadedMovie(
                    movieId = movie.id,
                    movieTitle = movie.title ?: "",
                    quality = quality,
                    hash = torrentHash,
                    timestamp = System.currentTimeMillis(),
                    movie = movie
                )
            )
        }
    }

    /** Adds [language] to the excluded-language preference and syncs it to Firestore. */
    fun addLanguageToFilter(language: String, onError: (UiText) -> Unit) {
        val currentFilters = preferenceManager.getFilteredLanguages()
        val languageLower = language.lowercase()
        val filterList = currentFilters.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toMutableList()
        
        if (!filterList.contains(languageLower)) {
            filterList.add(languageLower)
            val newFilters = filterList.joinToString(", ")
            
            viewModelScope.launch {
                // Store original state for rollback
                val originalFilters = currentFilters
                
                // Update local first
                preferenceManager.setFilteredLanguages(newFilters)
                
                try {
                    userLibraryRepository.saveFilteredLanguages(newFilters)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Rollback
                    preferenceManager.setFilteredLanguages(originalFilters)
                    onError(UiText.StringResource(R.string.error_sync_firestore))
                }
            }
        }
    }

    /** Cancels ongoing speech when this view model is cleared. */
    override fun onCleared() {
        voiceJob?.cancel()
        voiceManager.stop()
    }
}

/** Possible loading, success, and failure states for movie details. */
sealed interface DetailUiState {
    /** Movie details are being loaded. */
    object Loading : DetailUiState
    /**
     * Movie details are available.
     *
     * @property movie Loaded movie metadata.
     * @property isFavorite Whether the movie is in the user's favorites.
     */
    data class Success(val movie: Movie, val isFavorite: Boolean) : DetailUiState
    /**
     * Movie detail lookup failed.
     *
     * @property message User-facing failure description.
     */
    data class Error(val message: UiText) : DetailUiState
}
