package com.martinrevert.latorrentola.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

object IntentAppsFinder {
    fun hasAppsForMagnet(context: Context, magnetUri: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(magnetUri)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val resolveInfo = context.packageManager.queryIntentActivities(intent, 0)
        return resolveInfo.isNotEmpty()
    }
}
