package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.constants.Constants
import com.martinrevert.latorrentola.database.DateDao
import com.martinrevert.latorrentola.database.GenreDao
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.MovieDetails
import com.martinrevert.latorrentola.model.date.DateLastVisit
import com.martinrevert.latorrentola.model.stats.GenreStats
import com.martinrevert.latorrentola.utils.PreferenceManager
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

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

    /** Fetches a paginated YTS movie list with rating, quality, and default sorting filters. */
    suspend fun getMovies(
        page: Int,
        quality: String? = null,
        minimumRating: Float = PreferenceManager.DEFAULT_MINIMUM_RATING
    ): MovieDetails {
        val ratingString = if (minimumRating % 1f == 0f) {
            minimumRating.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", minimumRating)
        }
        return ytsService.getMovieDetails(
            Constants.PAGE_SIZE,
            ratingString,
            page,
            "true",
            "true",
            "year",
            "desc",
            quality
        )
    }

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
