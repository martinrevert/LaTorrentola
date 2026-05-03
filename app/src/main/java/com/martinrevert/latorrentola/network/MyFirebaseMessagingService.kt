package com.martinrevert.latorrentola.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.martinrevert.latorrentola.MainActivity
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.utils.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"]
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"]
        val movieJson = remoteMessage.data["PELI"]
        val movieId = remoteMessage.data["id"]

        if (title != null && body != null) {
            sendNotification(title, body, movieJson, movieId)
        }
    }

    override fun onNewToken(token: String) {
        // In a real app, you would send this token to your server
    }

    private fun sendNotification(title: String, body: String, movieJson: String?, movieId: String?) {
        val intent = Intent(this as Context, MainActivity::class.java).apply {
            movieJson?.let { putExtra("PELI", it) }
            movieId?.let { putExtra("MOVIE_ID", it) }
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent: PendingIntent? = TaskStackBuilder.create(this as Context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val channelId = "default_channel"
        val notificationBuilder = NotificationCompat.Builder(this as Context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Default Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }
}
