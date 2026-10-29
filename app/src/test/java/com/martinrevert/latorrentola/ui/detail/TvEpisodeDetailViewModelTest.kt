package com.martinrevert.latorrentola.ui.detail

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.database.TorrentDownloadDao
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.EZTV.downloadInfoHash
import com.martinrevert.latorrentola.model.torrent.PlaybackCompatibility
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.model.torrent.TorrentRelease
import com.martinrevert.latorrentola.model.torrent.VideoQualityGroup
import com.martinrevert.latorrentola.model.user.DownloadedEpisode
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.TvEpisodeReleasesRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.utils.PreferenceManager
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

class TvEpisodeDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tmdbRepository: TmdbRepository = mockk(relaxed = true)
    private val tvEpisodeReleasesRepository: TvEpisodeReleasesRepository = mockk(relaxed = true)
    private val userLibraryRepository: UserLibraryRepository = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private val torrentDownloadDao: TorrentDownloadDao = mockk(relaxed = true)
    private val downloadsFlow = MutableStateFlow<List<DownloadedEpisode>>(emptyList())
    private val torrentModeFlow = MutableStateFlow(TorrentHandlingMode.EXTERNAL_CLIENT)

    private lateinit var viewModel: TvEpisodeDetailViewModel

    @Before
    fun setUp() {
        every { userLibraryRepository.getDownloadedEpisodes() } returns downloadsFlow
        every { preferenceManager.torrentHandlingModeFlow } returns torrentModeFlow
        every { torrentDownloadDao.observeAll() } returns flowOf(emptyList())
        viewModel = TvEpisodeDetailViewModel(
            tmdbRepository,
            tvEpisodeReleasesRepository,
            userLibraryRepository,
            preferenceManager,
            torrentDownloadDao
        )
    }

    @Test
    fun `loadEpisode resolves details and torrents`() = runTest {
        val seriesId = 10
        val seasonNum = 1
        val episodeNum = 2
        val seriesName = "Breaking Bad"

        coEvery { tmdbRepository.getTvDetails(seriesId) } returns com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary(
            id = seriesId,
            name = seriesName,
            posterPath = "/poster_path.jpg"
        )
        coEvery {
            tmdbRepository.getTvSeasonDetails(
                seriesId,
                seasonNum
            )
        } returns com.martinrevert.latorrentola.model.TMDB.TmdbTvSeasonDetails(
            id = 1,
            seasonNumber = seasonNum,
            episodes = listOf(
                com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode(
                    id = 2,
                    episodeNumber = episodeNum,
                    seasonNumber = seasonNum,
                    name = "Ep 2"
                )
            )
        )
        val sampleTorrent = EztvTorrent(title = "Breaking.Bad.S01E02.720p", hash = "abc123hash")
        coEvery {
            tvEpisodeReleasesRepository.getEpisodeReleasesWithQualityGrouping(seriesId, seasonNum, episodeNum)
        } returns Pair(listOf(sampleTorrent), emptyMap())

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
        assertThat(successState.posterUrl).isEqualTo("https://image.tmdb.org/t/p/w500/poster_path.jpg")

        job.cancel()
    }

    @Test
    fun `markEpisodeAsDownloaded saves episode record with fallback hash if blank`() = runTest {
        val seriesId = 10
        val seasonNum = 1
        val episodeNum = 2

        coEvery {
            tmdbRepository.getTvSeasonDetails(
                seriesId,
                seasonNum
            )
        } returns com.martinrevert.latorrentola.model.TMDB.TmdbTvSeasonDetails(
            id = 1,
            seasonNumber = seasonNum,
            episodes = listOf(
                com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode(
                    id = 2,
                    episodeNumber = episodeNum,
                    seasonNumber = seasonNum,
                    name = "Ep 2"
                )
            )
        )

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

        val torrents = listOf(
            EztvTorrent(title = "Breaking.Bad.S01E02.720p", hash = "release-one"),
            EztvTorrent(title = "Breaking.Bad.S01E02.1080p", hash = "release-two")
        )
        coEvery {
            tmdbRepository.getTvSeasonDetails(
                seriesId,
                seasonNum
            )
        } returns com.martinrevert.latorrentola.model.TMDB.TmdbTvSeasonDetails(
            id = 1,
            seasonNumber = seasonNum,
            episodes = listOf(
                com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode(
                    id = 2,
                    episodeNumber = episodeNum,
                    seasonNumber = seasonNum,
                    name = "Ep 2"
                )
            )
        )
        coEvery {
            tvEpisodeReleasesRepository.getEpisodeReleasesWithQualityGrouping(seriesId, seasonNum, episodeNum)
        } returns Pair(torrents, emptyMap())

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
                hash = "release-one"
            )
        )
        testScheduler.advanceUntilIdle()

        val successState = viewModel.uiState.value as TvEpisodeDetailUiState.Success
        assertThat(successState.isDownloaded).isTrue()
        assertThat(successState.downloadedHashes).containsExactly("release-one")
        assertThat(
            successState.torrents.filter {
                it.downloadInfoHash()?.let(successState.downloadedHashes::contains) == true
            }
        ).hasSize(1)

        downloadsFlow.value = listOf(
            DownloadedEpisode(
                seriesId = seriesId,
                seasonNumber = seasonNum,
                episodeNumber = episodeNum,
                hash = "release-one"
            ),
            DownloadedEpisode(
                seriesId = seriesId,
                seasonNumber = seasonNum,
                episodeNumber = episodeNum,
                hash = "release-two"
            )
        )
        testScheduler.advanceUntilIdle()
        assertThat(
            (viewModel.uiState.value as TvEpisodeDetailUiState.Success).downloadedHashes
        ).containsExactly("release-one", "release-two")

        job.cancel()
    }

    /** Verifies hash normalization and extraction for EZTV releases. */
    @Test
    fun `downloadInfoHash uses a normalized torrent hash or magnet hash`() {
        val torrentHash = EztvTorrent(hash = "ABCDEF0123456789").downloadInfoHash()
        val magnetHash = EztvTorrent(
            magnetUrl = "magnet:?xt=urn:btih:4F1A2B3C4D5E6F7A8B9C0D1E2F3A4B5C6D7E8F9A"
        ).downloadInfoHash()

        assertThat(torrentHash).isEqualTo("abcdef0123456789")
        assertThat(magnetHash).isEqualTo("4f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a")
    }

    @Test
    fun `getNextEpisodeInfo returns cached next episode info from repository`() = runTest {
        val seriesId = 10
        val seasonNum = 1
        val episodeNum = 5

        val mockSeasonDetails = com.martinrevert.latorrentola.model.TMDB.TmdbTvSeasonDetails(
            id = 100,
            seasonNumber = 1,
            episodes = listOf(
                com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode(
                    id = 1,
                    episodeNumber = 5,
                    seasonNumber = 1,
                    name = "Episode 5",
                    airDate = "2023-01-01"
                ),
                com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode(
                    id = 2,
                    episodeNumber = 6,
                    seasonNumber = 1,
                    name = "Episode 6",
                    airDate = "2023-01-08"
                )
            )
        )

        coEvery { tmdbRepository.getTvSeasonDetails(seriesId, seasonNum) } returns mockSeasonDetails
        coEvery {
            tvEpisodeReleasesRepository.resolveNextEpisodeInfo(seriesId, "Breaking Bad", seasonNum, episodeNum)
        } returns arrayListOf("10", "Breaking Bad", "1", "6", "Episode 6")

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

        val nextInfo = viewModel.getNextEpisodeInfo()
        assertThat(nextInfo).isNotNull()
        assertThat(nextInfo!![2]).isEqualTo("1")
        assertThat(nextInfo[3]).isEqualTo("6")
        assertThat(nextInfo[4]).isEqualTo("Episode 6")

        job.cancel()
    }

    @Test
    fun `loadEpisode merges EZTV and Torrentio into releasesByQuality`() = runTest {
        val seriesId = 10
        val seasonNum = 1
        val episodeNum = 2
        val seriesName = "Breaking Bad"

        val eztvTorrent = EztvTorrent(title = "Breaking.Bad.S01E02.720p.HDTV", hash = "eztv_hash_1", seeds = 15)
        val eztvRelease = TorrentRelease(
            hash = "eztv_hash_1",
            title = "Breaking.Bad.S01E02.720p.HDTV",
            provider = "EZTV",
            qualityGroup = VideoQualityGroup.HD_720P,
            resolutionLabel = "720p HD",
            videoCodec = "x264",
            audioCodec = "AC3 5.1",
            formattedSize = "1.0 GB",
            sizeBytes = 1000000000L,
            seeds = 15,
            peers = 5,
            magnetUrl = "magnet:?xt=urn:btih:eztv_hash_1",
            compatibility = PlaybackCompatibility.NATIVE
        )
        val torrentioRelease1080p = TorrentRelease(
            hash = "torrentio_ep_1080p",
            title = "Breaking.Bad.S01E02.1080p.BluRay.DTS-HD.MA.5.1",
            provider = "1337x",
            qualityGroup = VideoQualityGroup.FHD_1080P,
            resolutionLabel = "1080p FHD",
            videoCodec = "x264",
            audioCodec = "DTS-HD MA 5.1",
            formattedSize = "4.5 GB",
            sizeBytes = 4500000000L,
            seeds = 45,
            peers = 10,
            magnetUrl = "magnet:?xt=urn:btih:torrentio_ep_1080p",
            compatibility = PlaybackCompatibility.AUDIO_REQUIRES_PASSTHROUGH_OR_EXTERNAL
        )

        val grouped = mapOf(
            VideoQualityGroup.HD_720P to listOf(eztvRelease),
            VideoQualityGroup.FHD_1080P to listOf(torrentioRelease1080p)
        )

        coEvery { tmdbRepository.getTvDetails(seriesId) } returns com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary(
            id = seriesId,
            name = seriesName
        )
        coEvery {
            tmdbRepository.getTvSeasonDetails(
                seriesId,
                seasonNum
            )
        } returns com.martinrevert.latorrentola.model.TMDB.TmdbTvSeasonDetails(
            id = 1,
            seasonNumber = seasonNum,
            episodes = listOf(
                com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode(
                    id = 2,
                    episodeNumber = episodeNum,
                    seasonNumber = seasonNum,
                    name = "Ep 2"
                )
            )
        )
        coEvery {
            tvEpisodeReleasesRepository.getEpisodeReleasesWithQualityGrouping(seriesId, seasonNum, episodeNum)
        } returns Pair(listOf(eztvTorrent), grouped)

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
        val success = state as TvEpisodeDetailUiState.Success
        assertThat(success.releasesByQuality).containsKey(VideoQualityGroup.HD_720P)
        assertThat(success.releasesByQuality).containsKey(VideoQualityGroup.FHD_1080P)
        assertThat(success.releasesByQuality[VideoQualityGroup.FHD_1080P]).hasSize(1)
        assertThat(success.releasesByQuality[VideoQualityGroup.HD_720P]).hasSize(1)

        job.cancel()
    }

    @Test
    fun `removeEpisodeDownload delegates hash removal to UserLibraryRepository`() = runTest {
        viewModel.removeEpisodeDownload("EP_HASH_TO_REMOVE")
        testScheduler.advanceUntilIdle()

        coVerify { userLibraryRepository.removeDownloadedEpisodeByHash("EP_HASH_TO_REMOVE") }
    }
}
