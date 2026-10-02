package com.martinrevert.latorrentola.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and persists user preferences, exposing reactive flows for UI-observed settings. */
@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext context: Context
) {
    /** Private on-device preference storage. */
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("latorrentola_prefs", Context.MODE_PRIVATE)

    /** Mutable backing stream for the selected theme. */
    private val _themeFlow = MutableStateFlow(getTheme())
    /** Observable selected theme mode. */
    val themeFlow: StateFlow<Int> = _themeFlow.asStateFlow()

    /** Mutable backing stream for excluded movie languages. */
    private val _filteredLanguagesFlow = MutableStateFlow(getFilteredLanguages())
    /** Observable comma-separated excluded language codes. */
    val filteredLanguagesFlow: StateFlow<String> = _filteredLanguagesFlow.asStateFlow()

    /** Mutable backing stream for the minimum movie rating. */
    private val _minimumRatingFlow = MutableStateFlow(getMinimumRating())
    /** Observable minimum movie rating. */
    val minimumRatingFlow: StateFlow<Float> = _minimumRatingFlow.asStateFlow()

    /** Mutable backing stream for the configured torrent handling mode. */
    private val _torrentHandlingModeFlow = MutableStateFlow(getTorrentHandlingMode())
    /** Observable torrent handling mode. */
    val torrentHandlingModeFlow: StateFlow<TorrentHandlingMode> =
        _torrentHandlingModeFlow.asStateFlow()

    /** Persists whether voice guidance is enabled. */
    fun setVoiceSystem(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_VOICE_SYSTEM, enabled) }
    }

    /** Returns whether voice guidance is enabled; defaults to `true`. */
    fun getVoiceSystem(): Boolean = sharedPreferences.getBoolean(KEY_VOICE_SYSTEM, true)

    /** Persists whether movie summaries are spoken aloud. */
    fun setVoiceSummary(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_VOICE_SUMMARY, enabled) }
    }

    /** Returns whether movie summaries are spoken aloud; defaults to `true`. */
    fun getVoiceSummary(): Boolean = sharedPreferences.getBoolean(KEY_VOICE_SUMMARY, true)

    /** Persists whether translated text is spoken aloud. */
    fun setVoiceTranslation(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_VOICE_TRANSLATION, enabled) }
    }

    /** Returns whether translated text is spoken aloud; defaults to `false`. */
    fun getVoiceTranslation(): Boolean = sharedPreferences.getBoolean(KEY_VOICE_TRANSLATION, false)

    /** Persists whether haptic feedback is enabled. */
    fun setVibrator(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_VIBRATOR, enabled) }
    }

    /** Returns whether haptic feedback is enabled; defaults to `false`. */
    fun getVibrator(): Boolean = sharedPreferences.getBoolean(KEY_VIBRATOR, false)

    /** Persists the current Firebase Cloud Messaging token. */
    fun setFcmToken(token: String) {
        sharedPreferences.edit { putString(KEY_FCM_TOKEN, token) }
    }

    /** Returns the stored FCM token, if one has been received. */
    fun getFcmToken(): String? = sharedPreferences.getString(KEY_FCM_TOKEN, null)

    /** Persists whether the device is subscribed to the broadcast FCM topic. */
    fun setFcmTopicSubscribed(subscribed: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_FCM_TOPIC_SUBSCRIBED, subscribed) }
    }

    /** Returns whether the device is recorded as subscribed to the broadcast FCM topic. */
    fun isFcmTopicSubscribed(): Boolean =
        sharedPreferences.getBoolean(KEY_FCM_TOPIC_SUBSCRIBED, false)

    /** Persists whether the device token has synchronized with the backend. */
    fun setFcmTokenSynced(synced: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_FCM_TOKEN_SYNCED, synced) }
    }

    /** Returns whether the device token is synchronized with the backend. */
    fun isFcmTokenSynced(): Boolean =
        sharedPreferences.getBoolean(KEY_FCM_TOKEN_SYNCED, false)

    /** Persists whether push notifications are enabled in app settings. */
    fun setPushEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_PUSH_ENABLED, enabled) }
    }

    /** Returns whether push notifications are enabled; defaults to `true`. */
    fun isPushEnabled(): Boolean = sharedPreferences.getBoolean(KEY_PUSH_ENABLED, true)

    /** Returns the current FCM backend synchronization retry count. */
    fun getFcmRetryCount(): Int = sharedPreferences.getInt(KEY_FCM_RETRY_COUNT, 0)

    /** Persists the FCM backend synchronization retry count. */
    fun setFcmRetryCount(count: Int) {
        sharedPreferences.edit { putInt(KEY_FCM_RETRY_COUNT, count) }
    }

    /** Increments the stored FCM synchronization retry count by one. */
    fun incrementFcmRetryCount() {
        setFcmRetryCount(getFcmRetryCount() + 1)
    }

    /** Persists and publishes the selected theme mode. */
    fun setTheme(theme: Int) {
        sharedPreferences.edit { putInt(KEY_THEME, theme) }
        _themeFlow.value = theme
    }

    /** Returns the selected theme mode, defaulting to [THEME_SYSTEM]. */
    fun getTheme(): Int = sharedPreferences.getInt(KEY_THEME, THEME_SYSTEM)

    /** Persists and publishes the comma-separated language exclusion list. */
    fun setFilteredLanguages(languages: String) {
        sharedPreferences.edit { putString(KEY_FILTERED_LANGUAGES, languages) }
        _filteredLanguagesFlow.value = languages
    }

    /** Returns the comma-separated excluded languages, or an empty string. */
    fun getFilteredLanguages(): String = sharedPreferences.getString(KEY_FILTERED_LANGUAGES, "") ?: ""

    /** Persists a minimum rating clamped to the supported range and publishes it. */
    fun setMinimumRating(rating: Float) {
        val roundedRating = (Math.round(rating * 10.0f) / 10.0f).coerceIn(MINIMUM_RATING_MIN, MINIMUM_RATING_MAX)
        sharedPreferences.edit { putFloat(KEY_MINIMUM_RATING, roundedRating) }
        _minimumRatingFlow.value = roundedRating
    }

    /** Returns the stored minimum rating, defaulting to [DEFAULT_MINIMUM_RATING]. */
    fun getMinimumRating(): Float {
        return try {
            sharedPreferences.getFloat(KEY_MINIMUM_RATING, DEFAULT_MINIMUM_RATING)
        } catch (_: ClassCastException) {
            val legacyInt = sharedPreferences.getInt(KEY_MINIMUM_RATING, DEFAULT_MINIMUM_RATING.toInt())
            val migratedRating = legacyInt.toFloat().coerceIn(MINIMUM_RATING_MIN, MINIMUM_RATING_MAX)
            sharedPreferences.edit { putFloat(KEY_MINIMUM_RATING, migratedRating) }
            migratedRating
        }
    }

    /** Persists and publishes the configured torrent handling mode. */
    fun setTorrentHandlingMode(mode: TorrentHandlingMode) {
        sharedPreferences.edit { putString(KEY_TORRENT_HANDLING_MODE, mode.name) }
        _torrentHandlingModeFlow.value = mode
    }

    /** Returns the configured torrent handling mode, defaulting to external handoff. */
    fun getTorrentHandlingMode(): TorrentHandlingMode {
        val storedMode = sharedPreferences.getString(KEY_TORRENT_HANDLING_MODE, null)
        return TorrentHandlingMode.values().firstOrNull { it.name == storedMode }
            ?: TorrentHandlingMode.EXTERNAL_CLIENT
    }

    companion object {
        /** Preference key controlling general voice guidance. */
        private const val KEY_VOICE_SYSTEM = "voice_system"
        /** Preference key controlling spoken movie summaries. */
        private const val KEY_VOICE_SUMMARY = "voice_summary"
        /** Preference key controlling spoken translations. */
        private const val KEY_VOICE_TRANSLATION = "voice_translation"
        /** Preference key controlling haptic feedback. */
        private const val KEY_VIBRATOR = "vibrator"
        /** Preference key for the current FCM token. */
        private const val KEY_FCM_TOKEN = "fcm_token"
        /** Preference key for FCM topic subscription state. */
        private const val KEY_FCM_TOPIC_SUBSCRIBED = "fcm_topic_subscribed"
        /** Preference key for backend token synchronization state. */
        private const val KEY_FCM_TOKEN_SYNCED = "fcm_token_synced"
        /** Preference key for the user's push notification setting. */
        private const val KEY_PUSH_ENABLED = "push_enabled"
        /** Preference key for the selected theme mode. */
        private const val KEY_THEME = "theme"
        /** Preference key for the backend synchronization retry count. */
        private const val KEY_FCM_RETRY_COUNT = "fcm_retry_count"
        /** Preference key for excluded movie languages. */
        private const val KEY_FILTERED_LANGUAGES = "filtered_languages"
        /** Preference key for the minimum movie rating. */
        private const val KEY_MINIMUM_RATING = "minimum_rating"
        /** Preference key for the selected torrent handling mode. */
        private const val KEY_TORRENT_HANDLING_MODE = "torrent_handling_mode"

        /** Follow the system theme. */
        const val THEME_SYSTEM = 0
        /** Always use the light theme. */
        const val THEME_LIGHT = 1
        /** Always use the dark theme. */
        const val THEME_DARK = 2
        /** Default minimum movie rating. */
        const val DEFAULT_MINIMUM_RATING = 6.0f
        /** Lowest supported minimum movie rating. */
        const val MINIMUM_RATING_MIN = 0.0f
        /** Highest supported minimum movie rating. */
        const val MINIMUM_RATING_MAX = 10.0f
        /** Step increment for adjusting minimum rating. */
        const val MINIMUM_RATING_STEP = 0.1f
    }
}
