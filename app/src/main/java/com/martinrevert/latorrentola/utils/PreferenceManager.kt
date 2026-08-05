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

    fun setFcmToken(token: String) {
        sharedPreferences.edit { putString(KEY_FCM_TOKEN, token) }
    }

    fun getFcmToken(): String? = sharedPreferences.getString(KEY_FCM_TOKEN, null)

    fun setFcmTopicSubscribed(subscribed: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_FCM_TOPIC_SUBSCRIBED, subscribed) }
    }

    fun isFcmTopicSubscribed(): Boolean =
        sharedPreferences.getBoolean(KEY_FCM_TOPIC_SUBSCRIBED, false)

    fun setFcmTokenSynced(synced: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_FCM_TOKEN_SYNCED, synced) }
    }

    fun isFcmTokenSynced(): Boolean =
        sharedPreferences.getBoolean(KEY_FCM_TOKEN_SYNCED, false)

    companion object {
        private const val KEY_VOICE_SYSTEM = "voice_system"
        private const val KEY_VOICE_SUMMARY = "voice_summary"
        private const val KEY_VOICE_TRANSLATION = "voice_translation"
        private const val KEY_VIBRATOR = "vibrator"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_FCM_TOPIC_SUBSCRIBED = "fcm_topic_subscribed"
        private const val KEY_FCM_TOKEN_SYNCED = "fcm_token_synced"
    }
}
