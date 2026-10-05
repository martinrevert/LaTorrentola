package com.martinrevert.latorrentola.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.network.FirebaseMessagingInitializer
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.utils.AutoPlayQualitySelectionMethod
import com.martinrevert.latorrentola.utils.OpenSubtitlesCredentialStore
import com.martinrevert.latorrentola.utils.OpenSubtitlesCredentials
import com.martinrevert.latorrentola.utils.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Maintains settings UI state and synchronizes account settings with Firebase.
 *
 * @property preferenceManager reads and persists local settings.
 * @property openSubtitlesCredentialStore securely persists subtitle service credentials.
 * @property firebaseMessagingInitializer applies push subscription changes.
 * @property userLibraryRepository observes and updates cloud-saved preferences.
 * @property authRepository exposes signed-in account changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val openSubtitlesCredentialStore: OpenSubtitlesCredentialStore,
    private val firebaseMessagingInitializer: FirebaseMessagingInitializer,
    private val userLibraryRepository: com.martinrevert.latorrentola.network.UserLibraryRepository,
    private val authRepository: com.martinrevert.latorrentola.network.AuthRepository
) : ViewModel() {

    /** Mutable backing state for settings displayed by the screen. */
    private val _uiState = MutableStateFlow(SettingsUiState())
    /** Current settings values observed by the UI. */
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    /** Debounced remote language-filter save job. */
    private var syncJob: kotlinx.coroutines.Job? = null
    /** Debounced remote minimum-rating save job. */
    private var ratingSyncJob: kotlinx.coroutines.Job? = null

    /** Loads local preferences, starts remote synchronization, and observes preference changes. */
    init {
        val localFiltered = preferenceManager.getFilteredLanguages()
        val openSubtitlesCredentials = openSubtitlesCredentialStore.load()
        _uiState.value = SettingsUiState(
            voiceSystem = preferenceManager.getVoiceSystem(),
            voiceSummary = preferenceManager.getVoiceSummary(),
            voiceTranslation = preferenceManager.getVoiceTranslation(),
            vibrator = preferenceManager.getVibrator(),
            pushEnabled = preferenceManager.isPushEnabled(),
            theme = preferenceManager.getTheme(),
            filteredLanguages = localFiltered,
            minimumRating = preferenceManager.getMinimumRating(),
            torrentHandlingMode = preferenceManager.getTorrentHandlingMode(),
            autoPlayQualitySelectionMethod = preferenceManager.getAutoPlayQualitySelectionMethod(),
            openSubtitlesUsername = openSubtitlesCredentials?.username.orEmpty(),
            openSubtitlesCredentialsConfigured = openSubtitlesCredentials != null
        )
        syncSettings()
        observeSettingsChanges()
    }

    /** Mirrors changes from reactive preferences into [uiState]. */
    private fun observeSettingsChanges() {
        viewModelScope.launch {
            preferenceManager.filteredLanguagesFlow.collect { languages ->
                if (_uiState.value.filteredLanguages != languages) {
                    _uiState.value = _uiState.value.copy(filteredLanguages = languages)
                }
            }
        }
        viewModelScope.launch {
            preferenceManager.minimumRatingFlow.collect { rating ->
                if (_uiState.value.minimumRating != rating) {
                    _uiState.value = _uiState.value.copy(minimumRating = rating)
                }
            }
        }
        viewModelScope.launch {
            preferenceManager.autoPlayQualitySelectionFlow.collect { method ->
                if (_uiState.value.autoPlayQualitySelectionMethod != method) {
                    _uiState.value = _uiState.value.copy(autoPlayQualitySelectionMethod = method)
                }
            }
        }
    }

    /** Observes authenticated account settings and applies remote changes locally. */
    private fun syncSettings() {
        viewModelScope.launch {
            authRepository.authStateFlow
                .filterNotNull()
                .flatMapLatest { user ->
                    combine(
                        userLibraryRepository.observeRemoteFilteredLanguages(user.uid),
                        userLibraryRepository.observeRemoteMinimumRating(user.uid)
                    ) { languages, rating -> languages to rating }
                }
                .collect { (remoteLanguages, remoteRating) ->
                    if (remoteLanguages != null &&
                        remoteLanguages != preferenceManager.getFilteredLanguages()
                    ) {
                        preferenceManager.setFilteredLanguages(remoteLanguages)
                        _uiState.value = _uiState.value.copy(filteredLanguages = remoteLanguages)
                    }
                    if (remoteRating != preferenceManager.getMinimumRating()) {
                        preferenceManager.setMinimumRating(remoteRating)
                    }
                }
        }
    }

    /** Updates and persists general voice guidance preference. */
    fun toggleVoiceSystem(enabled: Boolean) {
        preferenceManager.setVoiceSystem(enabled)
        _uiState.value = _uiState.value.copy(voiceSystem = enabled)
    }

    /** Updates and persists spoken movie-summary preference. */
    fun toggleVoiceSummary(enabled: Boolean) {
        preferenceManager.setVoiceSummary(enabled)
        _uiState.value = _uiState.value.copy(voiceSummary = enabled)
    }

    /** Updates and persists spoken translation preference. */
    fun toggleVoiceTranslation(enabled: Boolean) {
        preferenceManager.setVoiceTranslation(enabled)
        _uiState.value = _uiState.value.copy(voiceTranslation = enabled)
    }

    /** Updates and persists haptic feedback preference. */
    fun toggleVibrator(enabled: Boolean) {
        preferenceManager.setVibrator(enabled)
        _uiState.value = _uiState.value.copy(vibrator = enabled)
    }

    /** Updates push preference and synchronizes the FCM topic subscription. */
    fun togglePushEnabled(enabled: Boolean) {
        preferenceManager.setPushEnabled(enabled)
        _uiState.value = _uiState.value.copy(pushEnabled = enabled)
        firebaseMessagingInitializer.syncTopicSubscription(enabled)
    }

    /** Updates and persists the selected theme mode. */
    fun setTheme(theme: Int) {
        preferenceManager.setTheme(theme)
        _uiState.value = _uiState.value.copy(theme = theme)
    }

    /** Updates the language filter locally and debounces saving it to Firestore. */
    fun setFilteredLanguages(languages: String) {
        preferenceManager.setFilteredLanguages(languages)
        _uiState.value = _uiState.value.copy(filteredLanguages = languages)
        
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1000) // Debounce 1 second
            userLibraryRepository.saveFilteredLanguages(languages)
        }
    }

    /** Clamps, updates, and debounces saving the minimum rating to Firestore. */
    fun setMinimumRating(rating: Float) {
        val boundedRating = (Math.round(rating * 10.0f) / 10.0f).coerceIn(
            PreferenceManager.MINIMUM_RATING_MIN,
            PreferenceManager.MINIMUM_RATING_MAX
        )
        preferenceManager.setMinimumRating(boundedRating)
        _uiState.value = _uiState.value.copy(minimumRating = boundedRating)

        ratingSyncJob?.cancel()
        ratingSyncJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            userLibraryRepository.saveMinimumRating(boundedRating)
        }
    }

    /**
     * Updates and persists the torrent handling mode.
     *
     * @param mode Selected external, local-playback, or Cast mode.
     */
    fun setTorrentHandlingMode(mode: TorrentHandlingMode) {
        preferenceManager.setTorrentHandlingMode(mode)
        _uiState.value = _uiState.value.copy(torrentHandlingMode = mode)
    }

    /**
     * Updates and persists the auto-play quality selection method.
     *
     * @param method Selected method for choosing quality when auto-playing next episode.
     */
    fun setAutoPlayQualitySelectionMethod(method: AutoPlayQualitySelectionMethod) {
        preferenceManager.setAutoPlayQualitySelectionMethod(method)
        _uiState.value = _uiState.value.copy(autoPlayQualitySelectionMethod = method)
    }

    /**
     * Encrypts and stores the user's OpenSubtitles login.
     *
     * @param username Account username.
     * @param password Account password.
     */
    fun saveOpenSubtitlesCredentials(username: String, password: String) {
        openSubtitlesCredentialStore.save(OpenSubtitlesCredentials(username, password))
        _uiState.value = _uiState.value.copy(
            openSubtitlesUsername = username,
            openSubtitlesCredentialsConfigured = true
        )
    }

    /** Removes the user's saved OpenSubtitles login. */
    fun clearOpenSubtitlesCredentials() {
        openSubtitlesCredentialStore.clear()
        _uiState.value = _uiState.value.copy(
            openSubtitlesUsername = "",
            openSubtitlesCredentialsConfigured = false
        )
    }

}

