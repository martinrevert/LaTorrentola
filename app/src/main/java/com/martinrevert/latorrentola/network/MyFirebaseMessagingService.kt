package com.martinrevert.latorrentola.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.martinrevert.latorrentola.MainActivity
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.utils.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    @Inject
    lateinit var fcmRepository: FcmRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: getString(R.string.app_name)
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: remoteMessage.data["message"]
            ?: ""
        val movieJson = remoteMessage.data[FirebaseMessagingConfig.EXTRA_MOVIE_JSON]
            ?: remoteMessage.data["peli"]
        val movieId = remoteMessage.data[FirebaseMessagingConfig.EXTRA_MOVIE_ID]
            ?: remoteMessage.data["id"]

        if (body.isNotBlank()) {
            sendNotification(title, body, movieJson, movieId)
        } else {
            Log.d(TAG, "Message received without displayable body. id=${remoteMessage.messageId}")
        }
    }

    override fun onNewToken(token: String) {
        preferenceManager.setFcmToken(token)
        preferenceManager.setFcmTokenSynced(false)
        serviceScope.launch {
            fcmRepository.subscribe(token)
        }

        val messaging = FirebaseMessaging.getInstance()
        if (canDeliverNotifications()) {
            preferenceManager.setFcmTopicSubscribed(false)
            messaging.subscribeToTopic(FirebaseMessagingConfig.TOPIC_ALL)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        preferenceManager.setFcmTopicSubscribed(true)
                        Log.d(TAG, "Re-subscribed to topic after token refresh")
                    } else {
                        Log.w(TAG, "Topic re-subscription failed after token refresh", task.exception)
                    }
                }
        } else {
            messaging.unsubscribeFromTopic(FirebaseMessagingConfig.TOPIC_ALL)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        preferenceManager.setFcmTopicSubscribed(false)
                        Log.d(TAG, "Unsubscribed from topic after token refresh due to disabled notifications")
                    } else {
                        Log.w(TAG, "Topic unsubscription failed after token refresh", task.exception)
                    }
                }
        }
        Log.d(TAG, "FCM token refreshed")
    }

    private fun sendNotification(title: String, body: String, movieJson: String?, movieId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            movieJson?.let { putExtra(FirebaseMessagingConfig.EXTRA_MOVIE_JSON, it) }
            movieId?.let { putExtra(FirebaseMessagingConfig.EXTRA_MOVIE_ID, it) }
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent: PendingIntent? = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(
                intent.hashCode(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val channelId = FirebaseMessagingConfig.DEFAULT_CHANNEL_ID
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                FirebaseMessagingConfig.DEFAULT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = FirebaseMessagingConfig.DEFAULT_CHANNEL_DESCRIPTION
            notificationManager.createNotificationChannel(channel)
        }

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    companion object {
        private const val TAG = "MyFcmService"
    }

    private fun canDeliverNotifications(): Boolean {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
