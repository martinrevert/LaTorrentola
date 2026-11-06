package com.martinrevert.latorrentola.network

import android.content.Context
import com.martinrevert.latorrentola.utils.PreferenceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class FirebaseMessagingInitializerTest {

    private val context: Context = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private val fcmRepository: FcmRepository = mockk(relaxed = true)

    private lateinit var initializer: FirebaseMessagingInitializer

    @Before
    fun setUp() {
        initializer = FirebaseMessagingInitializer(
            context = context,
            preferenceManager = preferenceManager,
            fcmRepository = fcmRepository
        )
    }

    @Test
    fun `subscribeToDefaultTopic checks push preference when disabled`() {
        every { preferenceManager.isPushEnabled() } returns false

        initializer.subscribeToDefaultTopic()

        verify(exactly = 0) { preferenceManager.setFcmTopicSubscribed(any()) }
    }

    @Test
    fun `unsubscribeFromDefaultTopic does not crash when unauthenticated`() {
        every { preferenceManager.isPushEnabled() } returns true

        // Verify initializer instance handles topic state calls cleanly
        initializer.unsubscribeFromDefaultTopic()
    }
}
