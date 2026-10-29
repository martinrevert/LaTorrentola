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

    /** Observable selected theme mode. */
    val themeFlow: StateFlow<Int> get() = _themeFlow.asStateFlow()

    /** Observable comma-separated excluded language codes. */
    val filteredLanguagesFlow: StateFlow<String> get() = _filteredLanguagesFlow.asStateFlow()

    /** Observable minimum movie rating. */
    val minimumRatingFlow: StateFlow<Float> get() = _minimumRatingFlow.asStateFlow()

    /** Observable torrent handling mode. */
    val torrentHandlingModeFlow: StateFlow<TorrentHandlingMode> get() = _torrentHandlingModeFlow.asStateFlow()

    /** Observable auto-play quality selection method. */
    val autoPlayQualitySelectionFlow: StateFlow<AutoPlayQualitySelectionMethod> get() = _autoPlayQualitySelectionFlow.asStateFlow()

    /** Observable storage location identifier. */
    val storageLocationFlow: StateFlow<String> get() = _storageLocationFlow.asStateFlow()

    /** Observable setting for converting multichannel AAC 5.1 to AC3 5.1. */
    val convertAac51ToAc3Flow: StateFlow<Boolean> get() = _convertAac51ToAc3Flow.asStateFlow()

    /** Listener to mirror changes from SharedPreferences directly into reactive StateFlows. */
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_THEME -> _themeFlow.value = getTheme()
            KEY_FILTERED_LANGUAGES -> _filteredLanguagesFlow.value = getFilteredLanguages()
            KEY_MINIMUM_RATING -> _minimumRatingFlow.value = getMinimumRating()
            KEY_TORRENT_HANDLING_MODE -> _torrentHandlingModeFlow.value = getTorrentHandlingMode()
            KEY_AUTO_PLAY_QUALITY_SELECTION_METHOD -> _autoPlayQualitySelectionFlow.value = getAutoPlayQualitySelectionMethod()
            KEY_STORAGE_LOCATION -> _storageLocationFlow.value = getStorageLocation()
            KEY_CONVERT_AAC51_TO_AC3 -> _convertAac51ToAc3Flow.value = getConvertAac51ToAc3()
        }
    }

    init {
        _themeFlow.value = getTheme()
        _filteredLanguagesFlow.value = getFilteredLanguages()
        _minimumRatingFlow.value = getMinimumRating()
        _torrentHandlingModeFlow.value = getTorrentHandlingMode()
        _autoPlayQualitySelectionFlow.value = getAutoPlayQualitySelectionMethod()
        _storageLocationFlow.value = getStorageLocation()
        _convertAac51ToAc3Flow.value = getConvertAac51ToAc3()
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

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
        val roundedRating = (kotlin.math.round(rating * 10.0f) / 10.0f).coerceIn(MINIMUM_RATING_MIN, MINIMUM_RATING_MAX)
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

    /** Returns the configured torrent handling mode, defaulting to local playback/streaming. Automatically migrates legacy CHROMECAST mode to LOCAL_PLAYBACK. */
    fun getTorrentHandlingMode(): TorrentHandlingMode {
        val storedMode = sharedPreferences.getString(KEY_TORRENT_HANDLING_MODE, null)
        @Suppress("DEPRECATION")
        if (storedMode == "CHROMECAST" || storedMode == TorrentHandlingMode.CHROMECAST.name) {
            setTorrentHandlingMode(TorrentHandlingMode.LOCAL_PLAYBACK)
            return TorrentHandlingMode.LOCAL_PLAYBACK
        }
        @Suppress("DEPRECATION")
        return TorrentHandlingMode.entries.firstOrNull { it.name == storedMode && it != TorrentHandlingMode.CHROMECAST }
            ?: TorrentHandlingMode.LOCAL_PLAYBACK
    }

    /** Persists and publishes the auto-play quality selection method. */
    fun setAutoPlayQualitySelectionMethod(method: AutoPlayQualitySelectionMethod) {
        sharedPreferences.edit { putString(KEY_AUTO_PLAY_QUALITY_SELECTION_METHOD, method.name) }
        _autoPlayQualitySelectionFlow.value = method
    }

    /** Returns the configured auto-play quality selection method, defaulting to OFF. */
    fun getAutoPlayQualitySelectionMethod(): AutoPlayQualitySelectionMethod {
        val storedMethod = sharedPreferences.getString(KEY_AUTO_PLAY_QUALITY_SELECTION_METHOD, null)
        return AutoPlayQualitySelectionMethod.entries.firstOrNull { it.name == storedMethod }
            ?: AutoPlayQualitySelectionMethod.OFF
    }

    fun getSubtitleFontName(): String = sharedPreferences.getString(KEY_SUBTITLE_FONT_NAME, "Roboto") ?: "Roboto"
    fun setSubtitleFontName(fontName: String) { sharedPreferences.edit { putString(KEY_SUBTITLE_FONT_NAME, fontName) } }

    fun getSubtitleSizeFraction(): Float = sharedPreferences.getFloat(KEY_SUBTITLE_SIZE_FRACTION, 0.0533f)
    fun setSubtitleSizeFraction(fraction: Float) { sharedPreferences.edit { putFloat(KEY_SUBTITLE_SIZE_FRACTION, fraction) } }

    fun getSubtitleForegroundColor(): Int = sharedPreferences.getInt(KEY_SUBTITLE_FG_COLOR, android.graphics.Color.WHITE)
    fun setSubtitleForegroundColor(color: Int) { sharedPreferences.edit { putInt(KEY_SUBTITLE_FG_COLOR, color) } }

    fun getSubtitleBackgroundColor(): Int = sharedPreferences.getInt(KEY_SUBTITLE_BG_COLOR, android.graphics.Color.TRANSPARENT)
    fun setSubtitleBackgroundColor(color: Int) { sharedPreferences.edit { putInt(KEY_SUBTITLE_BG_COLOR, color) } }

    fun getSubtitleEdgeType(): Int = sharedPreferences.getInt(KEY_SUBTITLE_EDGE_TYPE, 1)
    fun setSubtitleEdgeType(edgeType: Int) { sharedPreferences.edit { putInt(KEY_SUBTITLE_EDGE_TYPE, edgeType) } }

    fun getSubtitleBottomOffset(): Float = sharedPreferences.getFloat(KEY_SUBTITLE_BOTTOM_OFFSET, 0.08f)
    fun setSubtitleBottomOffset(offset: Float) { sharedPreferences.edit { putFloat(KEY_SUBTITLE_BOTTOM_OFFSET, offset) } }

    /** Returns whether subtitles are enabled globally by user preference, defaulting to true. */
    fun areSubtitlesEnabled(): Boolean = sharedPreferences.getBoolean(KEY_SUBTITLES_ENABLED, true)
    /** Persists whether subtitles should be displayed. */
    fun setSubtitlesEnabled(enabled: Boolean) { sharedPreferences.edit { putBoolean(KEY_SUBTITLES_ENABLED, enabled) } }

    /** Persists and publishes the configured download storage location. */
    fun setStorageLocation(location: String) {
        sharedPreferences.edit { putString(KEY_STORAGE_LOCATION, location) }
        _storageLocationFlow.value = location
    }

    /** Returns the configured download storage location, defaulting to internal storage. */
    fun getStorageLocation(): String {
        val value = sharedPreferences.getString(KEY_STORAGE_LOCATION, "internal") ?: "internal"
        return if (value.isBlank() || value.startsWith("remote:")) "internal" else value
    }

    /** Persists whether multichannel AAC 5.1 tracks should be transcoded to AC3 5.1 for SPDIF/ARC surround passthrough. */
    fun setConvertAac51ToAc3(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_CONVERT_AAC51_TO_AC3, enabled) }
        _convertAac51ToAc3Flow.value = enabled
    }

    /** Returns whether AAC 5.1 to AC3 5.1 transcoding is enabled (defaults to false). */
    fun getConvertAac51ToAc3(): Boolean = sharedPreferences.getBoolean(KEY_CONVERT_AAC51_TO_AC3, false)

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
        /** Preference key for the auto-play quality selection method. */
        private const val KEY_AUTO_PLAY_QUALITY_SELECTION_METHOD = "auto_play_quality_selection_method"
        /** Preference key for the download storage location. */
        private const val KEY_STORAGE_LOCATION = "storage_location"
        /** Preference key controlling AAC 5.1 to AC3 5.1 transcoding. */
        private const val KEY_CONVERT_AAC51_TO_AC3 = "convert_aac51_to_ac3"
        private const val KEY_SUBTITLE_FONT_NAME = "subtitle_font_name"
        private const val KEY_SUBTITLE_SIZE_FRACTION = "subtitle_size_fraction"
        private const val KEY_SUBTITLE_FG_COLOR = "subtitle_fg_color"
        private const val KEY_SUBTITLE_BG_COLOR = "subtitle_bg_color"
        private const val KEY_SUBTITLE_EDGE_TYPE = "subtitle_edge_type"
        private const val KEY_SUBTITLE_BOTTOM_OFFSET = "subtitle_bottom_offset"
        private const val KEY_SUBTITLES_ENABLED = "subtitles_enabled"

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

        /** Mutable backing stream for the selected theme shared across instances. */
        private val _themeFlow = MutableStateFlow(THEME_SYSTEM)
        /** Mutable backing stream for excluded movie languages shared across instances. */
        private val _filteredLanguagesFlow = MutableStateFlow("")
        /** Mutable backing stream for the minimum movie rating shared across instances. */
        private val _minimumRatingFlow = MutableStateFlow(DEFAULT_MINIMUM_RATING)
        /** Mutable backing stream for the configured torrent handling mode shared across instances. */
        private val _torrentHandlingModeFlow = MutableStateFlow(TorrentHandlingMode.LOCAL_PLAYBACK)
        /** Mutable backing stream for the auto-play quality selection method shared across instances. */
        private val _autoPlayQualitySelectionFlow = MutableStateFlow(AutoPlayQualitySelectionMethod.OFF)
        /** Mutable backing stream for the configured download storage location shared across instances. */
        private val _storageLocationFlow = MutableStateFlow("internal")
        /** Mutable backing stream for AAC 5.1 to AC3 5.1 conversion preference shared across instances. */
        private val _convertAac51ToAc3Flow = MutableStateFlow(false)
    }
}

/** Method for selecting quality when auto-playing the next episode. */
enum class AutoPlayQualitySelectionMethod {
    /** Auto-play is disabled; default action is showing the EZTV episode list dialog. */
    OFF,
    /** Select the release with the most seeds/peers automatically. */
    BY_SEED_PEERS,
    /** Select the release with the highest quality (resolution) automatically. */
    BY_QUALITY,
    /** Do nothing when episode ends; completely manual (like movies). */
    DO_NOTHING
}