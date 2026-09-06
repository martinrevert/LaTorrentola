package com.martinrevert.latorrentola.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.martinrevert.latorrentola.model.stats.GenreStats
import kotlinx.coroutines.flow.Flow

@Dao
interface GenreDao {
    @Query("SELECT * FROM genre_stats ORDER BY count DESC LIMIT :limit")
    fun getTopGenres(limit: Int): Flow<List<GenreStats>>

    @Query("SELECT * FROM genre_stats WHERE count > 0 ORDER BY count DESC")
    fun getAllGenresWithCount(): Flow<List<GenreStats>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenre(genreStat: GenreStats)

    @Query("UPDATE genre_stats SET count = count + 1 WHERE genre = :genre")
    suspend fun incrementGenreCount(genre: String)

    suspend fun incrementOrInsert(genre: String) {
        insertGenre(GenreStats(genre, 0))
        incrementGenreCount(genre)
    }

    @Query("SELECT * FROM genre_stats")
    suspend fun getAllStats(): List<GenreStats>
}
