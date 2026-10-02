package com.martinrevert.latorrentola.ui.detail

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.user.DownloadedEpisode
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.network.EztvRepository
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.utils.PreferenceManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TvEpisodeDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tmdbRepository: TmdbRepository = mockk(relaxed = true)
    private val eztvRepository: EztvRepository = mockk(relaxed = true)
    private val userLibraryRepository: UserLibraryRepository = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private val downloadsFlow = MutableStateFlow<List<DownloadedEpisode>>(emptyList())
    private val torrentModeFlow = MutableStateFlow(TorrentHandlingMode.EXTERNAL_CLIENT)

    private lateinit var viewModel: TvEpisodeDetailViewModel

    @Before
    fun setUp() {
        every { userLibraryRepository.getDownloadedEpisodes() } returns downloadsFlow
        every { preferenceManager.torrentHandlingModeFlow } returns torrentModeFlow
        viewModel = TvEpisodeDetailViewModel(
            tmdbRepository,
            eztvRepository,
            userLibraryRepository,
            preferenceManager
        )
    }

    @Test
    fun `loadEpisode resolves details and torrents`() = runTest {
        val seriesId = 10
        val seasonNum = 1
        val episodeNum = 2
        val seriesName = "Breaking Bad"

        coEvery { tmdbRepository.getTvImdbId(seriesId) } returns "123456"
        coEvery { eztvRepository.getTorrentsForEpisode("123456", seasonNum, episodeNum) } returns listOf(
            EztvTorrent(title = "Breaking.Bad.S01E02.720p", hash = "abc123hash")
        )

        val job = backgroundScope.launch {
            viewModel.uiState.collect {}
        }

        viewModel.loadEpisode(
            seriesId = seriesId,
            seasonNumber = seasonNum,
            episodeNumber = episodeNum,
            seriesName = seriesName
        )
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(TvEpisodeDetailUiState.Success::class.java)
        val successState = state as TvEpisodeDetailUiState.Success
        assertThat(successState.seriesName).isEqualTo("Breaking Bad")
        assertThat(successState.torrents).hasSize(1)
        assertThat(successState.isDownloaded).isFalse()

        job.cancel()
    }

    @Test
    fun `markEpisodeAsDownloaded saves episode record with fallback hash if blank`() = runTest {
        val seriesId = 10
        val seasonNum = 1
        val episodeNum = 2

        coEvery { tmdbRepository.getTvImdbId(seriesId) } returns "123456"

        val job = backgroundScope.launch {
            viewModel.uiState.collect {}
        }

        viewModel.loadEpisode(
            seriesId = seriesId,
            seasonNumber = seasonNum,
            episodeNumber = episodeNum,
            seriesName = "Breaking Bad"
        )
        testScheduler.advanceUntilIdle()

        val blankHashTorrent = EztvTorrent(
            title = "Breaking.Bad.S01E02.1080p",
            hash = "",
            magnetUrl = "magnet:?xt=urn:btih:4f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a&dn=Test"
        )

        viewModel.markEpisodeAsDownloaded(blankHashTorrent)
        testScheduler.advanceUntilIdle()

        coVerify {
            userLibraryRepository.markEpisodeAsDownloaded(
                match {
                    it.seriesId == seriesId &&
                            it.seasonNumber == seasonNum &&
                            it.episodeNumber == episodeNum &&
                            it.hash == "4f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a"
                }
            )
        }

        job.cancel()
    }

    @Test
    fun `uiState updates isDownloaded reactively when downloads emit matching record`() = runTest {
        val seriesId = 10
        val seasonNum = 1
        val episodeNum = 2

        coEvery { tmdbRepository.getTvImdbId(seriesId) } returns "123456"

        val job = backgroundScope.launch {
            viewModel.uiState.collect {}
        }

        viewModel.loadEpisode(
            seriesId = seriesId,
            seasonNumber = seasonNum,
            episodeNumber = episodeNum,
            seriesName = "Breaking Bad"
        )
        testScheduler.advanceUntilIdle()

        assertThat((viewModel.uiState.value as TvEpisodeDetailUiState.Success).isDownloaded).isFalse()

        downloadsFlow.value = listOf(
            DownloadedEpisode(
                seriesId = seriesId,
                seriesName = "Breaking Bad",
                seasonNumber = seasonNum,
                episodeNumber = episodeNum,
                hash = "4f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a"
            )
        )
        testScheduler.advanceUntilIdle()

        val successState = viewModel.uiState.value as TvEpisodeDetailUiState.Success
        assertThat(successState.isDownloaded).isTrue()
        assertThat(successState.downloadedHashes).contains("4f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a")

        job.cancel()
    }
}
