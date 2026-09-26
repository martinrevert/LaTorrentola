package com.martinrevert.latorrentola.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.martinrevert.latorrentola.model.date.DateLastVisit

/** Room queries for last-visit date records. */
@Dao
interface DateDao {

    /** Inserts or replaces a visit date. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setDate(date: DateLastVisit)

    /** Returns the most recently inserted date record, if present. */
    @Query("SELECT * FROM date ORDER BY id DESC LIMIT 1")
    suspend fun getDate(): List<DateLastVisit>
}
