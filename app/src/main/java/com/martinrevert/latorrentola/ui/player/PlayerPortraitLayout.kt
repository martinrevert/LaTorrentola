package com.martinrevert.latorrentola.ui.player

import android.content.pm.ActivityInfo

/**
 * Pure layout & orientation helpers for the YouTube-style handheld portrait player.
 *
 * In portrait, the video is letterboxed in the vertical center of the screen. Instead of pinning the
 * controls to the screen edges (top bar under the status bar, scrubber over the navigation bar), the
 * controls are anchored to a "controls frame" that hugs the visible video.
 */
internal object PlayerPortraitLayout {

    /** Aspect ratio used while the video size is still unknown (and as the minimum frame height). */
    const val DEFAULT_ASPECT_RATIO = 16f / 9f

    /**
     * Minimum height (dp) required to fit top bar (48) + center controls (~64) + bottom bar
     * (timestamps/fullscreen ~40 + scrubber ~44) without overlap.
     */
    const val MIN_CONTROLS_FRAME_HEIGHT_DP = 200f

    /**
     * Returns the aspect ratio (width / height) of the video, or [DEFAULT_ASPECT_RATIO] if unknown/invalid.
     */
    fun resolveAspectRatio(videoWidth: Float?, videoHeight: Float?): Float {
        if (videoWidth == null || videoHeight == null) return DEFAULT_ASPECT_RATIO
        if (videoWidth <= 0f || videoHeight <= 0f) return DEFAULT_ASPECT_RATIO
        val ratio = videoWidth / videoHeight
        return if (ratio.isFinite() && ratio > 0f) ratio else DEFAULT_ASPECT_RATIO
    }

    /**
     * Height (dp) of the visible video frame when fitted (ContentScale.Fit) into [containerWidthDp] x [containerHeightDp].
     */
    fun videoFrameHeightDp(containerWidthDp: Float, containerHeightDp: Float, aspectRatio: Float): Float {
        if (containerWidthDp <= 0f || containerHeightDp <= 0f) return 0f
        val ratio = if (aspectRatio.isFinite() && aspectRatio > 0f) aspectRatio else DEFAULT_ASPECT_RATIO
        return (containerWidthDp / ratio).coerceAtMost(containerHeightDp)
    }

    /**
     * Height (dp) of the controls frame anchored to the video center. It matches the video height but is
     * expanded for very wide (e.g. 2.39:1 CinemaScope) movies so controls never overlap, and is always
     * clamped to the container height.
     */
    fun controlsFrameHeightDp(containerWidthDp: Float, containerHeightDp: Float, aspectRatio: Float): Float {
        if (containerWidthDp <= 0f || containerHeightDp <= 0f) return 0f
        val video = videoFrameHeightDp(containerWidthDp, containerHeightDp, aspectRatio)
        val minimum = maxOf(containerWidthDp / DEFAULT_ASPECT_RATIO, MIN_CONTROLS_FRAME_HEIGHT_DP)
        return maxOf(video, minimum).coerceAtMost(containerHeightDp)
    }

    /** Orientation requested when the user taps the fullscreen toggle. */
    fun targetOrientationForFullscreenToggle(isCurrentlyPortrait: Boolean): Int =
        if (isCurrentlyPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

    /**
     * YouTube-like unlock: after the fullscreen toggle forces an orientation, auto-rotation is restored
     * as soon as the physical device orientation matches the forced one (only when system auto-rotate is on).
     *
     * @param requestedOrientation the current `Activity.requestedOrientation`.
     * @param degrees value from `OrientationEventListener.onOrientationChanged` (0-359, or -1 if unknown).
     */
    fun shouldReleaseOrientationLock(
        requestedOrientation: Int,
        degrees: Int,
        autoRotateEnabled: Boolean
    ): Boolean {
        if (!autoRotateEnabled || degrees < 0) return false
        val d = degrees % 360
        val physicalPortrait = d <= 20 || d >= 340 || d in 160..200
        val physicalLandscape = d in 70..110 || d in 250..290
        return when (requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> physicalLandscape
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> physicalPortrait
            else -> false
        }
    }

    /**
     * YouTube-style system bars policy for handheld player (`Option A`):
     * - In **landscape** (`isOrientationPortrait == false`): always hide system bars (immersive fullscreen).
     * - In **portrait** (`isOrientationPortrait == true`): hide system bars when player controls are hidden
     *   (`controlsVisible == false`), and show them when the user taps to reveal controls (`controlsVisible == true`).
     */
    fun shouldShowSystemBars(
        isTv: Boolean,
        isOrientationPortrait: Boolean,
        controlsVisible: Boolean
    ): Boolean {
        if (isTv) return false
        return isOrientationPortrait && controlsVisible
    }
}

