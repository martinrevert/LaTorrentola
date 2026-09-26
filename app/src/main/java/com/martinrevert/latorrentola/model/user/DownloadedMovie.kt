package com.martinrevert.latorrentola.model.user

import com.google.firebase.firestore.IgnoreExtraProperties
import com.martinrevert.latorrentola.model.YTS.Movie
import kotlinx.serialization.Serializable

/**
 * A user's downloaded torrent entry, optionally retaining its movie details.
 *
 * @property movieId YTS identifier of the downloaded movie.
 * @property movieTitle Movie title stored with the download.
 * @property quality Quality label of the downloaded torrent.
 * @property hash Torrent info hash used to identify the version.
 * @property timestamp Time the download record was created, in milliseconds.
 * @property movie Full movie metadata when available.
 */
@IgnoreExtraProperties
@Serializable
data class DownloadedMovie(
    val movieId: Int = 0,
    val movieTitle: String = "",
    val quality: String = "",
    val hash: String = "", // Unique identifier for the torrent version
    val timestamp: Long = System.currentTimeMillis(),
    val movie: Movie? = null
)
