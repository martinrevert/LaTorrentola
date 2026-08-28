package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.constants.Constants
import com.martinrevert.latorrentola.database.DateDao
import com.martinrevert.latorrentola.database.GenreDao
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.MovieDetails
import com.martinrevert.latorrentola.model.date.DateLastVisit
import com.martinrevert.latorrentola.model.stats.GenreStats
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtsRepository @Inject constructor(
    private val ytsService: YtsService,
    private val userLibraryRepository: UserLibraryRepository,
    private val genreDao: GenreDao,
    private val dateDao: DateDao
) {

    suspend fun getMovies(page: Int, quality: String? = null): MovieDetails {
        return ytsService.getMovieDetails(Constants.PAGE_SIZE, Constants.MIN_RATING, page, "true", "true", "year", "desc", quality)
    }

    suspend fun searchMovies(query: String, page: Int, quality: String? = null): MovieDetails {
        return ytsService.getMovieSearch(Constants.PAGE_SIZE, query, page, "true", "year", "desc", quality)
    }

    suspend fun searchByGenre(genre: String, page: Int, quality: String? = null): MovieDetails {
        return ytsService.getGenreSearch(Constants.PAGE_SIZE, genre, page, "true", "year", "desc", quality)
    }

    suspend fun searchByQuality(quality: String, page: Int): MovieDetails {
        return ytsService.getTridiSearch(Constants.PAGE_SIZE, quality, page, "true", "year", "desc")
    }

    fun getFavoriteMovies(): Flow<List<Movie>> {
        return userLibraryRepository.getFavoriteMovies()
    }

    suspend fun isFavorite(movieId: Int): Boolean {
        return userLibraryRepository.isFavorite(movieId)
    }

    suspend fun addFavorite(movie: Movie) {
        userLibraryRepository.addFavorite(movie)
    }

    suspend fun removeFavorite(movie: Movie) {
        userLibraryRepository.removeFavorite(movie)
    }

    suspend fun getMovieFullDetails(movieId: Int): MovieDetails {
        return ytsService.getMovieFullDetails(movieId)
    }

    fun getTopGenres(limit: Int): Flow<List<GenreStats>> {
        return genreDao.getTopGenres(limit)
    }

    suspend fun recordGenreVisit(genre: String) {
        genreDao.incrementOrInsert(genre)
    }

    suspend fun getLastVisitDate(): DateLastVisit? {
        return dateDao.getDate().firstOrNull()
    }

    suspend fun setLastVisitDate(date: DateLastVisit) {
        dateDao.setDate(date)
    }
}
