package com.martinrevert.latorrentola.network

import com.google.common.truth.Truth.assertThat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class CrashlyticsManagerTest {

    private lateinit var crashlyticsManager: CrashlyticsManager
    private val firebaseCrashlytics: FirebaseCrashlytics = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns firebaseCrashlytics
        crashlyticsManager = CrashlyticsManager()
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    @Test
    fun `setCollectionEnabled delegates to FirebaseCrashlytics`() {
        crashlyticsManager.setCollectionEnabled(true)
        verify { firebaseCrashlytics.setCrashlyticsCollectionEnabled(true) }

        crashlyticsManager.setCollectionEnabled(false)
        verify { firebaseCrashlytics.setCrashlyticsCollectionEnabled(false) }
    }

    @Test
    fun `setUserId delegates to FirebaseCrashlytics`() {
        crashlyticsManager.setUserId("user_123")
        verify { firebaseCrashlytics.setUserId("user_123") }

        crashlyticsManager.setUserId(null)
        verify { firebaseCrashlytics.setUserId("") }
    }

    @Test
    fun `setCustomKey delegates to FirebaseCrashlytics`() {
        crashlyticsManager.setCustomKey("key1", "val1")
        verify { firebaseCrashlytics.setCustomKey("key1", "val1") }
    }

    @Test
    fun `recordException delegates to FirebaseCrashlytics`() {
        val exception = RuntimeException("Test error")
        crashlyticsManager.recordException(exception)
        verify { firebaseCrashlytics.recordException(exception) }
    }

    @Test
    fun `log delegates to FirebaseCrashlytics`() {
        crashlyticsManager.log("Test breadcrumb")
        verify { firebaseCrashlytics.log("Test breadcrumb") }
    }

    @Test
    fun `triggerTestCrash throws RuntimeException with expected message`() {
        var thrown: Throwable? = null
        try {
            crashlyticsManager.triggerTestCrash()
        } catch (t: Throwable) {
            thrown = t
        }
        assertThat(thrown).isNotNull()
        assertThat(thrown).isInstanceOf(RuntimeException::class.java)
        assertThat(thrown?.message).contains("Crashlytics Test Crash")
    }
}