/**
 * User-configurable preferences currently presented by the settings screen.
 *
 * @property voiceSystem Whether general voice guidance is enabled.
 * @property voiceSummary Whether movie summaries are spoken.
 * @property voiceTranslation Whether summaries are translated before speech.
 * @property vibrator Whether haptic feedback is enabled.
 * @property pushEnabled Whether push notifications are enabled.
 * @property theme Selected theme mode.
 * @property filteredLanguages Comma-separated language codes excluded from movie lists.
 * @property minimumRating Minimum movie rating used for filtering.
 * @property torrentHandlingMode How selected torrents are handed off or played.
 * @property autoPlayQualitySelectionMethod How quality is selected when auto-playing next episode.
 * @property openSubtitlesUsername Username stored for OpenSubtitles authentication.
 * @property openSubtitlesCredentialsConfigured Whether both OpenSubtitles credentials are saved.
 */
data class SettingsUiState(
    val voiceSystem: Boolean = true,
    val voiceSummary: Boolean = true,
    val voiceTranslation: Boolean = false,
    val vibrator: Boolean = false,
    val pushEnabled: Boolean = true,
    val theme: Int = PreferenceManager.THEME_SYSTEM,
    val filteredLanguages: String = "",
    val minimumRating: Float = PreferenceManager.DEFAULT_MINIMUM_RATING,
    val torrentHandlingMode: TorrentHandlingMode = TorrentHandlingMode.EXTERNAL_CLIENT,
    val autoPlayQualitySelectionMethod: AutoPlayQualitySelectionMethod = AutoPlayQualitySelectionMethod.OFF,
    val openSubtitlesUsername: String = "",
    val openSubtitlesCredentialsConfigured: Boolean = false
)