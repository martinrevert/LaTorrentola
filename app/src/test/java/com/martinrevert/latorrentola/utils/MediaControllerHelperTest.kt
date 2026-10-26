package com.martinrevert.latorrentola.utils

import android.content.Context
import android.content.ServiceConnection
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.session.MediaController
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class MediaControllerHelperTest {

    @Before
    fun setUp() {
        mockkStatic(MediaController::class)
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(MediaController::class)
        unmockkStatic(Log::class)
    }

    @Test
    fun `SafeMediaControllerContext uses applicationContext if available`() {
        val appContext = mockk<Context>(relaxed = true)
        val baseContext = mockk<Context> {
            every { applicationContext } returns appContext
        }

        val safeContext = SafeMediaControllerContext(baseContext)
        assertSame(appContext, safeContext.targetContext)
    }

    @Test
    fun `SafeMediaControllerContext falls back to baseContext if applicationContext is null`() {
        val baseContext = mockk<Context>(relaxed = true) {
            every { applicationContext } returns null
        }

        val safeContext = SafeMediaControllerContext(baseContext)
        assertSame(baseContext, safeContext.targetContext)
    }

    @Test
    fun `SafeMediaControllerContext unbindService suppresses IllegalArgumentException for unregistered service`() {
        val baseContext = mockk<Context> {
            every { applicationContext } returns null
        }
        val connection = mockk<ServiceConnection>()
        every { baseContext.unbindService(connection) } throws IllegalArgumentException("Service not registered: mock")

        val safeContext = SafeMediaControllerContext(baseContext)

        // Must not throw IllegalArgumentException
        safeContext.unbindService(connection)

        verify(exactly = 1) { baseContext.unbindService(connection) }
    }

    @Test
    fun `SafeMediaControllerContext unbindService delegates successfully when no exception thrown`() {
        val baseContext = mockk<Context>(relaxed = true) {
            every { applicationContext } returns null
        }
        val connection = mockk<ServiceConnection>()

        val safeContext = SafeMediaControllerContext(baseContext)
        safeContext.unbindService(connection)

        verify(exactly = 1) { baseContext.unbindService(connection) }
    }

    @Test
    fun `MediaControllerHelper releaseFuture handles null safely`() {
        // Must not throw on null
        MediaControllerHelper.releaseFuture(null)
    }

    @Test
    fun `MediaControllerHelper releaseFuture catches any exception from MediaController releaseFuture`() {
        val future = mockk<ListenableFuture<MediaController>>()
        every { MediaController.releaseFuture(future) } throws RuntimeException("Async failure")

        // Must not propagate exception
        MediaControllerHelper.releaseFuture(future)

        verify(exactly = 1) { MediaController.releaseFuture(future) }
    }

    @Test
    fun `MediaControllerHelper createSafeContext returns SafeMediaControllerContext instance`() {
        val context = mockk<Context>(relaxed = true)
        val safeContext = MediaControllerHelper.createSafeContext(context)
        assertNotNull(safeContext)
    }
}
