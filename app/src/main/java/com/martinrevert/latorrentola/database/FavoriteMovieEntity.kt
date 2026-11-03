package com.martinrevert.latorrentola.database

import androidx.room.Entity
import com.google.gson.Gson
import com.martinrevert.latorrentola.model.YTS.Movie

/**
 * Room entity representing a user's favorited movie.
 *
 * @property userId Account ID owning this favorite (enables isolated multi-account support).
 * @property movieId Numeric movie identifier.
 * @property title Title of the movie.
 * @property year Release year.
 * @property rating Movie rating as a string (e.g. "8.5").
 * @property mediumCoverImage Poster image URL.
 * @property genres Comma-separated list of genres.
 * @property movieJson Serialized full [Movie] payload for complete restoration.
 * @property updatedAt Milliseconds epoch of last update.
 */
@Entity(
    tableName = "favorites_movies",
    primaryKeys = ["userId", "movieId"]
)
data class FavoriteMovieEntity(
    val userId: String,
    val movieId: Int,
    val imdbId: String = "",
    val title: String,
    val year: Int = 0,
    val rating: String? = null,
    val mediumCoverImage: String? = null,
    val genres: String = "",
    val movieJson: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMovie(): Movie {
        if (!movieJson.isNullOrBlank()) {
            try {
                val parsed = Gson().fromJson(movieJson, Movie::class.java)
                if (parsed != null && parsed.id > 0) {
                    return if (parsed.imdbCode.isNullOrBlank() && imdbId.isNotBlank()) {
                        parsed.copy(imdbCode = imdbId)
                    } else {
                        parsed
                    }
                }
            } catch (_: Exception) {}
        }
        return Movie(
            id = movieId,
            imdbCode = imdbId.ifBlank { null },
            title = title,
            year = year,
            rating = rating,
            mediumCoverImage = mediumCoverImage,
            genres = if (genres.isNotBlank()) genres.split(",").map { it.trim() } else emptyList()
        )
    }

    companion object {
        fun from(userId: String, movie: Movie): FavoriteMovieEntity {
            val normalizedImdb = com.martinrevert.latorrentola.utils.MediaIdHelper.normalizeImdbId(movie.imdbCode) ?: ""
            return FavoriteMovieEntity(
                userId = userId,
                movieId = movie.id,
                imdbId = normalizedImdb,
                title = movie.title.orEmpty(),
                year = movie.year ?: 0,
                rating = movie.rating,
                mediumCoverImage = movie.mediumCoverImage,
                genres = movie.genres?.joinToString(",") ?: "",
                movieJson = try { Gson().toJson(movie) } catch (_: Exception) { null },
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}
