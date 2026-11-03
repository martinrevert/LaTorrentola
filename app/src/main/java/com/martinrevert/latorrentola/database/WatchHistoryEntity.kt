package com.martinrevert.latorrentola.database

import androidx.room.Entity

/**
 * Room entity caching playback progress locally for offline watch history recovery.
 *
 * @property userId Account ID owning this progress entry, preserving per-user history across sessions.
 * @property mediaId Unique media identifier (canonical movie ID or episode string).
 * @property title User-visible title of the movie or episode.
 * @property positionMs Last playback position in milliseconds.
 * @property durationMs Total duration of the media in milliseconds.
 * @property timestamp Last updated timestamp in milliseconds.
 * @property isEpisode Whether this progress item refers to a TV episode (true) or movie (false).
 */
@Entity(
    tableName = "watch_history",
    primaryKeys = ["userId", "mediaId"]
)
data class WatchHistoryEntity(
    val userId: String = "",
    val mediaId: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val timestamp: Long,
    val isEpisode: Boolean
)
