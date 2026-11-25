package com.martinrevert.latorrentola.ui.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.material3
import dev.chrisbanes.haze.rememberHazeState

private const val ClearPlayerGlassContainerAlpha = 0.14f
private const val ClearPlayerGlassTintAlpha = 0.12f
private const val ClearPlayerGlassBorderAlpha = 0.35f
private const val ClearPlayerGlassFallbackAlpha = 0.42f
private const val ClearPlayerGlassSpecularIntensity = 0.42f
private const val ClearPlayerGlassEdgeShadowAlpha = 0.25f
private const val ClearPlayerGlassAmbientResponse = 0.22f
private const val ClearPlayerGlassContrast = 0.12f
private const val ClearPlayerGlassWhitePoint = -0.18f
private const val ClearPlayerGlassChromaMultiplier = 1f

internal data class ClearPlayerGlassColors(
    val containerColor: Color,
    val tintColor: Color,
    val borderColor: Color,
    val fallbackColor: Color
)

internal data class ClearPlayerGlassOptics(
    val specularIntensity: Float,
    val edgeShadowColor: Color,
    val ambientResponse: Float,
    val contrast: Float,
    val whitePoint: Float,
    val chromaMultiplier: Float
)

internal fun resolveClearPlayerGlassColors(
    scrimColor: Color,
    rimColor: Color
): ClearPlayerGlassColors {
    return ClearPlayerGlassColors(
        containerColor = scrimColor.copy(alpha = ClearPlayerGlassContainerAlpha),
        tintColor = scrimColor.copy(alpha = ClearPlayerGlassTintAlpha),
        borderColor = rimColor.copy(alpha = ClearPlayerGlassBorderAlpha),
        fallbackColor = scrimColor.copy(alpha = ClearPlayerGlassFallbackAlpha)
    )
}

internal fun resolveClearPlayerGlassOptics(
    scrimColor: Color
): ClearPlayerGlassOptics {
    return ClearPlayerGlassOptics(
        specularIntensity = ClearPlayerGlassSpecularIntensity,
        edgeShadowColor = scrimColor.copy(alpha = ClearPlayerGlassEdgeShadowAlpha),
        ambientResponse = ClearPlayerGlassAmbientResponse,
        contrast = ClearPlayerGlassContrast,
        whitePoint = ClearPlayerGlassWhitePoint,
        chromaMultiplier = ClearPlayerGlassChromaMultiplier
    )
}

internal fun isClearPlayerGlassActive(
    hasHazeState: Boolean,
    isInspectionMode: Boolean,
    sdkInt: Int
): Boolean {
    return hasHazeState && (isInspectionMode || sdkInt >= Build.VERSION_CODES.S)
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
 * Applies Haze 2.1 Clear Liquid Glass (`GlassStyle.clear.material3(...)`) per individual player
 * control object over live handheld video (`TextureView` + `hazeSource`), bound to Material 3
 * color scheme tokens via `haze-glass-material3`.
 *
 * - On Android 12+ (API 31+) and Compose Preview: applies shallow 2.dp blur + edge refraction
 *   with Material 3 scrim/tint tokens and NO opaque stacked background so video remains clearly visible.
 * - On pre-Android 12 or when `hazeState` is null: falls back to a clean translucent scrim fill
 *   and subtle 1.dp rim stroke derived from Material 3 tokens.
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
    val colorScheme = MaterialTheme.colorScheme
    val glassColors = resolveClearPlayerGlassColors(
        scrimColor = colorScheme.scrim,
        rimColor = colorScheme.outline
    )
    val glassOptics = resolveClearPlayerGlassOptics(scrimColor = colorScheme.scrim)
    val clipped = this.clip(shape)
    return if (glassActive && hazeState != null) {
        val baseStyle = remember(shape, glassOptics) {
            GlassStyle.clear.then {
                if (shape is RoundedCornerShape) {
                    shape(shape)
                }
                specularIntensity(glassOptics.specularIntensity)
                edgeShadow(glassOptics.edgeShadowColor)
                ambientResponse(glassOptics.ambientResponse)
                contrast(glassOptics.contrast)
                whitePoint(glassOptics.whitePoint)
                chromaMultiplier(glassOptics.chromaMultiplier)
                optics {
                    copy(blurRadius = OpticalSizeValue.Fixed(2.dp))
                }
            }
        }
        val style = baseStyle.material3(
            containerColor = glassColors.containerColor,
            tint = glassColors.tintColor
        )
        clipped
            .hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = style
            )
            .border(width = 0.75.dp, color = glassColors.borderColor, shape = shape)
    } else {
        clipped
            .background(
                color = glassColors.fallbackColor,
                shape = shape
            )
            .border(width = 1.dp, color = glassColors.borderColor, shape = shape)
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
