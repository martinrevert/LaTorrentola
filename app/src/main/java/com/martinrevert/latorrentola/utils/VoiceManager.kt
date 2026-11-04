package com.martinrevert.latorrentola.utils

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages text-to-speech playback and queues a request until engine initialization completes.
 * Offloads all Binder IPC calls to a background thread to prevent UI thread hangs/ANRs on TV devices.
 *
 * @property context Application context used to create the speech engine.
 */
@Singleton
class VoiceManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Platform speech engine instance, when created. */
    private var tts: TextToSpeech? = null
    /** Whether the speech engine reported successful initialization. */
    @Volatile
    private var isInitialized = false
    /** Most recent speech request and locale received before initialization. */
    @Volatile
    private var pendingSpeech: Pair<String, Locale>? = null

    /** Creates the platform speech engine and registers this manager as its listener. */
    init {
        scope.launch {
            try {
                tts = TextToSpeech(context, this@VoiceManager)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to instantiate TextToSpeech engine", e)
            }
        }
    }

    /** Marks the engine ready and speaks any queued text using the requested locale off the main thread. */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            scope.launch {
                try {
                    // Pre-warm the engine off the main thread to avoid Binder IPC deadlock on the UI thread
                    tts?.language = Locale.US
                    isInitialized = true
                    val speech = pendingSpeech
                    if (speech != null) {
                        pendingSpeech = null
                        speakInternal(speech.first, speech.second)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Error in TextToSpeech onInit baseline setup", e)
                }
            }
        } else {
            Log.w(TAG, "TextToSpeech initialization failed with status: $status")
        }
    }

    /**
     * Speaks the provided text using a voice that matches the requested locale.
     * Offloads all Binder IPC calls to Dispatchers.IO to prevent freezing the UI thread.
     */
    fun speak(text: String, locale: Locale = Locale.US) {
        if (isInitialized) {
            scope.launch {
                speakInternal(text, locale)
            }
        } else {
            pendingSpeech = Pair(text, locale)
        }
    }

    private fun speakInternal(text: String, locale: Locale) {
        try {
            val engine = tts ?: return
            // Force a stop to clear any pending phonetic states from other languages
            try {
                engine.stop()
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to stop TTS engine before speak", e)
            }

            // 1. Explicitly set the language for the engine
            val availability = engine.setLanguage(locale)

            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                // 2. Scan all available voices specifically for the target language (English or Spanish).
                // Wrap in try-catch to protect against vendor TTS engines on TV devices that hang/fail on getVoices() IPC.
                try {
                    val voices = engine.voices
                    if (!voices.isNullOrEmpty()) {
                        // Filter for voices that belong to the requested language
                        val matchingVoices = voices.filter { it.locale.language == locale.language }

                        // Priority:
                        // - Best: Same language + Same country (e.g. es-ES or en-US) + Local
                        // - Mid: Same language + Local
                        // - Low: Same language (any)
                        val targetVoice = matchingVoices.find {
                            it.locale.country == locale.country && !it.isNetworkConnectionRequired
                        } ?: matchingVoices.find {
                            !it.isNetworkConnectionRequired
                        } ?: matchingVoices.firstOrNull()

                        if (targetVoice != null) {
                            engine.voice = targetVoice
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to query or set voice from engine, falling back to default language voice", e)
                }
            }

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "VOICE_ID_${System.currentTimeMillis()}")
            }

            // Using QUEUE_FLUSH is essential to ensure the new language/accent is applied immediately
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID))
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to speak text via TextToSpeech IPC", e)
        }
    }

    /** Stops any currently speaking utterance off the main thread and cancels pending speech. */
    fun stop() {
        pendingSpeech = null
        scope.launch {
            try {
                tts?.stop()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to stop TextToSpeech", e)
            }
        }
    }

    /** Shuts down the speech engine and releases its resources. */
    fun shutdown() {
        pendingSpeech = null
        try {
            tts?.shutdown()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to shutdown TextToSpeech", e)
        }
        scope.cancel()
    }

    companion object {
        private const val TAG = "VoiceManager"
    }
}

