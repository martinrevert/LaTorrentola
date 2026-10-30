package com.martinrevert.latorrentola.utils

import android.util.Log
import com.martinrevert.latorrentola.BuildConfig
import com.martinrevert.latorrentola.network.CrashlyticsManager

/**
 * Centralized logging utility for LaTorrentola.
 *
 * Directs informational, warning, and error diagnostics cleanly across development
 * and production builds. In release builds, [d] and [v] are completely stripped at
 * bytecode level by R8 rules, and critical errors reported via [e] or [recordException]
 * are automatically sent to Firebase Crashlytics as non-fatal telemetry.
 */
object AppLogger {

    @Volatile
    private var crashlyticsManager: CrashlyticsManager? = null

    /**
     * Initializes the [CrashlyticsManager] instance used for error and breadcrumb forwarding.
     */
    fun init(manager: CrashlyticsManager) {
        crashlyticsManager = manager
    }

    /**
     * Sets or overrides the CrashlyticsManager instance (mainly for testing or custom scoping).
     */
    internal fun setCrashlyticsManagerForTesting(manager: CrashlyticsManager?) {
        crashlyticsManager = manager
    }

    /**
     * Verbose logging. Completely eliminated in release builds by R8.
     */
    inline fun v(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message())
        }
    }

    /**
     * Verbose logging overload with static string.
     */
    fun v(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message)
        }
    }

    /**
     * Debug logging. Evaluates the message lambda only in debug builds.
     * Completely stripped at bytecode level by R8 in release builds.
     */
    inline fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message())
        }
    }

    /**
     * Debug logging overload with static string.
     */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    /**
     * Informational logging. Preserved in production for system diagnostics.
     */
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        crashlyticsManager?.log("[$tag] $message")
    }

    /**
     * Warning logging. Preserved in production and added as breadcrumb.
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
            val exceptionDetail = throwable.message?.takeIf { it.isNotBlank() } ?: throwable.javaClass.simpleName
            crashlyticsManager?.log("[$tag] WARN: $message ($exceptionDetail)")
        } else {
            Log.w(tag, message)
            crashlyticsManager?.log("[$tag] WARN: $message")
        }
    }

    /**
     * Error logging. Logs to logcat and automatically records breadcrumb and non-fatal
     * exception to Firebase Crashlytics for production monitoring.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        crashlyticsManager?.logError(tag, message, throwable)
    }

    /**
     * Directly reports an uncaught or caught exception to Crashlytics with an optional contextual message.
     */
    fun recordException(throwable: Throwable, tag: String? = null, message: String? = null) {
        val effectiveTag = tag ?: "AppLogger"
        val effectiveMessage = message ?: throwable.message ?: throwable.javaClass.simpleName
        Log.e(effectiveTag, effectiveMessage, throwable)
        crashlyticsManager?.logError(effectiveTag, effectiveMessage, throwable)
    }
}
