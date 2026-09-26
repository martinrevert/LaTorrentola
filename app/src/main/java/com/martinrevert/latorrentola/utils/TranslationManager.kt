package com.martinrevert.latorrentola.utils

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import javax.inject.Inject
import javax.inject.Singleton

/** Downloads and uses ML Kit's English-to-Spanish on-device translation model. */
@Singleton
class TranslationManager @Inject constructor() {

    /** Translation configuration shared by the translator instance. */
    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.SPANISH)
        .build()

    /** ML Kit translator configured with [options]. */
    private val translator = Translation.getClient(options)

    /** Ensures the translation model is available on Wi-Fi, then translates [text]. */
    fun translate(text: String, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translatedText ->
                        onSuccess(translatedText)
                    }
                    .addOnFailureListener { exception ->
                        onError(exception)
                    }
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    /** Releases translator resources. */
    fun close() {
        translator.close()
    }
}
