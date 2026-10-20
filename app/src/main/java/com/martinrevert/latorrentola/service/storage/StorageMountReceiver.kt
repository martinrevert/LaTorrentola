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
        Log.i(TAG, "Storage broadcast received: $action (data: ${intent.data}, extras: ${intent.extras})")

        when (action) {
            "android.os.storage.action.VOLUME_STATE_CHANGED" -> {
                val state = intent.getIntExtra("android.os.storage.extra.VOLUME_STATE", -1)
                val fsUuid = intent.getStringExtra("android.os.storage.extra.FS_UUID")
                Log.i(TAG, "VOLUME_STATE_CHANGED: state=$state, fsUuid=$fsUuid")
                if (state == 2 || state == 3) {
                    // 2 = STATE_MOUNTED, 3 = STATE_MOUNTED_READ_ONLY
                    StorageEvents.notifyMounted(fsUuid)
                } else if (state == 0 || state == 5 || state == 7 || state == 8) {
                    // 0 = UNMOUNTED, 5 = BAD_REMOVAL, 7 = EJECTING, 8 = UNMOUNTABLE
                    handleUnmount(context, fsUuid)
                }
            }
            "android.hardware.usb.action.USB_DEVICE_ATTACHED" -> {
                Log.i(TAG, "USB_DEVICE_ATTACHED broadcast received")
                StorageEvents.notifyMounted(null)
            }
            "android.hardware.usb.action.USB_DEVICE_DETACHED" -> {
                Log.i(TAG, "USB_DEVICE_DETACHED broadcast received")
                handleUnmount(context, null)
            }
            Intent.ACTION_MEDIA_EJECT,
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_BAD_REMOVAL,
            Intent.ACTION_MEDIA_REMOVED -> {
                Log.w(TAG, "External storage unmounted/ejected. Notifying TorrentDownloadService and StorageEvents.")
                handleUnmount(context, intent.data?.path)
            }
            Intent.ACTION_MEDIA_MOUNTED -> {
                Log.i(TAG, "External storage mounted: ${intent.data}")
                StorageEvents.notifyMounted(intent.data?.path)
            }
        }
    }

    private fun handleUnmount(context: Context, pathOrUuid: String?) {
        StorageEvents.notifyUnmounted(pathOrUuid)
        val serviceIntent = Intent(context, TorrentDownloadService::class.java).apply {
            this.action = TorrentDownloadService.ACTION_STORAGE_UNMOUNTED
        }
        try {
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch storage unmount intent to service", e)
        }
    }

    companion object {
        private const val TAG = "StorageMountReceiver"
    }
}
