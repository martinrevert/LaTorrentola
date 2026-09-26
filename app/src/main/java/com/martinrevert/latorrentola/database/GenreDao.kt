package com.martinrevert.latorrentola.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.martinrevert.latorrentola.model.stats.GenreStats
import kotlinx.coroutines.flow.Flow

/** Room queries for per-genre visit statistics. */
@Dao
interface GenreDao {
    /** Observes the highest-count genres, capped at [limit]. */
    @Query("SELECT * FROM genre_stats ORDER BY count DESC LIMIT :limit")
    fun getTopGenres(limit: Int): Flow<List<GenreStats>>

    /** Observes genres with recorded visits, ordered by descending count. */
    @Query("SELECT * FROM genre_stats WHERE count > 0 ORDER BY count DESC")
    fun getAllGenresWithCount(): Flow<List<GenreStats>>

    /** Inserts [genreStat] unless a row with the same genre already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenre(genreStat: GenreStats)

    /** Increments the visit count for [genre]. */
    @Query("UPDATE genre_stats SET count = count + 1 WHERE genre = :genre")
    suspend fun incrementGenreCount(genre: String)

    /** Inserts [genre] at zero if needed, then records one visit. */
    suspend fun incrementOrInsert(genre: String) {
        insertGenre(GenreStats(genre, 0))
        incrementGenreCount(genre)
    }

    /** Returns all stored genre statistics once. */
    @Query("SELECT * FROM genre_stats")
    suspend fun getAllStats(): List<GenreStats>
}
