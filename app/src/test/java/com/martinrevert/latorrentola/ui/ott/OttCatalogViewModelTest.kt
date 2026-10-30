package com.martinrevert.latorrentola.ui.ott

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.TMDB.TmdbMoviePage
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieSummary
import com.martinrevert.latorrentola.model.TMDB.TmdbTvPage
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.ui.home.TvGenreSort
import com.martinrevert.latorrentola.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OttCatalogViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tmdbRepository: TmdbRepository = mockk(relaxed = true)
    private lateinit var viewModel: OttCatalogViewModel

    @Before
    fun setUp() {
        coEvery { tmdbRepository.getTvGenres() } returns emptyList()
        viewModel = OttCatalogViewModel(tmdbRepository)
    }

    @Test
    fun `setProvider with isTv true loads TV series catalog`() = runTest {
        val seriesList = listOf(
            TmdbTvSummary(id = 1, name = "Stranger Things", voteAverage = 8.6)
        )
        coEvery {
            tmdbRepository.discoverTvByProvider(
                providerId = 8,
                region = any(),
                sortBy = "popularity.desc",
                page = 1
            )
        } returns TmdbTvPage(page = 1, totalPages = 3, totalResults = 30, results = seriesList)

        viewModel.setProvider(providerId = 8, isTv = true)

        assertThat(viewModel.uiState.value).isEqualTo(OttCatalogUiState.SuccessTv(seriesList))
        coVerify {
            tmdbRepository.discoverTvByProvider(
                providerId = 8,
                region = any(),
                sortBy = "popularity.desc",
                page = 1
            )
        }
    }

    @Test
    fun `setProvider with isTv false loads Movies catalog`() = runTest {
        val movieList = listOf(
            TmdbMovieSummary(id = 10, title = "The Irishman", voteAverage = 7.8, releaseDate = "2019-11-01")
        )
        coEvery {
            tmdbRepository.discoverMoviesByProvider(
                providerId = 8,
                region = any(),
                sortBy = "popularity.desc",
                page = 1
            )
        } returns TmdbMoviePage(page = 1, totalPages = 2, totalResults = 20, results = movieList)

        viewModel.setProvider(providerId = 8, isTv = false)

        assertThat(viewModel.uiState.value).isEqualTo(OttCatalogUiState.SuccessMovies(movieList))
        coVerify {
            tmdbRepository.discoverMoviesByProvider(
                providerId = 8,
                region = any(),
                sortBy = "popularity.desc",
                page = 1
            )
        }
    }

    @Test
    fun `changing sort reloads catalog with new sort key`() = runTest {
        coEvery {
            tmdbRepository.discoverMoviesByProvider(
                providerId = 337,
                region = any(),
                sortBy = any(),
                page = 1
            )
        } returns TmdbMoviePage(page = 1, totalPages = 1, totalResults = 10, results = emptyList())

        viewModel.setProvider(providerId = 337, isTv = false)
        viewModel.setSort(TvGenreSort.RATING)

        assertThat(viewModel.sort.value).isEqualTo(TvGenreSort.RATING)
        coVerify {
            tmdbRepository.discoverMoviesByProvider(
                providerId = 337,
                region = any(),
                sortBy = "vote_average.desc",
                page = 1
            )
        }
    }

    @Test
    fun `sorting by NAME for movies uses original_title sort key`() = runTest {
        coEvery {
            tmdbRepository.discoverMoviesByProvider(
                providerId = 337,
                region = any(),
                sortBy = any(),
                page = 1
            )
        } returns TmdbMoviePage(page = 1, totalPages = 1, totalResults = 10, results = emptyList())

        viewModel.setProvider(providerId = 337, isTv = false)
        viewModel.setSort(TvGenreSort.NAME)

        coVerify {
            tmdbRepository.discoverMoviesByProvider(
                providerId = 337,
                region = any(),
                sortBy = "original_title.desc",
                page = 1
            )
        }
    }

    @Test
    fun `sorting by NAME for TV series uses name sort key`() = runTest {
        coEvery {
            tmdbRepository.discoverTvByProvider(
                providerId = 337,
                region = any(),
                sortBy = any(),
                page = 1
            )
        } returns TmdbTvPage(page = 1, totalPages = 1, totalResults = 10, results = emptyList())

        viewModel.setProvider(providerId = 337, isTv = true)
        viewModel.setSort(TvGenreSort.NAME)

        coVerify {
            tmdbRepository.discoverTvByProvider(
                providerId = 337,
                region = any(),
                sortBy = "name.desc",
                page = 1
            )
        }
    }

    @Test
    fun `toggling direction updates sort direction from desc to asc`() = runTest {
        coEvery {
            tmdbRepository.discoverTvByProvider(
                providerId = 119,
                region = any(),
                sortBy = any(),
                page = 1
            )
        } returns TmdbTvPage(page = 1, totalPages = 1, totalResults = 5, results = emptyList())

        viewModel.setProvider(providerId = 119, isTv = true)
        assertThat(viewModel.descending.value).isTrue()

        viewModel.toggleDirection()
        assertThat(viewModel.descending.value).isFalse()

        coVerify {
            tmdbRepository.discoverTvByProvider(
                providerId = 119,
                region = any(),
                sortBy = "popularity.asc",
                page = 1
            )
        }
    }

    @Test
    fun `pagination appends new items and deduplicates`() = runTest {
        val moviePage1 = listOf(
            TmdbMovieSummary(id = 1, title = "Movie 1"),
            TmdbMovieSummary(id = 2, title = "Movie 2")
        )
        val moviePage2 = listOf(
            TmdbMovieSummary(id = 2, title = "Movie 2 Duplicate"),
            TmdbMovieSummary(id = 3, title = "Movie 3")
        )
        coEvery {
            tmdbRepository.discoverMoviesByProvider(providerId = 8, region = any(), sortBy = any(), page = 1)
        } returns TmdbMoviePage(page = 1, totalPages = 2, totalResults = 3, results = moviePage1)
        coEvery {
            tmdbRepository.discoverMoviesByProvider(providerId = 8, region = any(), sortBy = any(), page = 2)
        } returns TmdbMoviePage(page = 2, totalPages = 2, totalResults = 3, results = moviePage2)

        viewModel.setProvider(providerId = 8, isTv = false)
        viewModel.loadMore()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(OttCatalogUiState.SuccessMovies::class.java)
        val movies = (state as OttCatalogUiState.SuccessMovies).movies
        assertThat(movies.map { it.id }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `catalog error sets Error uiState with error message`() = runTest {
        coEvery {
            tmdbRepository.discoverMoviesByProvider(providerId = 8, region = any(), sortBy = any(), page = 1)
        } throws RuntimeException("Service Unavailable")

        viewModel.setProvider(providerId = 8, isTv = false)

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(OttCatalogUiState.Error::class.java)
        val error = state as OttCatalogUiState.Error
        assertThat(error.message).isInstanceOf(UiText.DynamicString::class.java)
        assertThat((error.message as UiText.DynamicString).value).isEqualTo("Service Unavailable")
    }

    @Test
    fun `focus restoration tracking updates correctly`() {
        viewModel.setLastClickedId(55)
        assertThat(viewModel.lastClickedId.value).isEqualTo(55)

        viewModel.clearLastClickedId()
        assertThat(viewModel.lastClickedId.value).isNull()
    }
}
