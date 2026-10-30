package com.martinrevert.latorrentola.network

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.omdb.OmdbMovieResponse
import com.martinrevert.latorrentola.model.omdb.OmdbRating
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class OmdbRepositoryTest {

    private val omdbService: OmdbService = mockk()
    private lateinit var repository: OmdbRepository

    @Before
    fun setUp() {
        repository = OmdbRepository(omdbService)
    }

    @Test
    fun `getRatingsByImdbId parses ratings and caches result`() = runTest {
        val imdbId = "tt1375666"
        val response = OmdbMovieResponse(
            response = "True",
            imdbRating = "8.8",
            metascore = "74",
            ratings = listOf(
                OmdbRating(source = "Internet Movie Database", value = "8.8/10"),
                OmdbRating(source = "Rotten Tomatoes", value = "87%"),
                OmdbRating(source = "Metacritic", value = "74/100")
            )
        )
        coEvery { omdbService.getMovieByImdbId(any(), imdbId) } returns response

        val result = repository.getRatingsByImdbId(imdbId)

        assertThat(result).isNotNull()
        assertThat(result?.imdbRating).isEqualTo("8.8/10")
        assertThat(result?.rottenTomatoesRating).isEqualTo("87%")
        assertThat(result?.metacriticRating).isEqualTo("74/100")

        // Second call should return from cache without querying service again
        val cachedResult = repository.getRatingsByImdbId(imdbId)
        assertThat(cachedResult).isEqualTo(result)
        coVerify(exactly = 1) { omdbService.getMovieByImdbId(any(), imdbId) }
    }

    @Test
    fun `getRatingsByImdbId with empty id returns null`() = runTest {
        val result = repository.getRatingsByImdbId("")
        assertThat(result).isNull()
        coVerify(exactly = 0) { omdbService.getMovieByImdbId(any(), any()) }
    }

    @Test
    fun `getRatingsByImdbId returns null when response is False`() = runTest {
        val imdbId = "tt0000000"
        coEvery { omdbService.getMovieByImdbId(any(), imdbId) } returns OmdbMovieResponse(
            response = "False",
            error = "Movie not found!"
        )

        val result = repository.getRatingsByImdbId(imdbId)
        assertThat(result).isNull()
    }

    @Test
    fun `getRatingsByImdbId normalizes numeric imdb id without tt prefix`() = runTest {
        val numericId = "1375666"
        val expectedNormalizedId = "tt1375666"
        val response = OmdbMovieResponse(
            response = "True",
            imdbRating = "8.8",
            metascore = "74"
        )
        coEvery { omdbService.getMovieByImdbId(any(), expectedNormalizedId) } returns response

        val result = repository.getRatingsByImdbId(numericId)
        assertThat(result).isNotNull()
        assertThat(result?.imdbRating).isEqualTo("8.8/10")
        coVerify(exactly = 1) { omdbService.getMovieByImdbId(any(), expectedNormalizedId) }
    }

    @Test
    fun `getRatingsByImdbId handles null or NA ratings safely without NPE`() = runTest {
        val imdbId = "tt9999999"
        val response = OmdbMovieResponse(
            response = "True",
            imdbRating = "N/A",
            metascore = "N/A",
            ratings = listOf(
                OmdbRating(source = null, value = null),
                OmdbRating(source = "Rotten Tomatoes", value = "N/A"),
                OmdbRating(source = "Internet Movie Database", value = "  ")
            )
        )
        coEvery { omdbService.getMovieByImdbId(any(), imdbId) } returns response

        val result = repository.getRatingsByImdbId(imdbId)
        assertThat(result).isNotNull()
        assertThat(result?.imdbRating).isNull()
        assertThat(result?.rottenTomatoesRating).isNull()
        assertThat(result?.metacriticRating).isNull()
    }
}
