package com.martinrevert.latorrentola.ui.detail

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TvDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tmdbRepository: TmdbRepository = mockk(relaxed = true)
    private val userLibraryRepository: UserLibraryRepository = mockk(relaxed = true)
    private val favoritesFlow = MutableStateFlow<List<TmdbTvSummary>>(emptyList())
    private lateinit var viewModel: TvDetailViewModel

    @Before
    fun setUp() {
        every { userLibraryRepository.getDownloadedEpisodes() } returns flowOf(emptyList())
        every { userLibraryRepository.getFavoriteTvSeries() } returns favoritesFlow
        viewModel = TvDetailViewModel(tmdbRepository, userLibraryRepository)
    }

    @Test
    fun `toggleFavorite adds series to library when not saved`() = runTest {
        val series = TmdbTvSummary(id = 100, name = "Test TV Series")
        
        viewModel.toggleFavorite(series)
        testScheduler.advanceUntilIdle()

        coVerify { userLibraryRepository.addFavoriteTvSeries(series) }
        assertThat(viewModel.favoriteActionError.value).isNull()
    }

    @Test
    fun `toggleFavorite removes series from library when already saved`() = runTest {
        val series = TmdbTvSummary(id = 100, name = "Test TV Series")
        favoritesFlow.value = listOf(series)
        
        // Subscribe to favoriteTvSeriesIds state flow so stateIn collects from userLibraryRepository
        val job = backgroundScope.launch {
            viewModel.favoriteTvSeriesIds.collect {}
        }
        testScheduler.advanceUntilIdle()
        
        viewModel.toggleFavorite(series)
        testScheduler.advanceUntilIdle()

        coVerify { userLibraryRepository.removeFavoriteTvSeries(series) }
        assertThat(viewModel.favoriteActionError.value).isNull()
        job.cancel()
    }

    @Test
    fun `toggleFavorite sets readable error message when update fails`() = runTest {
        val series = TmdbTvSummary(id = 100, name = "Test TV Series")
        coEvery { userLibraryRepository.addFavoriteTvSeries(series) } throws RuntimeException("Network error")

        viewModel.toggleFavorite(series)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.favoriteActionError.value).isEqualTo("Network error")
    }

    @Test
    fun `toggleFavorite fallback error avoids literal null string`() = runTest {
        val series = TmdbTvSummary(id = 100, name = "Test TV Series")
        coEvery { userLibraryRepository.addFavoriteTvSeries(series) } throws NullPointerException(null)

        viewModel.toggleFavorite(series)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.favoriteActionError.value).isNotEqualTo("null")
        assertThat(viewModel.favoriteActionError.value).isEqualTo("Unable to update TV favorites")
    }
}
