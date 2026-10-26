package com.martinrevert.latorrentola.utils

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.ServiceConnection
import android.util.Log
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.martinrevert.latorrentola.service.PlaybackService

/**
 * ContextWrapper that wraps applicationContext and suppresses
 * IllegalArgumentException: Service not registered during unbindService.
 *
 * This prevents crashes in MediaControllerImplBase during Activity lifecycle teardown
 * when the service connection is unbound asynchronously after LoadedApk has already
 * performed context registration cleanup, or when Android OS disconnects the service prematurely.
 */
class SafeMediaControllerContext(base: Context) : ContextWrapper(base.applicationContext ?: base) {
    val targetContext: Context = base.applicationContext ?: base

    override fun unbindService(conn: ServiceConnection) {
        try {
            targetContext.unbindService(conn)
        } catch (e: IllegalArgumentException) {
            Log.w("SafeMediaController", "Ignored unbindService IllegalArgumentException: ${e.message}")
        } catch (e: Exception) {
            Log.w("SafeMediaController", "Ignored unexpected error in unbindService: ${e.message}")
        }
    }
}

object MediaControllerHelper {
    /**
     * Creates a SafeMediaControllerContext for building MediaControllers safely.
     */
    fun createSafeContext(context: Context): SafeMediaControllerContext = SafeMediaControllerContext(context)

    /**
     * Builds a MediaController ListenableFuture safely bound to applicationContext with unbindService exception safety.
     */
    fun buildControllerAsync(context: Context): ListenableFuture<MediaController> {
        val safeContext = createSafeContext(context)
        val sessionToken = SessionToken(safeContext, ComponentName(safeContext, PlaybackService::class.java))
        return MediaController.Builder(safeContext, sessionToken).buildAsync()
    }

    /**
     * Safely releases a controller future without leaking or crashing.
     */
    fun releaseFuture(future: ListenableFuture<MediaController>?) {
        if (future != null) {
            try {
                MediaController.releaseFuture(future)
            } catch (e: Exception) {
                Log.w("SafeMediaController", "Error releasing MediaController future: ${e.message}")
            }
        }
    }
}
