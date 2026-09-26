package com.martinrevert.latorrentola.utils

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Returns whether this context is running on a television-style device.
 *
 * Uses the modern UI mode service with Leanback feature checks as a fallback.
 */
fun Context.isTvDevice(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
        return true
    }
    
    // Fallback to feature check for older devices or specific implementations
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature("android.hardware.type.television")
}
