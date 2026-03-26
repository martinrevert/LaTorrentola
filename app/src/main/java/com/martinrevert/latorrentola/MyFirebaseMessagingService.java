package com.martinrevert.latorrentola;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.BackoffPolicy;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.martinrevert.latorrentola.network.FCMRegistrationWorker;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "default_channel_id";
    private static final String CHANNEL_NAME = "Default Channel";
    private static final String TAG = "FCM_Service";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        String title = null;
        String body = null;

        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }

        Map<String, String> data = remoteMessage.getData();
        String movieJson = data.get("PELI");
        String movieId = data.get("id");

        // Use data payload if notification payload is missing
        if (title == null) title = data.get("title");
        if (body == null) body = data.get("body");

        if (title != null && body != null) {
            sendNotification(title, body, movieJson, movieId);
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);
        scheduleTokenRegistration(token);
    }

    private void scheduleTokenRegistration(String token) {
        Data inputData = new Data.Builder()
                .putString("token", token)
                .build();

        OneTimeWorkRequest registrationWork = new OneTimeWorkRequest.Builder(FCMRegistrationWorker.class)
                .setInputData(inputData)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build();

        WorkManager.getInstance(this).enqueue(registrationWork);
    }

    private void sendNotification(String title, String messageBody, String movieJson, String movieId) {
        Intent intent;
        if (movieJson != null) {
            intent = new Intent(this, PeliActivity.class);
            intent.putExtra("PELI", movieJson);
        } else if (movieId != null) {
            intent = new Intent(this, PeliActivity.class);
            intent.putExtra("MOVIE_ID", movieId);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent;
        if (movieJson != null || movieId != null) {
            // Build the back stack so pressing back goes to MainActivity
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
            stackBuilder.addNextIntentWithParentStack(intent);
            pendingIntent = stackBuilder.getPendingIntent(0,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String ringtoneStr = sharedPreferences.getString("system_ringtone", null);
        Uri soundUri;
        if (ringtoneStr != null && !ringtoneStr.isEmpty()) {
            soundUri = Uri.parse(ringtoneStr);
        } else {
            soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setSound(soundUri)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For Android O and above, we need to delete and recreate the channel if we want to change the sound,
            // or just ensure it's created with the correct sound the first time.
            // Note: Once a channel is created, you cannot change its importance or sound programmatically.
            // A common workaround is to create a new channel with a different ID if the user changes the sound.
            
            // To make it simple and respect the user's choice, we'll use a dynamic channel ID based on the sound URI.
            String dynamicChannelId = CHANNEL_ID + "_" + soundUri.toString().hashCode();
            notificationBuilder.setChannelId(dynamicChannelId);

            if (notificationManager.getNotificationChannel(dynamicChannelId) == null) {
                NotificationChannel channel = new NotificationChannel(dynamicChannelId,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT);
                
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build();
                channel.setSound(soundUri, audioAttributes);
                
                notificationManager.createNotificationChannel(channel);
            }
        }

        notificationManager.notify(0, notificationBuilder.build());
    }
}
