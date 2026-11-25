package com.martinrevert.latorrentola.ui.theme

import android.os.Build
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HazeModifiersTest {

    @Test
    fun `isClearPlayerGlassActive returns true when hazeState is present on Android 12 plus or preview`() {
        assertThat(
            isClearPlayerGlassActive(
                hasHazeState = true,
                isInspectionMode = false,
                sdkInt = Build.VERSION_CODES.S
            )
        ).isTrue()

        assertThat(
            isClearPlayerGlassActive(
                hasHazeState = true,
                isInspectionMode = true,
                sdkInt = Build.VERSION_CODES.R
            )
        ).isTrue()
    }

    @Test
    fun `isClearPlayerGlassActive returns false when hazeState is null or pre-Android 12 at runtime`() {
        assertThat(
            isClearPlayerGlassActive(
                hasHazeState = false,
                isInspectionMode = false,
                sdkInt = Build.VERSION_CODES.S
            )
        ).isFalse()

        assertThat(
            isClearPlayerGlassActive(
                hasHazeState = true,
                isInspectionMode = false,
                sdkInt = Build.VERSION_CODES.R
            )
        ).isFalse()
    }

    @Test
    fun `resolveClearPlayerGlassFallbackColor returns transparent when active and dark fallback otherwise`() {
        val customFallback = Color.Black.copy(alpha = 0.42f)

        assertThat(
            resolveClearPlayerGlassFallbackColor(
                isGlassActive = true,
                fallbackColor = customFallback
            )
        ).isEqualTo(Color.Transparent)

        assertThat(
            resolveClearPlayerGlassFallbackColor(
                isGlassActive = false,
                fallbackColor = customFallback
            )
        ).isEqualTo(customFallback)
    }

    @Test
    fun `shouldEnableSheetBackdropBlur requires enabled flag, non-inspection mode, and Android 12 plus`() {
        assertThat(
            shouldEnableSheetBackdropBlur(
                enabled = true,
                isInspectionMode = false,
                sdkInt = Build.VERSION_CODES.S
            )
        ).isTrue()

        assertThat(
            shouldEnableSheetBackdropBlur(
                enabled = false,
                isInspectionMode = false,
                sdkInt = Build.VERSION_CODES.S
            )
        ).isFalse()

        assertThat(
            shouldEnableSheetBackdropBlur(
                enabled = true,
                isInspectionMode = true,
                sdkInt = Build.VERSION_CODES.S
            )
        ).isFalse()

        assertThat(
            shouldEnableSheetBackdropBlur(
                enabled = true,
                isInspectionMode = false,
                sdkInt = Build.VERSION_CODES.R
            )
        ).isFalse()
    }

    @Test
    fun `resolveSheetBackdropTargetRadiusPx returns maxRadiusPx only when blur is enabled and sheet is visible`() {
        assertThat(
            resolveSheetBackdropTargetRadiusPx(
                canBlur = true,
                isSheetVisible = true,
                maxRadiusPx = 42f
            )
        ).isEqualTo(42f)

        assertThat(
            resolveSheetBackdropTargetRadiusPx(
                canBlur = true,
                isSheetVisible = false,
                maxRadiusPx = 42f
            )
        ).isEqualTo(0f)

        assertThat(
            resolveSheetBackdropTargetRadiusPx(
                canBlur = false,
                isSheetVisible = true,
                maxRadiusPx = 42f
            )
        ).isEqualTo(0f)
    }
}
