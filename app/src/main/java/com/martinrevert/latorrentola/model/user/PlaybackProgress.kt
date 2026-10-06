package com.martinrevert.latorrentola.model.user

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

/**
 * Represents the last playback position and progress for a movie or TV episode,
 * enabling cross-device resume and watch history synchronization.
 *
 * @property mediaId Unique media identifier (movie ID or episode info hash).
 * @property title User-visible title of the movie or episode.
 * @property positionMs Last playback position in milliseconds.
 * @property durationMs Total duration of the media in milliseconds.
 * @property timestamp Last updated timestamp in milliseconds.
 * @property isEpisode Whether this progress item refers to a TV episode (true) or movie (false).
 */
@IgnoreExtraProperties
@Serializable
data class PlaybackProgress(
    @SerializedName("media_id")
    val mediaId: String = "",
    @SerializedName("title")
    val title: String = "",
    @SerializedName("position_ms")
    val positionMs: Long = 0L,
    @SerializedName("duration_ms")
    val durationMs: Long = 0L,
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("is_episode")
    val isEpisode: Boolean = false
) {
    /** Calculates watch progress as a percentage between 0 and 100. */
    val progressPercent: Int
        get() = if (durationMs > 0L) {
            ((positionMs.toDouble() / durationMs.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }

    /** Returns true if the video is nearly finished (>= 95%). */
    val isCompleted: Boolean
        get() = progressPercent >= 95
}
