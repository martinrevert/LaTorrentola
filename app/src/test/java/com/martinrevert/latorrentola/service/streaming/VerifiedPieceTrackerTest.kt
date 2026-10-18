package com.martinrevert.latorrentola.service.streaming

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.BitSet

class VerifiedPieceTrackerTest {

    @Test
    fun `isPieceVerified returns true only when piece is marked`() {
        val tracker = VerifiedPieceTracker(totalPieces = 100)

        assertThat(tracker.isPieceVerified(0)).isFalse()
        assertThat(tracker.isPieceVerified(50)).isFalse()

        tracker.markPieceVerified(50)

        assertThat(tracker.isPieceVerified(0)).isFalse()
        assertThat(tracker.isPieceVerified(50)).isTrue()
    }

    @Test
    fun `isRangeVerified correctly maps byte range to piece indices`() {
        // pieceLength = 1MB (1,048,576 bytes), fileOffset = 0
        val pieceLen = 1024 * 1024
        val tracker = VerifiedPieceTracker(totalPieces = 10)

        // Range covering piece 0 only: 0 to 500,000
        assertThat(tracker.isRangeVerified(0L, 500_000L, 0L, pieceLen)).isFalse()
        tracker.markPieceVerified(0)
        assertThat(tracker.isRangeVerified(0L, 500_000L, 0L, pieceLen)).isTrue()

        // Range spanning piece 0 and piece 1: 500,000 to 1,500,000
        assertThat(tracker.isRangeVerified(500_000L, 1_500_000L, 0L, pieceLen)).isFalse()
        tracker.markPieceVerified(1)
        assertThat(tracker.isRangeVerified(500_000L, 1_500_000L, 0L, pieceLen)).isTrue()
    }

    @Test
    fun `isRangeVerified respects fileOffset when mapped within multi-file torrent`() {
        // fileOffset is 2.5MB into the torrent, pieceLength = 1MB
        val pieceLen = 1024 * 1024
        val fileOffset = 2_500_000L // sits in piece 2 (2MB-3MB)
        val tracker = VerifiedPieceTracker(totalPieces = 10)

        // Byte 0 of this file is in piece 2
        tracker.markPieceVerified(2)
        assertThat(tracker.isRangeVerified(0L, 100_000L, fileOffset, pieceLen)).isTrue()

        // Byte 700,000 of this file is at offset 3,200,000 (piece 3)
        assertThat(tracker.isRangeVerified(0L, 700_000L, fileOffset, pieceLen)).isFalse()
        tracker.markPieceVerified(3)
        assertThat(tracker.isRangeVerified(0L, 700_000L, fileOffset, pieceLen)).isTrue()
    }

    @Test
    fun `awaitRange immediately returns true if already verified`() {
        val tracker = VerifiedPieceTracker(totalPieces = 5)
        tracker.markPieceVerified(0)

        val success = tracker.awaitRange(0L, 100L, 0L, 1024, 1000L)
        assertThat(success).isTrue()
    }

    @Test
    fun `awaitRange times out if pieces are never marked`() {
        val tracker = VerifiedPieceTracker(totalPieces = 5)

        val startTime = System.currentTimeMillis()
        val success = tracker.awaitRange(0L, 100L, 0L, 1024, 100L)
        val elapsed = System.currentTimeMillis() - startTime

        assertThat(success).isFalse()
        assertThat(elapsed).isAtLeast(90L)
    }

    @Test
    fun `awaitRange unblocks as soon as needed piece is marked verified on another thread`() = runBlocking {
        val tracker = VerifiedPieceTracker(totalPieces = 10)
        val pieceLen = 1024

        var awaitResult = false
        val job = launch(Dispatchers.IO) {
            awaitResult = tracker.awaitRange(0L, 500L, 0L, pieceLen, 2000L)
        }

        delay(100)
        tracker.markPieceVerified(0)

        job.join()
        assertThat(awaitResult).isTrue()
    }

    @Test
    fun `setAllVerified immediately verifies all ranges`() {
        val tracker = VerifiedPieceTracker(totalPieces = 20)
        tracker.setAllVerified()

        assertThat(tracker.isAllVerified).isTrue()
        assertThat(tracker.isRangeVerified(0L, 10_000_000L, 0L, 1024)).isTrue()
        assertThat(tracker.awaitRange(0L, 10_000_000L, 0L, 1024, 500L)).isTrue()
        assertThat(tracker.isPieceVerified(15)).isTrue()
    }

    @Test
    fun `markPiecesVerified with BitSet updates multiple pieces`() {
        val tracker = VerifiedPieceTracker(totalPieces = 10)
        val bitSet = BitSet()
        bitSet.set(1)
        bitSet.set(3)
        bitSet.set(5)

        tracker.markPiecesVerified(bitSet)

        assertThat(tracker.isPieceVerified(1)).isTrue()
        assertThat(tracker.isPieceVerified(2)).isFalse()
        assertThat(tracker.isPieceVerified(3)).isTrue()
        assertThat(tracker.isPieceVerified(5)).isTrue()
        assertThat(tracker.verifiedPieceCount()).isEqualTo(3)
    }
}
