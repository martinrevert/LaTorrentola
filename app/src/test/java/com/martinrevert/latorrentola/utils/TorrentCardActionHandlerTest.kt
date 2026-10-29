package com.martinrevert.latorrentola.utils

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.torrent.TorrentDownload
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.service.TorrentBufferState
import com.martinrevert.latorrentola.service.TorrentDownloadService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Test

class TorrentCardActionHandlerTest {

    private fun createDownload(
        infoHash: String = "hash123",
        title: String = "Test Torrent",
        state: String = TorrentDownloadService.STATE_DOWNLOADING,
        progressPercent: Int = 0,
        mediaPath: String? = null
    ): TorrentDownload = TorrentDownload(
        infoHash = infoHash,
        magnetUri = "magnet:?xt=urn:btih:$infoHash",
        title = title,
        state = state,
        progressPercent = progressPercent,
        mediaPath = mediaPath,
        castWhenReady = false,
        updatedAtMillis = System.currentTimeMillis()
    )

    @Test
    fun `resolveVisualState returns START_DOWNLOAD when torrent has no download or buffer`() {
        val state = TorrentCardActionHandler.resolveVisualState(
            releaseHash = "hash123",
            releaseSeeds = 20,
            releasePeers = 10,
            download = null,
            activeBuffer = null,
            mediaController = null
        )

        assertThat(state.action).isEqualTo(TorrentCardAction.START_DOWNLOAD)
        assertThat(state.isActive).isFalse()
        assertThat(state.isReady).isFalse()
        assertThat(state.readyProgress).isEqualTo(0f)
        assertThat(state.seeds).isEqualTo(20)
        assertThat(state.peers).isEqualTo(10)
    }

    @Test
    fun `resolveVisualState returns PAUSE_DOWNLOAD when download is actively downloading`() {
        val download = createDownload(
            state = TorrentDownloadService.STATE_DOWNLOADING,
            progressPercent = 45
        )

        val state = TorrentCardActionHandler.resolveVisualState(
            releaseHash = "hash123",
            download = download,
            activeBuffer = null,
            mediaController = null
        )

        assertThat(state.action).isEqualTo(TorrentCardAction.PAUSE_DOWNLOAD)
        assertThat(state.isActive).isTrue()
        assertThat(state.readyProgress).isEqualTo(0.45f)
    }

    @Test
    fun `resolveVisualState returns RESUME_DOWNLOAD when download is paused`() {
        val download = createDownload(
            state = TorrentDownloadService.STATE_PAUSED,
            progressPercent = 30
        )

        val state = TorrentCardActionHandler.resolveVisualState(
            releaseHash = "hash123",
            download = download,
            activeBuffer = null,
            mediaController = null
        )

        assertThat(state.action).isEqualTo(TorrentCardAction.RESUME_DOWNLOAD)
        assertThat(state.isReady).isFalse()
        assertThat(state.readyProgress).isEqualTo(0.30f)
    }

    @Test
    fun `resolveVisualState returns PLAY_MEDIA when torrent is 100 percent ready and not playing`() {
        val download = createDownload(
            state = TorrentDownloadService.STATE_COMPLETED,
            progressPercent = 100
        )

        val state = TorrentCardActionHandler.resolveVisualState(
            releaseHash = "hash123",
            download = download,
            activeBuffer = null,
            mediaController = null
        )

        assertThat(state.action).isEqualTo(TorrentCardAction.PLAY_MEDIA)
        assertThat(state.isReady).isTrue()
        assertThat(state.isDownloaded).isTrue()
        assertThat(state.readyProgress).isEqualTo(1.0f)
    }

    @Test
    fun `resolveVisualState returns PAUSE_MEDIA when torrent is currently playing in mediaController`() {
        val download = createDownload(
            state = TorrentDownloadService.STATE_COMPLETED,
            progressPercent = 100
        )

        val mediaController = mockk<MediaController>()
        val mediaItem = MediaItem.Builder().setMediaId("hash123").build()

        every { mediaController.isPlaying } returns true
        every { mediaController.currentMediaItem } returns mediaItem

        val state = TorrentCardActionHandler.resolveVisualState(
            releaseHash = "hash123",
            download = download,
            activeBuffer = null,
            mediaController = mediaController
        )

        assertThat(state.action).isEqualTo(TorrentCardAction.PAUSE_MEDIA)
        assertThat(state.isPlaying).isTrue()
        assertThat(state.isReady).isTrue()
    }

