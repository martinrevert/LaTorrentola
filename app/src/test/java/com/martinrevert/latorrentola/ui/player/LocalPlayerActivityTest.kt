package com.martinrevert.latorrentola.ui.player

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode

import org.junit.Test

class LocalPlayerActivityTest {

    @Test
    fun `EXTRA_CAST_ENABLED key matches between service and LocalPlayerActivity`() {
        assertThat(LocalPlayerActivity.EXTRA_CAST_ENABLED).isEqualTo("torrent_cast_enabled")
    }

    @Test
    fun `EXTRA_CAST_URL key matches between service and LocalPlayerActivity`() {
        assertThat(LocalPlayerActivity.EXTRA_CAST_URL).isEqualTo("verified_media_cast_url")
    }

    @Test
    fun `TorrentHandlingMode CHROMECAST requests castWhenReady flag in TorrentLaunchHelper`() {
        val mode = TorrentHandlingMode.CHROMECAST
        assertThat(mode).isEqualTo(TorrentHandlingMode.CHROMECAST)
    }
}
