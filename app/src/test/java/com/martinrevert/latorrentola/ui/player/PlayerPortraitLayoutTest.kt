package com.martinrevert.latorrentola.ui.player

import android.content.pm.ActivityInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerPortraitLayoutTest {

    @Test
    fun `resolveAspectRatio falls back to 16-9 when size unknown or invalid`() {
        val default = PlayerPortraitLayout.DEFAULT_ASPECT_RATIO
        assertThat(PlayerPortraitLayout.resolveAspectRatio(null, null)).isEqualTo(default)
        assertThat(PlayerPortraitLayout.resolveAspectRatio(1920f, 0f)).isEqualTo(default)
        assertThat(PlayerPortraitLayout.resolveAspectRatio(-1f, 1080f)).isEqualTo(default)
    }

    @Test
    fun `resolveAspectRatio computes width over height`() {
        assertThat(PlayerPortraitLayout.resolveAspectRatio(1920f, 800f)).isWithin(0.001f).of(2.4f)
    }

    @Test
    fun `videoFrameHeightDp fits 16-9 video on a portrait phone`() {
        val height = PlayerPortraitLayout.videoFrameHeightDp(360f, 780f, 16f / 9f)
        assertThat(height).isWithin(0.01f).of(202.5f)
    }

    @Test
    fun `videoFrameHeightDp is clamped to container height`() {
        val height = PlayerPortraitLayout.videoFrameHeightDp(360f, 100f, 16f / 9f)
        assertThat(height).isEqualTo(100f)
    }

    @Test
    fun `controlsFrameHeightDp matches 16-9 video height on regular phone`() {
        val frame = PlayerPortraitLayout.controlsFrameHeightDp(400f, 850f, 16f / 9f)
        assertThat(frame).isWithin(0.01f).of(225f)
    }

    @Test
    fun `controlsFrameHeightDp expands for cinemascope movies so controls do not overlap`() {
        val video = PlayerPortraitLayout.videoFrameHeightDp(360f, 780f, 2.39f)
        val frame = PlayerPortraitLayout.controlsFrameHeightDp(360f, 780f, 2.39f)
        assertThat(video).isLessThan(PlayerPortraitLayout.MIN_CONTROLS_FRAME_HEIGHT_DP)
        assertThat(frame).isAtLeast(PlayerPortraitLayout.MIN_CONTROLS_FRAME_HEIGHT_DP)
    }

    @Test
    fun `controlsFrameHeightDp on narrow foldable cover screen respects minimum height`() {
        // e.g. Galaxy Z Fold cover screen ~ 300dp wide
        val frame = PlayerPortraitLayout.controlsFrameHeightDp(300f, 800f, 16f / 9f)
        assertThat(frame).isEqualTo(PlayerPortraitLayout.MIN_CONTROLS_FRAME_HEIGHT_DP)
    }

    @Test
    fun `controlsFrameHeightDp follows tall vertical videos and never exceeds container`() {
        val frame = PlayerPortraitLayout.controlsFrameHeightDp(360f, 640f, 9f / 16f)
        assertThat(frame).isEqualTo(640f)
        assertThat(PlayerPortraitLayout.controlsFrameHeightDp(0f, 640f, 1f)).isEqualTo(0f)
    }

    @Test
    fun `fullscreen toggle targets landscape from portrait and portrait from landscape`() {
        assertThat(PlayerPortraitLayout.targetOrientationForFullscreenToggle(isCurrentlyPortrait = true))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
        assertThat(PlayerPortraitLayout.targetOrientationForFullscreenToggle(isCurrentlyPortrait = false))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    }

    @Test
    fun `orientation lock is released only when physical orientation matches and auto-rotate is on`() {
        val landscape = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val portrait = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        assertThat(PlayerPortraitLayout.shouldReleaseOrientationLock(landscape, 90, true)).isTrue()
        assertThat(PlayerPortraitLayout.shouldReleaseOrientationLock(landscape, 270, true)).isTrue()
        assertThat(PlayerPortraitLayout.shouldReleaseOrientationLock(landscape, 0, true)).isFalse()

        assertThat(PlayerPortraitLayout.shouldReleaseOrientationLock(portrait, 5, true)).isTrue()
        assertThat(PlayerPortraitLayout.shouldReleaseOrientationLock(portrait, 90, true)).isFalse()

        assertThat(PlayerPortraitLayout.shouldReleaseOrientationLock(landscape, 90, false)).isFalse()
        assertThat(PlayerPortraitLayout.shouldReleaseOrientationLock(landscape, -1, true)).isFalse()
        assertThat(
            PlayerPortraitLayout.shouldReleaseOrientationLock(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, 90, true)
        ).isFalse()
    }

    @Test
    fun `shouldShowSystemBars hides bars in landscape and when controls hidden in portrait`() {
        // Handheld landscape: always hidden
        assertThat(PlayerPortraitLayout.shouldShowSystemBars(isTv = false, isOrientationPortrait = false, controlsVisible = true)).isFalse()
        assertThat(PlayerPortraitLayout.shouldShowSystemBars(isTv = false, isOrientationPortrait = false, controlsVisible = false)).isFalse()

        // Handheld portrait: shown only when controls are visible
        assertThat(PlayerPortraitLayout.shouldShowSystemBars(isTv = false, isOrientationPortrait = true, controlsVisible = true)).isTrue()
        assertThat(PlayerPortraitLayout.shouldShowSystemBars(isTv = false, isOrientationPortrait = true, controlsVisible = false)).isFalse()

        // TV: always false
        assertThat(PlayerPortraitLayout.shouldShowSystemBars(isTv = true, isOrientationPortrait = true, controlsVisible = true)).isFalse()
    }
}

