package com.martinrevert.latorrentola.model.torrent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TorrentReleaseGroupingTest {

    @Test
    fun `groupByQuality correctly groups, deduplicates and sorts releases`() {
        val release4k = TorrentRelease(
            hash = "HASH4K_A",
            title = "Episode.S01E02.2160p.HDR",
            qualityGroup = VideoQualityGroup.UHD_4K,
            resolutionLabel = "4K",
            audioCodec = "DTS 5.1",
            seeds = 50,
            sizeBytes = 4_000_000_000L
        )
        val release1080pHighSeeds = TorrentRelease(
            hash = "HASH1080_A",
            title = "Episode.S01E02.1080p.WEB",
            qualityGroup = VideoQualityGroup.FHD_1080P,
            resolutionLabel = "1080p",
            audioCodec = "AAC",
            seeds = 200,
            sizeBytes = 1_500_000_000L
        )
        val release1080pLowSeeds = TorrentRelease(
            hash = "HASH1080_B",
            title = "Episode.S01E02.1080p.HDTV",
            qualityGroup = VideoQualityGroup.FHD_1080P,
            resolutionLabel = "1080p",
            audioCodec = "AAC",
            seeds = 100,
            sizeBytes = 2_000_000_000L
        )
        val duplicate1080p = TorrentRelease(
            hash = "hash1080_a", // lowercase duplicate
            title = "Episode.S01E02.1080p.Duplicate",
            qualityGroup = VideoQualityGroup.FHD_1080P,
            resolutionLabel = "1080p",
            audioCodec = "AAC",
            seeds = 50,
            sizeBytes = 1_000_000_000L
        )
        val release720p = TorrentRelease(
            hash = "HASH720",
            title = "Episode.S01E02.720p.HDTV",
            qualityGroup = VideoQualityGroup.HD_720P,
            resolutionLabel = "720p",
            audioCodec = "AAC",
            seeds = 30,
            sizeBytes = 600_000_000L
        )
        val releaseOther = TorrentRelease(
            hash = "HASHOTHER",
            title = "Episode.S01E02.480p",
            qualityGroup = VideoQualityGroup.OTHER,
            resolutionLabel = "SD",
            audioCodec = "MP3",
            seeds = 10,
            sizeBytes = 300_000_000L
        )

        val input = listOf(
            release720p,
            release1080pLowSeeds,
            releaseOther,
            release1080pHighSeeds,
            duplicate1080p,
            release4k
        )

        val grouped = input.groupByQuality()

        // 1. Quality group ordering must be UHD_4K -> FHD_1080P -> HD_720P -> OTHER
        val keys = grouped.keys.toList()
        assertThat(keys).containsExactly(
            VideoQualityGroup.UHD_4K,
            VideoQualityGroup.FHD_1080P,
            VideoQualityGroup.HD_720P,
            VideoQualityGroup.OTHER
        ).inOrder()

        // 2. 1080p releases should be deduplicated (2 instead of 3) and sorted by seeds descending
        val list1080p = grouped[VideoQualityGroup.FHD_1080P].orEmpty()
        assertThat(list1080p).hasSize(2)
        assertThat(list1080p[0].hash).isEqualTo("HASH1080_A")
        assertThat(list1080p[1].hash).isEqualTo("HASH1080_B")

        // 3. Other groups have expected counts
        assertThat(grouped[VideoQualityGroup.UHD_4K]).containsExactly(release4k)
        assertThat(grouped[VideoQualityGroup.HD_720P]).containsExactly(release720p)
        assertThat(grouped[VideoQualityGroup.OTHER]).containsExactly(releaseOther)
    }

    @Test
    fun `groupByQuality returns empty map when input list is empty`() {
        val grouped = emptyList<TorrentRelease>().groupByQuality()
        assertThat(grouped).isEmpty()
    }

    @Test
    fun `groupByQuality preserves releases with blank infohashes if titles or providers differ`() {
        val releaseBlankHash1 = TorrentRelease(
            hash = "",
            title = "Episode.S01E02.1080p.ReleaseA",
            qualityGroup = VideoQualityGroup.FHD_1080P,
            resolutionLabel = "1080p",
            audioCodec = "AAC",
            provider = "Torrentio"
        )
        val releaseBlankHash2 = TorrentRelease(
            hash = "   ",
            title = "Episode.S01E02.1080p.ReleaseB",
            qualityGroup = VideoQualityGroup.FHD_1080P,
            resolutionLabel = "1080p",
            audioCodec = "AAC",
            provider = "EZTV"
        )

        val grouped = listOf(releaseBlankHash1, releaseBlankHash2).groupByQuality()
        val list1080p = grouped[VideoQualityGroup.FHD_1080P].orEmpty()

        assertThat(list1080p).hasSize(2)
        assertThat(list1080p).containsExactly(releaseBlankHash1, releaseBlankHash2)
    }
}
