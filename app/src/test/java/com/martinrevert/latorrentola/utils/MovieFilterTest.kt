package com.martinrevert.latorrentola.utils

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.YTS.Movie
import org.junit.Test

class MovieFilterTest {

    @Test
    fun `filterMovies should return empty list when input is null`() {
        val result = MovieFilter.filterMovies(null, "en")
        assertThat(result).isEmpty()
    }

    @Test
    fun `filterMovies should return original list when excluded languages is blank`() {
        val movies = listOf(Movie(id = 1, language = "en"))
        val result = MovieFilter.filterMovies(movies, "  ")
        assertThat(result).isEqualTo(movies)
    }

    @Test
    fun `filterMovies should filter movies correctly`() {
        val movies = listOf(
            Movie(id = 1, language = "en"),
            Movie(id = 2, language = "es"),
            Movie(id = 3, language = "fr")
        )
        val result = MovieFilter.filterMovies(movies, "en, fr")
        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(2)
    }

    @Test
    fun `filterMovies should handle null movie language`() {
        val movies = listOf(Movie(id = 1, language = null))
        val result = MovieFilter.filterMovies(movies, "en")
        assertThat(result).hasSize(1)
    }

    @Test
    fun `filterMovies should return original list when excluded list becomes empty after trim`() {
        val movies = listOf(Movie(id = 1, language = "en"))
        val result = MovieFilter.filterMovies(movies, ",,")
        assertThat(result).isEqualTo(movies)
    }
}