    @Test
    fun `resolveVisualState reflects activeBuffer state and progress when matching hash`() {
        val buffer = TorrentBufferState(
            infoHash = "hash123",
            title = "Streaming Movie",
            state = TorrentDownloadService.STATE_BUFFERING,
            readyProgress = 0.85f,
            totalProgress = 0.40f,
            downloadSpeedBytes = 2_500_000L,
            numSeeds = 55,
            numPeers = 30
        )

        val state = TorrentCardActionHandler.resolveVisualState(
            releaseHash = "hash123",
            download = null,
            activeBuffer = buffer,
            mediaController = null
        )

        assertThat(state.isActive).isTrue()
        assertThat(state.readyProgress).isEqualTo(0.85f)
        assertThat(state.downloadSpeedBytes).isEqualTo(2_500_000L)
        assertThat(state.seeds).isEqualTo(55)
        assertThat(state.peers).isEqualTo(30)
        assertThat(state.action).isEqualTo(TorrentCardAction.PAUSE_DOWNLOAD)
    }

    @Test
    fun `shouldDebounce throttles rapid calls within minimum interval`() {
        TorrentCardActionHandler.resetDebounceForTesting()
        // First call with interval 500ms
        val firstCallDebounced = TorrentCardActionHandler.shouldDebounce(minIntervalMs = 500L)
        // Immediate second call should be debounced
        val secondCallDebounced = TorrentCardActionHandler.shouldDebounce(minIntervalMs = 500L)

        assertThat(firstCallDebounced).isFalse()
        assertThat(secondCallDebounced).isTrue()
    }

    @Test
    fun `performCardClick invokes onDownloadStarted only when TorrentLaunchResult Started is returned`() {
        TorrentCardActionHandler.resetDebounceForTesting()
        val context = mockk<Context>(relaxed = true)
        mockkObject(TorrentLaunchHelper)
        try {
            every {
                TorrentLaunchHelper.launch(
                    context = any(),
                    mode = any(),
                    magnetUri = any(),
                    title = any(),
                    nextEpisodeInfo = any(),
                    seriesId = any(),
                    seasonNumber = any(),
                    episodeNumber = any(),
                    movieId = any(),
                    posterUrl = any(),
                    requiredSizeBytes = any()
                )
            } returns TorrentLaunchResult.Started

            var startedCount = 0
            val started = TorrentCardActionHandler.performCardClick(
                context = context,
                releaseHash = "hash123",
                releaseTitle = "Test Movie",
                releaseMagnetUri = "magnet:?xt=urn:btih:hash123",
                releaseSizeBytes = 1000L,
                download = null,
                activeBuffer = null,
                mediaController = null,
                isTv = false,
                torrentHandlingMode = TorrentHandlingMode.LOCAL_PLAYBACK,
                onDownloadStarted = { startedCount++ }
            )

            assertThat(started).isTrue()
            assertThat(startedCount).isEqualTo(1)
        } finally {
            unmockkObject(TorrentLaunchHelper)
        }
    }

    @Test
    fun `performCardClick does not invoke onDownloadStarted on InsufficientStorage`() {
        TorrentCardActionHandler.resetDebounceForTesting()
        val context = mockk<Context>(relaxed = true)
        mockkObject(TorrentLaunchHelper)
        try {
            every {
                TorrentLaunchHelper.launch(
                    context = any(),
                    mode = any(),
                    magnetUri = any(),
                    title = any(),
                    nextEpisodeInfo = any(),
                    seriesId = any(),
                    seasonNumber = any(),
                    episodeNumber = any(),
                    movieId = any(),
                    posterUrl = any(),
                    requiredSizeBytes = any()
                )
            } returns TorrentLaunchResult.InsufficientStorage(requiredBytes = 2000L, availableBytes = 500L)

            var startedCount = 0
            var reportedAvailable = 0L
            var reportedRequired = 0L

            val started = TorrentCardActionHandler.performCardClick(
                context = context,
                releaseHash = "hash123",
                releaseTitle = "Test Movie",
                releaseMagnetUri = "magnet:?xt=urn:btih:hash123",
                releaseSizeBytes = 2000L,
                download = null,
                activeBuffer = null,
                mediaController = null,
                isTv = false,
                torrentHandlingMode = TorrentHandlingMode.LOCAL_PLAYBACK,
                onInsufficientStorage = { avail, req ->
                    reportedAvailable = avail
                    reportedRequired = req
                },
                onDownloadStarted = { startedCount++ }
            )

            assertThat(started).isFalse()
            assertThat(startedCount).isEqualTo(0)
            assertThat(reportedAvailable).isEqualTo(500L)
            assertThat(reportedRequired).isEqualTo(2000L)
        } finally {
            unmockkObject(TorrentLaunchHelper)
        }
    }

