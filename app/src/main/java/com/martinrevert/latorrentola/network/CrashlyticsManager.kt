package com.martinrevert.latorrentola.network

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
     *
     * Final gate: transient device-side network failures (DNS, connect, timeout) are never
     * reported as non-fatal issues, regardless of which caller reaches this point.
     */
    fun recordException(throwable: Throwable) {
        val transientCause = findTransientNetworkCause(throwable)
        if (transientCause != null) {
            log("WARN: transient network failure ignored (${transientCause.javaClass.simpleName})")
            return
        }
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
        val transientCause = findTransientNetworkCause(throwable)
        if (transientCause != null) {
            log("[$tag] WARN: $message (${transientCause.javaClass.simpleName})")
            return
        }
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
        private const val MAX_CAUSE_DEPTH = 6

        /**
         * Walks the cause chain of [throwable] (up to [MAX_CAUSE_DEPTH] levels) and returns the
         * first transient, client-environment network failure ([UnknownHostException],
         * [ConnectException], [NoRouteToHostException], or [SocketTimeoutException]), or `null`
         * if none is present.
         */
        fun findTransientNetworkCause(throwable: Throwable?): Throwable? {
            var current = throwable
            var depth = 0
            while (current != null && depth < MAX_CAUSE_DEPTH) {
                if (current is UnknownHostException ||
                    current is ConnectException ||
                    current is NoRouteToHostException ||
                    current is SocketTimeoutException
                ) {
                    return current
                }
                current = current.cause
                depth++
            }
            return null
        }

        /**
         * Returns `true` when [throwable] (or any cause in its chain) is a transient device-side
         * network failure that must never be reported as a Crashlytics non-fatal issue.
         */
        fun isTransientNetworkError(throwable: Throwable?): Boolean =
            findTransientNetworkCause(throwable) != null
    }
}
