package com.martinrevert.latorrentola.service

import android.content.Context
import androidx.media3.cast.DefaultCastOptionsProvider
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.martinrevert.latorrentola.BuildConfig

/**
 * Custom Cast OptionsProvider for LaTorrentola.
 * Uses custom CAST_APP_ID registered in the Google Cast SDK Developer Console (e.g. for "LaTorrentola"),
 * or falls back to Google's standard Default Media Receiver if not configured.
 */
class AppCastOptionsProvider : OptionsProvider {

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun getCastOptions(context: Context): CastOptions {
        val configuredAppId = BuildConfig.CAST_APP_ID.trim()
        val effectiveAppId = if (configuredAppId.isNotEmpty()) {
            configuredAppId
        } else {
            DefaultCastOptionsProvider.APP_ID_DEFAULT_RECEIVER_WITH_DRM
        }

        return CastOptions.Builder()
            .setReceiverApplicationId(effectiveAppId)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
