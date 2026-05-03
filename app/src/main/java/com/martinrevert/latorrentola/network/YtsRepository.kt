package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.database.MovieDao
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.MovieDetails
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtsRepository @Inject constructor(
    private val ytsService: YtsService,
    private val movieDao: MovieDao
) {

    suspend fun getMovies(page: Int): MovieDetails {
        return ytsService.getMovieDetails(50, "6", page, "true", "true")
    }

    suspend fun searchMovies(query: String): MovieDetails {
        return ytsService.getMovieSearch(50, query, "true")
    }

    suspend fun searchByGenre(genre: String, page: Int): MovieDetails {
        return ytsService.getGenreSearch(50, genre, page, "true")
    }

    suspend fun searchByQuality(quality: String, page: Int): MovieDetails {
        return ytsService.getTridiSearch(50, quality, page, "true")
    }

    fun getFavoriteMovies(): Flow<List<Movie>> {
        return movieDao.getAll()
    }

    suspend fun isFavorite(movieId: Int): Boolean {
        return movieDao.getMovie(movieId) != null
    }

    suspend fun addFavorite(movie: Movie) {
        movieDao.insertMovie(movie)
    }

    suspend fun removeFavorite(movie: Movie) {
        movieDao.delete(movie)
    }

    suspend fun getMovieFullDetails(movieId: Int): MovieDetails {
        return ytsService.getMovieFullDetails(movieId)
    }
}
