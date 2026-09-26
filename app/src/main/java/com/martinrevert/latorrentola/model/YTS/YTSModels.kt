package com.martinrevert.latorrentola.model.YTS

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

/**
 * Envelope returned by YTS movie detail and list endpoints.
 *
 * @property status API response status.
 * @property statusMessage Human-readable API status message.
 * @property data Response payload, when present.
 */
@IgnoreExtraProperties
@Serializable
data class MovieDetails(
    @SerializedName("status")
    val status: String? = null,
    @SerializedName("status_message")
    val statusMessage: String? = null,
    @SerializedName("data")
    val data: Data? = null
)

/**
 * Movie data and pagination metadata in a YTS response.
 *
 * @property movieCount Number of matching movies reported by the API.
 * @property limit Maximum number of results requested per page.
 * @property pageNumber Current result page.
 * @property movies Movie results for list requests.
 * @property movie Single movie result for detail requests.
 * @property meta Server and API execution metadata.
 */
@IgnoreExtraProperties
@Serializable
data class Data(
    @SerializedName("movie_count")
    val movieCount: Int? = null,
    @SerializedName("limit")
    val limit: Int? = null,
    @SerializedName("page_number")
    val pageNumber: Int? = null,
    @SerializedName("movies")
    val movies: List<Movie>? = null,
    @SerializedName("movie")
    val movie: Movie? = null,
    @SerializedName("@meta")
    val meta: Meta? = null
)

/**
 * Cast member summary associated with a YTS movie.
 *
 * @property name Cast member name.
 * @property characterName Character portrayed in the movie.
 * @property urlSmallImage Small profile image URL.
 * @property imdbCode IMDb person identifier.
 */
@IgnoreExtraProperties
@Serializable
data class Cast(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("character_name")
    val characterName: String? = null,
    @SerializedName("url_small_image")
    val urlSmallImage: String? = null,
    @SerializedName("imdb_code")
    val imdbCode: String? = null
)

/**
 * Server execution metadata returned by YTS.
 *
 * @property serverTime Server timestamp.
 * @property serverTimezone Server timezone identifier.
 * @property apiVersion YTS API version number.
 * @property executionTime Time spent executing the request.
 */
@IgnoreExtraProperties
@Serializable
data class Meta(
    @SerializedName("server_time")
    val serverTime: Int? = null,
    @SerializedName("server_timezone")
    val serverTimezone: String? = null,
    @SerializedName("api_version")
    val apiVersion: Int? = null,
    @SerializedName("execution_time")
    val executionTime: String? = null
)
