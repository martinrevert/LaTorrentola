package com.martinrevert.latorrentola.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Persists and exposes local watch history playback progress. */
@Dao
interface WatchHistoryDao {

    /** Observes all watch history items ordered by most recently updated for a specific user. */
    @Query("SELECT * FROM watch_history WHERE userId = :userId ORDER BY timestamp DESC")
    fun observeAll(userId: String): Flow<List<WatchHistoryEntity>>

    /** Observes all watch history items ordered by most recently updated across profiles. */
    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<WatchHistoryEntity>>

    /** Returns watch progress for a specific canonical media item and user, falling back to unassigned records. */
    @Query("SELECT * FROM watch_history WHERE (userId = :userId OR (userId = '' AND :userId != '')) AND mediaId = :mediaId ORDER BY CASE WHEN userId = :userId THEN 0 ELSE 1 END, timestamp DESC LIMIT 1")
    suspend fun get(userId: String, mediaId: String): WatchHistoryEntity?

    /** Claims unassigned pre-migration watch history records for a newly authenticated user. */
    @Query("UPDATE watch_history SET userId = :userId WHERE userId = ''")
    suspend fun claimUnassignedItems(userId: String): Int

    /** Inserts or replaces playback progress. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchHistoryEntity)

    /** Returns all watch history records for a specific user. */
    @Query("SELECT * FROM watch_history WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getAll(userId: String): List<WatchHistoryEntity>

    /** Returns all watch history records across profiles. */
    @Query("SELECT * FROM watch_history")
    suspend fun getAll(): List<WatchHistoryEntity>

    /** Removes a watch history record for a specific user. */
    @Query("DELETE FROM watch_history WHERE userId = :userId AND mediaId = :mediaId")
    suspend fun delete(userId: String, mediaId: String)
}
