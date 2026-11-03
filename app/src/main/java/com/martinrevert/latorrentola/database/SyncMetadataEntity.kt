package com.martinrevert.latorrentola.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Tracks the last cloud synchronization epoch per collection and user.
 */
@Entity(
    tableName = "sync_metadata",
    primaryKeys = ["userId", "collectionKey"]
)
data class SyncMetadataEntity(
    val userId: String,
    val collectionKey: String,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)

@Dao
interface SyncMetadataDao {
    @Query("SELECT lastSyncTimestamp FROM sync_metadata WHERE userId = :userId AND collectionKey = :collectionKey LIMIT 1")
    suspend fun getLastSyncTimestamp(userId: String, collectionKey: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setLastSyncTimestamp(metadata: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
