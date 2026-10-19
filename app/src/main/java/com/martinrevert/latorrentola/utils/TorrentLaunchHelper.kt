package com.martinrevert.latorrentola.utils

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.service.TorrentDownloadService
import java.net.URLEncoder

/** Starts a torrent using the selected handling mode without exposing partial files. */
object TorrentLaunchHelper {

    /** Minimum required free disk space in bytes (1 GB). */
    const val MIN_REQUIRED_STORAGE_BYTES = 1_000_000_000L

    /**
     * Returns the available storage space in bytes for the active storage directory.
     *
     * @param context App context used to locate active storage.
     * @return Free disk space in bytes.
     */
    fun getAvailableStorageBytes(context: Context): Long {
        return com.martinrevert.latorrentola.service.storage.TorrentStorageResolver.getAvailableStorageBytes(context)
    }

    /**
     * Verifies if the available disk space meets the 1 GB minimum requirement and optional file size.
     *
     * @param context App context.
     * @param requiredSizeBytes Optional video release size in bytes.
     * @return `true` if free disk space is greater than or equal to the required bytes.
     */
    fun hasEnoughStorage(context: Context, requiredSizeBytes: Long? = null): Boolean {
        val available = getAvailableStorageBytes(context)
        val required = maxOf(MIN_REQUIRED_STORAGE_BYTES, requiredSizeBytes ?: 0L)
        return available >= required
    }

    /**
     * Builds the app's standard magnet URI for a torrent hash and human-readable title.
     *
     * @param hash Torrent info hash.
     * @param title Human-readable media title.
     * @return Formatted BTIH magnet URI with the app's standard trackers.
     */
    fun buildMagnetUri(hash: String, title: String): String {
        val encodedTitle = URLEncoder.encode(title, Charsets.UTF_8.name())
        return "magnet:?xt=urn:btih:$hash&dn=$encodedTitle" +
            "&tr=udp://open.demonii.com:1337/announce" +
            "&tr=udp://tracker.openbittorrent.com:80"
    }

    /**
     * Opens [magnetUri] directly in another app, without an intent chooser.
     *
     * @param context Context used to launch the activity.
     * @param magnetUri Valid magnet URI to hand off.
     * @return `true` if a compatible app accepted the intent.
     */
    fun openExternal(context: Context, magnetUri: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnetUri)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /**
     * Queues the magnet for a retained, app-private, complete-file download.
     *
     * @param context Context used to start the foreground download service.
     * @param magnetUri Valid hexadecimal-BTIH magnet URI.
     * @param title User-visible media title.
     * @param castWhenReady Whether to expose verified media ranges to a Cast receiver.
     * @param nextEpisodeInfo Optional next episode metadata passed to the player upon completion.
     */
    fun downloadLocally(
        context: Context,
        magnetUri: String,
        title: String,
        castWhenReady: Boolean,
        nextEpisodeInfo: ArrayList<String>? = null,
        seriesId: Int = 0,
        seasonNumber: Int = 0,
        episodeNumber: Int = 0,
        movieId: Int = 0
    ) {
        val intent = Intent(context, TorrentDownloadService::class.java).apply {
            action = TorrentDownloadService.ACTION_DOWNLOAD
            putExtra(TorrentDownloadService.EXTRA_MAGNET_URI, magnetUri)
            putExtra(TorrentDownloadService.EXTRA_TITLE, title)
            putExtra(TorrentDownloadService.EXTRA_CAST_WHEN_READY, castWhenReady)
            putExtra(TorrentDownloadService.EXTRA_SERIES_ID, seriesId)
            putExtra(TorrentDownloadService.EXTRA_SEASON_NUMBER, seasonNumber)
            putExtra(TorrentDownloadService.EXTRA_EPISODE_NUMBER, episodeNumber)
            putExtra(TorrentDownloadService.EXTRA_MOVIE_ID, movieId)
            if (nextEpisodeInfo != null) {
                putStringArrayListExtra(TorrentDownloadService.EXTRA_NEXT_EPISODE_INFO, nextEpisodeInfo)
            }
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * Dispatches a torrent according to the selected mode and reports unsupported modes.
     *
     * @param context Context used to perform the selected launch action.
     * @param mode User-selected torrent handling behavior.
     * @param magnetUri Magnet URI for the release.
     * @param title User-visible media title.
     * @param nextEpisodeInfo Optional next episode metadata passed to the player upon completion.
     * @param seriesId TV series ID.
     * @param seasonNumber Current season number.
     * @param episodeNumber Current episode number.
     * @param movieId Movie ID.
     * @param requiredSizeBytes Optional size in bytes of the torrent release.
     * @return Result describing whether the request started or why it was rejected.
     */
    fun launch(
        context: Context,
        mode: TorrentHandlingMode,
        magnetUri: String,
        title: String,
        nextEpisodeInfo: ArrayList<String>? = null,
        seriesId: Int = 0,
        seasonNumber: Int = 0,
        episodeNumber: Int = 0,
        movieId: Int = 0,
        requiredSizeBytes: Long? = null
    ): TorrentLaunchResult {
        if (mode == TorrentHandlingMode.LOCAL_PLAYBACK || mode == TorrentHandlingMode.CHROMECAST) {
            val required = maxOf(MIN_REQUIRED_STORAGE_BYTES, requiredSizeBytes ?: 0L)
            val available = getAvailableStorageBytes(context)
            if (available < required) {
                return TorrentLaunchResult.InsufficientStorage(availableBytes = available, requiredBytes = required)
            }
            if (requiredSizeBytes != null) {
                val targetDir = com.martinrevert.latorrentola.service.storage.TorrentStorageResolver.resolveStorageDirectory(context)
                if (!com.martinrevert.latorrentola.service.storage.StorageVolumeInspector.canStoreFileSize(targetDir, requiredSizeBytes)) {
                    return TorrentLaunchResult.Fat32LimitExceeded
                }
            }
        }
        return when (mode) {
            TorrentHandlingMode.EXTERNAL_CLIENT ->
                if (openExternal(context, magnetUri)) TorrentLaunchResult.Started
                else TorrentLaunchResult.NoExternalClient
            TorrentHandlingMode.LOCAL_PLAYBACK -> {
                downloadLocally(context, magnetUri, title, false, nextEpisodeInfo, seriesId, seasonNumber, episodeNumber, movieId)
                TorrentLaunchResult.Started
            }
            TorrentHandlingMode.CHROMECAST -> {
                downloadLocally(context, magnetUri, title, true, nextEpisodeInfo, seriesId, seasonNumber, episodeNumber, movieId)
                TorrentLaunchResult.Started
            }
        }
    }
}

/** Result of attempting to hand a torrent to a selected playback mode. */
sealed interface TorrentLaunchResult {
    /** The requested mode accepted the torrent. */
    data object Started : TorrentLaunchResult
    /** The device has no app that can handle a magnet URI. */
    data object NoExternalClient : TorrentLaunchResult
    /** No external client accepted a magnet URI. */
    data object CastUnavailable : TorrentLaunchResult
    /** Free storage space is lower than required (1 GB minimum). */
    data class InsufficientStorage(val availableBytes: Long, val requiredBytes: Long) : TorrentLaunchResult
    /** Target volume is formatted with FAT32 and the file size exceeds 4 GiB. */
    data object Fat32LimitExceeded : TorrentLaunchResult
}
