package com.martinrevert.latorrentola.utils

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import android.os.StatFs
import org.junit.Test
import java.io.File

class TorrentLaunchHelperTest {

    @Test
    fun `MIN_REQUIRED_STORAGE_BYTES should be 1 GB`() {
        assertThat(TorrentLaunchHelper.MIN_REQUIRED_STORAGE_BYTES).isEqualTo(1_000_000_000L)
    }

    @Test
    fun `buildMagnetUri should format valid BTIH magnet link`() {
        val hash = "abc123def456"
        val title = "Inception (2010)"
        val uri = TorrentLaunchHelper.buildMagnetUri(hash, title)

        assertThat(uri).startsWith("magnet:?xt=urn:btih:abc123def456")
        assertThat(uri).contains("dn=Inception")
        assertThat(uri).contains("tr=udp://open.demonii.com:1337/announce")
    }

    @Test
    fun `launch should return InsufficientStorage when free disk space is less than 1 GB`() {
        val context = mockk<Context>()
        val filesDir = mockk<File>()
        every { context.filesDir } returns filesDir
        every { filesDir.absolutePath } returns "/mock/files/dir"

        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns 500_000_000L // 500 MB < 1 GB

        val result = TorrentLaunchHelper.launch(
            context = context,
            mode = TorrentHandlingMode.LOCAL_PLAYBACK,
            magnetUri = "magnet:?xt=urn:btih:123",
            title = "Test Movie"
        )

        assertThat(result).isInstanceOf(TorrentLaunchResult.InsufficientStorage::class.java)
        val storageResult = result as TorrentLaunchResult.InsufficientStorage
        assertThat(storageResult.availableBytes).isEqualTo(500_000_000L)
        assertThat(storageResult.requiredBytes).isEqualTo(1_000_000_000L)
    }

    @Test
    fun `launch should require video size if video size exceeds 1 GB`() {
        val context = mockk<Context>()
        val filesDir = mockk<File>()
        every { context.filesDir } returns filesDir
        every { filesDir.absolutePath } returns "/mock/files/dir"

        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns 1_500_000_000L // 1.5 GB

        val videoSizeBytes = 2_500_000_000L // 2.5 GB

        val result = TorrentLaunchHelper.launch(
            context = context,
            mode = TorrentHandlingMode.LOCAL_PLAYBACK,
            magnetUri = "magnet:?xt=urn:btih:123",
            title = "Large Movie",
            requiredSizeBytes = videoSizeBytes
        )

        assertThat(result).isInstanceOf(TorrentLaunchResult.InsufficientStorage::class.java)
        val storageResult = result as TorrentLaunchResult.InsufficientStorage
        assertThat(storageResult.availableBytes).isEqualTo(1_500_000_000L)
        assertThat(storageResult.requiredBytes).isEqualTo(2_500_000_000L)
    }

    @Test
    fun `launch with legacy CHROMECAST mode should route to downloadLocally`() {
        val context = mockk<Context>()
        val filesDir = mockk<File>()
        every { context.filesDir } returns filesDir
        every { filesDir.absolutePath } returns "/mock/files/dir"
        every { context.startService(any()) } returns null

        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns 2_000_000_000L

        @Suppress("DEPRECATION")
        val result = TorrentLaunchHelper.launch(
            context = context,
            mode = TorrentHandlingMode.CHROMECAST,
            magnetUri = "magnet:?xt=urn:btih:123",
            title = "Test Legacy Cast"
        )

        assertThat(result).isEqualTo(TorrentLaunchResult.Started)
    }
}
