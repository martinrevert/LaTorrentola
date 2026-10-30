package com.martinrevert.latorrentola.utils

import com.martinrevert.latorrentola.network.CrashlyticsManager
import io.mockk.mockk
import io.mockk.verify
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
