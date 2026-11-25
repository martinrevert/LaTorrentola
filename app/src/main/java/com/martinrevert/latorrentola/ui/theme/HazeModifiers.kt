package com.martinrevert.latorrentola.ui.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.rememberHazeState

private val ClearPlayerGlassTint = Color.Black.copy(alpha = 0.12f)
private val ClearPlayerGlassBorderColor = Color.White.copy(alpha = 0.22f)
private val ClearPlayerGlassFallbackColor = Color.Black.copy(alpha = 0.42f)

internal fun isClearPlayerGlassActive(
    hasHazeState: Boolean,
    isInspectionMode: Boolean,
    sdkInt: Int
): Boolean {
    return hasHazeState && (isInspectionMode || sdkInt >= Build.VERSION_CODES.S)
}

internal fun resolveClearPlayerGlassFallbackColor(
    isGlassActive: Boolean,
    fallbackColor: Color = ClearPlayerGlassFallbackColor
): Color {
    return if (isGlassActive) Color.Transparent else fallbackColor
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
 * Applies Haze 2.1 Clear Liquid Glass (`GlassStyle.clear`) per individual player control object
 * over live handheld video (`TextureView` + `hazeSource`).
 *
 * - On Android 12+ (API 31+) and Compose Preview: applies shallow 2.dp blur + edge refraction
 *   with an ultra-light tint and NO opaque stacked background so video remains clearly visible.
 * - On pre-Android 12 or when `hazeState` is null: falls back to a clean translucent dark fill
 *   and subtle 1.dp rim stroke.
 */
@Composable
fun Modifier.hazeClearPlayerGlass(
    hazeState: HazeState?,
    shape: Shape
): Modifier {
    val glassActive = isClearPlayerGlassActive(
        hasHazeState = hazeState != null,
        isInspectionMode = LocalInspectionMode.current,
        sdkInt = Build.VERSION.SDK_INT
    )
    val clipped = this.clip(shape)
    return if (glassActive && hazeState != null) {
        val style = remember(shape) {
            GlassStyle.clear.then {
                if (shape is RoundedCornerShape) {
                    shape(shape)
                }
                tint(ClearPlayerGlassTint)
                optics {
                    copy(blurRadius = OpticalSizeValue.Fixed(2.dp))
                }
            }
        }
        clipped
            .hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = style
            )
            .border(width = 0.75.dp, color = ClearPlayerGlassBorderColor, shape = shape)
    } else {
        clipped
            .background(
                color = resolveClearPlayerGlassFallbackColor(isGlassActive = false),
                shape = shape
            )
            .border(width = 1.dp, color = ClearPlayerGlassBorderColor, shape = shape)
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
