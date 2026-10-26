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
    val isMkv: Boolean = false,
    val startupBufferBytes: Long = calculateStartupBufferBytes(fileLength)
) {
    companion object {
        const val MIN_STARTUP_BUFFER_BYTES = 8L * 1024L * 1024L // 8 MB
        const val MAX_STARTUP_BUFFER_BYTES = 32L * 1024L * 1024L // 32 MB
        const val MP4_TAIL_BYTES = 2_000_000L // 2 MB
        const val MKV_TAIL_BYTES = 5_000_000L // 5 MB
        const val LOOKAHEAD_PIECES_DEFAULT = 32
        const val SEEK_JUMP_THRESHOLD_PIECES = 16

        /**
         * Calculates proportional, adaptive startup buffer size based on file size.
         * 0.3% of file size, clamped between 8 MB and 32 MB:
         * - 700 MB - 2 GB (720p/1080p): 8 MB buffer (~30-60s of playback)
         * - 4 GB - 8 GB (High-bitrate 1080p): 12-24 MB buffer (~25s of playback)
         * - 15 GB - 50 GB (4K UHD Remux): 32 MB max buffer (~10-15s of playback)
         */
        fun calculateStartupBufferBytes(fileLength: Long): Long {
            if (fileLength <= 0L) return MIN_STARTUP_BUFFER_BYTES
            val proportional = (fileLength * 0.003).toLong()
            return proportional.coerceIn(MIN_STARTUP_BUFFER_BYTES, MAX_STARTUP_BUFFER_BYTES)
        }

        /**
         * Calculates container metadata/index probe tail size based on file size and container format.
         */
        fun calculateTailBytes(fileLength: Long, isMkv: Boolean): Long {
            val base = if (isMkv) MKV_TAIL_BYTES else MP4_TAIL_BYTES
            return when {
                fileLength > 15L * 1024L * 1024L * 1024L -> 8_000_000L // 8 MB for 4K remuxes (>15 GB)
                fileLength > 5L * 1024L * 1024L * 1024L -> 6_000_000L  // 6 MB for 5-15 GB
                else -> base
            }
        }
    }

    private var lastPlannedPiece: Int = -1

    val startupPieceEnd: Int = (
        (fileOffset + minOf(fileLength - 1, startupBufferBytes)) / pieceLength
    ).toInt().coerceIn(firstPiece, lastPiece)

    val tailBytes: Long = calculateTailBytes(fileLength, isMkv)

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
        val isHead = requestedStart < startupBufferBytes
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

        // 1. Ensure startup/head pieces are verified ONLY when starting up or probing container headers
        if (isHead) {
            for (p in firstPiece..startupPieceEnd) {
                if (!tracker.isPieceVerified(p)) {
                    prioritizedPieces.add(p)
                    deadlines[p] = 500
                }
            }
        }

        // 2. Ensure tail pieces are prioritized ONLY when probing container index (Cues/moov)
        if (isTail) {
            for (p in tailPieceStart..lastPiece) {
                if (!tracker.isPieceVerified(p)) {
                    prioritizedPieces.add(p)
                    deadlines[p] = 500
                }
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
