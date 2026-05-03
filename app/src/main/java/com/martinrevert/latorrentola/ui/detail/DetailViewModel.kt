package com.martinrevert.latorrentola.ui.detail

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
class DetailViewModel @Inject constructor(
    private val ytsRepository: YtsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun setMovie(movie: Movie) {
        viewModelScope.launch {
            val isFavorite = ytsRepository.isFavorite(movie.id)
            _uiState.value = DetailUiState.Success(movie, isFavorite)
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
}

sealed interface DetailUiState {
    object Loading : DetailUiState
    data class Success(val movie: Movie, val isFavorite: Boolean) : DetailUiState
    data class Error(val message: String) : DetailUiState
}
