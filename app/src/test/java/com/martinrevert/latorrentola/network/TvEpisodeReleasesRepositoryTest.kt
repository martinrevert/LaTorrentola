package com.martinrevert.latorrentola.network

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeasonDetails
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.torrent.PlaybackCompatibility
import com.martinrevert.latorrentola.model.torrent.TorrentRelease
import com.martinrevert.latorrentola.model.torrent.VideoQualityGroup
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class TvEpisodeReleasesRepositoryTest {

    private val tmdbRepository: TmdbRepository = mockk(relaxed = true)
    private val eztvRepository: EztvRepository = mockk(relaxed = true)
    private val torrentioRepository: TorrentioRepository = mockk(relaxed = true)

    private lateinit var repository: TvEpisodeReleasesRepository

    @Before
    fun setUp() {
        repository = TvEpisodeReleasesRepository(
            tmdbRepository = tmdbRepository,
            eztvRepository = eztvRepository,
            torrentioRepository = torrentioRepository
        )
    }

    @Test
    fun `resolveNextEpisodeInfo returns next episode in same season`() = runTest {
        val seriesId = 1399
        val seasonNumber = 1
        val currentEpisodeNumber = 1

        val seasonDetails = TmdbTvSeasonDetails(
            id = 1,
            seasonNumber = 1,
            episodes = listOf(
                TmdbTvEpisode(id = 1, episodeNumber = 1, seasonNumber = 1, name = "Winter Is Coming"),
                TmdbTvEpisode(id = 2, episodeNumber = 2, seasonNumber = 1, name = "The Kingsroad")
            )
        )
        coEvery { tmdbRepository.getTvSeasonDetails(seriesId, seasonNumber) } returns seasonDetails

        val nextInfo = repository.resolveNextEpisodeInfo(seriesId, "Game of Thrones", seasonNumber, currentEpisodeNumber)

        assertThat(nextInfo).isNotNull()
        assertThat(nextInfo!![0]).isEqualTo("1399")
        assertThat(nextInfo[1]).isEqualTo("Game of Thrones")
        assertThat(nextInfo[2]).isEqualTo("1")
        assertThat(nextInfo[3]).isEqualTo("2")
        assertThat(nextInfo[4]).isEqualTo("The Kingsroad")
    }

    @Test
    fun `resolveNextEpisodeInfo rolls over to first episode of next season`() = runTest {
        val seriesId = 1399
        val seasonNumber = 1
        val currentEpisodeNumber = 2

        val season1Details = TmdbTvSeasonDetails(
            id = 1,
            seasonNumber = 1,
            episodes = listOf(
                TmdbTvEpisode(id = 1, episodeNumber = 1, seasonNumber = 1, name = "Ep 1"),
                TmdbTvEpisode(id = 2, episodeNumber = 2, seasonNumber = 1, name = "Ep 2")
            )
        )
        val season2Details = TmdbTvSeasonDetails(
            id = 2,
            seasonNumber = 2,
            episodes = listOf(
                TmdbTvEpisode(id = 3, episodeNumber = 1, seasonNumber = 2, name = "Season 2 Premiere")
            )
        )
        coEvery { tmdbRepository.getTvSeasonDetails(seriesId, 1) } returns season1Details
        coEvery { tmdbRepository.getTvSeasonDetails(seriesId, 2) } returns season2Details

        val nextInfo = repository.resolveNextEpisodeInfo(seriesId, "Game of Thrones", seasonNumber, currentEpisodeNumber)

        assertThat(nextInfo).isNotNull()
        assertThat(nextInfo!![2]).isEqualTo("2")
        assertThat(nextInfo[3]).isEqualTo("1")
        assertThat(nextInfo[4]).isEqualTo("Season 2 Premiere")
    }

    @Test
    fun `resolveNextEpisodeInfo returns null for invalid inputs`() = runTest {
        assertThat(repository.resolveNextEpisodeInfo(-1, "Name", 1, 1)).isNull()
        assertThat(repository.resolveNextEpisodeInfo(10, "Name", 0, 1)).isNull()
        assertThat(repository.resolveNextEpisodeInfo(10, "Name", 1, 0)).isNull()
    }

    @Test
    fun `getEpisodeReleases fetches parallel EZTV and Torrentio deduplicated and sorted by seeds`() = runTest {
        val imdbId = "tt0944947"
        val season = 1
        val episode = 2

        val eztvTorrent = EztvTorrent(title = "GoT.S01E02.720p", hash = "HASH_SHARED", seeds = 10)
        val eztvMapped = TorrentRelease(
            hash = "hash_shared",
            title = "GoT.S01E02.720p",
            qualityGroup = VideoQualityGroup.HD_720P,
            resolutionLabel = "720p",
            audioCodec = "AAC",
            seeds = 10,
            provider = "EZTV"
        )
        val torrentioDuplicate = TorrentRelease(
            hash = "HASH_SHARED",
            title = "GoT.S01E02.720p.Torrentio",
            qualityGroup = VideoQualityGroup.HD_720P,
            resolutionLabel = "720p",
            audioCodec = "AAC",
            seeds = 5,
            provider = "Torrentio"
        )
        val torrentioUnique = TorrentRelease(
            hash = "hash_torrentio_unique",
            title = "GoT.S01E02.1080p.Remux",
            qualityGroup = VideoQualityGroup.FHD_1080P,
            resolutionLabel = "1080p",
            audioCodec = "DTS",
            seeds = 250,
            provider = "Torrentio"
        )

        coEvery { eztvRepository.getTorrentsForEpisode(imdbId, season, episode) } returns listOf(eztvTorrent)
        coEvery { torrentioRepository.getSeriesTorrents(imdbId, season, episode) } returns listOf(torrentioDuplicate, torrentioUnique)
        coEvery { torrentioRepository.fromEztvTorrent(eztvTorrent) } returns eztvMapped

        val releases = repository.getEpisodeReleases(imdbId, season, episode)

        // Deduplication by hash (case-insensitive):
        // eztvMapped (hash: "hash_shared") and torrentioDuplicate (hash: "HASH_SHARED") should be deduplicated to 1 release.
        // plus torrentioUnique (hash: "hash_torrentio_unique").
        assertThat(releases).hasSize(2)
        // Highest seeds first: torrentioUnique has 250, hash_shared has 10
        assertThat(releases[0].hash).isEqualTo("hash_torrentio_unique")
        assertThat(releases[1].hash).isEqualTo("hash_shared")
    }

    @Test
    fun `getEpisodeReleases handles provider exceptions gracefully`() = runTest {
        val imdbId = "tt0944947"
        val season = 1
        val episode = 2

        val torrentioRelease = TorrentRelease(
            hash = "hash1",
            title = "GoT.S01E02.1080p",
            qualityGroup = VideoQualityGroup.FHD_1080P,
            resolutionLabel = "1080p",
            audioCodec = "AAC",
            seeds = 50,
            provider = "Torrentio"
        )

        coEvery { eztvRepository.getTorrentsForEpisode(imdbId, season, episode) } throws RuntimeException("EZTV offline")
        coEvery { torrentioRepository.getSeriesTorrents(imdbId, season, episode) } returns listOf(torrentioRelease)

        val releases = repository.getEpisodeReleases(imdbId, season, episode)

        assertThat(releases).containsExactly(torrentioRelease)
    }

    @Test
    fun `getEpisodeReleasesBySeriesId resolves IMDb ID from TMDB`() = runTest {
        val seriesId = 1399
        val season = 1
        val episode = 2

        coEvery { tmdbRepository.getTvImdbId(seriesId) } returns "tt0944947"
        coEvery { eztvRepository.getTorrentsForEpisode("tt0944947", season, episode) } returns emptyList()
        coEvery { torrentioRepository.getSeriesTorrents("tt0944947", season, episode) } returns emptyList()

        val releases = repository.getEpisodeReleasesBySeriesId(seriesId, season, episode)
        assertThat(releases).isEmpty()

        coEvery { tmdbRepository.getTvImdbId(seriesId) } returns null
        val releasesNoImdb = repository.getEpisodeReleasesBySeriesId(seriesId, season, episode)
        assertThat(releasesNoImdb).isEmpty()
    }

    @Test
    fun `getEpisodeReleasesWithQualityGrouping returns raw EZTV and grouped releases`() = runTest {
        val seriesId = 1399
        val season = 1
        val episode = 2

        val eztvTorrent = EztvTorrent(title = "GoT.S01E02.720p", hash = "eztv1", seeds = 10)
        val eztvMapped = TorrentRelease(
            hash = "eztv1",
            title = "GoT.S01E02.720p",
            qualityGroup = VideoQualityGroup.HD_720P,
            resolutionLabel = "720p",
            audioCodec = "AAC",
            seeds = 10,
            provider = "EZTV"
        )
        val torrentioRelease = TorrentRelease(
            hash = "t1",
            title = "GoT.S01E02.1080p",
            qualityGroup = VideoQualityGroup.FHD_1080P,
            resolutionLabel = "1080p",
            audioCodec = "DTS",
            seeds = 100,
            provider = "Torrentio"
        )

        coEvery { tmdbRepository.getTvImdbId(seriesId) } returns "tt0944947"
        coEvery { eztvRepository.getTorrentsForEpisode("tt0944947", season, episode) } returns listOf(eztvTorrent)
        coEvery { torrentioRepository.getSeriesTorrents("tt0944947", season, episode) } returns listOf(torrentioRelease)
        coEvery { torrentioRepository.fromEztvTorrent(eztvTorrent) } returns eztvMapped

        val (rawEztv, grouped) = repository.getEpisodeReleasesWithQualityGrouping(seriesId, season, episode)

        assertThat(rawEztv).containsExactly(eztvTorrent)
        assertThat(grouped).containsKey(VideoQualityGroup.HD_720P)
        assertThat(grouped).containsKey(VideoQualityGroup.FHD_1080P)
        assertThat(grouped[VideoQualityGroup.HD_720P]).containsExactly(eztvMapped)
        assertThat(grouped[VideoQualityGroup.FHD_1080P]).containsExactly(torrentioRelease)
    }

    @Test
    fun `fromEztvTorrent delegates to torrentioRepository`() {
        val eztvTorrent = EztvTorrent(title = "Test", hash = "hash1")
        val release = TorrentRelease(hash = "hash1", title = "Test", qualityGroup = VideoQualityGroup.OTHER, resolutionLabel = "SD", audioCodec = "", provider = "EZTV")

        coEvery { torrentioRepository.fromEztvTorrent(eztvTorrent) } returns release

        val result = repository.fromEztvTorrent(eztvTorrent)
        assertThat(result).isEqualTo(release)
    }
}
