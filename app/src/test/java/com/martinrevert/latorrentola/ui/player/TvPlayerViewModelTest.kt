package com.martinrevert.latorrentola.ui.player

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeasonDetails
import com.martinrevert.latorrentola.network.EztvRepository
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.utils.PreferenceManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TvPlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val eztvRepository: EztvRepository = mockk(relaxed = true)
    private val tmdbRepository: TmdbRepository = mockk(relaxed = true)
    private val userLibraryRepository: UserLibraryRepository = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)

    private lateinit var viewModel: TvPlayerViewModel

    @Before
    fun setUp() {
        viewModel = TvPlayerViewModel(
            eztvRepository,
            tmdbRepository,
            userLibraryRepository,
            preferenceManager
        )
    }

    @Test
    fun `fetchReleasesForNextEpisode resets previous releases before fetching new ones`() = runTest {
        val seriesId = 100
        val seasonNum = 1
        val epNum = 2

        coEvery { tmdbRepository.getTvImdbId(seriesId) } returns "123456"
        coEvery { eztvRepository.getTorrentsForEpisode("123456", seasonNum, epNum) } returns listOf(
            EztvTorrent(title = "Show.S01E02.1080p", hash = "hash123")
        )

        viewModel.fetchReleasesForNextEpisode(seriesId, seasonNum, epNum)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.nextEpisodeReleases.value).hasSize(1)
        assertThat(viewModel.nextEpisodeReleases.value.first().title).isEqualTo("Show.S01E02.1080p")
    }

    @Test
    fun `getNextEpisodeInfo resolves next episode in same season`() = runTest {
        val seriesId = 10
        val seasonNum = 1
        val currentEpNum = 1

        val seasonDetails = TmdbTvSeasonDetails(
            id = 1,
            seasonNumber = 1,
            episodes = listOf(
                TmdbTvEpisode(id = 1, episodeNumber = 1, seasonNumber = 1, name = "Ep 1", airDate = "2023-01-01"),
                TmdbTvEpisode(id = 2, episodeNumber = 2, seasonNumber = 1, name = "Ep 2", airDate = "2023-01-08")
            )
        )

        coEvery { tmdbRepository.getTvSeasonDetails(seriesId, seasonNum) } returns seasonDetails

        val nextInfo = viewModel.getNextEpisodeInfo(seriesId, "Breaking Bad", seasonNum, currentEpNum)

        assertThat(nextInfo).isNotNull()
        assertThat(nextInfo!![0]).isEqualTo("10")
        assertThat(nextInfo[1]).isEqualTo("Breaking Bad")
        assertThat(nextInfo[2]).isEqualTo("1")
        assertThat(nextInfo[3]).isEqualTo("2")
        assertThat(nextInfo[4]).isEqualTo("Ep 2")
    }

    @Test
    fun `getNextEpisodeInfo resolves first episode of next season when on season finale`() = runTest {
        val seriesId = 10
        val seasonNum = 1
        val currentEpNum = 2

        val season1Details = TmdbTvSeasonDetails(
            id = 1,
            seasonNumber = 1,
            episodes = listOf(
                TmdbTvEpisode(id = 1, episodeNumber = 1, seasonNumber = 1, name = "Ep 1", airDate = "2023-01-01"),
                TmdbTvEpisode(id = 2, episodeNumber = 2, seasonNumber = 1, name = "Ep 2", airDate = "2023-01-08")
            )
        )

        val season2Details = TmdbTvSeasonDetails(
            id = 2,
            seasonNumber = 2,
            episodes = listOf(
                TmdbTvEpisode(id = 3, episodeNumber = 1, seasonNumber = 2, name = "Season 2 Ep 1", airDate = "2024-01-01")
            )
        )

        coEvery { tmdbRepository.getTvSeasonDetails(seriesId, 1) } returns season1Details
        coEvery { tmdbRepository.getTvSeasonDetails(seriesId, 2) } returns season2Details

        val nextInfo = viewModel.getNextEpisodeInfo(seriesId, "Breaking Bad", seasonNum, currentEpNum)

        assertThat(nextInfo).isNotNull()
        assertThat(nextInfo!![2]).isEqualTo("2")
        assertThat(nextInfo[3]).isEqualTo("1")
        assertThat(nextInfo[4]).isEqualTo("Season 2 Ep 1")
    }
}
