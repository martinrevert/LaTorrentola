package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.YTS.Torrent
import com.martinrevert.latorrentola.model.torrent.PlaybackCompatibility
import com.martinrevert.latorrentola.model.torrent.VideoQualityGroup
import com.martinrevert.latorrentola.model.torrentio.TorrentioBehaviorHints
import com.martinrevert.latorrentola.model.torrentio.TorrentioResponse
import com.martinrevert.latorrentola.model.torrentio.TorrentioStream
import com.martinrevert.latorrentola.utils.DevicePlaybackCapabilities
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TorrentioRepositoryTest {

    private val torrentioService: TorrentioService = mockk()
    private val capabilities: DevicePlaybackCapabilities = mockk()
    private lateinit var repository: TorrentioRepository

    @Before
    fun setUp() {
        every {
            capabilities.evaluateRelease(any(), any(), any(), any())
        } returns PlaybackCompatibility.NATIVE

        repository = TorrentioRepository(torrentioService, capabilities)
    }

    @Test
    fun getMovieTorrents_mapsStreamsToReleasesCorrectly() = runTest {
        val stream = TorrentioStream(
            name = "Torrentio\n4k HDR",
            title = "Inception 2010 Bluray 2160p DTS-HD MA 5.1\n👤 250 💾 15.2 GB ⚙️ 1337x",
            infoHash = "2849211f150e966ef7924f43aec22727a644e10d",
            fileIdx = 0,
            behaviorHints = TorrentioBehaviorHints(filename = "Inception.2010.2160p.mkv")
        )

        coEvery { torrentioService.getMovieStreams("tt1375666") } returns TorrentioResponse(listOf(stream))

        val results = repository.getMovieTorrents("1375666")
        assertEquals(1, results.size)
        val release = results.first()
        assertEquals("2849211f150e966ef7924f43aec22727a644e10d", release.hash)
        assertEquals(VideoQualityGroup.UHD_4K, release.qualityGroup)
        assertEquals("4K", release.resolutionLabel)
        assertEquals(250, release.seeds)
        assertEquals("1337x", release.provider)
        assertEquals("DTS-HD MA 5.1", release.audioCodec)
    }

    @Test
    fun getSeriesTorrents_filtersOutEztvAndSeasonPacks() = runTest {
        val singleEpisodeValid = TorrentioStream(
            name = "Torrentio\n1080p",
            title = "Breaking.Bad.S01E01.Pilot.1080p.mkv\n👤 150 💾 1.2 GB ⚙️ 1337x",
            infoHash = "VALID_HASH_1"
        )
        val eztvStream = TorrentioStream(
            name = "Torrentio\n720p",
            title = "Breaking.Bad.S01E01.720p.HDTV.x264-EZTV\n👤 90 💾 500 MB ⚙️ EZTV",
            infoHash = "EZTV_HASH"
        )
        val seasonPack = TorrentioStream(
            name = "Torrentio\n1080p",
            title = "Breaking Bad Complete S01-S05 1080p\n👤 300 💾 50 GB ⚙️ ThePirateBay",
            infoHash = "SEASON_PACK_HASH"
        )

        coEvery {
            torrentioService.getSeriesStreams("tt0903747:1:1")
        } returns TorrentioResponse(listOf(singleEpisodeValid, eztvStream, seasonPack))

        val results = repository.getSeriesTorrents("tt0903747", 1, 1)
        assertEquals(1, results.size)
        assertEquals("valid_hash_1", results.first().hash)
        assertEquals("1337x", results.first().provider)
    }

    @Test
    fun fromYtsTorrent_forcesAacAudioFormat() {
        val ytsTorrent51 = Torrent(
            hash = "YTS_HASH_51",
            quality = "1080p",
            type = "bluray",
            seeds = 100,
            peers = 10,
            size = "2.1 GB",
            audioChannels = "5.1"
        )

        val release51 = repository.fromYtsTorrent(ytsTorrent51, "Inception")
        assertTrue(release51.isYts)
        assertEquals("YTS", release51.provider)
        assertEquals("AAC 5.1", release51.audioCodec)
        assertEquals(VideoQualityGroup.FHD_1080P, release51.qualityGroup)

        val ytsTorrentStereo = Torrent(
            hash = "YTS_HASH_STEREO",
            quality = "720p",
            type = "web",
            seeds = 50,
            peers = 5,
            size = "900 MB",
            audioChannels = "2.0"
        )

        val releaseStereo = repository.fromYtsTorrent(ytsTorrentStereo, "Inception")
        assertEquals("AAC 2.0", releaseStereo.audioCodec)
    }

    @Test
    fun fromEztvTorrent_mapsFieldsCorrectly() {
        val eztvTorrent = EztvTorrent(
            id = 1234,
            hash = "EZTV_TEST_HASH",
            title = "Show.S01E01.1080p.WEB-DL.DDP5.1.x264",
            seeds = 75,
            peers = 8,
            sizeBytes = "1500000000"
        )

        val release = repository.fromEztvTorrent(eztvTorrent)
        assertFalse(release.isYts)
        assertEquals("EZTV", release.provider)
        assertEquals("DDP 5.1", release.audioCodec)
        assertEquals(VideoQualityGroup.FHD_1080P, release.qualityGroup)
        assertEquals(75, release.seeds)
    }
}
