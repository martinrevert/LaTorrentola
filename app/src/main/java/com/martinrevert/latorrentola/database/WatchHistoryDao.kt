package com.martinrevert.latorrentola.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Persists and exposes local watch history playback progress. */
@Dao
interface WatchHistoryDao {

    /** Observes all watch history items ordered by most recently updated. */
    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<WatchHistoryEntity>>

    /** Returns watch progress for a specific media item. */
    @Query("SELECT * FROM watch_history WHERE mediaId = :mediaId LIMIT 1")
    suspend fun get(mediaId: String): WatchHistoryEntity?

    /** Inserts or replaces playback progress. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchHistoryEntity)

    /** Removes a watch history record. */
    @Query("DELETE FROM watch_history WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: String)
}
