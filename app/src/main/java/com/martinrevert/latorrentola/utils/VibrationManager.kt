package com.martinrevert.latorrentola.utils

import android.content.Context
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.os.VibrationEffect
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides waveform haptic feedback using the vibrator API available on the device.
 *
 * @property context Application context used to retrieve the vibrator service.
 */
@Singleton
class VibrationManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** Lazily resolves the platform vibrator service, if available. */
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Plays [pattern] once without repeating it. */
    fun vibrate(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }
}
