package com.martinrevert.latorrentola.model.TMDB

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A paginated list of TMDB television series. */
@Serializable
data class TmdbTvPage(
    /** @property page Current result page. */
    @SerializedName("page")
    @SerialName("page")
    val page: Int = 1,
    /** @property totalPages Number of available pages. */
    @SerializedName("total_pages")
    @SerialName("total_pages")
    val totalPages: Int = 1,
    /** @property totalResults Total result count reported by TMDB. */
    @SerializedName("total_results")
    @SerialName("total_results")
    val totalResults: Int = 0,
    /** @property results Series returned on this page. */
    @SerializedName("results")
    @SerialName("results")
    val results: List<TmdbTvSummary> = emptyList()
)

/**
 * The catalog fields shared by TMDB TV list and detail responses.
 *
 * @property id TMDB series identifier.
 * @property name Localized series title.
 * @property originalName Original series title.
 * @property overview Series synopsis.
 * @property posterPath Relative poster image path.
 * @property backdropPath Relative backdrop image path.
 * @property firstAirDate First broadcast date in ISO format.
 * @property voteAverage TMDB average user rating.
 * @property voteCount Number of TMDB user ratings.
 * @property popularity TMDB popularity score.
 * @property genreIds Genre identifiers included in list results.
 * @property genres Full genre objects included in detail results.
 * @property originCountry Countries associated with the series.
 * @property originalLanguage Original language code.
 * @property status Current or final production status.
 * @property numberOfSeasons Number of seasons reported by TMDB.
 * @property numberOfEpisodes Number of episodes reported by TMDB.
 * @property seasons Season summaries included in detail results.
 * @property aggregateCredits Aggregate cast and crew response, when requested.
 */
@Serializable
data class TmdbTvSummary(
    @SerializedName("id")
    @SerialName("id")
    val id: Int,
    @SerializedName("name")
    @SerialName("name")
    val name: String? = null,
    @SerializedName("original_name")
    @SerialName("original_name")
    val originalName: String? = null,
    @SerializedName("overview")
    @SerialName("overview")
    val overview: String? = null,
    @SerializedName("poster_path")
    @SerialName("poster_path")
    val posterPath: String? = null,
    @SerializedName("backdrop_path")
    @SerialName("backdrop_path")
    val backdropPath: String? = null,
    @SerializedName("first_air_date")
    @SerialName("first_air_date")
    val firstAirDate: String? = null,
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
    val genreIds: List<Int> = emptyList(),
    @SerializedName("genres")
    @SerialName("genres")
    val genres: List<TmdbTvGenre> = emptyList(),
    @SerializedName("origin_country")
    @SerialName("origin_country")
    val originCountry: List<String> = emptyList(),
    @SerializedName("original_language")
    @SerialName("original_language")
    val originalLanguage: String? = null,
    @SerializedName("status")
    @SerialName("status")
    val status: String? = null,
    @SerializedName("number_of_seasons")
    @SerialName("number_of_seasons")
    val numberOfSeasons: Int? = null,
    @SerializedName("number_of_episodes")
    @SerialName("number_of_episodes")
    val numberOfEpisodes: Int? = null,
    @SerializedName("seasons")
    @SerialName("seasons")
    val seasons: List<TmdbTvSeason> = emptyList(),
    @SerializedName("aggregate_credits")
    @SerialName("aggregate_credits")
    val aggregateCredits: TmdbTvAggregateCredits? = null
) {
    /** Full-size poster image URL, or `null` when no poster is available. */
    val fullPosterUrl: String?
        get() = posterPath?.takeIf(String::isNotBlank)?.let { "https://image.tmdb.org/t/p/w500$it" }

    /** Full-size backdrop image URL, or `null` when no backdrop is available. */
    val fullBackdropUrl: String?
        get() = backdropPath?.takeIf(String::isNotBlank)?.let { "https://image.tmdb.org/t/p/w1280$it" }

    /** Four-digit year of the series' initial air date. */
    val firstAirYear: String
        get() = firstAirDate.orEmpty().take(4)
}

/**
 * A genre entry from TMDB's television genre catalog.
 *
 * @property id TMDB television genre identifier.
 * @property name Localized genre label.
 */
@Serializable
data class TmdbTvGenre(
    @SerializedName("id")
    @SerialName("id")
    val id: Int,
    @SerializedName("name")
    @SerialName("name")
    val name: String
)

/**
 * TV genre catalog returned by TMDB.
 *
 * @property genres Available television genres.
 */
@Serializable
data class TmdbTvGenrePage(
    @SerializedName("genres")
    @SerialName("genres")
    val genres: List<TmdbTvGenre> = emptyList()
)

/**
 * Television cast response included with series details.
 *
 * @property cast Cast members credited to the series.
 */
@Serializable
data class TmdbTvAggregateCredits(
    @SerializedName("cast")
    @SerialName("cast")
    val cast: List<TmdbTvCastMember> = emptyList()
)

/**
 * A cast member credited to a television series.
 *
 * @property id TMDB person identifier.
 * @property name Cast member's display name.
 * @property profilePath Relative profile image path.
 * @property roles Characters portrayed by the cast member.
 */
