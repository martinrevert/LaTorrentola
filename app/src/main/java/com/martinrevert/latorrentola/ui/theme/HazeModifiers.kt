package com.martinrevert.latorrentola.ui.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.material3
import dev.chrisbanes.haze.rememberHazeState

private val AppBarGlassBaseStyle = GlassStyle {
    shape(RoundedCornerShape(0.dp))
}

internal fun resolveHazeAppBarContainerColor(
    hasGlass: Boolean,
    isApi31OrInspection: Boolean,
    surfaceColor: Color,
    fallbackAlpha: Float = 0.90f
): Color {
    return if (hasGlass && isApi31OrInspection) {
        Color.Transparent
    } else {
        surfaceColor.copy(alpha = fallbackAlpha)
    }
}

internal fun shouldEnableSheetBackdropBlur(
    enabled: Boolean,
    isInspectionMode: Boolean,
    sdkInt: Int
): Boolean {
    return enabled && !isInspectionMode && sdkInt >= Build.VERSION_CODES.S
}

internal fun resolveSheetBackdropTargetRadiusPx(
    canBlur: Boolean,
    isSheetVisible: Boolean,
    maxRadiusPx: Float
): Float {
    return if (canBlur && isSheetVisible) maxRadiusPx else 0f
}

/**
 * Creates and remembers a [HazeState] only on handheld devices.
 * Android TV strictly sandboxes Haze shaders to avoid GPU overhead and D-pad focus issues.
 */
@Composable
fun rememberHandheldHazeState(isTv: Boolean): HazeState? {
    return if (!isTv) rememberHazeState() else null
}

/**
 * Returns [Color.Transparent] when Haze 2.1 glass is active (Android 12+ or Compose Preview),
 * or a translucent Material 3 surface fallback on pre-Android 12 devices or when glass is disabled.
 */
@Composable
fun hazeAppBarContainerColor(
    hasGlass: Boolean,
    fallbackAlpha: Float = 0.90f
): Color {
    val isApi31OrInspection = LocalInspectionMode.current || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return resolveHazeAppBarContainerColor(
        hasGlass = hasGlass,
        isApi31OrInspection = isApi31OrInspection,
        surfaceColor = MaterialTheme.colorScheme.surface,
        fallbackAlpha = fallbackAlpha
    )
}

/**
 * Applies Tier 1 edge-to-edge Material 3 Haze glass styling to full-width app bars and headers.
 * Explicitly sets zero corner radius so the blur spans flush across screen edges
 * without inheriting `GlassDefaults.shape` (16.dp).
 */
@Composable
fun Modifier.hazeAppBarGlass(hazeState: HazeState?): Modifier {
    val isApi31OrInspection = LocalInspectionMode.current || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return if (hazeState != null && isApi31OrInspection) {
        this.hazeGlass(
            input = HazeInput.Sources(hazeState),
            style = AppBarGlassBaseStyle.material3()
        )
    } else {
        this
    }
}

/**
 * Applies Tier 1 Material 3 Haze glass styling to floating pills, capsules, and player HUD cards,
 * keeping clip and glass shader geometry strictly synchronized.
 */
@Composable
fun Modifier.hazePillGlass(
    hazeState: HazeState?,
    shape: Shape
): Modifier {
    val isApi31OrInspection = LocalInspectionMode.current || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val clipped = this.clip(shape)
    return if (hazeState != null && isApi31OrInspection) {
        val baseStyle = remember(shape) {
            when (shape) {
                is RoundedCornerShape -> GlassStyle { shape(shape) }
                else -> AppBarGlassBaseStyle
            }
        }
        clipped.hazeGlass(
            input = HazeInput.Sources(hazeState),
            style = baseStyle.material3()
        )
    } else {
        clipped
    }
}

/**
 * Applies Tier 2 smooth full-screen backdrop blur behind modal bottom sheets on Android 12+ handhelds.
 * Animates blur radius in the graphics layer so the screen root never recomposes per frame.
 */
@Composable
fun Modifier.hazeSheetBackdropBlur(
    isSheetVisible: Boolean,
    enabled: Boolean = true
): Modifier {
    val canBlur = shouldEnableSheetBackdropBlur(
        enabled = enabled,
        isInspectionMode = LocalInspectionMode.current,
        sdkInt = Build.VERSION.SDK_INT
    )
    val maxRadiusPx = with(LocalDensity.current) { 18.dp.toPx() }
    val targetRadiusPx = resolveSheetBackdropTargetRadiusPx(
        canBlur = canBlur,
        isSheetVisible = isSheetVisible,
        maxRadiusPx = maxRadiusPx
    )
    val blurRadiusPx = remember { Animatable(targetRadiusPx) }
    LaunchedEffect(targetRadiusPx) {
        if (blurRadiusPx.targetValue != targetRadiusPx || blurRadiusPx.value != targetRadiusPx) {
            blurRadiusPx.animateTo(
                targetValue = targetRadiusPx,
                animationSpec = tween(durationMillis = 250)
            )
        }
    }
    return this.graphicsLayer {
        val radius = if (canBlur) blurRadiusPx.value else 0f
        if (radius > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            renderEffect = RenderEffect
                .createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
            clip = true
            shape = RectangleShape
        } else {
            renderEffect = null
            clip = false
        }
    }
}
