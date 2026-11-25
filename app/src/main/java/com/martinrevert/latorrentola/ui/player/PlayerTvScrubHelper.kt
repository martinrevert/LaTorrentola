package com.martinrevert.latorrentola.ui.player

/**
 * Pure helper computing predictable, time-based D-pad scrubbing steps for Android TV
 * timeline controls, preventing abrupt percentage-based Slider jumps or accidental seeks to 00:00.
 */
internal object PlayerTvScrubHelper {

    const val STEP_SHORT_MS = 10_000L
    const val STEP_MEDIUM_MS = 30_000L
    const val STEP_LONG_MS = 60_000L
    const val DEBOUNCE_COMMIT_MS = 450L

    /**
     * Resolves the step duration in milliseconds based on key repeat acceleration and total video duration.
     * - Single press / repeats 0..4: 10s
     * - Medium hold / repeats 5..12: 30s
     * - Long hold / repeats > 12: 60s
     * For short clips (< 60s), scales the step proportionally so a single press never skips the whole clip.
     */
    fun stepDurationMs(repeatCount: Int, totalDurationMs: Long): Long {
        if (totalDurationMs <= 0L) return STEP_SHORT_MS
        val baseStep = when {
            repeatCount <= 4 -> STEP_SHORT_MS
            repeatCount <= 12 -> STEP_MEDIUM_MS
            else -> STEP_LONG_MS
        }
        return if (totalDurationMs < STEP_LONG_MS) {
            val scaledMax = (totalDurationMs / 10L).coerceAtLeast(1_000L).coerceAtMost(totalDurationMs)
            baseStep.coerceAtMost(scaledMax)
        } else {
            baseStep.coerceAtMost(totalDurationMs)
        }
    }

    /**
     * Computes the next scrubbed position clamped to `[0L, totalDurationMs]`.
     *
     * @param currentPositionMs Base playback or in-progress scrub position in milliseconds.
     * @param totalDurationMs Total media duration in milliseconds.
     * @param direction Negative for rewind (D-pad Left), positive for forward (D-pad Right).
     * @param repeatCount Native key event repeatCount (0 for single tap).
     */
    fun computeNextPositionMs(
        currentPositionMs: Long,
        totalDurationMs: Long,
        direction: Int,
        repeatCount: Int
    ): Long {
        if (totalDurationMs <= 0L) return 0L
        val safeCurrent = currentPositionMs.coerceIn(0L, totalDurationMs)
        if (direction == 0) return safeCurrent
        val step = stepDurationMs(
            repeatCount = repeatCount.coerceAtLeast(0),
            totalDurationMs = totalDurationMs
        )
        val delta = if (direction > 0) step else -step
        return (safeCurrent + delta).coerceIn(0L, totalDurationMs)
    }

    fun positionToFraction(positionMs: Long, totalDurationMs: Long): Float {
        if (totalDurationMs <= 0L) return 0f
        return (positionMs.coerceIn(0L, totalDurationMs).toFloat() / totalDurationMs.toFloat())
            .coerceIn(0f, 1f)
    }

    fun fractionToPositionMs(fraction: Float, totalDurationMs: Long): Long {
        if (totalDurationMs <= 0L || !fraction.isFinite()) return 0L
        val clamped = fraction.toDouble().coerceIn(0.0, 1.0)
        return (clamped * totalDurationMs.toDouble()).toLong()
            .coerceIn(0L, totalDurationMs)
    }
}
