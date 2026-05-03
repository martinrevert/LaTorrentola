package com.martinrevert.latorrentola.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("latorrentola_prefs", Context.MODE_PRIVATE)

    fun setVoiceSystem(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_VOICE_SYSTEM, enabled) }
    }

    fun getVoiceSystem(): Boolean = sharedPreferences.getBoolean(KEY_VOICE_SYSTEM, true)

    fun setVoiceSummary(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_VOICE_SUMMARY, enabled) }
    }

    fun getVoiceSummary(): Boolean = sharedPreferences.getBoolean(KEY_VOICE_SUMMARY, true)

    fun setVoiceTranslation(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_VOICE_TRANSLATION, enabled) }
    }

    fun getVoiceTranslation(): Boolean = sharedPreferences.getBoolean(KEY_VOICE_TRANSLATION, false)

    fun setVibrator(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_VIBRATOR, enabled) }
    }

    fun getVibrator(): Boolean = sharedPreferences.getBoolean(KEY_VIBRATOR, false)

    companion object {
        private const val KEY_VOICE_SYSTEM = "voice_system"
        private const val KEY_VOICE_SUMMARY = "voice_summary"
        private const val KEY_VOICE_TRANSLATION = "voice_translation"
        private const val KEY_VIBRATOR = "vibrator"
    }
}
