package com.martinrevert.latorrentola.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.martinrevert.latorrentola.model.stats.TvGenreStats
import kotlinx.coroutines.flow.Flow

/** Room queries for TMDB television genre visit statistics. */
@Dao
interface TvGenreDao {
    /** Observes TV genres ordered by descending visit count. */
    @Query("SELECT * FROM tv_genre_stats ORDER BY count DESC")
    fun observeByPopularity(): Flow<List<TvGenreStats>>

    /** Inserts [stats] unless a row with the same TMDB genre ID already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(stats: TvGenreStats)

    /** Increments the visit count for [genreId]. */
    @Query("UPDATE tv_genre_stats SET count = count + 1 WHERE genreId = :genreId")
    suspend fun increment(genreId: Int)

    /** Inserts [genreId] at zero if needed, then records one visit atomically. */
    @Transaction
    suspend fun incrementOrInsert(genreId: Int) {
        insert(TvGenreStats(genreId = genreId))
        increment(genreId)
    }
}
