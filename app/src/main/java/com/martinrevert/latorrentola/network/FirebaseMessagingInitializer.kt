package com.martinrevert.latorrentola.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.martinrevert.latorrentola.utils.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates the notification channel and coordinates FCM topic/backend subscriptions.
 *
 * @property context Application context for notification services and permissions.
 * @property preferenceManager stores notification and retry preferences.
 * @property fcmRepository synchronizes tokens with the app backend.
 */
@Singleton
class FirebaseMessagingInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager,
    private val fcmRepository: FcmRepository
) {

    /** Background scope used for backend token synchronization. */
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Creates required notification resources and synchronizes the initial topic state. */
    fun initialize() {
        ensureNotificationChannel()
        syncTopicSubscription(shouldSubscribeToNotifications())
    }

    /** Subscribes to broadcast notifications when push notifications are enabled. */
    fun subscribeToDefaultTopic() {
        if (preferenceManager.isPushEnabled()) {
            syncTopicSubscription(true)
        }
    }

    /** Removes the device from the broadcast notification topic. */
    fun unsubscribeFromDefaultTopic() {
        syncTopicSubscription(false)
    }

    /** Creates the general notification channel on Android O and newer if it is absent. */
    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val existingChannel = notificationManager.getNotificationChannel(
            FirebaseMessagingConfig.DEFAULT_CHANNEL_ID
        )
        if (existingChannel != null) return

        val channel = NotificationChannel(
            FirebaseMessagingConfig.DEFAULT_CHANNEL_ID,
            FirebaseMessagingConfig.DEFAULT_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = FirebaseMessagingConfig.DEFAULT_CHANNEL_DESCRIPTION
        }

        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Applies the requested FCM topic state and mirrors it to the backend.
     *
     * @param shouldSubscribe `true` to join the topic, `false` to leave it.
     */
    fun syncTopicSubscription(shouldSubscribe: Boolean) {
        if (shouldSubscribe) {
            FirebaseMessaging.getInstance().subscribeToTopic(FirebaseMessagingConfig.TOPIC_ALL)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        preferenceManager.setFcmTopicSubscribed(true)
                        Log.d(TAG, "Subscribed to topic '${FirebaseMessagingConfig.TOPIC_ALL}'")
                        syncWithBackend(true)
                    } else {
                        Log.w(TAG, "Topic subscription failed", task.exception)
                        // Even if topic fails, try backend sync to maintain consistency
                        syncWithBackend(true)
                    }
                }
            return
        }

        FirebaseMessaging.getInstance().unsubscribeFromTopic(FirebaseMessagingConfig.TOPIC_ALL)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    preferenceManager.setFcmTopicSubscribed(false)
                    Log.d(TAG, "Unsubscribed from topic '${FirebaseMessagingConfig.TOPIC_ALL}'")
                    syncWithBackend(false)
                } else {
                    Log.w(TAG, "Topic unsubscription failed", task.exception)
                    syncWithBackend(false)
                }
            }
    }

    /** Synchronizes the current FCM token with the backend after topic processing. */
    private fun syncWithBackend(shouldSubscribe: Boolean) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "FCM token fetch failed for backend sync", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            if (token.isNullOrBlank()) return@addOnCompleteListener

            scope.launch {
                val success = fcmRepository.syncToken(token, shouldSubscribe)
                if (shouldSubscribe) {
                    if (success) {
                        preferenceManager.setFcmRetryCount(0)
                        preferenceManager.setFcmTokenSynced(true)
                    } else {
                        preferenceManager.incrementFcmRetryCount()
                        val retryCount = preferenceManager.getFcmRetryCount()
                        Log.w(TAG, "Backend sync failed. Retry count: $retryCount")
                        if (retryCount >= 5) {
                            Log.e(TAG, "Max retries reached. Disabling push notifications.")
                            preferenceManager.setPushEnabled(false)
                            preferenceManager.setFcmRetryCount(0)
                            // We don't call syncTopicSubscription(false) here to avoid loops, 
                            // but the user will see it disabled in UI.
                        }
                    }
                } else {
                    // On unsubscription we don't necessarily need retries as much, 
                    // but we clear synced flag if it failed.
                    if (success) {
                        preferenceManager.setFcmTokenSynced(false)
                        preferenceManager.setFcmRetryCount(0)
                    }
                }
            }
        }
    }

    /** Checks app preference and platform permission state before allowing notifications. */
    private fun shouldSubscribeToNotifications(): Boolean {
        if (!preferenceManager.isPushEnabled()) return false
        
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!notificationsEnabled) return false

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        /** Log tag used for FCM initialization diagnostics. */
        private const val TAG = "FirebaseMessagingInit"
    }
}
