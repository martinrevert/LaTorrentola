package com.martinrevert.latorrentola.model.stats

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Locally persisted visit count for a TMDB television genre.
 *
 * @property genreId TMDB genre identifier and primary key.
 * @property count Number of times the user opened this genre.
 */
@Entity(tableName = "tv_genre_stats")
data class TvGenreStats(
    @PrimaryKey
    val genreId: Int,
    val count: Int = 0
)