    @Test
    fun `performCardClick does not invoke onDownloadStarted on debounced click or pause and resume actions`() {
        TorrentCardActionHandler.resetDebounceForTesting()
        val context = mockk<Context>(relaxed = true)
        mockkObject(TorrentLaunchHelper)
        try {
            every {
                TorrentLaunchHelper.launch(
                    context = any(),
                    mode = any(),
                    magnetUri = any(),
                    title = any(),
                    nextEpisodeInfo = any(),
                    seriesId = any(),
                    seasonNumber = any(),
                    episodeNumber = any(),
                    movieId = any(),
                    posterUrl = any(),
                    requiredSizeBytes = any()
                )
            } returns TorrentLaunchResult.Started

            var startedCount = 0

            // First call succeeds and sets debounce timestamp
            val firstStarted = TorrentCardActionHandler.performCardClick(
                context = context,
                releaseHash = "hash123",
                releaseTitle = "Test Movie",
                releaseMagnetUri = "magnet:?xt=urn:btih:hash123",
                releaseSizeBytes = 1000L,
                download = null,
                activeBuffer = null,
                mediaController = null,
                isTv = false,
                torrentHandlingMode = TorrentHandlingMode.LOCAL_PLAYBACK,
                onDownloadStarted = { startedCount++ }
            )
            assertThat(firstStarted).isTrue()
            assertThat(startedCount).isEqualTo(1)

            // Immediate second click is debounced
            val debouncedStarted = TorrentCardActionHandler.performCardClick(
                context = context,
                releaseHash = "hash123",
                releaseTitle = "Test Movie",
                releaseMagnetUri = "magnet:?xt=urn:btih:hash123",
                releaseSizeBytes = 1000L,
                download = null,
                activeBuffer = null,
                mediaController = null,
                isTv = false,
                torrentHandlingMode = TorrentHandlingMode.LOCAL_PLAYBACK,
                onDownloadStarted = { startedCount++ }
            )
            assertThat(debouncedStarted).isFalse()
            assertThat(startedCount).isEqualTo(1)

            // Reset debounce and test PAUSE_DOWNLOAD and RESUME_DOWNLOAD
            TorrentCardActionHandler.resetDebounceForTesting()
            val pauseStarted = TorrentCardActionHandler.performCardClick(
                context = context,
                releaseHash = "hash123",
                releaseTitle = "Test Movie",
                releaseMagnetUri = "magnet:?xt=urn:btih:hash123",
                releaseSizeBytes = 1000L,
                download = createDownload(state = TorrentDownloadService.STATE_DOWNLOADING, progressPercent = 50),
                activeBuffer = null,
                mediaController = null,
                isTv = false,
                torrentHandlingMode = TorrentHandlingMode.LOCAL_PLAYBACK,
                onDownloadStarted = { startedCount++ }
            )
            assertThat(pauseStarted).isFalse()
            assertThat(startedCount).isEqualTo(1)

            TorrentCardActionHandler.resetDebounceForTesting()
            val resumeStarted = TorrentCardActionHandler.performCardClick(
                context = context,
                releaseHash = "hash123",
                releaseTitle = "Test Movie",
                releaseMagnetUri = "magnet:?xt=urn:btih:hash123",
                releaseSizeBytes = 1000L,
                download = createDownload(state = TorrentDownloadService.STATE_PAUSED, progressPercent = 50),
                activeBuffer = null,
                mediaController = null,
                isTv = false,
                torrentHandlingMode = TorrentHandlingMode.LOCAL_PLAYBACK,
                onDownloadStarted = { startedCount++ }
            )
            assertThat(resumeStarted).isFalse()
            assertThat(startedCount).isEqualTo(1)
            verify(exactly = 2) { context.startService(any()) }
        } finally {
            unmockkObject(TorrentLaunchHelper)
        }
    }
}
