package com.martinrevert.latorrentola.network

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeason
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeasonDetails
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
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

        val nextInfo =
            repository.resolveNextEpisodeInfo(seriesId, "Game of Thrones", seasonNumber, currentEpisodeNumber)

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

        val nextInfo =
            repository.resolveNextEpisodeInfo(seriesId, "Game of Thrones", seasonNumber, currentEpisodeNumber)

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
        coEvery { torrentioRepository.getSeriesTorrents(imdbId, season, episode) } returns listOf(
            torrentioDuplicate,
            torrentioUnique
        )
        coEvery { torrentioRepository.fromEztvTorrent(eztvTorrent) } returns eztvMapped

        val releases = repository.getEpisodeReleases(imdbId, season, episode)

        assertThat(releases).hasSize(2)
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

        coEvery {
            eztvRepository.getTorrentsForEpisode(
                imdbId,
                season,
                episode
            )
        } throws RuntimeException("EZTV offline")
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
        val release = TorrentRelease(
            hash = "hash1",
            title = "Test",
            qualityGroup = VideoQualityGroup.OTHER,
            resolutionLabel = "SD",
            audioCodec = "",
            provider = "EZTV"
        )

        coEvery { torrentioRepository.fromEztvTorrent(eztvTorrent) } returns release

        val result = repository.fromEztvTorrent(eztvTorrent)
        assertThat(result).isEqualTo(release)
    }

    @Test
    fun `evaluateNextEpisode returns SERIES_ENDED when on final episode of an ended series`() = runTest {
        val seriesId = 1396
        coEvery { tmdbRepository.getTvDetails(seriesId) } returns TmdbTvSummary(
            id = seriesId,
            name = "Breaking Bad",
            status = "Ended",
            inProduction = false,
            seasons = listOf(TmdbTvSeason(id = 5, seasonNumber = 5, episodeCount = 16))
        )
        coEvery { tmdbRepository.getTvSeasonDetails(seriesId, 5) } returns TmdbTvSeasonDetails(
            id = 5,
            seasonNumber = 5,
            episodes = listOf(
                TmdbTvEpisode(id = 15, episodeNumber = 15, seasonNumber = 5, name = "Granite State"),
                TmdbTvEpisode(id = 16, episodeNumber = 16, seasonNumber = 5, name = "Felina")
            )
        )
        coEvery { tmdbRepository.getTvSeasonDetails(seriesId, 6) } throws RuntimeException("404")

        val eval = repository.evaluateNextEpisode(
            seriesId = seriesId,
            seriesName = "Breaking Bad",
            currentSeasonNumber = 5,
            currentEpisodeNumber = 16,
            todayIso = "2026-10-28"
        )

        assertThat(eval.state).isEqualTo(SeriesContinuationState.SERIES_ENDED)
        assertThat(eval.isSeriesContinuing).isFalse()
        assertThat(eval.releases).isEmpty()
    }

    @Test
    fun `evaluateNextEpisode returns SEASON_COMPLETE_SERIES_CONTINUES with calendar schedule when season finished and series continues`() = runTest {
        val seriesId = 95396
        val nextAirEp = TmdbTvEpisode(
            id = 201,
            episodeNumber = 1,
            seasonNumber = 3,
            name = "Hello Again",
            airDate = "2027-01-15"
        )
        coEvery { tmdbRepository.getTvDetails(seriesId) } returns TmdbTvSummary(
            id = seriesId,
            name = "Severance",
            status = "Returning Series",
            inProduction = true,
            nextEpisodeToAir = nextAirEp,
            seasons = listOf(
                TmdbTvSeason(id = 1, seasonNumber = 1, episodeCount = 9),
                TmdbTvSeason(id = 2, seasonNumber = 2, episodeCount = 10)
            )
        )
        coEvery { tmdbRepository.getTvSeasonDetails(seriesId, 2) } returns TmdbTvSeasonDetails(
            id = 2,
            seasonNumber = 2,
            episodes = listOf(
                TmdbTvEpisode(id = 210, episodeNumber = 10, seasonNumber = 2, name = "Cold Harbor", airDate = "2025-03-21")
            )
        )
        coEvery { tmdbRepository.getTvSeasonDetails(seriesId, 3) } throws RuntimeException("404")

        val eval = repository.evaluateNextEpisode(
            seriesId = seriesId,
            seriesName = "Severance",
            currentSeasonNumber = 2,
            currentEpisodeNumber = 10,
            todayIso = "2026-10-28"
        )

        assertThat(eval.state).isEqualTo(SeriesContinuationState.SEASON_COMPLETE_SERIES_CONTINUES)
        assertThat(eval.isSeriesContinuing).isTrue()
        assertThat(eval.nextEpisodeAirDate).isEqualTo("2027-01-15")
        assertThat(eval.upcomingSchedules).hasSize(1)
        val schedule = eval.upcomingSchedules.first()
        assertThat(schedule.buildCalendarEventTitle()).isEqualTo("Severance S03E01 · Hello Again")
        assertThat(schedule.toEpochMillisUtc()).isNotNull()
    }

    @Test
    fun `evaluateNextEpisode detects remaining episodes even when TMDB status is Ended`() = runTest {
        val seriesId = 1396
        coEvery { tmdbRepository.getTvDetails(seriesId) } returns TmdbTvSummary(
            id = seriesId,
            name = "Breaking Bad",
            status = "Ended",
            inProduction = false,
            seasons = listOf(TmdbTvSeason(id = 5, seasonNumber = 5, episodeCount = 4))
        )
        coEvery { tmdbRepository.getTvSeasonDetails(seriesId, 5) } returns TmdbTvSeasonDetails(
            id = 5,
            seasonNumber = 5,
            episodes = listOf(
                TmdbTvEpisode(id = 1, episodeNumber = 1, seasonNumber = 5, name = "Ep 1", airDate = "2012-07-15"),
                TmdbTvEpisode(id = 2, episodeNumber = 2, seasonNumber = 5, name = "Ep 2", airDate = "2012-07-22"),
                TmdbTvEpisode(id = 3, episodeNumber = 3, seasonNumber = 5, name = "Ep 3", airDate = "2012-07-29"),
                TmdbTvEpisode(id = 4, episodeNumber = 4, seasonNumber = 5, name = "Ep 4", airDate = "2012-08-05")
            )
        )
        coEvery { tmdbRepository.getTvImdbId(seriesId) } returns "tt0903747"
        coEvery { eztvRepository.getTorrentsForEpisode("tt0903747", 5, 3) } returns emptyList()
        coEvery { torrentioRepository.getSeriesTorrents("tt0903747", 5, 3) } returns emptyList()

        val eval = repository.evaluateNextEpisode(
            seriesId = seriesId,
            seriesName = "Breaking Bad",
            currentSeasonNumber = 5,
            currentEpisodeNumber = 2,
            todayIso = "2026-10-28"
        )

        assertThat(eval.state).isEqualTo(SeriesContinuationState.REMAINING_EPISODES_NO_TORRENTS)
        assertThat(eval.isSeriesContinuing).isFalse()
        assertThat(eval.remainingEpisodesCount).isEqualTo(2)
        assertThat(eval.nextSeasonNumber).isEqualTo(5)
        assertThat(eval.nextEpisodeNumber).isEqualTo(3)
        assertThat(eval.upcomingSchedules).isEmpty()
    }

    @Test
    fun `evaluateNextEpisode excludes historical air dates from upcomingSchedules`() = runTest {
        val seriesId = 100
        coEvery { tmdbRepository.getTvDetails(seriesId) } returns TmdbTvSummary(
            id = seriesId,
            name = "Test Series",
            status = "Returning Series",
            inProduction = true,
            seasons = listOf(TmdbTvSeason(id = 1, seasonNumber = 1, episodeCount = 3))
        )
        coEvery { tmdbRepository.getTvSeasonDetails(seriesId, 1) } returns TmdbTvSeasonDetails(
            id = 1,
            seasonNumber = 1,
            episodes = listOf(
                TmdbTvEpisode(id = 1, episodeNumber = 1, seasonNumber = 1, name = "Past Ep", airDate = "2020-01-01"),
                TmdbTvEpisode(id = 2, episodeNumber = 2, seasonNumber = 1, name = "Today Ep", airDate = "2026-10-28"),
                TmdbTvEpisode(id = 3, episodeNumber = 3, seasonNumber = 1, name = "Future Ep", airDate = "2026-11-05")
            )
        )
        coEvery { tmdbRepository.getTvImdbId(seriesId) } returns "tt1234567"
        coEvery { eztvRepository.getTorrentsForEpisode("tt1234567", 1, 2) } returns emptyList()
        coEvery { torrentioRepository.getSeriesTorrents("tt1234567", 1, 2) } returns emptyList()

        val eval = repository.evaluateNextEpisode(
            seriesId = seriesId,
            seriesName = "Test Series",
            currentSeasonNumber = 1,
            currentEpisodeNumber = 1,
            todayIso = "2026-10-28"
        )

        assertThat(eval.upcomingSchedules).hasSize(2)
        assertThat(eval.upcomingSchedules.map { it.airDateIso }).containsExactly("2026-10-28", "2026-11-05").inOrder()
    }

    @Test
    fun `evaluateNextEpisode propagates network exceptions from TMDB repository`() = runTest {
        val seriesId = 500
        coEvery { tmdbRepository.getTvDetails(seriesId) } throws java.io.IOException("Network offline")

        try {
            repository.evaluateNextEpisode(
                seriesId = seriesId,
                seriesName = "Offline Series",
                currentSeasonNumber = 1,
                currentEpisodeNumber = 1
            )
            org.junit.Assert.fail("Expected IOException was not thrown")
        } catch (e: java.io.IOException) {
            assertThat(e.message).isEqualTo("Network offline")
        }
    }
}
