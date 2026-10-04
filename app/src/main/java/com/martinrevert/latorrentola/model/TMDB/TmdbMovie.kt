package com.martinrevert.latorrentola.model.TMDB

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Result returned by TMDB `find/{external_id}` endpoint.
 *
 * @property movieResults Matching movies for the provided external ID.
 */
@Serializable
data class TmdbFindResponse(
    @SerializedName("movie_results")
    @SerialName("movie_results")
    val movieResults: List<TmdbMovieResult>? = null
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
