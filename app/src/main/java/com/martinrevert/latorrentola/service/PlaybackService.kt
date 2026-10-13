package com.martinrevert.latorrentola.service

import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.CastTrackSelector
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastContext
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.utils.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

import java.io.File

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

    @Inject
    lateinit var preferenceManager: PreferenceManager

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        try {
            // Build the local player
            val exoPlayer = ExoPlayer.Builder(this).build()
            localPlayer = exoPlayer

            val trackSelector = object : CastTrackSelector() {
                override fun evaluate(request: CastTrackSelectorRequest): CastTrackSelectorResult {
                    val builder = request.buildResultUpon()
                    val params = request.trackSelectionParameters

                    // If text is disabled, clear text selections
                    if (params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
                        val newSelections = request.currentlySelectedTrackGroups.filter { it.type != C.TRACK_TYPE_TEXT }
                        builder.setSelections(com.google.common.collect.ImmutableSet.copyOf(newSelections))
                        return builder.build()
                    }

                    // Find the preferred text track from ExoPlayer parameters
                    val textTrackGroups = request.trackGroupList.filter { it.type == C.TRACK_TYPE_TEXT }
                    if (textTrackGroups.isEmpty()) return builder.build()

                    var targetGroup: androidx.media3.common.TrackGroup? = null
                    
                    // 1. Check direct override
                    val override = params.overrides.values.firstOrNull { it.mediaTrackGroup.type == C.TRACK_TYPE_TEXT }
                    if (override != null) {
                        targetGroup = override.mediaTrackGroup
                    } else {
                        // 2. Check preferred language
                        val prefLang = params.preferredTextLanguages.firstOrNull()
                        if (prefLang != null) {
                            targetGroup = textTrackGroups.firstOrNull { 
                                it.getFormat(0).language?.equals(prefLang, ignoreCase = true) == true 
                            }
                        }
                    }
                    
                    // 3. Fallback to first available text track
                    if (targetGroup == null) {
                        targetGroup = textTrackGroups.first()
                    }

                    val selectedGroups = request.currentlySelectedTrackGroups.filter { it.type != C.TRACK_TYPE_TEXT }.toMutableSet()
                    selectedGroups.add(targetGroup)
                    builder.setSelections(com.google.common.collect.ImmutableSet.copyOf(selectedGroups))
                    
                    return builder.build()
                }
            }

            val remotePlayerInstance = RemoteCastPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setMediaItemConverter(object : androidx.media3.cast.MediaItemConverter {
                    val defaultConverter = androidx.media3.cast.DefaultMediaItemConverter()
                    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
                        return defaultConverter.toMediaItem(mediaQueueItem)
                    }
                    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
                        val castUrl = mediaItem.mediaMetadata.extras?.getString(EXTRA_CAST_URL)
                        val uriStr = mediaItem.localConfiguration?.uri?.toString().orEmpty()
                        val lanIp = VerifiedTorrentHttpServer.getLanIpAddress()

                        val targetUrl = when {
                            !castUrl.isNullOrBlank() && !castUrl.contains("127.0.0.1") && !castUrl.contains("localhost") -> castUrl
                            !lanIp.isNullOrBlank() && (uriStr.contains("127.0.0.1") || uriStr.contains("localhost")) ->
                                uriStr.replace("127.0.0.1", lanIp).replace("localhost", lanIp)
                            else -> uriStr
                        }

                        Log.d(TAG, "Converting MediaItem for Cast: targetUrl=$targetUrl")

                        val mime = when {
                            uriStr.endsWith(".webm", true) -> "video/webm"
                            else -> "video/mp4"
                        }

                        val castMediaItem = mediaItem.buildUpon()
                            .setUri(targetUrl)
                            .setMimeType(mime)
                            .build()

                        return defaultConverter.toMediaQueueItem(castMediaItem)
                    }
                })
                .build()

            // Build the CastPlayer to handle local vs remote handoff automatically
            // CastPlayer defaults to checking CastContext implicitly if available
            val castPlayerBuilder = CastPlayer.Builder(this)
                .setLocalPlayer(exoPlayer)
                .setRemotePlayer(remotePlayerInstance)
            
            val mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(this)
            
            // Note: In Media3 1.11+, RemoteCastPlayer is built implicitly by CastPlayer.Builder
            // if we just build it. However, because of CastContext async loading, we do it safely:
            try {
                CastContext.getSharedInstance(this, mainExecutor)
                    .addOnSuccessListener { castContext ->
                        Log.d(TAG, "CastContext initialized successfully in PlaybackService")
                        castContext.sessionManager.addSessionManagerListener(
                            object : com.google.android.gms.cast.framework.SessionManagerListener<com.google.android.gms.cast.framework.CastSession> {
                                override fun onSessionStarted(session: com.google.android.gms.cast.framework.CastSession, sessionId: String) {
                                    applyCastSubtitleStyle(session)
                                }
                                override fun onSessionResumed(session: com.google.android.gms.cast.framework.CastSession, wasSuspended: Boolean) {
                                    applyCastSubtitleStyle(session)
                                }
                                override fun onSessionStarting(session: com.google.android.gms.cast.framework.CastSession) {}
                                override fun onSessionResuming(session: com.google.android.gms.cast.framework.CastSession, sessionId: String) {}
                                override fun onSessionEnded(session: com.google.android.gms.cast.framework.CastSession, error: Int) {}
                                override fun onSessionEnding(session: com.google.android.gms.cast.framework.CastSession) {}
                                override fun onSessionStartFailed(session: com.google.android.gms.cast.framework.CastSession, error: Int) {}
                                override fun onSessionResumeFailed(session: com.google.android.gms.cast.framework.CastSession, error: Int) {}
                                override fun onSessionSuspended(session: com.google.android.gms.cast.framework.CastSession, reason: Int) {}
                            },
                            com.google.android.gms.cast.framework.CastSession::class.java
                        )
                        applyCastSubtitleStyle(castContext.sessionManager.currentCastSession)
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

    private fun applyCastSubtitleStyle(session: com.google.android.gms.cast.framework.CastSession?) {
        val client = session?.remoteMediaClient ?: return

        client.registerCallback(object : com.google.android.gms.cast.framework.media.RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                if (client.hasMediaSession()) {
                    sendTextTrackStyle(client)
                }
            }
            override fun onQueueStatusUpdated() {
                if (client.hasMediaSession()) {
                    sendTextTrackStyle(client)
                }
            }
        })

        if (client.hasMediaSession()) {
            sendTextTrackStyle(client)
        }
    }

    private fun sendTextTrackStyle(client: com.google.android.gms.cast.framework.media.RemoteMediaClient) {
        try {
            val fg = preferenceManager.getSubtitleForegroundColor()
            val bg = preferenceManager.getSubtitleBackgroundColor()
            val edge = preferenceManager.getSubtitleEdgeType()
            val size = preferenceManager.getSubtitleSizeFraction()

            val castBgColor = if (bg == android.graphics.Color.TRANSPARENT || android.graphics.Color.alpha(bg) == 0) {
                android.graphics.Color.argb(1, 0, 0, 0) // 0x01000000: Non-zero alpha so Cast SDK doesn't treat it as COLOR_UNSPECIFIED (0)
            } else {
                bg
            }

            val textTrackStyle = com.google.android.gms.cast.TextTrackStyle().apply {
                foregroundColor = fg
                backgroundColor = castBgColor
                windowType = com.google.android.gms.cast.TextTrackStyle.WINDOW_TYPE_NONE
                windowColor = android.graphics.Color.argb(1, 0, 0, 0)
                edgeType = when (edge) {
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE -> com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_OUTLINE
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW -> com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_DROP_SHADOW
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_RAISED -> com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_RAISED
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DEPRESSED -> com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_DEPRESSED
                    else -> com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_NONE
                }
                edgeColor = android.graphics.Color.BLACK
                fontScale = (size / 0.0533f).coerceIn(0.5f, 2.5f)
                fontGenericFamily = com.google.android.gms.cast.TextTrackStyle.FONT_FAMILY_SANS_SERIF
            }
            client.setTextTrackStyle(textTrackStyle)
            Log.i(TAG, "Applied custom TextTrackStyle to active Cast session")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply Cast subtitle style: ${e.message}")
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
        const val EXTRA_CAST_URL = "extra_cast_url"
    }
}