@Serializable
data class TmdbTvCastMember(
    @SerializedName("id")
    @SerialName("id")
    val id: Int,
    @SerializedName("name")
    @SerialName("name")
    val name: String? = null,
    @SerializedName("profile_path")
    @SerialName("profile_path")
    val profilePath: String? = null,
    @SerializedName("roles")
    @SerialName("roles")
    val roles: List<TmdbTvCastRole> = emptyList()
) {
    /** Full-size profile image URL, or `null` when unavailable. */
    val fullProfileUrl: String?
        get() = profilePath?.takeIf(String::isNotBlank)?.let { "https://image.tmdb.org/t/p/w342$it" }
}

/**
 * A character role for a television cast member.
 *
 * @property character Character name.
 */
@Serializable
data class TmdbTvCastRole(
    @SerializedName("character")
    @SerialName("character")
    val character: String? = null
)

/**
 * A season summary returned with a television series.
 *
 * @property id TMDB season identifier.
 * @property name Display name of the season.
 * @property overview Season synopsis.
 * @property posterPath Relative season poster path.
 * @property seasonNumber Season number, where zero may represent specials.
 * @property episodeCount Number of episodes in the season.
 * @property airDate Season premiere date.
 */
@Serializable
data class TmdbTvSeason(
    @SerializedName("id")
    @SerialName("id")
    val id: Int,
    @SerializedName("name")
    @SerialName("name")
    val name: String? = null,
    @SerializedName("overview")
    @SerialName("overview")
    val overview: String? = null,
    @SerializedName("poster_path")
    @SerialName("poster_path")
    val posterPath: String? = null,
    @SerializedName("season_number")
    @SerialName("season_number")
    val seasonNumber: Int,
    @SerializedName("episode_count")
    @SerialName("episode_count")
    val episodeCount: Int? = null,
    @SerializedName("air_date")
    @SerialName("air_date")
    val airDate: String? = null
) {
    /** Full-size season poster image URL, or `null` when unavailable. */
    val fullPosterUrl: String?
        get() = posterPath?.takeIf(String::isNotBlank)?.let { "https://image.tmdb.org/t/p/w500$it" }
}

/**
 * Full response for one television season.
 *
 * @property id TMDB season identifier.
 * @property name Season display name.
 * @property overview Season synopsis.
 * @property seasonNumber Season number.
 * @property episodes Episodes included in the season.
 */
@Serializable
data class TmdbTvSeasonDetails(
    @SerializedName("id")
    @SerialName("id")
    val id: Int,
    @SerializedName("name")
    @SerialName("name")
    val name: String? = null,
    @SerializedName("overview")
    @SerialName("overview")
    val overview: String? = null,
    @SerializedName("season_number")
    @SerialName("season_number")
    val seasonNumber: Int,
    @SerializedName("episodes")
    @SerialName("episodes")
    val episodes: List<TmdbTvEpisode> = emptyList()
)

/**
 * An episode in a television season.
 *
 * @property id TMDB episode identifier.
 * @property name Episode title.
 * @property overview Episode synopsis.
 * @property episodeNumber Episode number within its season.
 * @property seasonNumber Parent season number.
 * @property stillPath Relative episode still image path.
 * @property airDate Episode air date.
 * @property runtime Episode runtime in minutes.
 * @property voteAverage TMDB average user rating.
 */
@Serializable
data class TmdbTvEpisode(
    @SerializedName("id")
    @SerialName("id")
    val id: Int,
    @SerializedName("name")
    @SerialName("name")
    val name: String? = null,
    @SerializedName("overview")
    @SerialName("overview")
    val overview: String? = null,
    @SerializedName("episode_number")
    @SerialName("episode_number")
    val episodeNumber: Int,
    @SerializedName("season_number")
    @SerialName("season_number")
    val seasonNumber: Int,
    @SerializedName("still_path")
    @SerialName("still_path")
    val stillPath: String? = null,
    @SerializedName("air_date")
    @SerialName("air_date")
    val airDate: String? = null,
    @SerializedName("runtime")
    @SerialName("runtime")
    val runtime: Int? = null,
    @SerializedName("vote_average")
    @SerialName("vote_average")
    val voteAverage: Double? = null
) {
    /** Full-size episode still image URL, or `null` when unavailable. */
    val fullStillUrl: String?
        get() = stillPath?.takeIf(String::isNotBlank)?.let { "https://image.tmdb.org/t/p/w780$it" }
}

/**
 * External identifiers linked to a TMDB TV series.
 *
 * @property id TMDB series identifier.
 * @property imdbId Linked IMDb title identifier (e.g., "tt0944947").
 * @property tvdbId Linked TheTVDB series identifier.
 */
@Serializable
data class TmdbTvExternalIds(
    @SerializedName("id")
    @SerialName("id")
    val id: Int = 0,
    @SerializedName("imdb_id")
    @SerialName("imdb_id")
    val imdbId: String? = null,
    @SerializedName("tvdb_id")
    @SerialName("tvdb_id")
    val tvdbId: Int? = null
)

