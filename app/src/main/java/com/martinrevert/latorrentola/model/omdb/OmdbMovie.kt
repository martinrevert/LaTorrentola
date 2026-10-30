package com.martinrevert.latorrentola.model.omdb

import com.google.gson.annotations.SerializedName

/**
 * Raw JSON response from the OMDb API movie details endpoint.
 */
data class OmdbMovieResponse(
    @SerializedName("Title") val title: String? = null,
    @SerializedName("Year") val year: String? = null,
    @SerializedName("imdbRating") val imdbRating: String? = null,
    @SerializedName("imdbVotes") val imdbVotes: String? = null,
    @SerializedName("imdbID") val imdbId: String? = null,
    @SerializedName("Metascore") val metascore: String? = null,
    @SerializedName("Ratings") val ratings: List<OmdbRating>? = null,
    @SerializedName("Response") val response: String? = null,
    @SerializedName("Error") val error: String? = null
)

/**
 * Individual rating entry from OMDb containing the provider source and score value.
 */
data class OmdbRating(
    @SerializedName("Source") val source: String? = null,
    @SerializedName("Value") val value: String? = null
)

/**
 * Aggregated and formatted ratings displayed on the movie detail screen.
 *
 * @property imdbRating Formatted IMDb score (e.g. "8.8/10").
 * @property rottenTomatoesRating Formatted Rotten Tomatoes score (e.g. "87%").
 * @property metacriticRating Formatted Metacritic score (e.g. "74/100").
 */
data class MovieRatings(
    val imdbRating: String? = null,
    val rottenTomatoesRating: String? = null,
    val metacriticRating: String? = null
)
