package com.martinrevert.latorrentola.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room DAO for persisted local account profiles. */
@Dao
interface SavedAccountDao {
    @Query("SELECT * FROM saved_accounts ORDER BY lastActiveTimestamp DESC")
    fun observeAll(): Flow<List<SavedAccountEntity>>

    @Query("SELECT * FROM saved_accounts ORDER BY lastActiveTimestamp DESC")
    suspend fun getAll(): List<SavedAccountEntity>

    @Query("SELECT * FROM saved_accounts WHERE uid = :uid LIMIT 1")
    suspend fun get(uid: String): SavedAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: SavedAccountEntity)

    @Query("DELETE FROM saved_accounts WHERE uid = :uid")
    suspend fun delete(uid: String)
}
