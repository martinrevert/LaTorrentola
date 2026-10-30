package com.martinrevert.latorrentola.model.TMDB

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Result returned by TMDB `find/{external_id}` endpoint.
 *
 * @property movieResults Matching movies for the provided external ID.
 * @property tvResults Matching TV series for the provided external ID.
 */
@Serializable
data class TmdbFindResponse(
    @SerializedName("movie_results")
    @SerialName("movie_results")
    val movieResults: List<TmdbMovieResult>? = null,

    @SerializedName("tv_results")
    @SerialName("tv_results")
    val tvResults: List<TmdbTvSummary>? = null
)

/**
 * Movie summary returned in a TMDB find query.
 *
 * @property id TMDB movie identifier.
 * @property title Movie title.
 */
@Serializable
data class TmdbMovieResult(
    @SerializedName("id")
    @SerialName("id")
    val id: Int,

    @SerializedName("title")
    @SerialName("title")
    val title: String? = null
)

/**
 * Credits response for a TMDB movie.
 *
 * @property id TMDB movie identifier.
 * @property cast Cast members in the movie.
 */
@Serializable
data class TmdbMovieCreditsResponse(
    @SerializedName("id")
    @SerialName("id")
    val id: Int? = null,

    @SerializedName("cast")
    @SerialName("cast")
    val cast: List<TmdbMovieCastMember>? = null
)

/**
 * A cast member in a TMDB movie.
 *
 * @property id TMDB person identifier.
 * @property name Person display name.
 * @property character Character name portrayed in the movie.
 * @property profilePath Relative path to profile image.
 * @property order Cast order index.
 */
@Serializable
data class TmdbMovieCastMember(
    @SerializedName("id")
    @SerialName("id")
    val id: Int,

    @SerializedName("name")
    @SerialName("name")
    val name: String? = null,

    @SerializedName("character")
    @SerialName("character")
    val character: String? = null,

    @SerializedName("profile_path")
    @SerialName("profile_path")
    val profilePath: String? = null,

    @SerializedName("order")
    @SerialName("order")
    val order: Int? = null
) {
    /** Profile image URL, or `null` if unavailable. */
    val fullProfileUrl: String?
        get() = profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
}

/**
 * Summary of a TMDB movie returned by discovery or search endpoints.
 *
 * @property id TMDB movie identifier.
 * @property title Localized movie title.
 * @property originalTitle Original movie title.
 * @property overview Movie synopsis.
 * @property posterPath Relative poster image path.
 * @property backdropPath Relative backdrop image path.
 * @property releaseDate Release date in ISO format.
 * @property voteAverage TMDB average user rating.
 * @property voteCount Number of TMDB user ratings.
 * @property popularity TMDB popularity score.
 * @property genreIds Genre identifiers included in list results.
 * @property originalLanguage Original language code.
 */
@Serializable
data class TmdbMovieSummary(
    @SerializedName("id")
    @SerialName("id")
    val id: Int,

    @SerializedName("title")
    @SerialName("title")
    val title: String? = null,

    @SerializedName("original_title")
    @SerialName("original_title")
    val originalTitle: String? = null,

    @SerializedName("overview")
    @SerialName("overview")
    val overview: String? = null,

    @SerializedName("poster_path")
    @SerialName("poster_path")
    val posterPath: String? = null,

    @SerializedName("backdrop_path")
    @SerialName("backdrop_path")
    val backdropPath: String? = null,

    @SerializedName("release_date")
    @SerialName("release_date")
    val releaseDate: String? = null,

    @SerializedName("vote_average")
    @SerialName("vote_average")
    val voteAverage: Double? = null,

    @SerializedName("vote_count")
    @SerialName("vote_count")
    val voteCount: Int? = null,

    @SerializedName("popularity")
    @SerialName("popularity")
    val popularity: Double? = null,

    @SerializedName("genre_ids")
    @SerialName("genre_ids")
    val genreIds: List<Int>? = emptyList(),

    @SerializedName("original_language")
    @SerialName("original_language")
    val originalLanguage: String? = null
) {
    /** Full-size poster image URL, or `null` when no poster is available. */
    val fullPosterUrl: String?
        get() = posterPath?.takeIf(String::isNotBlank)?.let { "https://image.tmdb.org/t/p/w500$it" }

    /** Full-size backdrop image URL, or `null` when no backdrop is available. */
    val fullBackdropUrl: String?
        get() = backdropPath?.takeIf(String::isNotBlank)?.let { "https://image.tmdb.org/t/p/w1280$it" }

    /** Four-digit year of the movie release date. */
    val releaseYear: String
        get() = releaseDate.orEmpty().take(4)
}

/** Paginated list of TMDB movies returned by discovery. */
@Serializable
data class TmdbMoviePage(
    @SerializedName("page")
    @SerialName("page")
    val page: Int = 1,

    @SerializedName("total_pages")
    @SerialName("total_pages")
    val totalPages: Int = 1,

    @SerializedName("total_results")
    @SerialName("total_results")
    val totalResults: Int = 0,

    @SerializedName("results")
    @SerialName("results")
    val results: List<TmdbMovieSummary>? = emptyList()
)

