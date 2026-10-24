package com.martinrevert.latorrentola.utils

import com.martinrevert.latorrentola.model.torrent.VideoQualityGroup
import com.martinrevert.latorrentola.model.torrentio.TorrentioBehaviorHints
import com.martinrevert.latorrentola.model.torrentio.TorrentioStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentioMetadataParserTest {

    @Test
    fun extractSeeds_parsesCorrectly() {
        val title = "Inception 2010 PROPER Bluray 2160p AV1 HDR10 OPUS 5.1\n👤 281 💾 8.91 GB ⚙️ 1337x"
        assertEquals(281, TorrentioMetadataParser.extractSeeds(title))

        val titleZero = "Title without seeds indicator"
        assertEquals(0, TorrentioMetadataParser.extractSeeds(titleZero))
    }

    @Test
    fun extractSizeAndBytes_parsesGigabytesAndMegabytes() {
        val titleGb = "Breaking Bad S01E01 1080p\n👤 673 💾 928.25 MB ⚙️ 1337x"
        assertEquals("928.25 MB", TorrentioMetadataParser.extractSize(titleGb))
        val bytesMb = TorrentioMetadataParser.parseSizeToBytes("928.25 MB")
        assertTrue(bytesMb != null && bytesMb > 900_000_000L && bytesMb < 1_000_000_000L)

        val bytesGb = TorrentioMetadataParser.parseSizeToBytes("8.91 GB")
        assertTrue(bytesGb != null && bytesGb > 8_000_000_000L && bytesGb < 10_000_000_000L)
    }

    @Test
    fun extractProvider_parsesIndexers() {
        val title = "Movie 1080p\n👤 10 💾 2 GB ⚙️ ThePirateBay"
        assertEquals("ThePirateBay", TorrentioMetadataParser.extractProvider(title))
    }

    @Test
    fun isSeasonPack_detectsFullSeasonsAndRejectsThem() {
        assertTrue(TorrentioMetadataParser.isSeasonPack("Breaking Bad Complete S01-S05 1080p"))
        assertTrue(TorrentioMetadataParser.isSeasonPack("Breaking Bad Season 1 Complete 720p"))
        assertTrue(TorrentioMetadataParser.isSeasonPack("Breaking Bad S01-S05 Seasons 1-5"))
        assertTrue(TorrentioMetadataParser.isSeasonPack("Breaking Bad S01 Complete - 1080p"))
        assertTrue(TorrentioMetadataParser.isSeasonPack("Breaking Bad Temporada 1 Completa"))

        // Single episodes should NOT be detected as season packs
        assertFalse(TorrentioMetadataParser.isSeasonPack("Breaking.Bad.S01E01.Pilot.1080p.mkv"))
        assertFalse(TorrentioMetadataParser.isSeasonPack("Breaking Bad 1x01 Pilot 720p"))
    }

    @Test
    fun isEztvStream_detectsEztvProviderAndTitle() {
        val eztvStream = TorrentioStream(
            title = "Show S01E01 720p\n👤 50 💾 500 MB ⚙️ EZTV"
        )
        assertTrue(TorrentioMetadataParser.isEztvStream(eztvStream))

        val eztvInTitle = TorrentioStream(
            title = "Show.S01E01.720p.HDTV.x264-EZTV\n👤 50 💾 500 MB ⚙️ ThePirateBay"
        )
        assertTrue(TorrentioMetadataParser.isEztvStream(eztvInTitle))

        val otherStream = TorrentioStream(
            title = "Show S01E01 1080p\n👤 150 💾 1.2 GB ⚙️ 1337x",
            behaviorHints = TorrentioBehaviorHints(filename = "Show.S01E01.1080p.mkv")
        )
        assertFalse(TorrentioMetadataParser.isEztvStream(otherStream))
    }

    @Test
    fun extractResolution_groupsProperly() {
        val (group4k, label4k) = TorrentioMetadataParser.extractResolution("Torrentio\n4k HDR", "Movie 2160p")
        assertEquals(VideoQualityGroup.UHD_4K, group4k)
        assertEquals("4K", label4k)

        val (group1080p, label1080p) = TorrentioMetadataParser.extractResolution("Torrentio\n1080p", "Movie 1080p BluRay")
        assertEquals(VideoQualityGroup.FHD_1080P, group1080p)
        assertEquals("1080p", label1080p)

        val (group720p, label720p) = TorrentioMetadataParser.extractResolution("Torrentio\n720p", "Movie 720p HDTV")
        assertEquals(VideoQualityGroup.HD_720P, group720p)
        assertEquals("720p", label720p)
    }

    @Test
    fun extractAudioCodec_handlesYtsAsAacAndParsesHighFidelityCodecs() {
        // YTS Rule: always AAC
        assertEquals("AAC 5.1", TorrentioMetadataParser.extractAudioCodec(null, null, isYts = true, audioChannels = "5.1"))
        assertEquals("AAC 2.0", TorrentioMetadataParser.extractAudioCodec(null, null, isYts = true, audioChannels = "2.0"))
        assertEquals("AAC", TorrentioMetadataParser.extractAudioCodec(null, null, isYts = true, audioChannels = null))

        // High fidelity formats from Torrentio
        assertEquals("DTS-HD MA 5.1", TorrentioMetadataParser.extractAudioCodec("Inception.2010.2160p.DTS-HD.MA.5.1.mkv"))
        assertEquals("TrueHD Atmos", TorrentioMetadataParser.extractAudioCodec("Inception.2010.2160p.TrueHD.Atmos.mkv"))
        assertEquals("DDP 5.1", TorrentioMetadataParser.extractAudioCodec("Breaking.Bad.S01E01.1080p.NF.WEB-DL.DDP5.1.mkv"))
        assertEquals("AC3 5.1", TorrentioMetadataParser.extractAudioCodec("Inception.2010.AC3.5.1.mkv"))
        assertEquals("Opus 5.1", TorrentioMetadataParser.extractAudioCodec("Inception.2010.OPUS.5.1.mkv"))
        assertEquals("FLAC", TorrentioMetadataParser.extractAudioCodec("Movie.1080p.FLAC.mkv"))
    }
}
