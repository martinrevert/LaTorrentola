package com.martinrevert.latorrentola.model.TMDB

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbSearchPersonResponse(
    @SerializedName("page")
    @SerialName("page")
    val page: Int? = null,

    @SerializedName("results")
    @SerialName("results")
    val results: List<TmdbPersonResult>? = null
)

@Serializable
data class TmdbPersonResult(
    @SerializedName("id")
    @SerialName("id")
    val id: Int,

    @SerializedName("name")
    @SerialName("name")
    val name: String? = null,

    @SerializedName("profile_path")
    @SerialName("profile_path")
    val profilePath: String? = null,

    @SerializedName("popularity")
    @SerialName("popularity")
    val popularity: Double? = null,

    @SerializedName("known_for_department")
    @SerialName("known_for_department")
    val knownForDepartment: String? = null
)

@Serializable
data class TmdbActorDetail(
    @SerializedName("id")
    @SerialName("id")
    val id: Int,

    @SerializedName("name")
    @SerialName("name")
    val name: String? = null,

    @SerializedName("biography")
    @SerialName("biography")
    val biography: String? = null,

    @SerializedName("birthday")
    @SerialName("birthday")
    val birthday: String? = null,

    @SerializedName("deathday")
    @SerialName("deathday")
    val deathday: String? = null,

    @SerializedName("place_of_birth")
    @SerialName("place_of_birth")
    val placeOfBirth: String? = null,

    @SerializedName("profile_path")
    @SerialName("profile_path")
    val profilePath: String? = null,

    @SerializedName("known_for_department")
    @SerialName("known_for_department")
    val knownForDepartment: String? = null,

    @SerializedName("combined_credits")
    @SerialName("combined_credits")
    val combinedCredits: TmdbCreditsResponse? = null
) {
    val fullProfileUrl: String?
        get() = if (!profilePath.isNullOrEmpty()) "https://image.tmdb.org/t/p/w500$profilePath" else null
}

@Serializable
data class TmdbCreditsResponse(
    @SerializedName("cast")
    @SerialName("cast")
    val cast: List<TmdbCastCredit>? = null
)

@Serializable
data class TmdbCastCredit(
    @SerializedName("id")
    @SerialName("id")
    val id: Int,

    @SerializedName("title")
    @SerialName("title")
    val title: String? = null,

    @SerializedName("name")
    @SerialName("name")
    val name: String? = null,

    @SerializedName("character")
    @SerialName("character")
    val character: String? = null,

    @SerializedName("poster_path")
    @SerialName("poster_path")
    val posterPath: String? = null,

    @SerializedName("release_date")
    @SerialName("release_date")
    val releaseDate: String? = null,

    @SerializedName("first_air_date")
    @SerialName("first_air_date")
    val firstAirDate: String? = null,

    @SerializedName("vote_average")
    @SerialName("vote_average")
    val voteAverage: Double? = null
) {
    val displayTitle: String
        get() = title ?: name ?: ""

    val displayYear: String
        get() = (releaseDate ?: firstAirDate ?: "").take(4)

    val fullPosterUrl: String?
        get() = if (!posterPath.isNullOrEmpty()) "https://image.tmdb.org/t/p/w342$posterPath" else null
}
