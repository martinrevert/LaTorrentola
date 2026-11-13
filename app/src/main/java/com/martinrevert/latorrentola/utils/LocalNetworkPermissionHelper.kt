package com.martinrevert.latorrentola.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.martinrevert.latorrentola.BuildConfig

/**
 * Centralizes Android 17 (API 37) `ACCESS_LOCAL_NETWORK` permission detection,
 * MediaRouter2 discovery gating (`Issue #581018323` mitigation), and App Settings fallback intents.
 */
object LocalNetworkPermissionHelper {

    /** Android 17 ("Cinnamon Bun") API level introducing runtime Local Network access enforcement. */
    const val ANDROID_17_SDK_INT = Build.VERSION_CODES.CINNAMON_BUN

    /** Runtime permission required on Android 17+ for LAN mDNS/SSDP/Cast and SMB/NFS traffic. */
    @SuppressLint("InlinedApi")
    const val PERMISSION_ACCESS_LOCAL_NETWORK = Manifest.permission.ACCESS_LOCAL_NETWORK

    /** Returns `true` when running on Android 17 (API 37) or newer. */
    fun isLocalNetworkPermissionRequired(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt >= ANDROID_17_SDK_INT

    /**
     * Returns `true` when local network access is available to the app:
     * always `true` prior to Android 17, or when [PERMISSION_ACCESS_LOCAL_NETWORK] is granted on API 37+.
     */
    fun hasLocalNetworkPermission(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        if (!isLocalNetworkPermissionRequired(sdkInt)) return true
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_ACCESS_LOCAL_NETWORK
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns `true` when the system will no longer display the runtime permission dialog
     * and the user should be directed to App Settings instead.
     */
    fun shouldOpenAppSettings(
        hasRequestedBefore: Boolean,
        shouldShowRationale: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        if (!isLocalNetworkPermissionRequired(sdkInt)) return false
        return hasRequestedBefore && !shouldShowRationale
    }

    /** Checks whether Android recommends showing a permission rationale for [PERMISSION_ACCESS_LOCAL_NETWORK]. */
    fun shouldShowRationale(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        if (!isLocalNetworkPermissionRequired(sdkInt)) return false
        val activity = findActivity(context) ?: return false
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            PERMISSION_ACCESS_LOCAL_NETWORK
        )
    }

    /**
     * Computes `MediaRouter.addCallback` flags.
     * Returns `0` when permission is missing so callers never register MediaRouter2 callbacks prematurely
     * (prevents Android 17 Issue #581018323 stale empty-route lock).
     */
    fun resolveDiscoveryCallbackFlags(
        hasPermission: Boolean,
        activeScan: Boolean
    ): Int {
        if (!hasPermission) return 0
        return if (activeScan) {
            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
        } else {
            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
        }
    }

    /** Resolves the Cast Receiver Application ID, falling back to the default media receiver when blank. */
    fun resolveCastReceiverAppId(configuredAppId: String = BuildConfig.CAST_APP_ID): String =
        configuredAppId.trim().ifEmpty { CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID }

    /** Builds the [MediaRouteSelector] used by Cast discovery sheets and observers. */
    fun buildCastMediaRouteSelector(configuredAppId: String = BuildConfig.CAST_APP_ID): MediaRouteSelector =
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(resolveCastReceiverAppId(configuredAppId)))
            .build()

    /** Declarative specification for fallback system Settings intents (JVM-testable without Robolectric). */
    data class SettingsIntentSpec(
        val action: String,
        val packageName: String? = null
    )

    /** Builds ordered fallback specifications to open the app's permission settings page. */
    fun buildAppSettingsIntentSpecs(packageName: String): List<SettingsIntentSpec> = listOf(
        SettingsIntentSpec(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageName = packageName),
        SettingsIntentSpec(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
        SettingsIntentSpec(Settings.ACTION_SETTINGS)
    )

    /** Builds ordered fallback intents to open the app's permission settings page. */
    fun buildAppSettingsIntents(
        packageName: String,
        intentFactory: (SettingsIntentSpec) -> Intent = ::createSettingsIntent
    ): List<Intent> = buildAppSettingsIntentSpecs(packageName).map(intentFactory)

    /** Launches system App Settings so the user can manually grant Local Network access. */
    fun openAppSettings(
        context: Context,
        intentFactory: (SettingsIntentSpec) -> Intent = ::createSettingsIntent
    ) {
        for (intent in buildAppSettingsIntents(context.packageName, intentFactory)) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try next settings fallback
            } catch (_: SecurityException) {
                // Try next settings fallback
            }
        }
    }

    private fun createSettingsIntent(spec: SettingsIntentSpec): Intent =
        Intent(spec.action).apply {
            spec.packageName?.let { pkg ->
                data = Uri.fromParts("package", pkg, null)
            }
        }

    private fun findActivity(context: Context): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
