package com.martinrevert.latorrentola.model.torrent

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Locally stored transfer metadata, separate from user-scoped download history. */
@Entity(tableName = "torrent_downloads")
data class TorrentDownload(
    /** Normalized torrent info hash used as the stable local job identifier. */
    @PrimaryKey val infoHash: String,
    /** Magnet used to recover and resume the transfer. */
    val magnetUri: String,
    /** User-visible media name. */
    val title: String,
    /** Current transfer state. */
    val state: String,
    /** Last observed progress as an integer percentage. */
    val progressPercent: Int,
    /** App-private path to the selected video file, when metadata is available. */
    val mediaPath: String?,
    /** Whether the user selected the Chromecast playback mode for this torrent. */
    val castWhenReady: Boolean,
    /** Last state update time in Unix milliseconds. */
    val updatedAtMillis: Long,
    /** Optional TV series ID for this download. */
    val seriesId: Int? = null,
    /** Optional TV series season number for this download. */
    val seasonNumber: Int? = null,
    /** Optional TV series episode number for this download. */
    val episodeNumber: Int? = null,
    /** Optional Movie ID for this download. */
    val movieId: Int? = null
)
