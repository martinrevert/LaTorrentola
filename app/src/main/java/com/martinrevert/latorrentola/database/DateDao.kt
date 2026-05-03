package com.martinrevert.latorrentola.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.martinrevert.latorrentola.model.date.DateLastVisit

@Dao
interface DateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setDate(date: DateLastVisit)

    @Query("SELECT * FROM date ORDER BY id DESC LIMIT 1")
    suspend fun getDate(): List<DateLastVisit>
}
