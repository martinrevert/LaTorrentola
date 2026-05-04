package com.martinrevert.latorrentola.utils

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            // Pre-warm the engine with English as a safe baseline
            tts?.setLanguage(Locale.US)
            pendingText?.let {
                speak(it, Locale.US)
                pendingText = null
            }
        }
    }

    /**
     * Speaks the provided text using a voice that matches the requested locale.
     * It bypasses the system default and explicitly searches for a native voice.
     */
    fun speak(text: String, locale: Locale = Locale.US) {
        if (isInitialized) {
            // Force a stop to clear any pending phonetic states from other languages
            tts?.stop()
            
            // 1. Explicitly set the language for the engine
            val availability = tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                // 2. Scan all available voices specifically for the target language (English or Spanish)
                val voices = tts?.voices
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
                        tts?.voice = targetVoice
                    }
                }
            }

            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "VOICE_ID_${System.currentTimeMillis()}")
            
            // Using QUEUE_FLUSH is essential to ensure the new language/accent is applied immediately
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID))
        } else {
            pendingText = text
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
