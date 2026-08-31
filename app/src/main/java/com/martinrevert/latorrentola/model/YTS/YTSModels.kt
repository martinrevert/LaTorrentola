package com.martinrevert.latorrentola.model.YTS

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

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
