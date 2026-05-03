package com.martinrevert.latorrentola.model.stats

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "genre_stats")
data class GenreStats(
    @PrimaryKey
    val genre: String,
    val count: Int = 0
)
