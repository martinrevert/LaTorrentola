package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.BuildConfig
import com.martinrevert.latorrentola.database.TvGenreDao
import com.martinrevert.latorrentola.model.TMDB.TmdbActorDetail
import com.martinrevert.latorrentola.model.TMDB.TmdbTvGenre
import com.martinrevert.latorrentola.model.TMDB.TmdbTvPage
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeasonDetails
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates TMDB requests and converts lookup failures into nullable/result values.
 *
 * @property tmdbService HTTP client for TMDB endpoints.
 * @property tvGenreDao local TV genre usage statistics.
 */
@Singleton
class TmdbRepository @Inject constructor(
    private val tmdbService: TmdbService,
    private val tvGenreDao: TvGenreDao
) {
    /** API key injected through the app build configuration. */
    private val apiKey: String
        get() = BuildConfig.TMDB_API_KEY

    /**
     * Looks up an actor by name, then fetches their profile and combined credits.
     *
     * @param actorName Name used for the TMDB people search.
     * @return Actor detail on success, or the lookup/network failure.
     */
    suspend fun getActorDetails(actorName: String): Result<TmdbActorDetail> {
        return try {
            if (apiKey.isEmpty()) {
                return Result.failure(IllegalStateException("TMDB API key not configured"))
            }

            val searchResponse = tmdbService.searchPerson(
                apiKey = apiKey,
                query = actorName,
                language = "es-ES"
            )

            val personId = searchResponse.results?.firstOrNull()?.id
                ?: return Result.failure(NoSuchElementException("Actor not found"))

            getActorDetails(personId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Loads actor details and combined credits using the person's TMDB identifier.
     *
     * @param personId TMDB person identifier from the TV cast record.
     * @return Actor detail on success, or the network/configuration failure.
     */
    suspend fun getActorDetails(personId: Int): Result<TmdbActorDetail> {
        return try {
            if (apiKey.isEmpty()) {
                return Result.failure(IllegalStateException("TMDB API key not configured"))
            }

            Result.success(
                tmdbService.getPersonDetail(
                    personId = personId,
                    apiKey = apiKey,
                    appendToResponse = "combined_credits",
                    language = "es-ES"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Returns a linked IMDb identifier for a TMDB movie, or `null` when unavailable. */
    suspend fun getMovieImdbId(tmdbMovieId: Int): String? {
        return try {
            if (apiKey.isEmpty()) return null
            val response = tmdbService.getMovieExternalIds(movieId = tmdbMovieId, apiKey = apiKey)
            val imdbId = response.imdbId
            if (imdbId.isNullOrEmpty()) null else imdbId
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Loads one page from the requested TMDB TV Home feed.
     *
     * @param feed TMDB feed to request.
     * @param page Requested page number.
     * @return A page of TV series.
     */
    suspend fun getHomeTvFeed(feed: TmdbTvFeed, page: Int): TmdbTvPage {
        requireConfiguredApiKey()
        return when (feed) {
            TmdbTvFeed.ON_THE_AIR -> tmdbService.getOnTheAirTv(apiKey = apiKey, page = page)
            TmdbTvFeed.AIRING_TODAY -> tmdbService.getAiringTodayTv(apiKey = apiKey, page = page)
            TmdbTvFeed.POPULAR -> tmdbService.getPopularTv(apiKey = apiKey, page = page)
            TmdbTvFeed.TOP_RATED -> tmdbService.getTopRatedTv(apiKey = apiKey, page = page)
        }
    }

    /** Loads the TMDB television genre catalog. */
    suspend fun getTvGenres(): List<TmdbTvGenre> {
        requireConfiguredApiKey()
        return tmdbService.getTvGenres(apiKey = apiKey).genres
    }

    /** Observes TMDB TV genre IDs ordered by local usage count. */
    fun observeTvGenreUsage(): Flow<Map<Int, Int>> =
        tvGenreDao.observeByPopularity().map { stats -> stats.associate { it.genreId to it.count } }

    /** Records one user visit to the TMDB TV genre [genreId]. */
    suspend fun recordTvGenreVisit(genreId: Int) {
        tvGenreDao.incrementOrInsert(genreId)
    }

    /**
     * Searches TMDB for TV series matching [query].
     *
     * @param query Search query text.
     * @param page Page number to retrieve.
     * @return Matching page of TV series summaries.
     */
    suspend fun searchTvSeries(query: String, page: Int = 1): TmdbTvPage {
        requireConfiguredApiKey()
        return tmdbService.searchTv(
            apiKey = apiKey,
            query = query,
            page = page
        )
    }

    /**
     * Loads one sorted page of series matching a TV genre.
     *
     * @param genreId TMDB TV genre identifier.
     * @param sortBy TMDB discovery sort key.
     * @param page Requested result page.
     * @return The requested page of matching series.
     */
    suspend fun discoverTvByGenre(genreId: Int, sortBy: String, page: Int): TmdbTvPage {
        requireConfiguredApiKey()
        return tmdbService.discoverTvByGenre(
            apiKey = apiKey,
            genreId = genreId,
            sortBy = sortBy,
            page = page
        )
    }

    /**
     * Loads a series with its seasons and aggregate cast.
     *
     * @param seriesId TMDB TV series identifier.
     * @return The series detail response.
     */
    suspend fun getTvDetails(seriesId: Int): TmdbTvSummary {
        requireConfiguredApiKey()
        return tmdbService.getTvDetails(seriesId = seriesId, apiKey = apiKey)
    }

    /**
     * Loads episode listings for a series season.
     *
     * @param seriesId TMDB TV series identifier.
     * @param seasonNumber Season number to retrieve.
     * @return Season metadata and episodes.
     */
    suspend fun getTvSeasonDetails(seriesId: Int, seasonNumber: Int): TmdbTvSeasonDetails {
        requireConfiguredApiKey()
        return tmdbService.getTvSeasonDetails(
            seriesId = seriesId,
            seasonNumber = seasonNumber,
            apiKey = apiKey
        )
    }

    /**
     * Returns a linked IMDb identifier for a TMDB TV series, or `null` when unavailable.
     * Strips "tt" prefix if present for API compatibility.
     *
     * @param seriesId TMDB TV series identifier.
     */
    suspend fun getTvImdbId(seriesId: Int): String? {
        return try {
            if (apiKey.isEmpty()) return null
            val response = tmdbService.getTvExternalIds(seriesId = seriesId, apiKey = apiKey)
            val imdbId = response.imdbId
            if (imdbId.isNullOrEmpty()) null else imdbId.removePrefix("tt")
        } catch (e: Exception) {
            null
        }
    }

    /** Fails clearly when the build does not provide a TMDB API key. */
    private fun requireConfiguredApiKey() {
        check(apiKey.isNotBlank()) { "TMDB API key not configured" }
    }
}
