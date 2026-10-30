package com.martinrevert.latorrentola.network

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Firebase Crashlytics logging, user attribution, and crash diagnostics.
 */
@Singleton
class CrashlyticsManager @Inject constructor() {

    private val crashlytics: FirebaseCrashlytics?
        get() = try {
            FirebaseCrashlytics.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseCrashlytics is not available: ${e.message}")
            null
        }

    /**
     * Controls whether crash reports are collected and uploaded.
     */
    fun setCollectionEnabled(enabled: Boolean) {
        try {
            crashlytics?.setCrashlyticsCollectionEnabled(enabled)
            Log.d(TAG, "Crashlytics collection enabled: $enabled")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update Crashlytics collection state", e)
        }
    }

    /**
     * Attaches a unique user identifier to crash reports.
     *
     * @param userId Unique user ID, or empty/null on sign-out.
     */
    fun setUserId(userId: String?) {
        try {
            crashlytics?.setUserId(userId.orEmpty())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set Crashlytics user ID", e)
        }
    }

    /**
     * Attaches a custom key-value attribute for contextual diagnostic filtering.
     */
    fun setCustomKey(key: String, value: String) {
        try {
            crashlytics?.setCustomKey(key, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set custom key: $key", e)
        }
    }

    /**
     * Records a handled or caught non-fatal exception.
     */
    fun recordException(throwable: Throwable) {
        try {
            crashlytics?.recordException(throwable)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record non-fatal exception", e)
        }
    }

    /**
     * Adds a breadcrumb log entry to the next crash report.
     */
    fun log(message: String) {
        try {
            crashlytics?.log(message)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write Crashlytics breadcrumb log", e)
        }
    }

    /**
     * Records an error message as a breadcrumb and reports the associated non-fatal exception.
     */
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        log("[$tag] $message")
        recordException(throwable ?: Exception("[$tag] $message"))
    }

    /**
     * Throws an intentional unhandled exception to verify the Crashlytics pipeline.
     */
    fun triggerTestCrash() {
        throw RuntimeException("Crashlytics Test Crash - Verificación inicial de LaTorrentola")
    }

    companion object {
        private const val TAG = "CrashlyticsManager"
    }
}
