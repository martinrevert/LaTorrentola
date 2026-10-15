package com.martinrevert.latorrentola.ui.downloads

import android.content.Context
import android.content.Intent
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.database.TorrentDownloadDao
import com.martinrevert.latorrentola.model.torrent.TorrentDownload
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.service.TorrentDownloadService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TorrentDownloadsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val torrentDownloadDao: TorrentDownloadDao = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val downloadsFlow = MutableStateFlow<List<TorrentDownload>>(emptyList())

    private lateinit var viewModel: TorrentDownloadsViewModel

    @Before
    fun setup() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setAction(any()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns mockk(relaxed = true)
        every { torrentDownloadDao.observeAll() } returns downloadsFlow
        viewModel = TorrentDownloadsViewModel(torrentDownloadDao)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `downloads stateFlow emits observeAll data`() = runTest {
        val testDownload = TorrentDownload(
            infoHash = "HASH123",
            magnetUri = "magnet:?xt=urn:btih:HASH123",
            title = "Inception (2010)",
            state = TorrentDownloadService.STATE_DOWNLOADING,
            progressPercent = 45,
            mediaPath = null,
            castWhenReady = false,
            updatedAtMillis = 1000L,
            numSeeds = 15,
            numPeers = 3
        )

        viewModel.downloads.test {
            assertThat(awaitItem()).isEmpty()

            downloadsFlow.value = listOf(testDownload)
            val updated = awaitItem()
            assertThat(updated).hasSize(1)
            assertThat(updated.first().infoHash).isEqualTo("HASH123")
            assertThat(updated.first().numSeeds).isEqualTo(15)
            assertThat(updated.first().numPeers).isEqualTo(3)
        }
    }

    @Test
    fun `togglePause sends ACTION_PAUSE when active`() {
        val download = TorrentDownload(
            infoHash = "HASH123",
            magnetUri = "magnet:?",
            title = "Test",
            state = TorrentDownloadService.STATE_DOWNLOADING,
            progressPercent = 50,
            mediaPath = null,
            castWhenReady = false,
            updatedAtMillis = 1000L
        )

        val actionSlot = slot<String>()
        val extraSlot = slot<String>()

        every { anyConstructed<Intent>().setAction(capture(actionSlot)) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(TorrentDownloadService.EXTRA_INFO_HASH, capture(extraSlot)) } returns mockk(relaxed = true)

        viewModel.togglePause(context, download)

        verify { context.startService(any()) }
        assertThat(actionSlot.captured).isEqualTo(TorrentDownloadService.ACTION_PAUSE)
        assertThat(extraSlot.captured).isEqualTo("HASH123")
    }

    @Test
    fun `togglePause sends ACTION_RESUME when paused`() {
        val download = TorrentDownload(
            infoHash = "HASH123",
            magnetUri = "magnet:?",
            title = "Test",
            state = TorrentDownloadService.STATE_PAUSED,
            progressPercent = 50,
            mediaPath = null,
            castWhenReady = false,
            updatedAtMillis = 1000L
        )

        val actionSlot = slot<String>()

        every { anyConstructed<Intent>().setAction(capture(actionSlot)) } returns mockk(relaxed = true)

        viewModel.togglePause(context, download)

        verify { context.startService(any()) }
        assertThat(actionSlot.captured).isEqualTo(TorrentDownloadService.ACTION_RESUME)
    }

    @Test
    fun `delete sends ACTION_DELETE command`() {
        val download = TorrentDownload(
            infoHash = "HASH123",
            magnetUri = "magnet:?",
            title = "Test",
            state = TorrentDownloadService.STATE_COMPLETED,
            progressPercent = 100,
            mediaPath = null,
            castWhenReady = false,
            updatedAtMillis = 1000L
        )

        val actionSlot = slot<String>()
        val extraSlot = slot<String>()

        every { anyConstructed<Intent>().setAction(capture(actionSlot)) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(TorrentDownloadService.EXTRA_INFO_HASH, capture(extraSlot)) } returns mockk(relaxed = true)

        viewModel.delete(context, download)

        verify { context.startService(any()) }
        assertThat(actionSlot.captured).isEqualTo(TorrentDownloadService.ACTION_DELETE)
        assertThat(extraSlot.captured).isEqualTo("HASH123")
    }
}
