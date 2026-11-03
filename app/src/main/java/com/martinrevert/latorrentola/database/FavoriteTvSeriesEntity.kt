package com.martinrevert.latorrentola.database

import androidx.room.Entity
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary

/**
 * Room entity representing a user's favorited TV series.
 *
 * @property userId Account ID owning this favorite.
 * @property seriesId TMDB TV series identifier.
 * @property name Series name.
 * @property posterPath Poster path relative or absolute.
 * @property firstAirDate First air date.
 * @property voteAverage Rating.
 * @property genreIds Comma-separated genre IDs.
 * @property numberOfSeasons Total seasons.
 * @property numberOfEpisodes Total episodes.
 * @property updatedAt Milliseconds epoch of last update.
 */
@Entity(
    tableName = "favorites_tv",
    primaryKeys = ["userId", "seriesId"]
)
data class FavoriteTvSeriesEntity(
    val userId: String,
    val seriesId: Int,
    val imdbId: String = "",
    val name: String,
    val posterPath: String? = null,
    val firstAirDate: String? = null,
    val voteAverage: Double? = null,
    val genreIds: String = "",
    val numberOfSeasons: Int? = null,
    val numberOfEpisodes: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toTmdbTvSummary(): TmdbTvSummary = TmdbTvSummary(
        id = seriesId,
        imdbId = imdbId.ifBlank { null },
        name = name,
        posterPath = posterPath,
        firstAirDate = firstAirDate,
        voteAverage = voteAverage,
        genreIds = if (genreIds.isNotBlank()) genreIds.split(",").mapNotNull { it.trim().toIntOrNull() } else emptyList(),
        numberOfSeasons = numberOfSeasons,
        numberOfEpisodes = numberOfEpisodes
    )

    companion object {
        fun from(userId: String, series: TmdbTvSummary): FavoriteTvSeriesEntity {
            val genres = (series.genreIds.orEmpty().ifEmpty { series.genres.orEmpty().map { it.id } }).joinToString(",")
            val normalizedImdb = series.effectiveImdbId ?: ""
            return FavoriteTvSeriesEntity(
                userId = userId,
                seriesId = series.id,
                imdbId = normalizedImdb,
                name = series.name.orEmpty(),
                posterPath = series.posterPath,
                firstAirDate = series.firstAirDate,
                voteAverage = series.voteAverage,
                genreIds = genres,
                numberOfSeasons = series.numberOfSeasons,
                numberOfEpisodes = series.numberOfEpisodes,
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}
