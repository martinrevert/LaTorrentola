package com.martinrevert.latorrentola.service

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.SessionProvider

/** Configures Google Cast with the built-in receiver used for standard video playback. */
class AppCastOptionsProvider : OptionsProvider {

    /**
     * Returns options for the default Cast media receiver.
     *
     * @param context Application context supplied by the Cast SDK.
     * @return Cast configuration for its standard video receiver.
     */
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .build()

    /**
     * Uses the Cast SDK's standard session providers.
     *
     * @param context Application context supplied by the Cast SDK.
     * @return `null` to use only the SDK-provided session providers.
     */
    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
