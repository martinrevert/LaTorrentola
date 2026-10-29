package com.martinrevert.latorrentola.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.session.MediaController
import com.martinrevert.latorrentola.model.torrent.TorrentDownload
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.service.TorrentBufferState
import com.martinrevert.latorrentola.service.TorrentDownloadService
import com.martinrevert.latorrentola.ui.player.LocalPlayerActivity
import java.io.File

/**
 * Supported user-triggered actions from any torrent release/download card.
 */
enum class TorrentCardAction {
    START_DOWNLOAD,
    PAUSE_DOWNLOAD,
    RESUME_DOWNLOAD,
    PLAY_MEDIA,
    PAUSE_MEDIA
}

/**
 * Evaluated visual and interactive state for a torrent card.
 */
data class TorrentCardVisualState(
    val isActive: Boolean,
    val isPlaying: Boolean,
    val isDownloaded: Boolean,
    val isReady: Boolean,
    val readyProgress: Float,
    val downloadSpeedBytes: Long,
    val seeds: Int,
    val peers: Int,
    val action: TorrentCardAction,
    val canDelete: Boolean
)

object TorrentCardActionHandler {
    private const val TAG = "TorrentCardAction"

    @Volatile
    private var lastActionTimestamp = 0L

    /**
     * Checks if the user action should be debounced to prevent duplicate rapid inputs on touch and TV D-Pad.
     */
    fun shouldDebounce(minIntervalMs: Long = 400L): Boolean {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastActionTimestamp < minIntervalMs) {
                return true
            }
            lastActionTimestamp = now
            return false
        }
    }

    /**
     * Resets the debounce timestamp (useful for testing).
     */
    fun resetDebounceForTesting() {
        synchronized(this) {
            lastActionTimestamp = 0L
        }
    }

    /**
     * Resolves the visual state and primary tap action for a card.
     */
    fun resolveVisualState(
        releaseHash: String,
        releaseSeeds: Int = 0,
        releasePeers: Int = 0,
        download: TorrentDownload?,
        activeBuffer: TorrentBufferState?,
        mediaController: MediaController?
    ): TorrentCardVisualState {
        val isBufferActive = activeBuffer != null &&
            releaseHash.isNotBlank() &&
            activeBuffer.infoHash.equals(releaseHash, ignoreCase = true)

        val bufferProgress = if (isBufferActive) activeBuffer.readyProgress else 0f
        val isCompleted = download?.state == TorrentDownloadService.STATE_COMPLETED ||
            (download?.progressPercent ?: 0) >= 100

        val isReady = download?.state == TorrentDownloadService.STATE_READY || isCompleted || bufferProgress >= 1.0f

        val effectiveReadyProgress = when {
            isCompleted -> 1.0f
            isBufferActive -> bufferProgress
            isReady -> 1.0f
            download != null -> (download.progressPercent.coerceIn(0, 100)) / 100f
            else -> 0f
        }

        val curMedia = mediaController?.currentMediaItem
        val curMediaId = curMedia?.mediaId.orEmpty()
        val curInfoHash = curMedia?.mediaMetadata?.extras?.getString(LocalPlayerActivity.EXTRA_INFO_HASH).orEmpty()
        val isCurrentPlayerItem = releaseHash.isNotBlank() && (
            (curInfoHash.isNotBlank() && curInfoHash.equals(releaseHash, ignoreCase = true)) ||
            curMediaId.contains(releaseHash, ignoreCase = true)
        )
        val isMediaPlaying = isCurrentPlayerItem && (mediaController?.isPlaying == true)

        val isCurrentlyDownloading = download?.state in listOf(
            TorrentDownloadService.STATE_DOWNLOADING,
            TorrentDownloadService.STATE_BUFFERING,
            TorrentDownloadService.STATE_QUEUED
        ) || (isBufferActive && activeBuffer.state != TorrentDownloadService.STATE_PAUSED)

        val isPausedDownload = download?.state == TorrentDownloadService.STATE_PAUSED ||
            (isBufferActive && activeBuffer.state == TorrentDownloadService.STATE_PAUSED)

        val action = when {
            isReady || effectiveReadyProgress >= 1.0f -> {
                if (isMediaPlaying) TorrentCardAction.PAUSE_MEDIA else TorrentCardAction.PLAY_MEDIA
            }
            isCurrentlyDownloading -> TorrentCardAction.PAUSE_DOWNLOAD
            isPausedDownload -> TorrentCardAction.RESUME_DOWNLOAD
            else -> TorrentCardAction.START_DOWNLOAD
        }

        val seeds = if (isBufferActive && activeBuffer.numSeeds > 0) {
            activeBuffer.numSeeds
        } else {
            download?.numSeeds ?: releaseSeeds
        }
        val peers = if (isBufferActive && activeBuffer.numPeers > 0) {
            activeBuffer.numPeers
        } else {
            download?.numPeers ?: releasePeers
        }
        val speed = if (isBufferActive) activeBuffer.downloadSpeedBytes else 0L
        val isActive = isBufferActive || isCurrentlyDownloading || isCurrentPlayerItem
        val canDelete = download != null || isBufferActive || isCurrentPlayerItem

        return TorrentCardVisualState(
            isActive = isActive,
            isPlaying = isMediaPlaying,
            isDownloaded = isCompleted,
            isReady = isReady,
            readyProgress = effectiveReadyProgress,
            downloadSpeedBytes = speed,
            seeds = seeds,
            peers = peers,
            action = action,
            canDelete = canDelete
        )
    }

    /**
     * Executes the tap action for a card according to its resolved state.
     */
    fun performCardClick(
        context: Context,
        releaseHash: String,
        releaseTitle: String,
        releaseMagnetUri: String,
        releaseSizeBytes: Long?,
        download: TorrentDownload?,
        activeBuffer: TorrentBufferState?,
        mediaController: MediaController?,
        isTv: Boolean,
        torrentHandlingMode: TorrentHandlingMode,
        seriesId: Int = 0,
        seasonNumber: Int = 0,
        episodeNumber: Int = 0,
        movieId: Int = 0,
        posterUrl: String? = null,
        nextEpisodeInfo: ArrayList<String>? = null,
        onInsufficientStorage: ((Long, Long) -> Unit)? = null,
        onDownloadStarted: (() -> Unit)? = null
    ): Boolean {
        if (shouldDebounce()) {
            Log.d(TAG, "performCardClick debounced for hash: $releaseHash")
            return false
        }

        val visualState = resolveVisualState(
            releaseHash = releaseHash,
            download = download,
            activeBuffer = activeBuffer,
            mediaController = mediaController
        )

        // Preempt any other running torrent download or player if this action starts/resumes a different torrent
        if (visualState.action in listOf(TorrentCardAction.START_DOWNLOAD, TorrentCardAction.RESUME_DOWNLOAD, TorrentCardAction.PLAY_MEDIA)) {
            val curMedia = mediaController?.currentMediaItem
            val curInfoHash = curMedia?.mediaMetadata?.extras?.getString(LocalPlayerActivity.EXTRA_INFO_HASH).orEmpty()
            val isDifferentPlayer = curInfoHash.isNotBlank() && !curInfoHash.equals(releaseHash, ignoreCase = true)
            if (isDifferentPlayer) {
                try {
                    mediaController?.stop()
                    mediaController?.clearMediaItems()
                    LocalPlayerActivity.finishBackgroundPlayer(exceptInfoHash = null)
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping previous player on preemption", e)
                }
            }

            val isDifferentBuffer = activeBuffer != null &&
                activeBuffer.infoHash.isNotBlank() &&
                !activeBuffer.infoHash.equals(releaseHash, ignoreCase = true)
            if (isDifferentBuffer && activeBuffer.state != TorrentDownloadService.STATE_PAUSED) {
                val pausePrevIntent = Intent(context, TorrentDownloadService::class.java).apply {
                    action = TorrentDownloadService.ACTION_PAUSE
                    putExtra(TorrentDownloadService.EXTRA_INFO_HASH, activeBuffer.infoHash)
                }
                context.startService(pausePrevIntent)
            }
        }

        return when (visualState.action) {
            TorrentCardAction.PAUSE_DOWNLOAD -> {
                val pauseIntent = Intent(context, TorrentDownloadService::class.java).apply {
                    action = TorrentDownloadService.ACTION_PAUSE
                    putExtra(TorrentDownloadService.EXTRA_INFO_HASH, releaseHash)
                }
                context.startService(pauseIntent)
                false
            }
            TorrentCardAction.RESUME_DOWNLOAD -> {
                val resumeIntent = Intent(context, TorrentDownloadService::class.java).apply {
                    action = TorrentDownloadService.ACTION_RESUME
                    putExtra(TorrentDownloadService.EXTRA_INFO_HASH, releaseHash)
                }
                context.startService(resumeIntent)
                false
            }
            TorrentCardAction.START_DOWNLOAD -> {
                val magnetUri = releaseMagnetUri.ifBlank {
                    TorrentLaunchHelper.buildMagnetUri(releaseHash, releaseTitle)
                }
                val result = TorrentLaunchHelper.launch(
                    context = context,
                    mode = torrentHandlingMode,
                    magnetUri = magnetUri,
                    title = releaseTitle,
                    nextEpisodeInfo = nextEpisodeInfo,
                    seriesId = seriesId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    movieId = movieId,
                    posterUrl = posterUrl,
                    requiredSizeBytes = releaseSizeBytes
                )
                when (result) {
                    TorrentLaunchResult.Started -> {
                        onDownloadStarted?.invoke()
                        true
                    }
                    is TorrentLaunchResult.InsufficientStorage -> {
                        onInsufficientStorage?.invoke(result.availableBytes, result.requiredBytes)
                        false
                    }
                    else -> false
                }
            }
            TorrentCardAction.PAUSE_MEDIA -> {
                mediaController?.pause()
                false
            }
            TorrentCardAction.PLAY_MEDIA -> {
                val curMedia = mediaController?.currentMediaItem
                val curMediaId = curMedia?.mediaId.orEmpty()
                val curInfoHash = curMedia?.mediaMetadata?.extras?.getString(LocalPlayerActivity.EXTRA_INFO_HASH).orEmpty()
                val isCurrentPlayerItem = releaseHash.isNotBlank() && (
                    (curInfoHash.isNotBlank() && curInfoHash.equals(releaseHash, ignoreCase = true)) ||
                    curMediaId.contains(releaseHash, ignoreCase = true)
                )

                val readyIntent = TorrentDownloadService.getReadyPlayerIntent(releaseHash)
                val completedFile = download?.mediaPath?.let(::File)?.takeIf { it.isFile && it.canRead() }

                if (isTv) {
                    if (isCurrentPlayerItem) {
                        mediaController?.play()
                        if (!LocalPlayerActivity.isPlayerForeground) {
                            val launchIntent = Intent(context, LocalPlayerActivity::class.java).apply {
                                putExtra(LocalPlayerActivity.EXTRA_INFO_HASH, releaseHash)
                                putExtra(LocalPlayerActivity.EXTRA_TITLE, releaseTitle)
                                putExtra(LocalPlayerActivity.EXTRA_POSTER_URL, posterUrl)
                                if (seriesId > 0) putExtra(LocalPlayerActivity.EXTRA_SERIES_ID, seriesId)
                                if (seasonNumber > 0) putExtra(LocalPlayerActivity.EXTRA_SEASON_NUMBER, seasonNumber)
                                if (episodeNumber > 0) putExtra(LocalPlayerActivity.EXTRA_EPISODE_NUMBER, episodeNumber)
                                if (movieId > 0) putExtra(LocalPlayerActivity.EXTRA_MOVIE_ID, movieId)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(launchIntent)
                        }
                    } else if (readyIntent != null) {
                        val launchIntent = Intent(readyIntent).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(launchIntent)
                    } else if (completedFile != null) {
                        val launchIntent = Intent(context, LocalPlayerActivity::class.java).apply {
                            putExtra(LocalPlayerActivity.EXTRA_FILE_PATH, completedFile.absolutePath)
                            putExtra(LocalPlayerActivity.EXTRA_INFO_HASH, releaseHash)
                            putExtra(LocalPlayerActivity.EXTRA_TITLE, releaseTitle)
                            putExtra(LocalPlayerActivity.EXTRA_POSTER_URL, posterUrl)
                            if (seriesId > 0) putExtra(LocalPlayerActivity.EXTRA_SERIES_ID, seriesId)
                            if (seasonNumber > 0) putExtra(LocalPlayerActivity.EXTRA_SEASON_NUMBER, seasonNumber)
                            if (episodeNumber > 0) putExtra(LocalPlayerActivity.EXTRA_EPISODE_NUMBER, episodeNumber)
                            if (movieId > 0) putExtra(LocalPlayerActivity.EXTRA_MOVIE_ID, movieId)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(launchIntent)
                    } else {
                        val serviceIntent = Intent(context, TorrentDownloadService::class.java).apply {
                            action = TorrentDownloadService.ACTION_PLAY_STREAM
                            putExtra(TorrentDownloadService.EXTRA_INFO_HASH, releaseHash)
                            putExtra(TorrentDownloadService.EXTRA_TITLE, releaseTitle)
                            if (seriesId > 0) putExtra(TorrentDownloadService.EXTRA_SERIES_ID, seriesId)
                            if (seasonNumber > 0) putExtra(TorrentDownloadService.EXTRA_SEASON_NUMBER, seasonNumber)
                            if (episodeNumber > 0) putExtra(TorrentDownloadService.EXTRA_EPISODE_NUMBER, episodeNumber)
                            if (movieId > 0) putExtra(TorrentDownloadService.EXTRA_MOVIE_ID, movieId)
                            if (!posterUrl.isNullOrBlank()) putExtra(TorrentDownloadService.EXTRA_POSTER_URL, posterUrl)
                        }
                        context.startService(serviceIntent)
                    }
                } else {
                    if (isCurrentPlayerItem) {
                        mediaController?.play()
                    } else if (readyIntent != null) {
                        val mediaItem = PlayerIntentUtils.buildMediaItem(readyIntent)
                        mediaController?.setMediaItem(mediaItem)
                        mediaController?.prepare()
                        mediaController?.play()
                    } else if (completedFile != null && download != null) {
                        val mediaItem = PlayerIntentUtils.buildMediaItem(download, completedFile)
                        mediaController?.setMediaItem(mediaItem)
                        mediaController?.prepare()
                        mediaController?.play()
                    } else {
                        val serviceIntent = Intent(context, TorrentDownloadService::class.java).apply {
                            action = TorrentDownloadService.ACTION_PLAY_STREAM
                            putExtra(TorrentDownloadService.EXTRA_INFO_HASH, releaseHash)
                            putExtra(TorrentDownloadService.EXTRA_TITLE, releaseTitle)
                            if (seriesId > 0) putExtra(TorrentDownloadService.EXTRA_SERIES_ID, seriesId)
                            if (seasonNumber > 0) putExtra(TorrentDownloadService.EXTRA_SEASON_NUMBER, seasonNumber)
                            if (episodeNumber > 0) putExtra(TorrentDownloadService.EXTRA_EPISODE_NUMBER, episodeNumber)
                            if (movieId > 0) putExtra(TorrentDownloadService.EXTRA_MOVIE_ID, movieId)
                            if (!posterUrl.isNullOrBlank()) putExtra(TorrentDownloadService.EXTRA_POSTER_URL, posterUrl)
                        }
                        context.startService(serviceIntent)
                    }
                }
                true
            }
        }
    }

    /**
     * Stops playback and sends a request to delete and purge the torrent.
     */
    fun performCardDelete(
        context: Context,
        releaseHash: String,
        mediaController: MediaController?
    ) {
        try {
            val curMedia = mediaController?.currentMediaItem
            val curMediaId = curMedia?.mediaId.orEmpty()
            val curInfoHash = curMedia?.mediaMetadata?.extras?.getString(LocalPlayerActivity.EXTRA_INFO_HASH).orEmpty()
            val isCurrentPlayerItem = (curInfoHash.isNotBlank() && curInfoHash.equals(releaseHash, ignoreCase = true)) ||
                curMediaId.contains(releaseHash, ignoreCase = true)

            if (isCurrentPlayerItem) {
                mediaController?.stop()
                mediaController?.clearMediaItems()
                LocalPlayerActivity.finishBackgroundPlayer(onlyInfoHash = releaseHash)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping player before delete", e)
        }

        val deleteIntent = Intent(context, TorrentDownloadService::class.java).apply {
            action = TorrentDownloadService.ACTION_DELETE
            putExtra(TorrentDownloadService.EXTRA_INFO_HASH, releaseHash)
        }
        context.startService(deleteIntent)
    }
}
