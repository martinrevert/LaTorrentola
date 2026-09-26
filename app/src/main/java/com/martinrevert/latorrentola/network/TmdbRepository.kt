package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.BuildConfig
import com.martinrevert.latorrentola.model.TMDB.TmdbActorDetail
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates TMDB requests and converts lookup failures into nullable/result values.
 *
 * @property tmdbService HTTP client for TMDB endpoints.
 */
@Singleton
class TmdbRepository @Inject constructor(
    private val tmdbService: TmdbService
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

            val personDetail = tmdbService.getPersonDetail(
                personId = personId,
                apiKey = apiKey,
                appendToResponse = "combined_credits",
                language = "es-ES"
            )

            Result.success(personDetail)
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
}
