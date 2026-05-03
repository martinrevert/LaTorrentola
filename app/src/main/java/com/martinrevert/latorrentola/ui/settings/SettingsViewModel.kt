package com.martinrevert.latorrentola.ui.settings

import androidx.lifecycle.ViewModel
import com.martinrevert.latorrentola.utils.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = SettingsUiState(
            voiceSystem = preferenceManager.getVoiceSystem(),
            voiceSummary = preferenceManager.getVoiceSummary(),
            voiceTranslation = preferenceManager.getVoiceTranslation(),
            vibrator = preferenceManager.getVibrator()
        )
    }

    fun toggleVoiceSystem(enabled: Boolean) {
        preferenceManager.setVoiceSystem(enabled)
        _uiState.value = _uiState.value.copy(voiceSystem = enabled)
    }

    fun toggleVoiceSummary(enabled: Boolean) {
        preferenceManager.setVoiceSummary(enabled)
        _uiState.value = _uiState.value.copy(voiceSummary = enabled)
    }

    fun toggleVoiceTranslation(enabled: Boolean) {
        preferenceManager.setVoiceTranslation(enabled)
        _uiState.value = _uiState.value.copy(voiceTranslation = enabled)
    }

    fun toggleVibrator(enabled: Boolean) {
        preferenceManager.setVibrator(enabled)
        _uiState.value = _uiState.value.copy(vibrator = enabled)
    }
}

data class SettingsUiState(
    val voiceSystem: Boolean = true,
    val voiceSummary: Boolean = true,
    val voiceTranslation: Boolean = false,
    val vibrator: Boolean = false
)
