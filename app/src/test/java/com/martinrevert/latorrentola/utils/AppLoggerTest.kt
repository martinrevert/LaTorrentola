package com.martinrevert.latorrentola.utils

import com.martinrevert.latorrentola.network.CrashlyticsManager
import io.mockk.mockk
import io.mockk.verify
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class AppLoggerTest {

    private val crashlyticsManager: CrashlyticsManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        AppLogger.init(crashlyticsManager)
    }

    @After
    fun tearDown() {
        AppLogger.setCrashlyticsManagerForTesting(null)
    }

    @Test
    fun `i logs breadcrumb to CrashlyticsManager`() {
        AppLogger.i("TestTag", "Info message")
        verify { crashlyticsManager.log("[TestTag] Info message") }
    }

    @Test
    fun `w logs warning breadcrumb to CrashlyticsManager`() {
        AppLogger.w("TestTag", "Warning message")
        verify { crashlyticsManager.log("[TestTag] WARN: Warning message") }
    }

    @Test
    fun `w with throwable logs warning breadcrumb with exception message`() {
        val exception = RuntimeException("Boom")
        AppLogger.w("TestTag", "Warning with exception", exception)
        verify { crashlyticsManager.log("[TestTag] WARN: Warning with exception (Boom)") }
    }

    @Test
    fun `w with throwable lacking message logs class simple name instead of null`() {
        val exception = NullPointerException()
        AppLogger.w("TestTag", "Warning with null message exception", exception)
        verify { crashlyticsManager.log("[TestTag] WARN: Warning with null message exception (NullPointerException)") }
    }

    @Test
    fun `e delegates message and throwable to CrashlyticsManager logError`() {
        val exception = IllegalStateException("Critical crash")
        AppLogger.e("TestTag", "Critical error", exception)
        verify { crashlyticsManager.logError("TestTag", "Critical error", exception) }
    }

    @Test
    fun `e downgrades UnknownHostException to warning breadcrumb without recording non-fatal`() {
        val exception = UnknownHostException("Unable to resolve host \"torrentio.strem.fun\"")
        AppLogger.e("TorrentioRepository", "Error fetching streams", exception)
        verify(exactly = 0) { crashlyticsManager.logError(any(), any(), any()) }
        verify { crashlyticsManager.log(match { it.startsWith("[TorrentioRepository] WARN: Error fetching streams") }) }
    }

    @Test
    fun `e downgrades wrapped timeout and connect failures`() {
        AppLogger.e("Tag", "timeout", SocketTimeoutException("timeout"))
        AppLogger.e("Tag", "connect", ConnectException("refused"))
        AppLogger.e("Tag", "route", NoRouteToHostException("no route"))
        AppLogger.e("Tag", "wrapped", RuntimeException("outer", UnknownHostException("dns")))
        verify(exactly = 0) { crashlyticsManager.logError(any(), any(), any()) }
    }

    @Test
    fun `recordException downgrades UnknownHostException`() {
        AppLogger.recordException(UnknownHostException("dns"), "Tag", "msg")
        verify(exactly = 0) { crashlyticsManager.logError(any(), any(), any()) }
    }

    @Test
    fun `isTransientNetworkError is false for non-network and null throwables`() {
        assertFalse(AppLogger.isTransientNetworkError(null))
        assertFalse(AppLogger.isTransientNetworkError(IllegalStateException("boom")))
        assertTrue(AppLogger.isTransientNetworkError(UnknownHostException("dns")))
    }

    @Test
    fun `recordException with tag and message delegates to CrashlyticsManager logError`() {
        val exception = Exception("Failed task")
        AppLogger.recordException(exception, "TaskTag", "Task execution failed")
        verify { crashlyticsManager.logError("TaskTag", "Task execution failed", exception) }
    }

    @Test
    fun `recordException without tag or message uses default tag and exception message`() {
        val exception = Exception("Direct exception")
        AppLogger.recordException(exception)
        verify { crashlyticsManager.logError("AppLogger", "Direct exception", exception) }
    }

    @Test
    fun `recordException with tag only uses provided tag and exception message`() {
        val exception = Exception("Tag only exception")
        AppLogger.recordException(exception, tag = "CustomTag")
        verify { crashlyticsManager.logError("CustomTag", "Tag only exception", exception) }
    }

    @Test
    fun `recordException with message only uses default tag and provided message`() {
        val exception = Exception("Original message")
        AppLogger.recordException(exception, message = "Custom override message")
        verify { crashlyticsManager.logError("AppLogger", "Custom override message", exception) }
    }
}
