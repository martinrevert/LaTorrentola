package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.BuildConfig
import com.martinrevert.latorrentola.model.omdb.MovieRatings
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository providing cached movie ratings from the OMDb API.
 *
 * @property omdbService Retrofit service querying OMDb endpoints.
 */
@Singleton
class OmdbRepository @Inject constructor(
    private val omdbService: OmdbService
) {
    private val ratingsCache = ConcurrentHashMap<String, MovieRatings>()

    /**
     * Retrieves aggregated ratings for [imdbId], returning a cached result if previously loaded.
     *
     * @param imdbId IMDb identifier for the movie (e.g. "tt1375666").
     * @return [MovieRatings] containing IMDb, Rotten Tomatoes, and Metacritic scores, or null if unavailable.
     */
    suspend fun getRatingsByImdbId(imdbId: String): MovieRatings? = withContext(Dispatchers.IO) {
        val cleanImdbId = imdbId.trim()
        if (cleanImdbId.isEmpty()) return@withContext null

        val formattedId = if (cleanImdbId.startsWith("tt", ignoreCase = true)) cleanImdbId else "tt$cleanImdbId"

        ratingsCache[formattedId]?.let { return@withContext it }

        val apiKey = BuildConfig.OMDB_API_KEY
        if (apiKey.isBlank()) return@withContext null

        try {
            val response = omdbService.getMovieByImdbId(apiKey = apiKey, imdbId = formattedId)
            if (response.response.equals("True", ignoreCase = true)) {
                var imdb: String? = response.imdbRating?.takeIf { it != "N/A" && it.isNotBlank() }
                var rt: String? = null
                var meta: String? = response.metascore?.takeIf { it != "N/A" && it.isNotBlank() }

                response.ratings?.forEach { rating ->
                    val source = rating.source ?: return@forEach
                    val value = rating.value?.takeIf { it != "N/A" && it.isNotBlank() } ?: return@forEach
                    when {
                        source.contains("Internet Movie Database", ignoreCase = true) -> {
                            if (imdb == null) imdb = value
                        }
                        source.contains("Rotten Tomatoes", ignoreCase = true) -> {
                            rt = value
                        }
                        source.contains("Metacritic", ignoreCase = true) -> {
                            if (meta == null) meta = value
                        }
                    }
                }

                val formattedImdb = imdb?.let { if (!it.contains("/")) "$it/10" else it }
                val formattedMeta = meta?.let { if (!it.contains("/")) "$it/100" else it }

                val ratings = MovieRatings(
                    imdbRating = formattedImdb,
                    rottenTomatoesRating = rt,
                    metacriticRating = formattedMeta
                )
                ratingsCache[formattedId] = ratings
                ratings
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
