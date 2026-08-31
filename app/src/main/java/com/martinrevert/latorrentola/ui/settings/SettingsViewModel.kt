package com.martinrevert.latorrentola.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.network.FirebaseMessagingInitializer
import com.martinrevert.latorrentola.utils.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val firebaseMessagingInitializer: FirebaseMessagingInitializer,
    private val userLibraryRepository: com.martinrevert.latorrentola.network.UserLibraryRepository,
    private val authRepository: com.martinrevert.latorrentola.network.AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    private var syncJob: kotlinx.coroutines.Job? = null

    init {
        val localFiltered = preferenceManager.getFilteredLanguages()
        _uiState.value = SettingsUiState(
            voiceSystem = preferenceManager.getVoiceSystem(),
            voiceSummary = preferenceManager.getVoiceSummary(),
            voiceTranslation = preferenceManager.getVoiceTranslation(),
            vibrator = preferenceManager.getVibrator(),
            pushEnabled = preferenceManager.isPushEnabled(),
            theme = preferenceManager.getTheme(),
            filteredLanguages = localFiltered
        )
        syncSettings()
        observeSettingsChanges()
    }

    private fun observeSettingsChanges() {
        viewModelScope.launch {
            preferenceManager.filteredLanguagesFlow.collect { languages ->
                if (_uiState.value.filteredLanguages != languages) {
                    _uiState.value = _uiState.value.copy(filteredLanguages = languages)
                }
            }
        }
    }

    private fun syncSettings() {
        viewModelScope.launch {
            authRepository.authStateFlow
                .filterNotNull()
                .flatMapLatest { user ->
                    userLibraryRepository.observeRemoteFilteredLanguages(user.uid)
                }
                .collect { remoteFiltered ->
                    if (remoteFiltered != null && remoteFiltered != preferenceManager.getFilteredLanguages()) {
                        preferenceManager.setFilteredLanguages(remoteFiltered)
                        _uiState.value = _uiState.value.copy(filteredLanguages = remoteFiltered)
                    }
                }
        }
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

    fun togglePushEnabled(enabled: Boolean) {
        preferenceManager.setPushEnabled(enabled)
        _uiState.value = _uiState.value.copy(pushEnabled = enabled)
        firebaseMessagingInitializer.syncTopicSubscription(enabled)
    }

    fun setTheme(theme: Int) {
        preferenceManager.setTheme(theme)
        _uiState.value = _uiState.value.copy(theme = theme)
    }

    fun setFilteredLanguages(languages: String) {
        preferenceManager.setFilteredLanguages(languages)
        _uiState.value = _uiState.value.copy(filteredLanguages = languages)
        
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1000) // Debounce 1 second
            userLibraryRepository.saveFilteredLanguages(languages)
        }
    }
}

data class SettingsUiState(
    val voiceSystem: Boolean = true,
    val voiceSummary: Boolean = true,
    val voiceTranslation: Boolean = false,
    val vibrator: Boolean = false,
    val pushEnabled: Boolean = true,
    val theme: Int = PreferenceManager.THEME_SYSTEM,
    val filteredLanguages: String = ""
)
