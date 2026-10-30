package com.martinrevert.latorrentola.ui.ott

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.TMDB.TmdbWatchProvider
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state for the OTT streaming providers screen. */
sealed interface OttProvidersUiState {
    data object Loading : OttProvidersUiState
    data class Success(val providers: List<TmdbWatchProvider>) : OttProvidersUiState
    data class Error(val message: UiText) : OttProvidersUiState
}

/**
 * Loads the dynamic list of TMDB watch providers for Movies or TV series and tracks focus state.
 *
 * @property tmdbRepository TMDB API coordinator.
 */
@HiltViewModel
class OttProvidersViewModel @Inject constructor(
    private val tmdbRepository: TmdbRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OttProvidersUiState>(OttProvidersUiState.Loading)
    val uiState: StateFlow<OttProvidersUiState> = _uiState.asStateFlow()

    private val _lastClickedProviderId = MutableStateFlow<Int?>(null)
    val lastClickedProviderId: StateFlow<Int?> = _lastClickedProviderId.asStateFlow()

    private var loadedMode: Boolean? = null
    private var fetchJob: Job? = null

    fun loadProviders(isTvMode: Boolean) {
        if (loadedMode == isTvMode && _uiState.value is OttProvidersUiState.Success) return
        loadedMode = isTvMode
        _uiState.value = OttProvidersUiState.Loading
        fetchJob?.cancel()

        fetchJob = viewModelScope.launch {
            try {
                val providers = if (isTvMode) {
                    tmdbRepository.getTvWatchProviders()
                } else {
                    tmdbRepository.getMovieWatchProviders()
                }
                _uiState.value = OttProvidersUiState.Success(providers)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = OttProvidersUiState.Error(
                    e.localizedMessage?.takeIf(String::isNotBlank)?.let { UiText.DynamicString(it) }
                        ?: UiText.StringResource(R.string.network_error)
                )
            }
        }
    }

    fun setLastClickedProviderId(id: Int?) {
        _lastClickedProviderId.value = id
    }

    fun clearLastClickedProviderId() {
        _lastClickedProviderId.value = null
    }
}
