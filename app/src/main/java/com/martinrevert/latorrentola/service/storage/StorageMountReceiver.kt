package com.martinrevert.latorrentola.service.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.martinrevert.latorrentola.service.TorrentDownloadService

/**
 * Handles external storage (USB / SD card) lifecycle events: mount, unmount, and ejection.
 * Pauses active transfers if the underlying storage media is disconnected to prevent I/O errors.
 */
class StorageMountReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Storage broadcast received: $action (data: ${intent.data})")

        when (action) {
            Intent.ACTION_MEDIA_EJECT,
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_BAD_REMOVAL,
            Intent.ACTION_MEDIA_REMOVED -> {
                Log.w(TAG, "External storage unmounted/ejected. Notifying TorrentDownloadService.")
                val serviceIntent = Intent(context, TorrentDownloadService::class.java).apply {
                    this.action = TorrentDownloadService.ACTION_STORAGE_UNMOUNTED
                }
                try {
                    context.startService(serviceIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to dispatch storage unmount intent to service", e)
                }
            }
            Intent.ACTION_MEDIA_MOUNTED -> {
                Log.i(TAG, "External storage mounted: ${intent.data}")
            }
        }
    }

    companion object {
        private const val TAG = "StorageMountReceiver"
    }
}
