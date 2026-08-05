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

@Singleton
class FirebaseMessagingInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager,
    private val fcmRepository: FcmRepository
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    fun initialize() {
        ensureNotificationChannel()
        syncTopicSubscription(shouldSubscribeToNotifications())
        ensureTokenSynced()
    }

    fun subscribeToDefaultTopic() {
        syncTopicSubscription(true)
    }

    fun unsubscribeFromDefaultTopic() {
        syncTopicSubscription(false)
    }

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

    fun syncTopicSubscription(shouldSubscribe: Boolean) {
        if (shouldSubscribe) {
            if (preferenceManager.isFcmTopicSubscribed()) return

            FirebaseMessaging.getInstance().subscribeToTopic(FirebaseMessagingConfig.TOPIC_ALL)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        preferenceManager.setFcmTopicSubscribed(true)
                        Log.d(TAG, "Subscribed to topic '${FirebaseMessagingConfig.TOPIC_ALL}'")
                        preferenceManager.getFcmToken()?.let { token ->
                            scope.launch {
                                fcmRepository.subscribe(token)
                            }
                        }
                    } else {
                        Log.w(TAG, "Topic subscription failed", task.exception)
                    }
                }
            return
        }

        if (!preferenceManager.isFcmTopicSubscribed()) return

        FirebaseMessaging.getInstance().unsubscribeFromTopic(FirebaseMessagingConfig.TOPIC_ALL)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    preferenceManager.setFcmTopicSubscribed(false)
                    Log.d(TAG, "Unsubscribed from topic '${FirebaseMessagingConfig.TOPIC_ALL}'")
                    preferenceManager.getFcmToken()?.let { token ->
                        scope.launch {
                            fcmRepository.unsubscribe(token)
                        }
                    }
                } else {
                    Log.w(TAG, "Topic unsubscription failed", task.exception)
                }
            }
    }

    private fun ensureTokenSynced() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "FCM token fetch failed", task.exception)
                return@addOnCompleteListener
            }

            val newToken = task.result
            if (!newToken.isNullOrBlank() && newToken != preferenceManager.getFcmToken()) {
                preferenceManager.setFcmToken(newToken)
                preferenceManager.setFcmTokenSynced(false)
                Log.d(TAG, "FCM token updated")
            }

            preferenceManager.getFcmToken()?.let { token ->
                scope.launch {
                    fcmRepository.syncToken(token)
                }
            }
        }
    }

    private fun shouldSubscribeToNotifications(): Boolean {
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!notificationsEnabled) return false

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "FirebaseMessagingInit"
    }
}



