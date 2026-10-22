package com.martinrevert.latorrentola.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiquidShimmerProgressCardTest {

    @Test
    fun `formatTransferRate formats zero or negative bytes as 0 KB_s`() {
        assertThat(formatTransferRate(0L)).isEqualTo("0 KB/s")
        assertThat(formatTransferRate(-500L)).isEqualTo("0 KB/s")
    }

    @Test
    fun `formatTransferRate formats kilobytes per second correctly`() {
        assertThat(formatTransferRate(512 * 1024L)).isEqualTo("512 KB/s")
        assertThat(formatTransferRate(1023 * 1024L)).isEqualTo("1023 KB/s")
    }

    @Test
    fun `formatTransferRate formats megabytes per second with single decimal precision`() {
        assertThat(formatTransferRate(1024 * 1024L)).isEqualTo("1.0 MB/s")
        assertThat(formatTransferRate((2.5 * 1024 * 1024).toLong())).isEqualTo("2.5 MB/s")
        assertThat(formatTransferRate((10.75 * 1024 * 1024).toLong())).isEqualTo("10.8 MB/s")
    }

    @Test
    fun `TorrentDownloadService parseHexInfoHash correctly parses hex and base32 magnet hashes`() {
        val hexMagnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Test"
        assertThat(com.martinrevert.latorrentola.service.TorrentDownloadService.parseHexInfoHash(hexMagnet))
            .isEqualTo("0123456789abcdef0123456789abcdef01234567")

        val invalidMagnet = "magnet:?xt=urn:unknown:123"
        assertThat(com.martinrevert.latorrentola.service.TorrentDownloadService.parseHexInfoHash(invalidMagnet))
            .isNull()
    }

    @Test
    fun `activeBufferState prioritizes live buffer progress when updated`() {
        val state = com.martinrevert.latorrentola.service.TorrentBufferState(
            infoHash = "testhash123",
            title = "Test Video",
            state = com.martinrevert.latorrentola.service.TorrentDownloadService.STATE_BUFFERING,
            readyProgress = 0.05f,
            totalProgress = 0.01f,
            downloadSpeedBytes = 1_500_000L,
            numSeeds = 25,
            numPeers = 5
        )
        com.martinrevert.latorrentola.service.TorrentDownloadService.updateBufferState(state)

        val active = com.martinrevert.latorrentola.service.TorrentDownloadService.activeBufferState.value
        assertThat(active?.infoHash).isEqualTo("testhash123")
        assertThat(active?.readyProgress).isEqualTo(0.05f)

        // Clear after test
        com.martinrevert.latorrentola.service.TorrentDownloadService.updateBufferState(null)
        assertThat(com.martinrevert.latorrentola.service.TorrentDownloadService.activeBufferState.value).isNull()
    }
}
