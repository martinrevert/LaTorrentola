package com.martinrevert.latorrentola.service.streaming

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.libtorrent4j.Priority

class StreamingPiecePlannerTest {

    // 100 MB file, 1 MB pieceLength -> 100 pieces (0..99)
    private val fileOffset = 0L
    private val fileLength = 100L * 1024L * 1024L
    private val pieceLength = 1024 * 1024
    private val firstPiece = 0
    private val lastPiece = 99

    @Test
    fun `calculateResumePiece correctly maps mid-point time to piece index`() {
        val planner = StreamingPiecePlanner(
            fileOffset = fileOffset,
            fileLength = fileLength,
            pieceLength = pieceLength,
            firstPiece = firstPiece,
            lastPiece = lastPiece
        )

        // Halfway point: 60s in out of 120s -> 50 MB -> piece 50
        val piece50 = planner.calculateResumePiece(resumePositionMs = 60_000L, durationMs = 120_000L)
        assertThat(piece50).isEqualTo(50)

        // Beginning
        val piece0 = planner.calculateResumePiece(resumePositionMs = 0L, durationMs = 120_000L)
        assertThat(piece0).isEqualTo(0)

        // Near end: 110s out of 120s -> ~91.6 MB -> piece 91
        val piece91 = planner.calculateResumePiece(resumePositionMs = 110_000L, durationMs = 120_000L)
        assertThat(piece91).isEqualTo(91)
    }

    @Test
    fun `isTailProbe detects ranges within tail threshold`() {
        val planner = StreamingPiecePlanner(
            fileOffset = fileOffset,
            fileLength = fileLength,
            pieceLength = pieceLength,
            firstPiece = firstPiece,
            lastPiece = lastPiece,
            isMkv = true // tailBytes = 5 MB (last 5 MB is 95MB to 100MB)
        )

        // Byte 96 MB -> tail probe
        assertThat(planner.isTailProbe(96L * 1024L * 1024L, 97L * 1024L * 1024L)).isTrue()

        // Byte 10 MB -> not tail probe
        assertThat(planner.isTailProbe(10L * 1024L * 1024L, 11L * 1024L * 1024L)).isFalse()
    }

    @Test
    fun `isTailProbe returns false when fileLength is less than or equal to tailBytes or rangeStart is zero`() {
        val smallPlanner = StreamingPiecePlanner(
            fileOffset = 0L,
            fileLength = 3L * 1024L * 1024L, // 3 MB <= 5 MB MKV tailBytes
            pieceLength = pieceLength,
            firstPiece = 0,
            lastPiece = 2,
            isMkv = true
        )

        assertThat(smallPlanner.isTailProbe(0L, 1024L)).isFalse()
        assertThat(smallPlanner.isTailProbe(2L * 1024L * 1024L, 3L * 1024L * 1024L - 1L)).isFalse()
    }

    @Test
    fun `tail probe does not move sequentialRangeStart and prioritizes starting at currentPiece`() {
        val planner = StreamingPiecePlanner(
            fileOffset = fileOffset,
            fileLength = fileLength,
            pieceLength = pieceLength,
            firstPiece = firstPiece,
            lastPiece = lastPiece,
            isMkv = true
        )
        val tracker = VerifiedPieceTracker(totalPieces = 100)

        // 1. Initial playhead at 20 MB (piece 20)
        val plan1 = planner.planForRange(20L * 1024L * 1024L, 21L * 1024L * 1024L, tracker)
        assertThat(plan1.sequentialRangeStart).isEqualTo(20)
        assertThat(plan1.sequentialRangeEnd).isEqualTo(20 + StreamingPiecePlanner.SEQUENTIAL_WINDOW_PIECES)
        assertThat(plan1.isTailProbe).isFalse()

        // 2. ExoPlayer peeks at tail (98 MB -> piece 98) to read MKV cues
        val tailPlan = planner.planForRange(98L * 1024L * 1024L, 99L * 1024L * 1024L, tracker)
        assertThat(tailPlan.isTailProbe).isTrue()
        // sequentialRangeStart MUST stay at 20, not jump to 98!
        assertThat(tailPlan.sequentialRangeStart).isEqualTo(20)
        // Piece 98 (currentPiece) receives IMMEDIATE_DEADLINE_MS (150ms) and pieces 95..97 are not queued ahead of it
        assertThat(tailPlan.deadlines[98]).isEqualTo(StreamingPiecePlanner.IMMEDIATE_DEADLINE_MS)
        assertThat(tailPlan.deadlines[99]).isEqualTo(StreamingPiecePlanner.IMMEDIATE_DEADLINE_MS + 75)
        assertThat(tailPlan.piecesToPrioritize).doesNotContain(95)
    }

