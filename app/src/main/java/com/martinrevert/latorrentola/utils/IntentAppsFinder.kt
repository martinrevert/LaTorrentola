package com.martinrevert.latorrentola.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri

/** Helpers for detecting applications that can handle external intents. */
object IntentAppsFinder {
    /** Returns whether any installed application can open the supplied magnet URI. */
    fun hasAppsForMagnet(context: Context, magnetUri: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = magnetUri.toUri()
        }
        val resolveInfo = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolveInfo.isNotEmpty()
    }
}
