package com.martinrevert.latorrentola.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room DAO for user completed download markers (movies and episodes). */
@Dao
interface DownloadRecordDao {
    @Query("SELECT * FROM downloaded_movies WHERE userId = :userId ORDER BY timestamp DESC")
    fun observeDownloadedMovies(userId: String): Flow<List<DownloadedMovieEntity>>

    @Query("SELECT * FROM downloaded_movies WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getDownloadedMovies(userId: String): List<DownloadedMovieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovie(movie: DownloadedMovieEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovies(movies: List<DownloadedMovieEntity>)

    @Query("DELETE FROM downloaded_movies WHERE userId = :userId AND hash = :hash")
    suspend fun deleteMovieByHash(userId: String, hash: String)

    @Query("DELETE FROM downloaded_movies WHERE userId = :userId AND movieId = :movieId")
    suspend fun deleteMoviesByMovieId(userId: String, movieId: Int)

    @Query("DELETE FROM downloaded_movies WHERE userId = :userId AND imdbId = :imdbId")
    suspend fun deleteMoviesByImdbId(userId: String, imdbId: String)

    @Query("SELECT * FROM downloaded_movies WHERE userId = :userId AND ((:imdbId != '' AND imdbId = :imdbId) OR (:movieId > 0 AND movieId = :movieId)) LIMIT 1")
    suspend fun findDownloadedMovie(userId: String, movieId: Int, imdbId: String = ""): DownloadedMovieEntity?

    @Query("SELECT * FROM downloaded_episodes WHERE userId = :userId ORDER BY timestamp DESC")
    fun observeDownloadedEpisodes(userId: String): Flow<List<DownloadedEpisodeEntity>>

    @Query("SELECT * FROM downloaded_episodes WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getDownloadedEpisodes(userId: String): List<DownloadedEpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisode(episode: DownloadedEpisodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodes(episodes: List<DownloadedEpisodeEntity>)

    @Query("DELETE FROM downloaded_episodes WHERE userId = :userId AND hash = :hash")
    suspend fun deleteEpisodeByHash(userId: String, hash: String)

    @Query("DELETE FROM downloaded_episodes WHERE userId = :userId AND seriesId = :seriesId AND seasonNumber = :seasonNumber AND episodeNumber = :episodeNumber")
    suspend fun deleteEpisode(userId: String, seriesId: Int, seasonNumber: Int, episodeNumber: Int)

    @Query("DELETE FROM downloaded_episodes WHERE userId = :userId AND ((:imdbId != '' AND imdbId = :imdbId) OR (:seriesId > 0 AND seriesId = :seriesId)) AND seasonNumber = :seasonNumber AND episodeNumber = :episodeNumber")
    suspend fun deleteEpisodeByAnyId(userId: String, seriesId: Int, imdbId: String, seasonNumber: Int, episodeNumber: Int)

    @Query("SELECT * FROM downloaded_episodes WHERE userId = :userId AND ((:imdbId != '' AND imdbId = :imdbId) OR (:seriesId > 0 AND seriesId = :seriesId)) AND seasonNumber = :seasonNumber AND episodeNumber = :episodeNumber LIMIT 1")
    suspend fun findDownloadedEpisode(userId: String, seriesId: Int, imdbId: String, seasonNumber: Int, episodeNumber: Int): DownloadedEpisodeEntity?

    @Query("DELETE FROM downloaded_movies WHERE userId = :userId")
    suspend fun clearMoviesForUser(userId: String)

    @Query("DELETE FROM downloaded_episodes WHERE userId = :userId")
    suspend fun clearEpisodesForUser(userId: String)
}
