package com.martinrevert.latorrentola.service.streaming

import kotlin.math.abs

/**
 * Calculates priority windows and deadlines for torrent streaming.
 * Isolates sequential picker jumps and prioritizes mid-point resume positions.
 */
class StreamingPiecePlanner(
    val fileOffset: Long,
    val fileLength: Long,
    val pieceLength: Int,
    val firstPiece: Int,
    val lastPiece: Int,
    val isMkv: Boolean = false
) {
    companion object {
        const val STARTUP_BUFFER_BYTES = 5L * 1024L * 1024L // 5 MB
        const val MP4_TAIL_BYTES = 1_500_000L // 1.5 MB
        const val MKV_TAIL_BYTES = 5_000_000L // 5 MB
        const val LOOKAHEAD_PIECES_DEFAULT = 32
        const val SEEK_JUMP_THRESHOLD_PIECES = 4
    }

    private var lastPlannedPiece: Int = -1

    val startupPieceEnd: Int = (
        (fileOffset + minOf(fileLength - 1, STARTUP_BUFFER_BYTES)) / pieceLength
    ).toInt().coerceIn(firstPiece, lastPiece)

    val tailBytes: Long = if (isMkv) MKV_TAIL_BYTES else MP4_TAIL_BYTES

    val tailPieceStart: Int = (
        (fileOffset + maxOf(0L, fileLength - tailBytes)) / pieceLength
    ).toInt().coerceIn(firstPiece, lastPiece)

    /**
     * Calculates the piece index for a given playback position in milliseconds.
     */
    fun calculateResumePiece(resumePositionMs: Long, durationMs: Long): Int {
        if (resumePositionMs <= 0L || durationMs <= 0L || fileLength <= 0L) return firstPiece
        val estimatedOffset = ((resumePositionMs.toDouble() / durationMs.toDouble()) * fileLength).toLong()
        val offsetInFile = estimatedOffset.coerceIn(0L, fileLength - 1L)
        return ((fileOffset + offsetInFile) / pieceLength).toInt().coerceIn(firstPiece, lastPiece)
    }

    /**
     * Determines whether a byte range is a metadata probe at the end of the file.
     */
    fun isTailProbe(rangeStart: Long, rangeEnd: Long): Boolean {
        if (fileLength <= 0L) return false
        val threshold = maxOf(0L, fileLength - tailBytes)
        return rangeStart >= threshold
    }

    /**
     * Generates a streaming piece plan based on the current playhead or requested byte range.
     *
     * @param requestedStart Start byte of the HTTP request.
     * @param requestedEnd End byte of the HTTP request.
     * @param tracker In-memory tracker of which pieces are already verified.
     * @param lookaheadPieces Number of pieces to prioritize ahead of the playhead.
     */
    fun planForRange(
        requestedStart: Long,
        requestedEnd: Long,
        tracker: VerifiedPieceTracker,
        lookaheadPieces: Int = LOOKAHEAD_PIECES_DEFAULT
    ): StreamingPlan {
        val isTail = isTailProbe(requestedStart, requestedEnd)
        val currentPiece = ((fileOffset + requestedStart) / pieceLength).toInt().coerceIn(firstPiece, lastPiece)

        val isSeekJump = if (lastPlannedPiece != -1 && !isTail) {
            val delta = currentPiece - lastPlannedPiece
            delta < 0 || delta > SEEK_JUMP_THRESHOLD_PIECES
        } else {
            false
        }

        if (!isTail) {
            lastPlannedPiece = currentPiece
        }

        val prioritizedPieces = mutableListOf<Int>()
        val deadlines = mutableMapOf<Int, Int>()

        // 1. Ensure startup/head pieces are verified (for container headers)
        for (p in firstPiece..startupPieceEnd) {
            if (!tracker.isPieceVerified(p)) {
                prioritizedPieces.add(p)
                deadlines[p] = 500
            }
        }

        // 2. Ensure tail pieces are prioritized (for MKV Cues or MP4 moov atom)
        for (p in tailPieceStart..lastPiece) {
            if (!tracker.isPieceVerified(p)) {
                prioritizedPieces.add(p)
                deadlines[p] = 500
            }
        }

        // 3. Prioritize the active playback window
        val windowStart = if (isTail) {
            // For tail probes, do not move the sequential window forward
            lastPlannedPiece.takeIf { it != -1 } ?: firstPiece
        } else {
            currentPiece
        }

        val windowEnd = minOf(windowStart + lookaheadPieces, lastPiece)
        var deadlineStep = 500
        for (p in windowStart..windowEnd) {
            if (!tracker.isPieceVerified(p)) {
                prioritizedPieces.add(p)
                deadlines[p] = deadlineStep
                deadlineStep = minOf(deadlineStep + 100, 3000)
            }
        }

        return StreamingPlan(
            currentPlayheadPiece = currentPiece,
            sequentialRangeStart = windowStart,
            piecesToPrioritize = prioritizedPieces.distinct(),
            deadlines = deadlines,
            isSeekJump = isSeekJump,
            isTailProbe = isTail
        )
    }

    fun reset() {
        lastPlannedPiece = -1
    }
}

/**
 * Result of planning decisions to apply to the active libtorrent session.
 */
data class StreamingPlan(
    val currentPlayheadPiece: Int,
    val sequentialRangeStart: Int,
    val piecesToPrioritize: List<Int>,
    val deadlines: Map<Int, Int>, // pieceIndex -> deadlineMs
    val isSeekJump: Boolean,
    val isTailProbe: Boolean
)