    @Test
    fun `seek jump is detected when playhead moves significantly`() {
        val planner = StreamingPiecePlanner(
            fileOffset = fileOffset,
            fileLength = fileLength,
            pieceLength = pieceLength,
            firstPiece = firstPiece,
            lastPiece = lastPiece
        )
        val tracker = VerifiedPieceTracker(totalPieces = 100)

        // Start at piece 5
        val plan1 = planner.planForRange(5L * 1024L * 1024L, 6L * 1024L * 1024L, tracker)
        assertThat(plan1.isSeekJump).isFalse()

        // Normal sequential read: piece 6 -> no seek jump
        val plan2 = planner.planForRange(6L * 1024L * 1024L, 7L * 1024L * 1024L, tracker)
        assertThat(plan2.isSeekJump).isFalse()

        // Big forward jump: piece 50 -> seek jump true
        val plan3 = planner.planForRange(50L * 1024L * 1024L, 51L * 1024L * 1024L, tracker)
        assertThat(plan3.isSeekJump).isTrue()
        assertThat(plan3.sequentialRangeStart).isEqualTo(50)
        assertThat(plan3.sequentialRangeEnd).isEqualTo(98)

        // Backward jump: piece 30 -> seek jump true
        val plan4 = planner.planForRange(30L * 1024L * 1024L, 31L * 1024L * 1024L, tracker)
        assertThat(plan4.isSeekJump).isTrue()
        assertThat(plan4.sequentialRangeStart).isEqualTo(30)
        assertThat(plan4.sequentialRangeEnd).isEqualTo(30 + StreamingPiecePlanner.SEQUENTIAL_WINDOW_PIECES)
    }

    @Test
    fun `already verified pieces are excluded from prioritized list and deadlines`() {
        val planner = StreamingPiecePlanner(
            fileOffset = fileOffset,
            fileLength = fileLength,
            pieceLength = pieceLength,
            firstPiece = firstPiece,
            lastPiece = lastPiece
        )
        val tracker = VerifiedPieceTracker(totalPieces = 100)

        // Mark head pieces 0..5 verified
        for (i in 0..5) {
            tracker.markPieceVerified(i)
        }

        // Plan for start (piece 0)
        val plan = planner.planForRange(0L, 1024L, tracker, lookaheadPieces = 10)

        // Pieces 0..5 should NOT be in piecesToPrioritize because they are already verified!
        assertThat(plan.piecesToPrioritize).doesNotContain(0)
        assertThat(plan.piecesToPrioritize).doesNotContain(5)
        assertThat(plan.deadlines.containsKey(0)).isFalse()
        assertThat(plan.deadlines.containsKey(5)).isFalse()

        // Piece 6 should be prioritized with immediate deadline (150ms) and TOP_PRIORITY
        assertThat(plan.piecesToPrioritize).contains(6)
        assertThat(plan.deadlines[6]).isEqualTo(StreamingPiecePlanner.IMMEDIATE_DEADLINE_MS)
        assertThat(plan.piecePriorities[6]).isEqualTo(Priority.TOP_PRIORITY)
    }

