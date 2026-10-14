package com.martinrevert.latorrentola.model.user

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
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
    @get:PropertyName("media_id") @set:PropertyName("media_id") @SerializedName("media_id") var mediaId: String = "",
    @get:PropertyName("title") @set:PropertyName("title") @SerializedName("title") var title: String = "",
    @get:PropertyName("position_ms") @set:PropertyName("position_ms") @SerializedName("position_ms") var positionMs: Long = 0L,
    @get:PropertyName("duration_ms") @set:PropertyName("duration_ms") @SerializedName("duration_ms") var durationMs: Long = 0L,
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") @SerializedName("timestamp") var timestamp: Long = System.currentTimeMillis(),
    @get:PropertyName("is_episode") @set:PropertyName("is_episode") @SerializedName("is_episode") var isEpisode: Boolean = false
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
