package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.constants.Constants
import com.martinrevert.latorrentola.database.DateDao
import com.martinrevert.latorrentola.database.GenreDao
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.MovieDetails
import com.martinrevert.latorrentola.model.date.DateLastVisit
import com.martinrevert.latorrentola.model.stats.GenreStats
import com.martinrevert.latorrentola.utils.PreferenceManager
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Combines YTS API operations with Firestore library and Room statistics access.
 *
 * @property ytsService HTTP client for YTS movie requests.
 * @property fcmService HTTP client for recently announced movies.
 * @property userLibraryRepository cloud favorites and download access.
 * @property genreDao local genre visit statistics access.
 * @property dateDao local last-visit record access.
 */
@Singleton
class YtsRepository @Inject constructor(
    private val ytsService: YtsService,
    private val fcmService: FcmService,
    private val userLibraryRepository: UserLibraryRepository,
    private val genreDao: GenreDao,
    private val dateDao: DateDao
) {
    private data class MoviesPageCacheKey(
        val page: Int,
        val quality: String?,
        val ratingKey: String
    )

    private data class CachedMoviesPage(
        val response: MovieDetails,
        val fetchedAtMs: Long
    )

    companion object {
        /** Short in-memory TTL for home catalog pages (3 minutes) to bridge Login -> Home transitions. */
        const val HOME_PAGE_CACHE_TTL_MS = 3L * 60L * 1000L
    }

    private val moviesPageCache = ConcurrentHashMap<MoviesPageCacheKey, CachedMoviesPage>()
    private val inFlightMoviesRequests = ConcurrentHashMap<MoviesPageCacheKey, CompletableDeferred<MovieDetails>>()
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Fetches a paginated YTS movie list with rating, quality, and default sorting filters.
     *
     * Reuses a short-TTL in-memory cache and coalesces concurrent identical requests (e.g. a
     * speculative prefetch started on the Login screen) unless [forceRefresh] is `true`.
     */
    suspend fun getMovies(
        page: Int,
        quality: String? = null,
        minimumRating: Float = PreferenceManager.DEFAULT_MINIMUM_RATING,
        forceRefresh: Boolean = false
    ): MovieDetails {
        val ratingString = formatMinimumRating(minimumRating)
        val cacheKey = MoviesPageCacheKey(page = page, quality = quality, ratingKey = ratingString)

        if (!forceRefresh) {
            while (true) {
                val now = System.currentTimeMillis()
                moviesPageCache[cacheKey]?.let { cached ->
                    if ((now - cached.fetchedAtMs) < HOME_PAGE_CACHE_TTL_MS) {
                        return cached.response
                    }
                }
                val newDeferred = CompletableDeferred<MovieDetails>()
                val existing = inFlightMoviesRequests.putIfAbsent(cacheKey, newDeferred)
                if (existing != null) {
                    try {
                        return existing.await()
                    } catch (_: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        continue
                    }
                }
                return try {
                    val response = fetchMoviesFromService(page, quality, ratingString)
                    if (!response.data?.movies.isNullOrEmpty()) {
                        moviesPageCache[cacheKey] = CachedMoviesPage(response, System.currentTimeMillis())
                    }
                    inFlightMoviesRequests.remove(cacheKey, newDeferred)
                    newDeferred.complete(response)
                    response
                } catch (e: Throwable) {
                    inFlightMoviesRequests.remove(cacheKey, newDeferred)
                    newDeferred.completeExceptionally(e)
                    throw e
                } finally {
                    inFlightMoviesRequests.remove(cacheKey, newDeferred)
                }
            }
        }

        if (page == 1) {
            moviesPageCache.clear()
        } else {
            moviesPageCache.remove(cacheKey)
        }
        val response = fetchMoviesFromService(page, quality, ratingString)
        if (!response.data?.movies.isNullOrEmpty()) {
            moviesPageCache[cacheKey] = CachedMoviesPage(response, System.currentTimeMillis())
        }
        return response
    }

    /**
     * Speculatively prefetches page 1 of the Home movie catalog on an application-scoped coroutine
     * so navigating away from Login never cancels the in-flight HTTP request.
     */
    fun prefetchHomeFirstPage(
        quality: String? = null,
        minimumRating: Float = PreferenceManager.DEFAULT_MINIMUM_RATING
    ) {
        prefetchScope.launch {
            try {
                getMovies(
                    page = 1,
                    quality = quality,
                    minimumRating = minimumRating,
                    forceRefresh = false
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    private fun formatMinimumRating(minimumRating: Float): String =
        if (minimumRating % 1f == 0f) {
            minimumRating.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", minimumRating)
        }

    private suspend fun fetchMoviesFromService(
        page: Int,
        quality: String?,
        ratingString: String
    ): MovieDetails = ytsService.getMovieDetails(
        Constants.PAGE_SIZE,
        ratingString,
        page,
        "true",
        "true",
        "year",
        "desc",
        quality
    )

    /** Searches YTS movies by text query. */
    suspend fun searchMovies(query: String, page: Int, quality: String? = null): MovieDetails {
        return ytsService.getMovieSearch(Constants.PAGE_SIZE, query, page, "true", "year", "desc", quality)
    }

    /** Searches YTS movies by genre. */
    suspend fun searchByGenre(genre: String, page: Int, quality: String? = null): MovieDetails {
        return ytsService.getGenreSearch(Constants.PAGE_SIZE, genre, page, "true", "year", "desc", quality)
    }

    /** Searches YTS movies by torrent quality. */
    suspend fun searchByQuality(quality: String, page: Int): MovieDetails {
        return ytsService.getTridiSearch(Constants.PAGE_SIZE, quality, page, "true", "year", "desc")
    }

    /** Observes the signed-in user's favorite movies. */
    fun getFavoriteMovies(): Flow<List<Movie>> {
        return userLibraryRepository.getFavoriteMovies()
    }

    /** Checks whether [movieId] is in the user's favorites. */
    suspend fun isFavorite(movieId: Int): Boolean {
        return userLibraryRepository.isFavorite(movieId)
    }

    /** Adds [movie] to the user's favorites. */
    suspend fun addFavorite(movie: Movie) {
        userLibraryRepository.addFavorite(movie)
    }

    /** Removes [movie] from the user's favorites. */
    suspend fun removeFavorite(movie: Movie) {
        userLibraryRepository.removeFavorite(movie)
    }

    /** Fetches full YTS movie details, including images and cast. */
    suspend fun getMovieFullDetails(movieId: Int): MovieDetails {
        return ytsService.getMovieFullDetails(movieId)
    }

    /** Fetches a lightweight summary without images or cast. */
    suspend fun getMovieSummary(movieId: Int): MovieDetails {
        return ytsService.getMovieFullDetails(movieId, withImages = false, withCast = false)
    }

    /** Returns identifiers for movies recently announced by the FCM backend. */
    suspend fun getRecentMovieIds(): List<Int> {
        return fcmService.getRecentMovieIds().map { it.movieId }
    }

    /** Observes the most frequently visited genres, limited to [limit] entries. */
    fun getTopGenres(limit: Int): Flow<List<GenreStats>> {
        return genreDao.getTopGenres(limit)
    }

    /** Observes all locally recorded genres and visit counts. */
    fun getAllGenresWithCount(): Flow<List<GenreStats>> {
        return genreDao.getAllGenresWithCount()
    }

    /** Increments the local visit count for [genre], inserting it when needed. */
    suspend fun recordGenreVisit(genre: String) {
        genreDao.incrementOrInsert(genre)
    }

    /** Returns the locally persisted last-visit record, if any. */
    suspend fun getLastVisitDate(): DateLastVisit? {
        return dateDao.getDate().firstOrNull()
    }

    /** Persists [date] as the last-visit record. */
    suspend fun setLastVisitDate(date: DateLastVisit) {
        dateDao.setDate(date)
    }
}