    @Test
    fun `planForRange assigns staggered staircase deadlines and tiered priorities`() {
        val planner = StreamingPiecePlanner(
            fileOffset = fileOffset,
            fileLength = fileLength,
            pieceLength = pieceLength,
            firstPiece = firstPiece,
            lastPiece = lastPiece
        )
        val tracker = VerifiedPieceTracker(totalPieces = 100)

        val plan = planner.planForRange(0L, 1024L, tracker, lookaheadPieces = 12)

        // First 4 unverified pieces get critical immediate staircase deadlines (150, 200, 250, 300) and TOP_PRIORITY
        assertThat(plan.deadlines[0]).isEqualTo(150)
        assertThat(plan.deadlines[1]).isEqualTo(200)
        assertThat(plan.deadlines[2]).isEqualTo(250)
        assertThat(plan.deadlines[3]).isEqualTo(300)
        assertThat(plan.piecePriorities[0]).isEqualTo(Priority.TOP_PRIORITY)
        assertThat(plan.piecePriorities[3]).isEqualTo(Priority.TOP_PRIORITY)

        // Subsequent lookahead pieces step up from 400ms and use Priority.SIX
        assertThat(plan.deadlines[4]).isEqualTo(400)
        assertThat(plan.deadlines[5]).isEqualTo(500)
        assertThat(plan.piecePriorities[4]).isEqualTo(Priority.SIX)
        assertThat(plan.piecePriorities[5]).isEqualTo(Priority.SIX)
    }

    @Test
    fun `calculateStartupBufferBytes scales proportionally with bounds`() {
        // Small file (700 MB): 700 MB * 0.003 = 2.1 MB -> clamped to MIN (8 MB)
        val smallFile = 700L * 1024L * 1024L
        assertThat(StreamingPiecePlanner.calculateStartupBufferBytes(smallFile))
            .isEqualTo(8L * 1024L * 1024L)

        // Mid file (4 GB): 4 * 1024 * 1024 * 1024 * 0.003 = 12.88 MB
        val midFile = 4L * 1024L * 1024L * 1024L
        val midBuffer = StreamingPiecePlanner.calculateStartupBufferBytes(midFile)
        assertThat(midBuffer).isGreaterThan(8L * 1024L * 1024L)
        assertThat(midBuffer).isLessThan(32L * 1024L * 1024L)

        // Huge 4K remux file (25 GB): 25 GB * 0.003 = 75 MB -> clamped to MAX (32 MB)
        val hugeFile = 25L * 1024L * 1024L * 1024L
        assertThat(StreamingPiecePlanner.calculateStartupBufferBytes(hugeFile))
            .isEqualTo(32L * 1024L * 1024L)

        // Zero or negative file length returns minimum safe buffer
        assertThat(StreamingPiecePlanner.calculateStartupBufferBytes(0L))
            .isEqualTo(8L * 1024L * 1024L)
    }

    @Test
    fun `calculateTailBytes and calculateInitialTailBytes adapt based on container and file size`() {
        val normalMp4 = 1L * 1024L * 1024L * 1024L
        assertThat(StreamingPiecePlanner.calculateTailBytes(normalMp4, isMkv = false))
            .isEqualTo(2_000_000L)

        val normalMkv = 2L * 1024L * 1024L * 1024L
        assertThat(StreamingPiecePlanner.calculateTailBytes(normalMkv, isMkv = true))
            .isEqualTo(5_000_000L)

        val largeMkv = 8L * 1024L * 1024L * 1024L // 8 GB
        assertThat(StreamingPiecePlanner.calculateTailBytes(largeMkv, isMkv = true))
            .isEqualTo(6_000_000L)

        val huge4kRemux = 20L * 1024L * 1024L * 1024L // 20 GB
        assertThat(StreamingPiecePlanner.calculateTailBytes(huge4kRemux, isMkv = true))
            .isEqualTo(8_000_000L)

        // Initial tail probe is capped at 1 MB for rapid TTFF on large files
        assertThat(StreamingPiecePlanner.calculateInitialTailBytes(huge4kRemux))
            .isEqualTo(StreamingPiecePlanner.INITIAL_TAIL_PROBE_BYTES)
    }
}
