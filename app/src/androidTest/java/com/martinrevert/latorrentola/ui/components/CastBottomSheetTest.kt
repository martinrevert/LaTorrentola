package com.martinrevert.latorrentola.ui.components

import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.utils.LocalNetworkPermissionHelper
import com.martinrevert.latorrentola.utils.PreferenceManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CastBottomSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        unmockkObject(LocalNetworkPermissionHelper)
    }

    @Test
    fun castBottomSheetContent_permissionDenied_showsAllowAccessAndInvokesCallback() {
        var grantClicked = false

        composeTestRule.setContent {
            LaTorrentolaTheme {
                CastBottomSheetContent(
                    isTv = false,
                    hasLocalNetworkPermission = false,
                    shouldOpenAppSettings = false,
                    showRescanHint = false,
                    isCasting = false,
                    selectedRoute = null,
                    routes = emptyList(),
                    mediaTitle = "",
                    mediaSubtitle = "",
                    posterUrl = null,
                    hasPlayerControls = false,
                    isPlayerPlaying = false,
                    onTogglePlayPause = {},
                    onSetVolume = {},
                    onStopStreaming = {},
                    onSelectRoute = {},
                    onRequestLocalNetworkPermission = { grantClicked = true },
                    onRescanDevices = {}
                )
            }
        }

        val allowLabel = appContext.getString(R.string.local_network_permission_grant_button)
        composeTestRule.onNodeWithText(allowLabel).assertIsDisplayed().performClick()
        assertTrue(grantClicked)
    }

    @Test
    fun castBottomSheetContent_emptyRoutes_hidesSpinnerWhenRescanHintVisible() {
        composeTestRule.setContent {
            LaTorrentolaTheme {
                CastBottomSheetContent(
                    isTv = false,
                    hasLocalNetworkPermission = true,
                    shouldOpenAppSettings = false,
                    showRescanHint = true,
                    isCasting = false,
                    selectedRoute = null,
                    routes = emptyList(),
                    mediaTitle = "",
                    mediaSubtitle = "",
                    posterUrl = null,
                    hasPlayerControls = false,
                    isPlayerPlaying = false,
                    onTogglePlayPause = {},
                    onSetVolume = {},
                    onStopStreaming = {},
                    onSelectRoute = {},
                    onRequestLocalNetworkPermission = {},
                    onRescanDevices = {}
                )
            }
        }

        val noDevicesHint = appContext.getString(R.string.cast_no_devices_found_hint)
        val rescanLabel = appContext.getString(R.string.cast_rescan_devices)
        composeTestRule.onNodeWithText(noDevicesHint).assertIsDisplayed()
        composeTestRule.onNodeWithText(rescanLabel).assertIsDisplayed()
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
    }

    @Test
    fun castBottomSheet_secondDenialUpdatesButtonToOpenSettings() {
        val preferenceManager = PreferenceManager(appContext)
        preferenceManager.setLocalNetworkPermissionRequested(false)

        var rationale = false
        var denialStep = 0
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                rationale = denialStep++ == 0
                dispatchResult(requestCode, false)
            }
        }
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }

        mockkObject(LocalNetworkPermissionHelper)
        every { LocalNetworkPermissionHelper.hasLocalNetworkPermission(any(), any()) } returns false
        every { LocalNetworkPermissionHelper.shouldShowRationale(any(), any()) } answers { rationale }
        every { LocalNetworkPermissionHelper.shouldOpenAppSettings(any(), any(), any()) } answers {
            val requested = firstArg<Boolean>()
            val showRationale = secondArg<Boolean>()
            requested && !showRationale
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                CastBottomSheet(onDismissRequest = {})
            }
        }

        val allowLabel = appContext.getString(R.string.local_network_permission_grant_button)
        val settingsLabel = appContext.getString(R.string.local_network_permission_settings_button)

        // First denial (rationale = true): stays Allow Access
        composeTestRule.onNodeWithText(allowLabel).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(preferenceManager.getLocalNetworkPermissionRequested())
        composeTestRule.onNodeWithText(allowLabel).assertIsDisplayed()

        // Second denial (rationale = false): transitions immediately to Open Settings
        composeTestRule.onNodeWithText(allowLabel).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(settingsLabel).assertIsDisplayed()
    }

    @Test
    fun castBottomSheet_grantingPermissionClearsRequestedFlag() {
        val preferenceManager = PreferenceManager(appContext)
        preferenceManager.setLocalNetworkPermissionRequested(true)

        var granted = false
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                granted = true
                dispatchResult(requestCode, true)
            }
        }
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }

        mockkObject(LocalNetworkPermissionHelper)
        every { LocalNetworkPermissionHelper.hasLocalNetworkPermission(any(), any()) } answers { granted }
        every { LocalNetworkPermissionHelper.shouldShowRationale(any(), any()) } returns true
        every { LocalNetworkPermissionHelper.shouldOpenAppSettings(any(), any(), any()) } answers {
            val requested = firstArg<Boolean>()
            val showRationale = secondArg<Boolean>()
            requested && !showRationale
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                CastBottomSheet(onDismissRequest = {})
            }
        }

        val allowLabel = appContext.getString(R.string.local_network_permission_grant_button)
        composeTestRule.onNodeWithText(allowLabel).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertFalse(preferenceManager.getLocalNetworkPermissionRequested())
    }
}
