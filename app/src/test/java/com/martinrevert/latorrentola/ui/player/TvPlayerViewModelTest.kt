package com.martinrevert.latorrentola.ui.player

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.torrent.TorrentRelease
import com.martinrevert.latorrentola.model.torrent.VideoQualityGroup
import com.martinrevert.latorrentola.network.NextEpisodeEvaluation
import com.martinrevert.latorrentola.network.SeriesContinuationState
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.TvEpisodeReleasesRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.utils.PreferenceManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TvPlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tvEpisodeReleasesRepository: TvEpisodeReleasesRepository = mockk(relaxed = true)
    private val tmdbRepository: TmdbRepository = mockk(relaxed = true)
    private val userLibraryRepository: UserLibraryRepository = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)

    private lateinit var viewModel: TvPlayerViewModel

    @Before
    fun setUp() {
        viewModel = TvPlayerViewModel(
            tvEpisodeReleasesRepository,
            tmdbRepository,
            userLibraryRepository,
            preferenceManager
        )
    }

    @Test
    fun `fetchReleasesForNextEpisode queries tvEpisodeReleasesRepository and updates releases`() = runTest {
        val seriesId = 100
        val seasonNum = 1
        val epNum = 2

        val release1 = TorrentRelease(
            hash = "torrentiohash",
            title = "Show.S01E02.1080p.HDR",
            qualityGroup = VideoQualityGroup.FHD_1080P,
            resolutionLabel = "1080p",
            audioCodec = "DTS 5.1",
            seeds = 120,
            provider = "Torrentio"
        )
        val release2 = TorrentRelease(
            hash = "eztvhash",
            title = "Show.S01E02.720p",
            qualityGroup = VideoQualityGroup.HD_720P,
            resolutionLabel = "720p",
            audioCodec = "AAC",
            seeds = 50,
            provider = "EZTV"
        )

        coEvery {
            tvEpisodeReleasesRepository.getEpisodeReleasesBySeriesId(seriesId, seasonNum, epNum)
        } returns listOf(release1, release2)

        viewModel.fetchReleasesForNextEpisode(
            seriesId,
            seasonNum,
            epNum,
            seriesName = "Test Show",
            episodeName = "Episode 2"
        )
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.nextEpisodeReleases.value).hasSize(2)
        assertThat(viewModel.nextEpisodeReleases.value.first().hash).isEqualTo("torrentiohash")
        assertThat(viewModel.nextEpisodeData.value?.seriesName).isEqualTo("Test Show")
        assertThat(viewModel.nextEpisodeData.value?.episodeNumber).isEqualTo(2)
    }

    @Test
    fun `fetchNextEpisodeAndReleases resolves next episode and fetches its releases`() = runTest {
        val seriesId = 100
        val currentSeason = 1
        val currentEpisode = 1

        val nextInfo = arrayListOf("100", "Breaking Bad", "1", "2", "Episode Two")
        val nextRelease = TorrentRelease(
            hash = "releasehash",
            title = "Breaking.Bad.S01E02.1080p",
            qualityGroup = VideoQualityGroup.FHD_1080P,
            resolutionLabel = "1080p",
            audioCodec = "AAC",
            seeds = 80,
            provider = "Torrentio"
        )

        coEvery {
            tvEpisodeReleasesRepository.resolveNextEpisodeInfo(seriesId, "", currentSeason, currentEpisode)
        } returns nextInfo

        coEvery {
            tvEpisodeReleasesRepository.getEpisodeReleasesBySeriesId(seriesId, 1, 2)
        } returns listOf(nextRelease)

        viewModel.fetchNextEpisodeAndReleases(seriesId, currentSeason, currentEpisode)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.nextEpisodeData.value?.seasonNumber).isEqualTo(1)
        assertThat(viewModel.nextEpisodeData.value?.episodeNumber).isEqualTo(2)
        assertThat(viewModel.nextEpisodeData.value?.episodeName).isEqualTo("Episode Two")
        assertThat(viewModel.nextEpisodeReleases.value).containsExactly(nextRelease)
    }

    @Test
    fun `getNextEpisodeInfo delegates to tvEpisodeReleasesRepository`() = runTest {
        val seriesId = 10
        val seasonNum = 1
        val currentEpNum = 1
        val expectedInfo = arrayListOf("10", "Breaking Bad", "1", "2", "Ep 2")

        coEvery {
            tvEpisodeReleasesRepository.resolveNextEpisodeInfo(seriesId, "Breaking Bad", seasonNum, currentEpNum)
        } returns expectedInfo

        val result = viewModel.getNextEpisodeInfo(seriesId, "Breaking Bad", seasonNum, currentEpNum)

        assertThat(result).isEqualTo(expectedInfo)
        coVerify(exactly = 1) {
            tvEpisodeReleasesRepository.resolveNextEpisodeInfo(seriesId, "Breaking Bad", seasonNum, currentEpNum)
        }
    }

    @Test
    fun `evaluateNextEpisode updates state flows with repository evaluation`() = runTest {
        val expectedEvaluation = NextEpisodeEvaluation(
            state = SeriesContinuationState.SEASON_COMPLETE_SERIES_CONTINUES,
            seriesId = 95396,
            seriesName = "Severance",
            isSeriesContinuing = true,
            nextSeasonNumber = 3,
            nextEpisodeNumber = 1,
            nextEpisodeAirDate = "2027-01-15"
        )
        coEvery {
            tvEpisodeReleasesRepository.evaluateNextEpisode(95396, "Severance", 2, 10)
        } returns expectedEvaluation

        val evaluation = viewModel.evaluateNextEpisode(95396, "Severance", 2, 10)

        assertThat(evaluation).isEqualTo(expectedEvaluation)
        assertThat(viewModel.nextEpisodeEvaluation.value).isEqualTo(expectedEvaluation)
        assertThat(viewModel.nextEpisodeReleases.value).isEmpty()
        assertThat(viewModel.isLoading.value).isFalse()
    }

    @Test
    fun `evaluateNextEpisode handles exception gracefully and updates error state`() = runTest {
        coEvery {
            tvEpisodeReleasesRepository.evaluateNextEpisode(95396, "Severance", 2, 10)
        } throws RuntimeException("Network error")

        val evaluation = viewModel.evaluateNextEpisode(95396, "Severance", 2, 10)

        assertThat(evaluation.state).isEqualTo(SeriesContinuationState.SERIES_ENDED)
        assertThat(viewModel.error.value).isEqualTo("Network error")
        assertThat(viewModel.isLoading.value).isFalse()
    }
}
