package com.martinrevert.latorrentola.service.streaming

import org.libtorrent4j.Priority

/**
 * Calculates priority windows, staggered staircase deadlines, and sliding sequential bounds
 * for torrent streaming.
 *
 * Uses a hybrid sliding-window sequential + rarest-first strategy:
 * - Pieces inside the immediate playhead window (`[sequentialRangeStart..sequentialRangeEnd]`) are downloaded
 *   in sequential order with staggered deadlines (`150ms` for immediate pieces, stepping up for lookahead).
 * - Pieces beyond `sequentialRangeEnd` remain governed by libtorrent's native rarest-first picker so
 *   connected peers with later pieces can contribute full aggregate swarm throughput without idling.
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
        const val INITIAL_TAIL_PROBE_BYTES = 1_024_000L // 1 MB fast initial tail probe for TTFF
        const val LOOKAHEAD_PIECES_DEFAULT = 32
        const val SEQUENTIAL_WINDOW_PIECES = 48
        const val SEEK_JUMP_THRESHOLD_PIECES = 16
        const val CRITICAL_HEAD_PIECES = 4
        const val IMMEDIATE_DEADLINE_MS = 150
        const val LOOKAHEAD_BASE_DEADLINE_MS = 400
        const val LOOKAHEAD_STEP_DEADLINE_MS = 100
        const val MAX_DEADLINE_MS = 3000

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
         * Calculates full container metadata/index probe tail size based on file size and container format.
         */
        fun calculateTailBytes(fileLength: Long, isMkv: Boolean): Long {
            val base = if (isMkv) MKV_TAIL_BYTES else MP4_TAIL_BYTES
            return when {
                fileLength > 15L * 1024L * 1024L * 1024L -> 8_000_000L // 8 MB for 4K remuxes (>15 GB)
                fileLength > 5L * 1024L * 1024L * 1024L -> 6_000_000L  // 6 MB for 5-15 GB
                else -> base
            }
        }

        /**
         * Calculates the fast initial tail probe bytes required before launching the player.
         * Capped at 1 MB so 4K remuxes don't wait on 8 MB of tail pieces before first frame,
         * while any additional Cues/moov bytes are fetched on-demand via [planForRange].
         */
        fun calculateInitialTailBytes(fileLength: Long): Long {
            if (fileLength <= 0L) return 0L
            return minOf(fileLength, INITIAL_TAIL_PROBE_BYTES)
        }
    }

    private var lastPlannedPiece: Int = -1

    val startupPieceEnd: Int = (
        (fileOffset + minOf(maxOf(0L, fileLength - 1L), startupBufferBytes)) / pieceLength
    ).toInt().coerceIn(firstPiece, lastPiece)

    val tailBytes: Long = calculateTailBytes(fileLength, isMkv)

    val initialTailBytes: Long = calculateInitialTailBytes(fileLength)

    val initialTailPieceStart: Int = (
        (fileOffset + maxOf(0L, fileLength - initialTailBytes)) / pieceLength
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
        return fileLength > tailBytes && rangeStart > 0L && rangeStart >= fileLength - tailBytes
    }

    /**
     * Generates a streaming piece plan based on the current playhead or requested byte range.
     *
     * Uses staggered staircase deadlines (`150ms` -> `3000ms`) and tiered priorities (`TOP_PRIORITY`
     * for critical immediate pieces, `SIX` for lookahead) so `libtorrent`'s piece picker never suffers
     * from flat-deadline starvation when 15-30 pieces are simultaneously queued.
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
        val priorities = mutableMapOf<Int, Priority>()

        // 1. Ensure startup/head pieces are verified ONLY when starting up or probing container headers
        if (isHead) {
            var headRank = 0
            for (p in firstPiece..startupPieceEnd) {
                if (!tracker.isPieceVerified(p)) {
                    prioritizedPieces.add(p)
                    if (headRank < CRITICAL_HEAD_PIECES) {
                        deadlines[p] = IMMEDIATE_DEADLINE_MS + (headRank * 50)
                        priorities[p] = Priority.TOP_PRIORITY
                    } else {
                        val stepOffset = (headRank - CRITICAL_HEAD_PIECES) * LOOKAHEAD_STEP_DEADLINE_MS
                        deadlines[p] = minOf(LOOKAHEAD_BASE_DEADLINE_MS + stepOffset, MAX_DEADLINE_MS)
                        priorities[p] = Priority.SIX
                    }
                    headRank++
                }
            }
        }

        // 2. Ensure tail pieces are prioritized ONLY when probing container index (Cues/moov)
        if (isTail) {
            var tailRank = 0
            for (p in currentPiece..lastPiece) {
                if (!tracker.isPieceVerified(p)) {
                    prioritizedPieces.add(p)
                    deadlines[p] = minOf(IMMEDIATE_DEADLINE_MS + (tailRank * 75), 1200)
                    priorities[p] = Priority.TOP_PRIORITY
                    tailRank++
                }
            }
        }

        // 3. Prioritize the active playback window with staggered staircase deadlines
        val windowStart = if (isTail) {
            // For tail probes, do not move the sequential window forward
            lastPlannedPiece.takeIf { it != -1 } ?: firstPiece
        } else {
            currentPiece
        }

        val windowEnd = minOf(windowStart + lookaheadPieces, lastPiece)
        val seqRangeEnd = minOf(windowStart + maxOf(lookaheadPieces, SEQUENTIAL_WINDOW_PIECES), lastPiece)

        var windowRank = 0
        for (p in windowStart..windowEnd) {
            if (!tracker.isPieceVerified(p)) {
                prioritizedPieces.add(p)
                val computedDeadline = if (windowRank < CRITICAL_HEAD_PIECES) {
                    IMMEDIATE_DEADLINE_MS + (windowRank * 50)
                } else {
                    val stepOffset = (windowRank - CRITICAL_HEAD_PIECES) * LOOKAHEAD_STEP_DEADLINE_MS
                    minOf(LOOKAHEAD_BASE_DEADLINE_MS + stepOffset, MAX_DEADLINE_MS)
                }
                val existingDeadline = deadlines[p]
                if (existingDeadline == null || computedDeadline < existingDeadline) {
                    deadlines[p] = computedDeadline
                }
                if (windowRank < CRITICAL_HEAD_PIECES) {
                    priorities[p] = Priority.TOP_PRIORITY
                } else if (priorities[p] != Priority.TOP_PRIORITY) {
                    priorities[p] = Priority.SIX
                }
                windowRank++
            }
        }

        return StreamingPlan(
            currentPlayheadPiece = currentPiece,
            sequentialRangeStart = windowStart,
            sequentialRangeEnd = seqRangeEnd,
            piecesToPrioritize = prioritizedPieces.distinct(),
            deadlines = deadlines,
            piecePriorities = priorities,
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
    val sequentialRangeEnd: Int = sequentialRangeStart + StreamingPiecePlanner.SEQUENTIAL_WINDOW_PIECES,
    val piecesToPrioritize: List<Int>,
    val deadlines: Map<Int, Int>, // pieceIndex -> deadlineMs
    val piecePriorities: Map<Int, Priority> = emptyMap(), // pieceIndex -> Priority
    val isSeekJump: Boolean,
    val isTailProbe: Boolean
)
