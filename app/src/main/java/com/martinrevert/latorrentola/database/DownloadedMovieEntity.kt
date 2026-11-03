package com.martinrevert.latorrentola.database

import androidx.room.Entity
import com.google.gson.Gson
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.user.DownloadedMovie

@Entity(
    tableName = "downloaded_movies",
    primaryKeys = ["userId", "hash"]
)
data class DownloadedMovieEntity(
    val userId: String,
    val hash: String,
    val movieId: Int,
    val imdbId: String = "",
    val movieTitle: String,
    val quality: String,
    val timestamp: Long = System.currentTimeMillis(),
    val movieJson: String? = null
) {
    fun toDownloadedMovie(): DownloadedMovie {
        val parsedMovie = if (!movieJson.isNullOrBlank()) {
            try { Gson().fromJson(movieJson, Movie::class.java) } catch (_: Exception) { null }
        } else null

        return DownloadedMovie(
            movieId = movieId,
            imdbId = imdbId.ifBlank { parsedMovie?.imdbCode },
            movieTitle = movieTitle,
            quality = quality,
            hash = hash,
            timestamp = timestamp,
            movie = parsedMovie
        )
    }

    companion object {
        fun from(userId: String, download: DownloadedMovie): DownloadedMovieEntity {
            val normalizedImdb = com.martinrevert.latorrentola.utils.MediaIdHelper.normalizeImdbId(
                download.imdbId ?: download.movie?.imdbCode
            ) ?: ""
            return DownloadedMovieEntity(
                userId = userId,
                hash = download.hash,
                movieId = download.movieId,
                imdbId = normalizedImdb,
                movieTitle = download.movieTitle,
                quality = download.quality,
                timestamp = download.timestamp,
                movieJson = try { Gson().toJson(download.movie) } catch (_: Exception) { null }
            )
        }
    }
}
