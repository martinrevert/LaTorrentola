package com.martinrevert.latorrentola.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.network.YtsRepository
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.TranslationManager
import com.martinrevert.latorrentola.utils.VoiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val ytsRepository: YtsRepository,
    private val voiceManager: VoiceManager,
    private val translationManager: TranslationManager,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun setMovie(movie: Movie) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                val isFavorite = ytsRepository.isFavorite(movie.id)
                val fullDetailsResponse = ytsRepository.getMovieFullDetails(movie.id)
                val fullMovie = fullDetailsResponse.data?.movie ?: movie
                _uiState.value = DetailUiState.Success(fullMovie, isFavorite)
                handleVoice(fullMovie)
                
                // Record genre visits for personalization
                fullMovie.genres?.forEach { genre ->
                    ytsRepository.recordGenreVisit(genre)
                }
            } catch (e: Exception) {
                val isFavorite = ytsRepository.isFavorite(movie.id)
                _uiState.value = DetailUiState.Success(movie, isFavorite)
                handleVoice(movie)
            }
        }
    }

    private fun handleVoice(movie: Movie) {
        if (!preferenceManager.getVoiceSystem()) return

        val title = movie.title ?: ""
        val summary = movie.summary?.ifEmpty { movie.descriptionFull } ?: movie.descriptionFull ?: ""

        // Always read the title
        if (title.isNotEmpty()) {
            voiceManager.speak(title)
        }

        // Read summary if enabled
        if (preferenceManager.getVoiceSummary() && summary.isNotEmpty()) {
            if (preferenceManager.getVoiceTranslation()) {
                translationManager.translate(
                    text = summary,
                    onSuccess = { translatedText ->
                        voiceManager.speak(translatedText)
                    },
                    onError = {
                        // Fallback to English if translation fails
                        voiceManager.speak(summary)
                    }
                )
            } else {
                voiceManager.speak(summary)
            }
        }
    }

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

    override fun onCleared() {
        super.onCleared()
        voiceManager.stop()
    }
}

sealed interface DetailUiState {
    object Loading : DetailUiState
    data class Success(val movie: Movie, val isFavorite: Boolean) : DetailUiState
    data class Error(val message: String) : DetailUiState
}
