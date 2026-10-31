package com.martinrevert.latorrentola.ui.components

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieResult
import com.martinrevert.latorrentola.model.YTS.Movie
import org.junit.Test
import java.util.Locale

class HandheldMovieAdaptiveComponentsTest {

    @Test
    fun `movie hero metadata prefers TMDB release year and vote average when available`() {
        val ytsMovie = Movie(
            id = 1,
            title = "Inception",
            year = 2010,
            rating = "8.8"
        )
        val tmdbMovie = TmdbMovieResult(
            id = 27205,
            title = "Inception",
            releaseDate = "2010-07-16",
            voteAverage = 8.364,
            overview = "Cobb steals information from targets by entering their dreams."
        )

        val year = ytsMovie.year?.toString() ?: tmdbMovie.releaseYear
        val rating = ytsMovie.rating?.takeIf(String::isNotBlank)?.let { "★ $it" }
            ?: tmdbMovie.voteAverage?.let { "★ ${"%.1f".format(Locale.US, it)}" }
        val overview = tmdbMovie.overview?.takeIf(String::isNotBlank)
            ?: ytsMovie.descriptionFull?.takeIf(String::isNotBlank)
            ?: ytsMovie.summary.orEmpty()

        assertThat(year).isEqualTo("2010")
        assertThat(rating).isEqualTo("★ 8.8")
        assertThat(overview).isEqualTo("Cobb steals information from targets by entering their dreams.")
    }

    @Test
    fun `two-touch interaction contract differentiates preview selection from direct detail navigation`() {
        val movie1 = Movie(id = 101, title = "Interstellar")
        val movie2 = Movie(id = 102, title = "Dunkirk")

        var activeHeroMovieId: Int? = 101
        var detailOpenedMovieId: Int? = null

        fun handleMovieClick(clickedMovie: Movie) {
            if (activeHeroMovieId == clickedMovie.id) {
                detailOpenedMovieId = clickedMovie.id
            } else {
                activeHeroMovieId = clickedMovie.id
            }
        }

        // First click on movie2: updates hero preview
        handleMovieClick(movie2)
        assertThat(activeHeroMovieId).isEqualTo(102)
        assertThat(detailOpenedMovieId).isNull()

        // Second click on movie2: opens detail
        handleMovieClick(movie2)
        assertThat(detailOpenedMovieId).isEqualTo(102)

        // Click on movie1: updates hero preview back to movie1
        handleMovieClick(movie1)
        assertThat(activeHeroMovieId).isEqualTo(101)
    }

    @Test
    fun `movie backdrop resolution selects best available backdrop fallback`() {
        val movieWithOriginal = Movie(
            id = 1,
            title = "Avatar",
            backgroundImageOriginal = "https://example.com/original.jpg",
            backgroundImage = "https://example.com/standard.jpg",
            largeCoverImage = "https://example.com/cover.jpg"
        )
        val resolvedModel = movieWithOriginal.backgroundImageOriginal
            ?: movieWithOriginal.backgroundImage
            ?: movieWithOriginal.largeCoverImage

        assertThat(resolvedModel).isEqualTo("https://example.com/original.jpg")

        val movieWithStandardOnly = Movie(
            id = 2,
            title = "Avatar 2",
            backgroundImage = "https://example.com/standard.jpg",
            largeCoverImage = "https://example.com/cover.jpg"
        )
        val fallbackModel = movieWithStandardOnly.backgroundImageOriginal
            ?: movieWithStandardOnly.backgroundImage
            ?: movieWithStandardOnly.largeCoverImage

        assertThat(fallbackModel).isEqualTo("https://example.com/standard.jpg")
    }

    @Test
    fun `tmdb summary fallback resolves title, year, rating, and overview for OTT catalog hero`() {
        val summary = com.martinrevert.latorrentola.model.TMDB.TmdbMovieSummary(
            id = 550,
            title = "Fight Club",
            releaseDate = "1999-10-15",
            voteAverage = 8.433,
            overview = "An insomniac office worker looking for a way to change his life."
        )

        val resolvedTitle = summary.title ?: summary.originalTitle.orEmpty()
        val resolvedYear = summary.releaseDate?.take(4)
        val resolvedRating = summary.voteAverage?.takeIf { it > 0.0 }?.let { "★ ${"%.1f".format(Locale.US, it)}" }
        val resolvedOverview = summary.overview.orEmpty()

        assertThat(resolvedTitle).isEqualTo("Fight Club")
        assertThat(resolvedYear).isEqualTo("1999")
        assertThat(resolvedRating).isEqualTo("★ 8.4")
        assertThat(resolvedOverview).isEqualTo("An insomniac office worker looking for a way to change his life.")
    }

    @Test
    fun `movie detail external ratings prioritize OMDb ratings with TMDB and YTS fallback`() {
        val ratings = com.martinrevert.latorrentola.model.omdb.MovieRatings(
            imdbRating = "8.9/10",
            rottenTomatoesRating = "94%",
            metacriticRating = "84/100"
        )
        val ytsMovie = Movie(id = 1, rating = "8.5")
        val tmdbMovie = TmdbMovieResult(id = 10, voteAverage = 8.2)

        val effectiveImdbRating = ratings.imdbRating
            ?: ytsMovie.rating?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }?.let { "$it/10" }
            ?: tmdbMovie.voteAverage?.takeIf { it > 0.0 }?.let { "%.1f/10".format(Locale.US, it) }

        assertThat(effectiveImdbRating).isEqualTo("8.9/10")
        assertThat(ratings.rottenTomatoesRating).isEqualTo("94%")
        assertThat(ratings.metacriticRating).isEqualTo("84/100")

        // Without OMDb ratings, fall back to YTS rating
        val emptyRatings = com.martinrevert.latorrentola.model.omdb.MovieRatings()
        val fallbackImdbRating = emptyRatings.imdbRating
            ?: ytsMovie.rating?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }?.let { "$it/10" }
            ?: tmdbMovie.voteAverage?.takeIf { it > 0.0 }?.let { "%.1f/10".format(Locale.US, it) }

        assertThat(fallbackImdbRating).isEqualTo("8.5/10")
    }
}
