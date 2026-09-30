package com.martinrevert.latorrentola.model.user

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.gson.annotations.SerializedName
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimal Firestore-friendly metadata for a favorited TV series.
 *
 * @property id TMDB series identifier.
 * @property name Display title.
 * @property posterPath Relative TMDB poster path.
 * @property firstAirDate First broadcast date.
 * @property voteAverage TMDB average rating.
 * @property genreIds TMDB TV genre identifiers.
 */
@IgnoreExtraProperties
@Serializable
data class FavoriteTvSeries(
    @SerializedName("id")
    @SerialName("id")
    val id: Int = 0,
    @SerializedName("name")
    @SerialName("name")
    val name: String? = null,
    @SerializedName("poster_path")
    @SerialName("poster_path")
    val posterPath: String? = null,
    @SerializedName("first_air_date")
    @SerialName("first_air_date")
    val firstAirDate: String? = null,
    @SerializedName("vote_average")
    @SerialName("vote_average")
    val voteAverage: Double? = null,
    @SerializedName("genre_ids")
    @SerialName("genre_ids")
    val genreIds: List<Int>? = emptyList()
) {
    /** Converts stored metadata to the catalog model used by favorite cards. */
    fun toTmdbTvSummary(): TmdbTvSummary = TmdbTvSummary(
        id = id,
        name = name,
        posterPath = posterPath,
        firstAirDate = firstAirDate,
        voteAverage = voteAverage,
        genreIds = genreIds.orEmpty()
    )

    companion object {
        /** Creates a Firestore record from [series]. */
        fun from(series: TmdbTvSummary): FavoriteTvSeries = FavoriteTvSeries(
            id = series.id,
            name = series.name,
            posterPath = series.posterPath,
            firstAirDate = series.firstAirDate,
            voteAverage = series.voteAverage,
            genreIds = series.genreIds.orEmpty().ifEmpty { series.genres.orEmpty().map { it.id } }
        )
    }
}
