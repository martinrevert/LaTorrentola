package com.martinrevert.latorrentola.network

import android.util.Log
import com.martinrevert.latorrentola.utils.PreferenceManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FcmRepository @Inject constructor(
    private val fcmService: FcmService,
    private val preferenceManager: PreferenceManager
) {

    suspend fun subscribe(token: String) {
        try {
            val response = fcmService.subscribe(token)
            if (response.isSuccessful) {
                preferenceManager.setFcmTokenSynced(true)
                Log.d(TAG, "Subscribed to local FCM database")
            } else {
                Log.w(TAG, "Failed to subscribe to local FCM database: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to local FCM database", e)
        }
    }

    suspend fun unsubscribe(token: String) {
        try {
            val response = fcmService.unsubscribe(token)
            if (response.isSuccessful) {
                preferenceManager.setFcmTokenSynced(false)
                Log.d(TAG, "Unsubscribed from local FCM database")
            } else {
                Log.w(TAG, "Failed to unsubscribe from local FCM database: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unsubscribing from local FCM database", e)
        }
    }

    suspend fun syncToken(token: String) {
        if (!preferenceManager.isFcmTokenSynced()) {
            subscribe(token)
        }
    }

    companion object {
        private const val TAG = "FcmRepository"
    }
}
