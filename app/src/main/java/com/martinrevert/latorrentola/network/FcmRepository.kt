package com.martinrevert.latorrentola.network

import android.util.Log
import com.martinrevert.latorrentola.utils.PreferenceManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronizes the device's FCM token with the app backend.
 *
 * @property fcmService HTTP client for backend subscription operations.
 * @property preferenceManager stores token synchronization state.
 */
@Singleton
class FcmRepository @Inject constructor(
    private val fcmService: FcmService,
    private val preferenceManager: PreferenceManager
) {

    /** Registers [token] with the backend and records successful synchronization. */
    suspend fun subscribe(token: String): Boolean {
        return try {
            val response = fcmService.subscribe(token)
            if (response.isSuccessful) {
                preferenceManager.setFcmTokenSynced(true)
                Log.d(TAG, "Subscribed to local FCM database")
                true
            } else {
                Log.w(TAG, "Failed to subscribe to local FCM database: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to local FCM database", e)
            false
        }
    }

    /** Removes [token] from the backend and clears its synchronized preference on success. */
    suspend fun unsubscribe(token: String): Boolean {
        return try {
            val response = fcmService.unsubscribe(token)
            if (response.isSuccessful) {
                preferenceManager.setFcmTokenSynced(false)
                Log.d(TAG, "Unsubscribed from local FCM database")
                true
            } else {
                Log.w(TAG, "Failed to unsubscribe from local FCM database: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unsubscribing from local FCM database", e)
            false
        }
    }

    /** Applies the requested backend subscription state for [token]. */
    suspend fun syncToken(token: String, shouldSubscribe: Boolean): Boolean {
        return if (shouldSubscribe) {
            subscribe(token)
        } else {
            unsubscribe(token)
        }
    }

    companion object {
        /** Log tag used for token synchronization diagnostics. */
        private const val TAG = "FcmRepository"
    }
}
