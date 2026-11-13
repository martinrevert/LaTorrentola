package com.martinrevert.latorrentola.ui.media

import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.utils.LocalNetworkPermissionHelper
import com.martinrevert.latorrentola.utils.PreferenceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExternalMediaBrowserPermissionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        unmockkObject(LocalNetworkPermissionHelper)
    }

    @Test
    fun mobileLocalNetworkPermissionBanner_narrowWidth_stacksButtonBelowText() {
        composeTestRule.setContent {
            LaTorrentolaTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    MobileLocalNetworkPermissionBanner(
                        shouldOpenSettings = false,
                        onGrantClick = {}
                    )
                }
            }
        }

        val descText = appContext.getString(R.string.local_network_permission_network_desc)
        val buttonText = appContext.getString(R.string.local_network_permission_grant_button)

        val descNode = composeTestRule.onNodeWithText(descText).assertIsDisplayed()
        val buttonNode = composeTestRule.onNodeWithText(buttonText).assertIsDisplayed()

        val descBounds = descNode.getUnclippedBoundsInRoot()
        val buttonBounds = buttonNode.getUnclippedBoundsInRoot()

        assertTrue(
            "Action button should be stacked below banner description on narrow screens",
            buttonBounds.top >= descBounds.bottom
        )
        assertTrue(
            "Action button should fit within 320.dp width",
            buttonBounds.right <= 320.dp
        )
    }

    @Test
    fun externalMediaBrowserScreen_secondDenialUpdatesBannerToOpenSettings() {
        val preferenceManager = PreferenceManager(appContext)
        preferenceManager.setLocalNetworkPermissionRequested(false)

        val uiStateFlow = MutableStateFlow(ExternalMediaUiState(selectedTab = ExternalMediaTab.NETWORK))
        val eventsFlow = MutableSharedFlow<ExternalMediaEvent>()
        val viewModel = mockk<ExternalMediaBrowserViewModel>(relaxed = true)
        every { viewModel.uiState } returns uiStateFlow
        every { viewModel.events } returns eventsFlow

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
                ExternalMediaBrowserScreen(
                    viewModel = viewModel,
                    onBackClick = {}
                )
            }
        }

        val allowLabel = appContext.getString(R.string.local_network_permission_grant_button)
        val settingsLabel = appContext.getString(R.string.local_network_permission_settings_button)

        // First denial -> rationale = true, stays Allow Access
        composeTestRule.onNodeWithText(allowLabel).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(preferenceManager.getLocalNetworkPermissionRequested())
        composeTestRule.onNodeWithText(allowLabel).assertIsDisplayed()

        // Second denial -> rationale = false, transitions immediately to Open Settings
        composeTestRule.onNodeWithText(allowLabel).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(settingsLabel).assertIsDisplayed()
    }

    @Test
    fun externalMediaBrowserScreen_grantingPermissionClearsRequestedFlagAndScansNetwork() {
        val preferenceManager = PreferenceManager(appContext)
        preferenceManager.setLocalNetworkPermissionRequested(true)

        val uiStateFlow = MutableStateFlow(ExternalMediaUiState(selectedTab = ExternalMediaTab.NETWORK))
        val eventsFlow = MutableSharedFlow<ExternalMediaEvent>()
        val viewModel = mockk<ExternalMediaBrowserViewModel>(relaxed = true)
        every { viewModel.uiState } returns uiStateFlow
        every { viewModel.events } returns eventsFlow

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
                ExternalMediaBrowserScreen(
                    viewModel = viewModel,
                    onBackClick = {}
                )
            }
        }

        val allowLabel = appContext.getString(R.string.local_network_permission_grant_button)
        composeTestRule.onNodeWithText(allowLabel).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertFalse(preferenceManager.getLocalNetworkPermissionRequested())
        verify(exactly = 1) { viewModel.scanNetworkShares() }
    }
}
