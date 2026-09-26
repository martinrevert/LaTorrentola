package com.martinrevert.latorrentola.model.TMDB

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Paginated people-search response from TMDB.
 *
 * @property page Current result page.
 * @property results Matching people on the current page.
 */
@Serializable
data class TmdbSearchPersonResponse(
    @SerializedName("page")
    @SerialName("page")
    val page: Int? = null,

    @SerializedName("results")
    @SerialName("results")
    val results: List<TmdbPersonResult>? = null
)

/**
 * Person summary returned by a TMDB search.
 *
 * @property id TMDB person identifier.
 * @property name Person's display name.
 * @property profilePath Relative path to the profile image.
 * @property popularity TMDB popularity score.
 * @property knownForDepartment Department most associated with the person.
 */
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

/**
 * TMDB actor details and combined credits.
 *
 * @property id TMDB person identifier.
 * @property name Actor's display name.
 * @property biography Biographical text, if available.
 * @property birthday Actor's birth date.
 * @property deathday Actor's death date, if applicable.
 * @property placeOfBirth Actor's place of birth.
 * @property profilePath Relative path to the profile image.
 * @property knownForDepartment Department most associated with the actor.
 * @property combinedCredits Combined film and television credits.
 */
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
    /** Full-size TMDB profile image URL, or `null` if no profile is available. */
    val fullProfileUrl: String?
        get() = if (!profilePath.isNullOrEmpty()) "https://image.tmdb.org/t/p/w500$profilePath" else null
}

/**
 * Combined-credit response containing cast credits.
 *
 * @property cast Movies and television appearances credited to the person.
 */
@Serializable
data class TmdbCreditsResponse(
    @SerializedName("cast")
    @SerialName("cast")
    val cast: List<TmdbCastCredit>? = null
)

/**
 * A movie or television cast credit for a TMDB person.
 *
 * @property id TMDB media identifier.
 * @property title Movie title, if this is a movie credit.
 * @property name Television title, if this is a television credit.
 * @property character Character name credited to the person.
 * @property posterPath Relative path to the media poster.
 * @property releaseDate Movie release date.
 * @property firstAirDate Television first-air date.
 * @property voteAverage TMDB average user rating.
 */
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
    /** Best available display title, preferring the movie title to the TV name. */
    val displayTitle: String
        get() = title ?: name ?: ""

    /** Four-digit year derived from the movie release or TV first-air date. */
    val displayYear: String
        get() = (releaseDate ?: firstAirDate ?: "").take(4)

    /** Medium-sized TMDB poster URL, or `null` if no poster is available. */
    val fullPosterUrl: String?
        get() = if (!posterPath.isNullOrEmpty()) "https://image.tmdb.org/t/p/w342$posterPath" else null
}

/**
 * TMDB external identifiers for a movie.
 *
 * @property id TMDB movie identifier.
 * @property imdbId IMDb title identifier, when linked.
 */
@Serializable
data class TmdbMovieExternalIds(
    @SerializedName("id")
    @SerialName("id")
    val id: Int,

    @SerializedName("imdb_id")
    @SerialName("imdb_id")
    val imdbId: String? = null
)
