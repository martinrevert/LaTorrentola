package com.martinrevert.latorrentola.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerTvScrubHelperTest {

    private val twoHourMovieMs = 7_200_000L

    @Test
    fun `stepDurationMs scales progressively with key repeat count on standard length media`() {
        assertThat(PlayerTvScrubHelper.stepDurationMs(repeatCount = 0, totalDurationMs = twoHourMovieMs))
            .isEqualTo(10_000L)
        assertThat(PlayerTvScrubHelper.stepDurationMs(repeatCount = 4, totalDurationMs = twoHourMovieMs))
            .isEqualTo(10_000L)
        assertThat(PlayerTvScrubHelper.stepDurationMs(repeatCount = 5, totalDurationMs = twoHourMovieMs))
            .isEqualTo(30_000L)
        assertThat(PlayerTvScrubHelper.stepDurationMs(repeatCount = 12, totalDurationMs = twoHourMovieMs))
            .isEqualTo(30_000L)
        assertThat(PlayerTvScrubHelper.stepDurationMs(repeatCount = 13, totalDurationMs = twoHourMovieMs))
            .isEqualTo(60_000L)
    }

    @Test
    fun `stepDurationMs scales down safely for short clips under 60 seconds`() {
        assertThat(PlayerTvScrubHelper.stepDurationMs(repeatCount = 0, totalDurationMs = 30_000L))
            .isEqualTo(3_000L)
        assertThat(PlayerTvScrubHelper.stepDurationMs(repeatCount = 15, totalDurationMs = 30_000L))
            .isEqualTo(3_000L)
        assertThat(PlayerTvScrubHelper.stepDurationMs(repeatCount = 0, totalDurationMs = 5_000L))
            .isEqualTo(1_000L)
    }

    @Test
    fun `computeNextPositionMs steps cleanly from current playback position without resetting to zero`() {
        val currentMs = 600_000L // 10:00

        val stepBackward = PlayerTvScrubHelper.computeNextPositionMs(
            currentPositionMs = currentMs,
            totalDurationMs = twoHourMovieMs,
            direction = -1,
            repeatCount = 0
        )
        val stepForward = PlayerTvScrubHelper.computeNextPositionMs(
            currentPositionMs = currentMs,
            totalDurationMs = twoHourMovieMs,
            direction = 1,
            repeatCount = 0
        )

        assertThat(stepBackward).isEqualTo(590_000L)
        assertThat(stepForward).isEqualTo(610_000L)
    }

    @Test
    fun `computeNextPositionMs clamps strictly between zero and totalDurationMs`() {
        val clampedStart = PlayerTvScrubHelper.computeNextPositionMs(
            currentPositionMs = 4_000L,
            totalDurationMs = twoHourMovieMs,
            direction = -1,
            repeatCount = 0
        )
        val clampedEnd = PlayerTvScrubHelper.computeNextPositionMs(
            currentPositionMs = twoHourMovieMs - 5_000L,
            totalDurationMs = twoHourMovieMs,
            direction = 1,
            repeatCount = 15
        )
        val zeroDuration = PlayerTvScrubHelper.computeNextPositionMs(
            currentPositionMs = 15_000L,
            totalDurationMs = 0L,
            direction = 1,
            repeatCount = 0
        )

        assertThat(clampedStart).isEqualTo(0L)
        assertThat(clampedEnd).isEqualTo(twoHourMovieMs)
        assertThat(zeroDuration).isEqualTo(0L)
    }

    @Test
    fun `positionToFraction and fractionToPositionMs round trip safely and clamp out of range inputs`() {
        assertThat(PlayerTvScrubHelper.positionToFraction(3_600_000L, twoHourMovieMs))
            .isWithin(0.0001f)
            .of(0.5f)
        assertThat(PlayerTvScrubHelper.positionToFraction(-500L, twoHourMovieMs))
            .isEqualTo(0f)
        assertThat(PlayerTvScrubHelper.positionToFraction(twoHourMovieMs + 10_000L, twoHourMovieMs))
            .isEqualTo(1f)
        assertThat(PlayerTvScrubHelper.positionToFraction(10_000L, 0L))
            .isEqualTo(0f)

        assertThat(PlayerTvScrubHelper.fractionToPositionMs(0.5f, twoHourMovieMs))
            .isEqualTo(3_600_000L)
        assertThat(PlayerTvScrubHelper.fractionToPositionMs(-0.2f, twoHourMovieMs))
            .isEqualTo(0L)
        assertThat(PlayerTvScrubHelper.fractionToPositionMs(1.5f, twoHourMovieMs))
            .isEqualTo(twoHourMovieMs)
        assertThat(PlayerTvScrubHelper.fractionToPositionMs(0.5f, 0L))
            .isEqualTo(0L)
    }

    @Test
    fun `stepDurationMs never exceeds totalDurationMs for sub-second clips`() {
        assertThat(PlayerTvScrubHelper.stepDurationMs(repeatCount = 0, totalDurationMs = 500L))
            .isEqualTo(500L)
        assertThat(PlayerTvScrubHelper.stepDurationMs(repeatCount = 20, totalDurationMs = 250L))
            .isEqualTo(250L)
        assertThat(PlayerTvScrubHelper.stepDurationMs(repeatCount = 0, totalDurationMs = 1L))
            .isEqualTo(1L)
    }

    @Test
    fun `computeNextPositionMs handles zero direction and negative repeatCount safely`() {
        val currentMs = 600_000L

        assertThat(
            PlayerTvScrubHelper.computeNextPositionMs(
                currentPositionMs = currentMs,
                totalDurationMs = twoHourMovieMs,
                direction = 0,
                repeatCount = 0
            )
        ).isEqualTo(currentMs)

        assertThat(
            PlayerTvScrubHelper.computeNextPositionMs(
                currentPositionMs = -1_000L,
                totalDurationMs = twoHourMovieMs,
                direction = 0,
                repeatCount = 0
            )
        ).isEqualTo(0L)

        assertThat(
            PlayerTvScrubHelper.computeNextPositionMs(
                currentPositionMs = currentMs,
                totalDurationMs = twoHourMovieMs,
                direction = 1,
                repeatCount = -5
            )
        ).isEqualTo(610_000L)
    }

    @Test
    fun `fractionToPositionMs handles non-finite floats and preserves precision on long media`() {
        assertThat(PlayerTvScrubHelper.fractionToPositionMs(Float.NaN, twoHourMovieMs))
            .isEqualTo(0L)
        assertThat(PlayerTvScrubHelper.fractionToPositionMs(Float.POSITIVE_INFINITY, twoHourMovieMs))
            .isEqualTo(0L)
        assertThat(PlayerTvScrubHelper.fractionToPositionMs(Float.NEGATIVE_INFINITY, twoHourMovieMs))
            .isEqualTo(0L)

        val tenHourMediaMs = 36_000_000L
        assertThat(PlayerTvScrubHelper.fractionToPositionMs(0.5f, tenHourMediaMs))
            .isEqualTo(18_000_000L)
        assertThat(PlayerTvScrubHelper.fractionToPositionMs(1f, tenHourMediaMs))
            .isEqualTo(tenHourMediaMs)
    }
}
