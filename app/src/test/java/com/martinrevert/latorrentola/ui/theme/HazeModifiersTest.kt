package com.martinrevert.latorrentola.ui.theme

import android.os.Build
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HazeModifiersTest {

    @Test
    fun `resolveHazeAppBarContainerColor returns transparent when glass and API 31 or inspection are active`() {
        val surface = Color(0xFF121212)

        val resolved = resolveHazeAppBarContainerColor(
            hasGlass = true,
            isApi31OrInspection = true,
            surfaceColor = surface,
            fallbackAlpha = 0.90f
        )

        assertThat(resolved).isEqualTo(Color.Transparent)
    }

    @Test
    fun `resolveHazeAppBarContainerColor returns translucent surface when glass is disabled or pre-API 31`() {
        val surface = Color(0xFF121212)

        val withoutGlass = resolveHazeAppBarContainerColor(
            hasGlass = false,
            isApi31OrInspection = true,
            surfaceColor = surface,
            fallbackAlpha = 0.85f
        )
        val preApi31 = resolveHazeAppBarContainerColor(
            hasGlass = true,
            isApi31OrInspection = false,
            surfaceColor = surface,
            fallbackAlpha = 0.90f
        )

        assertThat(withoutGlass).isEqualTo(surface.copy(alpha = 0.85f))
        assertThat(preApi31).isEqualTo(surface.copy(alpha = 0.90f))
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
