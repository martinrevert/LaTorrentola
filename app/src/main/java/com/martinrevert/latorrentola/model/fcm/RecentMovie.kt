package com.martinrevert.latorrentola.model.fcm

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

/**
 * Movie summary sent by the FCM backend for recent-release notifications.
 *
 * @property movieId YTS identifier of the movie.
 * @property title Movie title, when supplied.
 * @property notifiedAt Notification timestamp as supplied by the backend.
 */
@Serializable
data class RecentMovie(
    @SerializedName("movieId")
    val movieId: Int,
    @SerializedName("title")
    val title: String? = null,
    @SerializedName("notifiedAt")
    val notifiedAt: String? = null
)
