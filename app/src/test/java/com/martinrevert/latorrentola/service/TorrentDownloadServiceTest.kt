package com.martinrevert.latorrentola.service

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.torrent.TorrentDownload
import org.junit.Test

class TorrentDownloadServiceTest {

    @Test
    fun `TorrentDownload model should support series and movie metadata`() {
        val download = TorrentDownload(
            infoHash = "hash123",
            magnetUri = "magnet:?xt=urn:btih:hash123",
            title = "Test Episode",
            state = "READY",
            progressPercent = 100,
            mediaPath = "/path/file.mp4",
            castWhenReady = false,
            updatedAtMillis = System.currentTimeMillis(),
            seriesId = 12,
            seasonNumber = 1,
            episodeNumber = 5,
            movieId = 0
        )

        assertThat(download.seriesId).isEqualTo(12)
        assertThat(download.seasonNumber).isEqualTo(1)
        assertThat(download.episodeNumber).isEqualTo(5)
        assertThat(download.movieId).isEqualTo(0)
    }

    @Test
    fun `ACTION_PLAY_STREAM and EXTRA_INFO_HASH constants match expected values`() {
        assertThat(TorrentDownloadService.ACTION_PLAY_STREAM)
            .isEqualTo("com.martinrevert.latorrentola.action.PLAY_TORRENT_STREAM")
        assertThat(TorrentDownloadService.EXTRA_INFO_HASH)
            .isEqualTo("torrent_info_hash")
    }

    @Test
    fun `TorrentBufferState should correctly model buffering and readiness attributes`() {
        val state = TorrentBufferState(
            infoHash = "abcdef1234567890",
            title = "Sample Video 1080p",
            state = TorrentDownloadService.STATE_BUFFERING,
            readyProgress = 0.75f,
            totalProgress = 0.12f,
            downloadSpeedBytes = 2_500_000L,
            numSeeds = 45,
            numPeers = 12
        )

        assertThat(state.infoHash).isEqualTo("abcdef1234567890")
        assertThat(state.title).isEqualTo("Sample Video 1080p")
        assertThat(state.state).isEqualTo(TorrentDownloadService.STATE_BUFFERING)
        assertThat(state.readyProgress).isEqualTo(0.75f)
        assertThat(state.totalProgress).isEqualTo(0.12f)
        assertThat(state.downloadSpeedBytes).isEqualTo(2_500_000L)
        assertThat(state.numSeeds).isEqualTo(45)
        assertThat(state.numPeers).isEqualTo(12)
    }

    @Test
    fun `activeBufferState flow is exposed and accessible on TorrentDownloadService companion`() {
        val flow = TorrentDownloadService.activeBufferState
        assertThat(flow).isNotNull()
    }

    @Test
    fun `ACTION_PAUSE_ALL constant matches expected value`() {
        assertThat(TorrentDownloadService.ACTION_PAUSE_ALL)
            .isEqualTo("com.martinrevert.latorrentola.action.PAUSE_ALL")
    }

    @Test
    fun `PlaybackService ACTION_STOP_PLAYBACK and EXTRA_EXCEPT_INFO_HASH constants match expected values`() {
        assertThat(PlaybackService.ACTION_STOP_PLAYBACK)
            .isEqualTo("com.martinrevert.latorrentola.action.STOP_PLAYBACK")
        assertThat(PlaybackService.EXTRA_EXCEPT_INFO_HASH)
            .isEqualTo("extra_except_info_hash")
    }

    @Test
    fun `TorrentDownload supports STATE_PAUSED state transitions`() {
        val download = TorrentDownload(
            infoHash = "preempted_hash_1",
            magnetUri = "magnet:?xt=urn:btih:preempted_hash_1",
            title = "Preempted Video",
            state = TorrentDownloadService.STATE_PAUSED,
            progressPercent = 42,
            mediaPath = null,
            castWhenReady = false,
            updatedAtMillis = System.currentTimeMillis()
        )
        assertThat(download.state).isEqualTo(TorrentDownloadService.STATE_PAUSED)
        assertThat(download.progressPercent).isEqualTo(42)
    }
}
