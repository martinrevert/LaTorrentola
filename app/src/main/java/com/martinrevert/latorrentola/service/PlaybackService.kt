package com.martinrevert.latorrentola.service

import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.android.gms.cast.framework.CastContext
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.network.UserLibraryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Manages background media playback and Cast session transfers.
 * Holds the ExoPlayer, RemoteCastPlayer, and acts as the proxy via CastPlayer.
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var localPlayer: ExoPlayer? = null
    private var remotePlayer: CastPlayer? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressReportingJob: Job? = null

    @Inject
    lateinit var userLibraryRepository: UserLibraryRepository

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        try {
            // Build the local player
            val exoPlayer = ExoPlayer.Builder(this).build()
            localPlayer = exoPlayer

            // Build the CastPlayer to handle local vs remote handoff automatically
            // CastPlayer defaults to checking CastContext implicitly if available
            val castPlayerBuilder = CastPlayer.Builder(this)
                .setLocalPlayer(exoPlayer)
            
            val mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(this)
            
            // Note: In Media3 1.11+, RemoteCastPlayer is built implicitly by CastPlayer.Builder
            // if we just build it. However, because of CastContext async loading, we do it safely:
            try {
                CastContext.getSharedInstance(this, mainExecutor)
                    .addOnSuccessListener {
                        Log.d(TAG, "CastContext initialized successfully in PlaybackService")
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "CastContext failed to initialize", it)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing CastContext", e)
            }

            val proxyPlayer = castPlayerBuilder.build()
            remotePlayer = proxyPlayer

            proxyPlayer.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying) {
                        saveCurrentProgress(proxyPlayer)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                        saveCurrentProgress(proxyPlayer)
                    }
                }
            })

            // Wrap the CastPlayer (which switches internally) into the MediaSession
            mediaSession = MediaSession.Builder(this, proxyPlayer).build()

            // Setup playback progress reporting
            startProgressReporting(proxyPlayer)

            Log.d(TAG, "PlaybackService created with MediaSession")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PlaybackService", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed. Stopping playback.")
        saveCurrentProgress(mediaSession?.player)
        mediaSession?.player?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "PlaybackService destroying")
        progressReportingJob?.cancel()
        saveCurrentProgress(mediaSession?.player)
        
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun startProgressReporting(player: Player) {
        progressReportingJob?.cancel()
        progressReportingJob = serviceScope.launch {
            while (true) {
                delay(10_000L)
                if (player.isPlaying) {
                    saveCurrentProgress(player)
                }
            }
        }
    }

    private fun saveCurrentProgress(player: Player?) {
        val p = player ?: return
        val pos = p.currentPosition
        val dur = p.duration
        if (dur <= 0L) return

        // We embed our metadata in the MediaItem's MediaMetadata bundle or ID
        val item = p.currentMediaItem ?: return
        val mediaId = item.mediaId
        if (mediaId.isBlank() || mediaId == MediaItem.DEFAULT_MEDIA_ID) return

        val title = item.mediaMetadata.title?.toString() ?: "Media Item"
        val extras = item.mediaMetadata.extras
        val isEpisode = extras?.getBoolean(EXTRA_IS_EPISODE, false) ?: false

        val progress = PlaybackProgress(
            mediaId = mediaId,
            title = title,
            positionMs = pos,
            durationMs = dur,
            timestamp = System.currentTimeMillis(),
            isEpisode = isEpisode
        )

        serviceScope.launch {
            try {
                userLibraryRepository.savePlaybackProgress(progress)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving playback progress", e)
            }
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        const val EXTRA_IS_EPISODE = "extra_is_episode"
    }
}
