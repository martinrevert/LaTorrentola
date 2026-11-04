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
) {
    /** Thumbnail TMDB profile image URL, or `null` if no profile is available. */
    val fullProfileUrl: String?
        get() = if (!profilePath.isNullOrEmpty()) "https://image.tmdb.org/t/p/w185$profilePath" else null
}

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
 * Combined-credit response containing cast and crew credits.
 *
 * @property cast Movies and television appearances credited to the person as cast.
 * @property crew Production and crew roles credited to the person.
 */
@Serializable
data class TmdbCreditsResponse(
    @SerializedName("cast")
    @SerialName("cast")
    val cast: List<TmdbCastCredit>? = null,

    @SerializedName("crew")
    @SerialName("crew")
    val crew: List<TmdbCrewCredit>? = null
)

/**
 * A crew credit for a TMDB person (such as directing, writing, producing).
 *
 * @property id TMDB media identifier.
 * @property title Movie title, if this is a movie credit.
 * @property name Television title, if this is a television credit.
 * @property job Role name (e.g., "Director", "Series Director").
 * @property department Department name (e.g., "Directing").
 * @property posterPath Relative path to the media poster.
 * @property releaseDate Movie release date.
 * @property firstAirDate Television first-air date.
 * @property voteAverage TMDB average user rating.
 * @property mediaType TMDB media type, such as `movie` or `tv`.
 */
@Serializable
data class TmdbCrewCredit(
    @SerializedName("id")
    @SerialName("id")
    val id: Int,

    @SerializedName("title")
    @SerialName("title")
    val title: String? = null,

    @SerializedName("name")
    @SerialName("name")
    val name: String? = null,

    @SerializedName("job")
    @SerialName("job")
    val job: String? = null,

    @SerializedName("department")
    @SerialName("department")
    val department: String? = null,

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
    val voteAverage: Double? = null,

    @SerializedName("media_type")
    @SerialName("media_type")
    val mediaType: String? = null
) {
    /** Whether this crew entry represents a primary director role. */
    fun isDirectorCredit(): Boolean {
        val j = job?.trim().orEmpty()
        return j.equals("Director", ignoreCase = true) ||
            j.equals("Series Director", ignoreCase = true) ||
            j.equals("Co-Director", ignoreCase = true)
    }

    /** Converts this director credit into a [TmdbCastCredit] marked with "Director" role. */
    fun toCastCredit(): TmdbCastCredit = TmdbCastCredit(
        id = id,
        title = title,
        name = name,
        character = "Director",
        posterPath = posterPath,
        releaseDate = releaseDate,
        firstAirDate = firstAirDate,
        voteAverage = voteAverage,
        mediaType = mediaType
    )
}

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
 * @property mediaType TMDB media type, such as `movie` or `tv`.
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
    val voteAverage: Double? = null,

    @SerializedName("media_type")
    @SerialName("media_type")
    val mediaType: String? = null
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
 * Deduplicates cast credits by media type + ID and sorts them by release/first-air year descending,
 * placing undated entries at the end.
 */
fun List<TmdbCastCredit>.sortedByYearDescending(): List<TmdbCastCredit> {
    return this
        .distinctBy { "${it.mediaType ?: "movie"}_${it.id}" }
        .sortedWith(
            compareByDescending<TmdbCastCredit> { it.displayYear.toIntOrNull() ?: 0 }
                .thenByDescending { it.releaseDate ?: it.firstAirDate ?: "" }
        )
}

/**
 * Combines acting and directing credits into a single deduplicated list,
 * sorted by release/first-air year descending, placing undated entries at the end.
 * If a media title appears in both (person both directed and acted in it),
 * the directing role is combined into the credit's character field (e.g. "Director / CharacterName").
 */
fun TmdbCreditsResponse.combineActingAndDirectingCredits(): List<TmdbCastCredit> {
    val castList = cast.orEmpty()
    val directingList = crew.orEmpty()
        .filter { it.isDirectorCredit() }
        .map { it.toCastCredit() }

    val directorKeys = directingList.map { "${it.mediaType ?: "movie"}_${it.id}" }.toSet()
    val combinedMap = linkedMapOf<String, TmdbCastCredit>()

    for (credit in directingList) {
        val key = "${credit.mediaType ?: "movie"}_${credit.id}"
        combinedMap[key] = credit
    }

    for (credit in castList) {
        val key = "${credit.mediaType ?: "movie"}_${credit.id}"
        val existing = combinedMap[key]
        if (existing != null && key in directorKeys) {
            val combinedRole = if (!credit.character.isNullOrBlank()) {
                "Director / ${credit.character}"
            } else {
                "Director"
            }
            combinedMap[key] = existing.copy(character = combinedRole)
        } else if (existing == null) {
            combinedMap[key] = credit
        }
    }

    return combinedMap.values.toList().sortedByYearDescending()
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
