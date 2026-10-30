package com.martinrevert.latorrentola.network

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.database.TvGenreDao
import com.martinrevert.latorrentola.model.TMDB.TmdbMoviePage
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieSummary
import com.martinrevert.latorrentola.model.TMDB.TmdbTvPage
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.TMDB.TmdbWatchProvider
import com.martinrevert.latorrentola.model.TMDB.TmdbWatchProvidersResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class TmdbRepositoryTest {

    private lateinit var repository: TmdbRepository
    private val tmdbService: TmdbService = mockk()
    private val tvGenreDao: TvGenreDao = mockk(relaxed = true)

    @Before
    fun setUp() {
        repository = TmdbRepository(tmdbService, tvGenreDao)
    }

    @Test
    fun `getMovieWatchProviders fetches and sorts providers by display priority`() = runTest {
        val rawProviders = listOf(
            TmdbWatchProvider(providerId = 119, providerName = "Prime Video", displayPriority = 5),
            TmdbWatchProvider(providerId = 8, providerName = "Netflix", displayPriority = 1)
        )
        val mockResponse = TmdbWatchProvidersResponse(results = rawProviders)
        coEvery {
            tmdbService.getMovieWatchProviders(apiKey = any(), watchRegion = "US")
        } returns mockResponse

        val result = repository.getMovieWatchProviders("US")

        assertThat(result.map { it.providerId }).containsExactly(8, 119).inOrder()
        coVerify(exactly = 1) {
            tmdbService.getMovieWatchProviders(apiKey = any(), watchRegion = "US")
        }
    }

    @Test
    fun `getTvWatchProviders fetches and sorts providers by display priority`() = runTest {
        val rawProviders = listOf(
            TmdbWatchProvider(providerId = 337, providerName = "Disney+", displayPriority = 3),
            TmdbWatchProvider(providerId = 8, providerName = "Netflix", displayPriority = 1)
        )
        val mockResponse = TmdbWatchProvidersResponse(results = rawProviders)
        coEvery {
            tmdbService.getTvWatchProviders(apiKey = any(), watchRegion = "ES")
        } returns mockResponse

        val result = repository.getTvWatchProviders("ES")

        assertThat(result.map { it.providerId }).containsExactly(8, 337).inOrder()
        coVerify(exactly = 1) {
            tmdbService.getTvWatchProviders(apiKey = any(), watchRegion = "ES")
        }
    }

    @Test
    fun `discoverMoviesByProvider forwards parameters to tmdbService`() = runTest {
        val mockPage = TmdbMoviePage(
            page = 1,
            totalPages = 5,
            totalResults = 100,
            results = listOf(
                TmdbMovieSummary(id = 101, title = "Mock Movie", releaseDate = "2024-05-10")
            )
        )
        coEvery {
            tmdbService.discoverMoviesByProvider(
                apiKey = any(),
                providerId = 8,
                watchRegion = "US",
                sortBy = "popularity.desc",
                page = 1,
                language = any()
            )
        } returns mockPage

        val result = repository.discoverMoviesByProvider(
            providerId = 8,
            region = "US",
            sortBy = "popularity.desc",
            page = 1
        )

        assertThat(result).isEqualTo(mockPage)
        coVerify {
            tmdbService.discoverMoviesByProvider(
                apiKey = any(),
                providerId = 8,
                watchRegion = "US",
                sortBy = "popularity.desc",
                page = 1,
                language = any()
            )
        }
    }

    @Test
    fun `discoverTvByProvider forwards parameters to tmdbService`() = runTest {
        val mockPage = TmdbTvPage(
            page = 2,
            totalPages = 10,
            totalResults = 200,
            results = listOf(
                TmdbTvSummary(id = 202, name = "Mock TV Series", firstAirDate = "2023-09-15")
            )
        )
        coEvery {
            tmdbService.discoverTvByProvider(
                apiKey = any(),
                providerId = 337,
                watchRegion = "AR",
                sortBy = "first_air_date.desc",
                page = 2,
                language = any()
            )
        } returns mockPage

        val result = repository.discoverTvByProvider(
            providerId = 337,
            region = "AR",
            sortBy = "first_air_date.desc",
            page = 2
        )

        assertThat(result).isEqualTo(mockPage)
        coVerify {
            tmdbService.discoverTvByProvider(
                apiKey = any(),
                providerId = 337,
                watchRegion = "AR",
                sortBy = "first_air_date.desc",
                page = 2,
                language = any()
            )
        }
    }
}
