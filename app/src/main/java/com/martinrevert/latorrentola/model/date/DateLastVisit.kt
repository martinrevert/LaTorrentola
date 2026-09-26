package com.martinrevert.latorrentola.model.date

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Room record holding the app's last-visit date.
 *
 * @property id Auto-generated local database identifier.
 * @property date Last recorded visit time.
 */
@Entity(tableName = "date")
data class DateLastVisit(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,
    @ColumnInfo
    var date: Date? = null
)
