package com.martinrevert.latorrentola.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.martinrevert.latorrentola.model.torrent.TorrentDownload
import kotlinx.coroutines.flow.Flow

/** Persists locally managed torrent jobs and exposes them to the download manager UI. */
@Dao
interface TorrentDownloadDao {

    /** Observes jobs ordered by most recently updated. */
    @Query("SELECT * FROM torrent_downloads ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<TorrentDownload>>

    /** Returns jobs that need to be resumed after process recreation. */
    @Query("SELECT * FROM torrent_downloads WHERE state IN ('QUEUED', 'DOWNLOADING', 'BUFFERING', 'READY')")
    suspend fun getRecoverable(): List<TorrentDownload>

    /** Returns a job by its stable info hash. */
    @Query("SELECT * FROM torrent_downloads WHERE infoHash = :infoHash LIMIT 1")
    suspend fun get(infoHash: String): TorrentDownload?

    /** Inserts or replaces a locally managed torrent job. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: TorrentDownload)

    /** Removes a completed or canceled job's local record. */
    @Query("DELETE FROM torrent_downloads WHERE infoHash = :infoHash")
    suspend fun delete(infoHash: String)
}
