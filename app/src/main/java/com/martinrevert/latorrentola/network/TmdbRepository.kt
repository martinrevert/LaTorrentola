package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.BuildConfig
import com.martinrevert.latorrentola.model.TMDB.TmdbActorDetail
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbRepository @Inject constructor(
    private val tmdbService: TmdbService
) {
    private val apiKey: String
        get() = BuildConfig.TMDB_API_KEY

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
}
