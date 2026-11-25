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
    fun `resolveClearPlayerGlassColors derives container, tint, border, and fallback from Material 3 tokens`() {
        val scrim = Color(0xFF000000)
        val rim = Color(0xFF86957D)

        val resolved = resolveClearPlayerGlassColors(
            scrimColor = scrim,
            rimColor = rim
        )

        assertThat(resolved.containerColor).isEqualTo(scrim.copy(alpha = 0.14f))
        assertThat(resolved.tintColor).isEqualTo(scrim.copy(alpha = 0.12f))
        assertThat(resolved.borderColor).isEqualTo(rim.copy(alpha = 0.35f))
        assertThat(resolved.fallbackColor).isEqualTo(scrim.copy(alpha = 0.42f))
    }

    @Test
    fun `resolveClearPlayerGlassOptics pins dark-calibrated parameters regardless of OS UI mode`() {
        val scrim = Color(0xFF000000)

        val optics = resolveClearPlayerGlassOptics(scrimColor = scrim)

        assertThat(optics.specularIntensity).isEqualTo(0.42f)
        assertThat(optics.edgeShadowColor).isEqualTo(scrim.copy(alpha = 0.25f))
        assertThat(optics.ambientResponse).isEqualTo(0.22f)
        assertThat(optics.contrast).isEqualTo(0.12f)
        assertThat(optics.whitePoint).isEqualTo(-0.18f)
        assertThat(optics.chromaMultiplier).isEqualTo(1f)
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
