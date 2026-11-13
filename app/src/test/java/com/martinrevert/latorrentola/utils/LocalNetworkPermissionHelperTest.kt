package com.martinrevert.latorrentola.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class LocalNetworkPermissionHelperTest {

    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(ContextCompat::class)
        mockkStatic(ActivityCompat::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(ContextCompat::class)
        unmockkStatic(ActivityCompat::class)
    }

    @Test
    fun `isLocalNetworkPermissionRequired is false below API 37 and true at API 37+`() {
        assertThat(LocalNetworkPermissionHelper.ANDROID_17_SDK_INT).isEqualTo(37)
        assertThat(LocalNetworkPermissionHelper.isLocalNetworkPermissionRequired(Build.VERSION_CODES.BAKLAVA)).isFalse()
        assertThat(LocalNetworkPermissionHelper.isLocalNetworkPermissionRequired(37)).isTrue()
        assertThat(LocalNetworkPermissionHelper.isLocalNetworkPermissionRequired(38)).isTrue()
    }

    @Test
    fun `shouldOpenAppSettings returns false below API 37 regardless of rationale flags`() {
        assertThat(
            LocalNetworkPermissionHelper.shouldOpenAppSettings(
                hasRequestedBefore = true,
                shouldShowRationale = false,
                sdkInt = Build.VERSION_CODES.BAKLAVA
            )
        ).isFalse()
    }

    @Test
    fun `shouldOpenAppSettings distinguishes first-time request, rationale, and permanent denial on API 37`() {
        assertThat(
            LocalNetworkPermissionHelper.shouldOpenAppSettings(
                hasRequestedBefore = false,
                shouldShowRationale = false,
                sdkInt = 37
            )
        ).isFalse()

        assertThat(
            LocalNetworkPermissionHelper.shouldOpenAppSettings(
                hasRequestedBefore = true,
                shouldShowRationale = true,
                sdkInt = 37
            )
        ).isFalse()

        assertThat(
            LocalNetworkPermissionHelper.shouldOpenAppSettings(
                hasRequestedBefore = true,
                shouldShowRationale = false,
                sdkInt = 37
            )
        ).isTrue()
    }

    @Test
    fun `resolveDiscoveryCallbackFlags returns 0 when permission is missing`() {
        assertThat(
            LocalNetworkPermissionHelper.resolveDiscoveryCallbackFlags(
                hasPermission = false,
                activeScan = false
            )
        ).isEqualTo(0)

        assertThat(
            LocalNetworkPermissionHelper.resolveDiscoveryCallbackFlags(
                hasPermission = false,
                activeScan = true
            )
        ).isEqualTo(0)
    }

    @Test
    fun `resolveDiscoveryCallbackFlags enables passive or active scan flags when granted`() {
        assertThat(
            LocalNetworkPermissionHelper.resolveDiscoveryCallbackFlags(
                hasPermission = true,
                activeScan = false
            )
        ).isEqualTo(MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)

        assertThat(
            LocalNetworkPermissionHelper.resolveDiscoveryCallbackFlags(
                hasPermission = true,
                activeScan = true
            )
        ).isEqualTo(
            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
        )
    }

    @Test
    fun `resolveCastReceiverAppId uses configured app id when present and falls back to default receiver`() {
        assertThat(LocalNetworkPermissionHelper.resolveCastReceiverAppId("  ABCD1234  "))
            .isEqualTo("ABCD1234")
        assertThat(LocalNetworkPermissionHelper.resolveCastReceiverAppId("   "))
            .isEqualTo(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
    }

    @Test
    fun `buildAppSettingsIntentSpecs returns ordered settings fallbacks with app package`() {
        val specs = LocalNetworkPermissionHelper.buildAppSettingsIntentSpecs("com.martinrevert.latorrentola")

        assertThat(specs).hasSize(3)
        assertThat(specs[0]).isEqualTo(
            LocalNetworkPermissionHelper.SettingsIntentSpec(
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                packageName = "com.martinrevert.latorrentola"
            )
        )
        assertThat(specs[1]).isEqualTo(
            LocalNetworkPermissionHelper.SettingsIntentSpec(
                action = Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
                packageName = null
            )
        )
        assertThat(specs[2]).isEqualTo(
            LocalNetworkPermissionHelper.SettingsIntentSpec(
                action = Settings.ACTION_SETTINGS,
                packageName = null
            )
        )
    }

    @Test
    fun `openAppSettings launches first available settings intent with NEW_TASK flag`() {
        every { context.packageName } returns "com.martinrevert.latorrentola"
        val createdIntents = mutableListOf<Pair<LocalNetworkPermissionHelper.SettingsIntentSpec, Intent>>()

        LocalNetworkPermissionHelper.openAppSettings(context) { spec ->
            mockk<Intent>(relaxed = true).also { intent ->
                createdIntents += spec to intent
            }
        }

        assertThat(createdIntents).hasSize(3)
        val (firstSpec, firstIntent) = createdIntents.first()
        assertThat(firstSpec.action).isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertThat(firstSpec.packageName).isEqualTo("com.martinrevert.latorrentola")
        verify(exactly = 1) { firstIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        verify(exactly = 1) { context.startActivity(firstIntent) }
    }

    @Test
    fun `openAppSettings falls back to secondary settings intent when first throws ActivityNotFoundException`() {
        every { context.packageName } returns "com.martinrevert.latorrentola"
        val intentsByAction = mutableMapOf<String, Intent>()

        every { context.startActivity(any()) } answers {
            val intent = firstArg<Intent>()
            if (intent === intentsByAction[Settings.ACTION_APPLICATION_DETAILS_SETTINGS]) {
                throw android.content.ActivityNotFoundException("not found")
            }
        }

        LocalNetworkPermissionHelper.openAppSettings(context) { spec ->
            mockk<Intent>(relaxed = true).also { intent ->
                intentsByAction[spec.action] = intent
            }
        }

        val secondIntent = intentsByAction.getValue(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
        val thirdIntent = intentsByAction.getValue(Settings.ACTION_SETTINGS)
        verify(exactly = 1) { context.startActivity(secondIntent) }
        verify(exactly = 0) { context.startActivity(thirdIntent) }
    }

    @Test
    fun `hasLocalNetworkPermission respects runtime check on API 37 or returns true on older SDKs`() {
        every {
            ContextCompat.checkSelfPermission(context, LocalNetworkPermissionHelper.PERMISSION_ACCESS_LOCAL_NETWORK)
        } returns PackageManager.PERMISSION_DENIED

        val expected = !LocalNetworkPermissionHelper.isLocalNetworkPermissionRequired()
        assertThat(LocalNetworkPermissionHelper.hasLocalNetworkPermission(context)).isEqualTo(expected)
    }

    @Test
    fun `hasLocalNetworkPermission checks runtime permission at API 37 and returns true below API 37`() {
        every {
            ContextCompat.checkSelfPermission(context, LocalNetworkPermissionHelper.PERMISSION_ACCESS_LOCAL_NETWORK)
        } returns PackageManager.PERMISSION_DENIED

        assertThat(
            LocalNetworkPermissionHelper.hasLocalNetworkPermission(
                context = context,
                sdkInt = Build.VERSION_CODES.BAKLAVA
            )
        ).isTrue()
        assertThat(
            LocalNetworkPermissionHelper.hasLocalNetworkPermission(
                context = context,
                sdkInt = 37
            )
        ).isFalse()

        every {
            ContextCompat.checkSelfPermission(context, LocalNetworkPermissionHelper.PERMISSION_ACCESS_LOCAL_NETWORK)
        } returns PackageManager.PERMISSION_GRANTED

        assertThat(
            LocalNetworkPermissionHelper.hasLocalNetworkPermission(
                context = context,
                sdkInt = 37
            )
        ).isTrue()
    }

    @Test
    fun `shouldShowRationale unwraps ContextWrapper to Activity on API 37 and returns false below API 37 or for non-Activity`() {
        val activity = mockk<Activity>(relaxed = true)
        val innerWrapper = mockk<ContextWrapper>(relaxed = true) {
            every { baseContext } returns activity
        }
        val wrapped = mockk<ContextWrapper>(relaxed = true) {
            every { baseContext } returns innerWrapper
        }
        val nonActivity = mockk<ContextWrapper>(relaxed = true) {
            every { baseContext } returns context
        }

        every {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                LocalNetworkPermissionHelper.PERMISSION_ACCESS_LOCAL_NETWORK
            )
        } returns true

        assertThat(
            LocalNetworkPermissionHelper.shouldShowRationale(
                context = wrapped,
                sdkInt = Build.VERSION_CODES.BAKLAVA
            )
        ).isFalse()
        assertThat(
            LocalNetworkPermissionHelper.shouldShowRationale(
                context = nonActivity,
                sdkInt = 37
            )
        ).isFalse()
        assertThat(
            LocalNetworkPermissionHelper.shouldShowRationale(
                context = wrapped,
                sdkInt = 37
            )
        ).isTrue()

        every {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                LocalNetworkPermissionHelper.PERMISSION_ACCESS_LOCAL_NETWORK
            )
        } returns false

        assertThat(
            LocalNetworkPermissionHelper.shouldShowRationale(
                context = wrapped,
                sdkInt = 37
            )
        ).isFalse()
    }

    @Test
    fun `shouldOpenAppSettings returns false after grant clears hasRequestedBefore even if permission is later revoked`() {
        assertThat(
            LocalNetworkPermissionHelper.shouldOpenAppSettings(
                hasRequestedBefore = false,
                shouldShowRationale = false,
                sdkInt = 37
            )
        ).isFalse()
    }
}
