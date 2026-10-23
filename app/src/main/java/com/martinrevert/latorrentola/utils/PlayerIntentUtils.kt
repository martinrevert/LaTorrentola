@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.martinrevert.latorrentola.utils

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.martinrevert.latorrentola.model.torrent.TorrentDownload
import com.martinrevert.latorrentola.service.PlaybackService
import com.martinrevert.latorrentola.ui.player.LocalPlayerActivity
import java.io.File

/**
 * Utility helper to construct consistent, canonical MediaItem instances for ExoPlayer
 * from launch Intents and TorrentDownload records.
 */
object PlayerIntentUtils {

    /**
     * Builds a canonical MediaItem from a LocalPlayerActivity launch Intent.
     */
    fun buildMediaItem(intent: Intent): MediaItem {
        val streamUrl = intent.getStringExtra(LocalPlayerActivity.EXTRA_STREAM_URL).orEmpty()
        val filePath = intent.getStringExtra(LocalPlayerActivity.EXTRA_FILE_PATH)
        val file = filePath?.let(::File)
        val isPartialStream = intent.getBooleanExtra(LocalPlayerActivity.EXTRA_PARTIAL_TORRENT_STREAM, false)
        val mediaUri = if (file != null && file.isFile && file.canRead() && !isPartialStream) {
            Uri.fromFile(file)
        } else {
            streamUrl.takeIf(String::isNotBlank)?.let(Uri::parse)
                ?: file?.takeIf { it.isFile && it.canRead() }?.let { Uri.fromFile(it) }
                ?: Uri.EMPTY
        }
        val title = intent.getStringExtra(LocalPlayerActivity.EXTRA_TITLE).orEmpty()
        val posterUrl = intent.getStringExtra(LocalPlayerActivity.EXTRA_POSTER_URL)
        val seriesId = intent.getIntExtra(LocalPlayerActivity.EXTRA_SERIES_ID, 0)
        val seasonNumber = intent.getIntExtra(LocalPlayerActivity.EXTRA_SEASON_NUMBER, 0)
        val episodeNumber = intent.getIntExtra(LocalPlayerActivity.EXTRA_EPISODE_NUMBER, 0)
        val movieId = intent.getIntExtra(LocalPlayerActivity.EXTRA_MOVIE_ID, 0)
        val mimeType = intent.getStringExtra(LocalPlayerActivity.EXTRA_MIME_TYPE) ?: file?.mediaMimeType()

        val isEpisode = seriesId > 0
        val mediaId = if (isEpisode) {
            MediaIdHelper.forEpisode(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                title = title
            )
        } else {
            MediaIdHelper.forMovie(
                movieId = movieId,
                title = title
            )
        }

        val itemBundle = Bundle().apply {
            putBoolean(PlaybackService.EXTRA_IS_EPISODE, isEpisode)
            val castUrl = intent.getStringExtra(LocalPlayerActivity.EXTRA_CAST_URL)
            if (!castUrl.isNullOrBlank()) {
                putString(PlaybackService.EXTRA_CAST_URL, castUrl)
            }
            if (streamUrl.isNotBlank()) {
                putString(LocalPlayerActivity.EXTRA_STREAM_URL, streamUrl)
            }
            if (!filePath.isNullOrBlank()) {
                putString(LocalPlayerActivity.EXTRA_FILE_PATH, filePath)
            }
            val infoHash = intent.getStringExtra(LocalPlayerActivity.EXTRA_INFO_HASH)
            if (!infoHash.isNullOrBlank()) {
                putString(LocalPlayerActivity.EXTRA_INFO_HASH, infoHash)
            }
            if (movieId > 0) {
                putInt(LocalPlayerActivity.EXTRA_MOVIE_ID, movieId)
            }
            if (seriesId > 0) {
                putInt(LocalPlayerActivity.EXTRA_SERIES_ID, seriesId)
            }
            if (seasonNumber > 0) {
                putInt(LocalPlayerActivity.EXTRA_SEASON_NUMBER, seasonNumber)
            }
            if (episodeNumber > 0) {
                putInt(LocalPlayerActivity.EXTRA_EPISODE_NUMBER, episodeNumber)
            }
            if (!mimeType.isNullOrBlank()) {
                putString(LocalPlayerActivity.EXTRA_MIME_TYPE, mimeType)
            }
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setArtworkUri(posterUrl?.takeIf(String::isNotBlank)?.let(Uri::parse))
            .setExtras(itemBundle)
            .build()

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(mediaUri)
            .setMimeType(mimeType)
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Builds a canonical MediaItem directly from a completed TorrentDownload record and File.
     */
    fun buildMediaItem(download: TorrentDownload, file: File): MediaItem {
        val (detectedS, detectedE) = LocalPlayerActivity.extractSeasonAndEpisode(download.title)
        val effSeason = download.seasonNumber ?: detectedS.takeIf { it > 0 } ?: 0
        val effEpisode = download.episodeNumber ?: detectedE.takeIf { it > 0 } ?: 0
        val isEpisode = (download.seriesId ?: 0) > 0

        val mediaId = if (isEpisode) {
            MediaIdHelper.forEpisode(
                seriesId = download.seriesId ?: 0,
                seasonNumber = effSeason,
                episodeNumber = effEpisode,
                title = download.title
            )
        } else {
            MediaIdHelper.forMovie(
                movieId = download.movieId ?: 0,
                title = download.title
            )
        }

        val itemBundle = Bundle().apply {
            putBoolean(PlaybackService.EXTRA_IS_EPISODE, isEpisode)
            putString(LocalPlayerActivity.EXTRA_FILE_PATH, file.absolutePath)
            if (download.infoHash.isNotBlank()) {
                putString(LocalPlayerActivity.EXTRA_INFO_HASH, download.infoHash)
            }
            if ((download.movieId ?: 0) > 0) {
                putInt(LocalPlayerActivity.EXTRA_MOVIE_ID, download.movieId ?: 0)
            }
            if ((download.seriesId ?: 0) > 0) {
                putInt(LocalPlayerActivity.EXTRA_SERIES_ID, download.seriesId ?: 0)
            }
            if (effSeason > 0) {
                putInt(LocalPlayerActivity.EXTRA_SEASON_NUMBER, effSeason)
            }
            if (effEpisode > 0) {
                putInt(LocalPlayerActivity.EXTRA_EPISODE_NUMBER, effEpisode)
            }
            if (!download.posterUrl.isNullOrBlank()) {
                putString(LocalPlayerActivity.EXTRA_POSTER_URL, download.posterUrl)
            }
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(download.title)
            .setDisplayTitle(download.title)
            .setArtworkUri(download.posterUrl?.takeIf(String::isNotBlank)?.let(Uri::parse))
            .setExtras(itemBundle)
            .build()

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(Uri.fromFile(file))
            .setMimeType(file.mediaMimeType())
            .setMediaMetadata(metadata)
            .build()
    }
}
