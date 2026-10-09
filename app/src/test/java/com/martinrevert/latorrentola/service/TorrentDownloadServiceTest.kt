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
}
