package com.martinrevert.latorrentola.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room DAO for user favorites (movies and TV series). */
@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites_movies WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeFavoriteMovies(userId: String): Flow<List<FavoriteMovieEntity>>

    @Query("SELECT * FROM favorites_movies WHERE userId = :userId ORDER BY updatedAt DESC")
    suspend fun getFavoriteMovies(userId: String): List<FavoriteMovieEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites_movies WHERE userId = :userId AND movieId = :movieId)")
    fun isMovieFavoriteFlow(userId: String, movieId: Int): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites_movies WHERE userId = :userId AND movieId = :movieId)")
    suspend fun isMovieFavorite(userId: String, movieId: Int): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorites_movies WHERE userId = :userId AND ((:imdbId != '' AND imdbId = :imdbId) OR (:movieId > 0 AND movieId = :movieId)))")
    fun isMovieFavoriteByAnyIdFlow(userId: String, movieId: Int, imdbId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites_movies WHERE userId = :userId AND ((:imdbId != '' AND imdbId = :imdbId) OR (:movieId > 0 AND movieId = :movieId)))")
    suspend fun isMovieFavoriteByAnyId(userId: String, movieId: Int, imdbId: String): Boolean

    @Query("SELECT * FROM favorites_movies WHERE userId = :userId AND imdbId = :imdbId LIMIT 1")
    suspend fun getMovieByImdbId(userId: String, imdbId: String): FavoriteMovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovie(movie: FavoriteMovieEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovies(movies: List<FavoriteMovieEntity>)

    @Query("DELETE FROM favorites_movies WHERE userId = :userId AND movieId = :movieId")
    suspend fun deleteMovie(userId: String, movieId: Int)

    @Query("DELETE FROM favorites_movies WHERE userId = :userId AND imdbId = :imdbId")
    suspend fun deleteMovieByImdbId(userId: String, imdbId: String)

    @Query("SELECT * FROM favorites_tv WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeFavoriteTvSeries(userId: String): Flow<List<FavoriteTvSeriesEntity>>

    @Query("SELECT * FROM favorites_tv WHERE userId = :userId ORDER BY updatedAt DESC")
    suspend fun getFavoriteTvSeries(userId: String): List<FavoriteTvSeriesEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites_tv WHERE userId = :userId AND seriesId = :seriesId)")
    fun isTvSeriesFavoriteFlow(userId: String, seriesId: Int): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites_tv WHERE userId = :userId AND seriesId = :seriesId)")
    suspend fun isTvSeriesFavorite(userId: String, seriesId: Int): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorites_tv WHERE userId = :userId AND ((:imdbId != '' AND imdbId = :imdbId) OR (:seriesId > 0 AND seriesId = :seriesId)))")
    fun isTvSeriesFavoriteByAnyIdFlow(userId: String, seriesId: Int, imdbId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites_tv WHERE userId = :userId AND ((:imdbId != '' AND imdbId = :imdbId) OR (:seriesId > 0 AND seriesId = :seriesId)))")
    suspend fun isTvSeriesFavoriteByAnyId(userId: String, seriesId: Int, imdbId: String): Boolean

    @Query("SELECT * FROM favorites_tv WHERE userId = :userId AND imdbId = :imdbId LIMIT 1")
    suspend fun getTvSeriesByImdbId(userId: String, imdbId: String): FavoriteTvSeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTvSeries(series: FavoriteTvSeriesEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTvSeriesList(list: List<FavoriteTvSeriesEntity>)

    @Query("DELETE FROM favorites_tv WHERE userId = :userId AND seriesId = :seriesId")
    suspend fun deleteTvSeries(userId: String, seriesId: Int)

    @Query("DELETE FROM favorites_tv WHERE userId = :userId AND imdbId = :imdbId")
    suspend fun deleteTvSeriesByImdbId(userId: String, imdbId: String)

    @Query("DELETE FROM favorites_movies WHERE userId = :userId")
    suspend fun clearMoviesForUser(userId: String)

    @Query("DELETE FROM favorites_tv WHERE userId = :userId")
    suspend fun clearTvForUser(userId: String)
}
