package com.martinrevert.latorrentola.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = mockk(relaxed = true)
    private lateinit var voiceManager: VoiceManager

    @Before
    fun setUp() {
        voiceManager = VoiceManager(context)
    }

    @Test
    fun `speak before onInit queues pending text without throwing`() = runTest {
        voiceManager.speak("Hello World", Locale.US)
    }

    @Test
    fun `speak before onInit preserves custom locale upon initialization`() = runTest {
        voiceManager.speak("Hola Mundo", Locale.forLanguageTag("es-ES"))
        voiceManager.onInit(TextToSpeech.SUCCESS)
        advanceUntilIdle()
    }

    @Test
    fun `stop before onInit cancels pending speech request`() = runTest {
        voiceManager.speak("Will be cancelled", Locale.US)
        voiceManager.stop()
        voiceManager.onInit(TextToSpeech.SUCCESS)
        advanceUntilIdle()
    }

    @Test
    fun `onInit with SUCCESS sets initialized state and processes queued speech`() = runTest {
        voiceManager.speak("Queued Speech", Locale.US)
        voiceManager.onInit(TextToSpeech.SUCCESS)
        advanceUntilIdle()
    }

    @Test
    fun `onInit with ERROR does not initialize engine`() = runTest {
        voiceManager.onInit(TextToSpeech.ERROR)
        advanceUntilIdle()
    }

    @Test
    fun `stop and shutdown complete without exceptions`() = runTest {
        voiceManager.stop()
        advanceUntilIdle()
        voiceManager.shutdown()
    }
}
