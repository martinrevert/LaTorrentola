package com.martinrevert.latorrentola.ui.home

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.TMDB.TmdbTvGenre
import com.martinrevert.latorrentola.model.TMDB.TmdbTvPage
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.TmdbTvFeed
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Verifies TV Home feed and genre discovery state behavior. */
class TvCatalogViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: TmdbRepository = mockk(relaxed = true)

    /** Loads On The Air after TV mode activates and requests the selected optional feed. */
    @Test
    fun `tv home loads on the air by default and changes feed`() = runTest {
        every { repository.observeTvGenreUsage() } returns flowOf(emptyMap())
        coEvery { repository.getTvGenres() } returns emptyList()
        coEvery { repository.getHomeTvFeed(any(), any()) } returns TmdbTvPage(
            totalPages = 1,
            results = listOf(TmdbTvSummary(id = 12, name = "Series"))
        )

        val viewModel = TvHomeViewModel(repository)
        viewModel.activate()

        assertThat(viewModel.selectedFeed.value).isEqualTo(TmdbTvFeed.ON_THE_AIR)
        assertThat((viewModel.uiState.value as TvHomeUiState.Success).series.single().id).isEqualTo(12)

        viewModel.selectFeed(TmdbTvFeed.POPULAR)

        assertThat(viewModel.selectedFeed.value).isEqualTo(TmdbTvFeed.POPULAR)
        coVerify { repository.getHomeTvFeed(TmdbTvFeed.POPULAR, 1) }
    }

    /** Applies TMDB sorting and loads more pages for the selected TV genre. */
    @Test
    fun `tv genre results use the selected sort and page`() = runTest {
        coEvery { repository.getTvGenres() } returns listOf(TmdbTvGenre(id = 18, name = "Drama"))
        coEvery { repository.discoverTvByGenre(any(), any(), any()) } returnsMany listOf(
            TmdbTvPage(
                totalPages = 2,
                results = listOf(TmdbTvSummary(id = 1, name = "First"))
            ),
            TmdbTvPage(
                totalPages = 2,
                results = listOf(TmdbTvSummary(id = 2, name = "Second"))
            ),
            TmdbTvPage(
                totalPages = 2,
                results = listOf(TmdbTvSummary(id = 3, name = "Third"))
            )
        )

        val viewModel = TvGenreResultsViewModel(repository)
        viewModel.setGenre(18)
        viewModel.setSort(TvGenreSort.RATING)

        assertThat(viewModel.uiState.value).isInstanceOf(TvGenreResultsUiState.Success::class.java)
        coVerify { repository.discoverTvByGenre(18, "vote_average.desc", 1) }

        viewModel.loadMore()
        assertThat(
            (viewModel.uiState.value as TvGenreResultsUiState.Success).series.map { it.id }
        ).containsExactly(2, 3).inOrder()
        coVerify { repository.discoverTvByGenre(18, "vote_average.desc", 2) }
    }
}
