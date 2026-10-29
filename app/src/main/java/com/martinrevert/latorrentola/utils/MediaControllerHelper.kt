package com.martinrevert.latorrentola.utils

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.ServiceConnection
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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

    /**
     * Remembers a playback state tick counter for an optional [MediaController] so leaf composables
     * recompose whenever `isPlaying`, `playbackState`, or `currentMediaItem` changes.
     */
    @Composable
    fun rememberPlaybackTick(mediaController: MediaController?): Int {
        val playbackTick = remember(mediaController) { mutableIntStateOf(0) }
        DisposableEffect(mediaController) {
            if (mediaController == null) return@DisposableEffect onDispose {}
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playbackTick.intValue++
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    playbackTick.intValue++
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    playbackTick.intValue++
                }
            }
            mediaController.addListener(listener)
            onDispose {
                mediaController.removeListener(listener)
            }
        }
        return playbackTick.intValue
    }

    /**
     * Remembers a MediaController in Compose that triggers recomposition on playback state changes.
     */
    @Composable
    fun rememberMediaController(): State<MediaController?> {
        val context = LocalContext.current
        val controllerState = remember { mutableStateOf<MediaController?>(null) }
        val playbackTick = remember { mutableIntStateOf(0) }

        DisposableEffect(context) {
            val controllerFuture = buildControllerAsync(context)
            var playerListener: Player.Listener? = null
            var controllerRef: MediaController? = null

            controllerFuture.addListener({
                try {
                    val controller = controllerFuture.get()
                    controllerRef = controller
                    controllerState.value = controller

                    val listener = object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            playbackTick.intValue++
                        }

                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int
                        ) {
                            playbackTick.intValue++
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            playbackTick.intValue++
                        }
                    }
                    playerListener = listener
                    controller.addListener(listener)
                } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                controllerRef?.let { c ->
                    playerListener?.let { l -> c.removeListener(l) }
                }
                releaseFuture(controllerFuture)
                controllerState.value = null
            }
        }

        return remember {
            derivedStateOf(neverEqualPolicy()) {
                playbackTick.intValue
                controllerState.value
            }
        }
    }
}
