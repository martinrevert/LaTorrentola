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

    @Test
    fun `markEpisodeAsCompleted delegates to repository`() = runTest {
        val series = TmdbTvSummary(id = 100, name = "Test Series")
        coEvery { tmdbRepository.getTvDetails(100) } returns series
        viewModel.load(100)
        testScheduler.advanceUntilIdle()

        val episode = com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode(
            id = 1, seasonNumber = 1, episodeNumber = 1, name = "Episode 1"
        )
        viewModel.markEpisodeAsCompleted(episode)
        testScheduler.advanceUntilIdle()

        coVerify {
            userLibraryRepository.markEpisodeAsCompleted(
                seriesId = 100,
                seasonNumber = 1,
                episodeNumber = 1,
                episodeTitle = "Episode 1",
                seriesName = "Test Series"
            )
        }
    }

    @Test
    fun `markEpisodeAsUncompleted delegates to repository`() = runTest {
        val series = TmdbTvSummary(id = 100, name = "Test Series")
        coEvery { tmdbRepository.getTvDetails(100) } returns series
        viewModel.load(100)
        testScheduler.advanceUntilIdle()

        val episode = com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode(
            id = 1, seasonNumber = 1, episodeNumber = 1, name = "Episode 1"
        )
        viewModel.markEpisodeAsUncompleted(episode)
        testScheduler.advanceUntilIdle()

        coVerify {
            userLibraryRepository.markEpisodeAsUncompleted(
                seriesId = 100,
                seasonNumber = 1,
                episodeNumber = 1
            )
        }
    }

    @Test
    fun `load fetches TV series logo and updates state`() = runTest {
        val series = TmdbTvSummary(id = 100, name = "Test TV Series")
        coEvery { tmdbRepository.getTvDetails(100) } returns series
        coEvery { tmdbRepository.getTvSeriesLogo(100) } returns "https://image.tmdb.org/t/p/w500/logo.png"

        viewModel.load(100)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.logoUrl.value).isEqualTo("https://image.tmdb.org/t/p/w500/logo.png")
        assertThat(viewModel.isLogoLoading.value).isFalse()
    }

    @Test
    fun `load cancels previous logo job when called with new seriesId`() = runTest {
        val series1 = TmdbTvSummary(id = 100, name = "Series 1")
        val series2 = TmdbTvSummary(id = 200, name = "Series 2")
        coEvery { tmdbRepository.getTvDetails(100) } returns series1
        coEvery { tmdbRepository.getTvDetails(200) } returns series2
        coEvery { tmdbRepository.getTvSeriesLogo(100) } coAnswers {
            kotlinx.coroutines.delay(500)
            "https://logo1.png"
        }
        coEvery { tmdbRepository.getTvSeriesLogo(200) } returns "https://logo2.png"

        viewModel.load(100)
        viewModel.load(200)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.logoUrl.value).isEqualTo("https://logo2.png")
    }

    @Test
    fun `load fetches TV series trailer key and updates state`() = runTest {
        val series = TmdbTvSummary(id = 100, name = "Test TV Series")
        coEvery { tmdbRepository.getTvDetails(100) } returns series
        coEvery { tmdbRepository.getTvSeriesTrailer(100) } returns "dQw4w9WgXcQ"

        viewModel.load(100)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.trailerKey.value).isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `load cancels previous trailer job when called with new seriesId`() = runTest {
        val series1 = TmdbTvSummary(id = 100, name = "Series 1")
        val series2 = TmdbTvSummary(id = 200, name = "Series 2")
        coEvery { tmdbRepository.getTvDetails(100) } returns series1
        coEvery { tmdbRepository.getTvDetails(200) } returns series2
        coEvery { tmdbRepository.getTvSeriesTrailer(100) } coAnswers {
            kotlinx.coroutines.delay(500)
            "trailer1"
        }
        coEvery { tmdbRepository.getTvSeriesTrailer(200) } returns "trailer2"

        viewModel.load(100)
        viewModel.load(200)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.trailerKey.value).isEqualTo("trailer2")
    }
}
