package com.martinrevert.latorrentola.model.user

import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.serialization.Serializable

@IgnoreExtraProperties
@Serializable
data class DownloadedMovie(
    val movieId: Int = 0,
    val movieTitle: String = "",
    val quality: String = "",
    val hash: String = "", // Unique identifier for the torrent version
    val timestamp: Long = System.currentTimeMillis()
)
