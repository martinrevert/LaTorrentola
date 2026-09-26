package com.martinrevert.latorrentola.model.stats

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Locally persisted count of visits associated with a movie genre.
 *
 * @property genre Genre name and primary key.
 * @property count Number of recorded visits.
 */
@Entity(tableName = "genre_stats")
data class GenreStats(
    @PrimaryKey
    val genre: String,
    val count: Int = 0
)
