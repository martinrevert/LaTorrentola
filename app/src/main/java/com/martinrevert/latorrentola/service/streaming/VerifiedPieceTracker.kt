package com.martinrevert.latorrentola.service.streaming

import java.util.BitSet
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe, non-blocking in-memory tracker for verified torrent pieces.
 * Eliminates synchronous JNI calls during media streaming reads.
 */
class VerifiedPieceTracker(
    val totalPieces: Int = 0,
    val fileOffset: Long = 0L,
    val pieceLength: Int = 0
) {
    private val lock = ReentrantLock()
    private val pieceCondition = lock.newCondition()
    private val verifiedBitSet = BitSet(maxOf(0, totalPieces))

    @Volatile
    var isAllVerified: Boolean = false
        private set

    fun markPieceVerified(pieceIndex: Int) {
        if (isAllVerified) return
        lock.withLock {
            if (pieceIndex in 0 until totalPieces) {
                verifiedBitSet.set(pieceIndex)
            }
            pieceCondition.signalAll()
        }
    }

    fun markPiecesVerified(pieces: Collection<Int>) {
        if (isAllVerified || pieces.isEmpty()) return
        lock.withLock {
            for (p in pieces) {
                if (p in 0 until totalPieces) {
                    verifiedBitSet.set(p)
                }
            }
            pieceCondition.signalAll()
        }
    }

    fun markPiecesVerified(bitSet: BitSet) {
        if (isAllVerified) return
        lock.withLock {
            verifiedBitSet.or(bitSet)
            pieceCondition.signalAll()
        }
    }

    fun markPiecesVerified(pieces: BooleanArray) {
        if (isAllVerified) return
        lock.withLock {
            for (i in pieces.indices) {
                if (i < totalPieces && pieces[i]) {
                    verifiedBitSet.set(i)
                }
            }
            pieceCondition.signalAll()
        }
    }

    fun getUnverifiedPieces(from: Int, to: Int): List<Int> {
        if (isAllVerified || totalPieces <= 0) return emptyList()
        val start = from.coerceIn(0, totalPieces - 1)
        val end = to.coerceIn(start, totalPieces - 1)
        return lock.withLock {
            val result = mutableListOf<Int>()
            var nextClear = verifiedBitSet.nextClearBit(start)
            while (nextClear in start..end) {
                result.add(nextClear)
                nextClear = verifiedBitSet.nextClearBit(nextClear + 1)
            }
            result
        }
    }

    fun setAllVerified() {
        lock.withLock {
            isAllVerified = true
            if (totalPieces > 0) {
                verifiedBitSet.set(0, totalPieces)
            }
            pieceCondition.signalAll()
        }
    }

    fun isPieceVerified(pieceIndex: Int): Boolean {
        if (isAllVerified) return true
        if (pieceIndex !in 0 until totalPieces) return false
        return lock.withLock { verifiedBitSet.get(pieceIndex) }
    }

    fun isRangeVerified(startByte: Long, endByte: Long, fallbackChecker: ((Int) -> Boolean)? = null): Boolean {
        return isRangeVerified(startByte, endByte, fileOffset, pieceLength, fallbackChecker)
    }

    fun awaitRange(startByte: Long, endByte: Long, timeoutMs: Long, fallbackChecker: ((Int) -> Boolean)? = null): Boolean {
        return awaitRange(startByte, endByte, fileOffset, pieceLength, timeoutMs, fallbackChecker)
    }

    fun isRangeVerified(
        startByte: Long,
        endByte: Long,
        fileOffset: Long,
        pieceLength: Int,
        fallbackChecker: ((Int) -> Boolean)? = null
    ): Boolean {
        if (isAllVerified) return true
        if (pieceLength <= 0 || startByte < 0 || endByte < startByte || totalPieces <= 0) return false
        val maxPiece = totalPieces - 1
        val firstPiece = ((fileOffset + startByte) / pieceLength).toInt().coerceIn(0, maxPiece)
        val lastPiece = ((fileOffset + endByte) / pieceLength).toInt().coerceIn(0, maxPiece)

        val unverified = mutableListOf<Int>()
        lock.withLock {
            for (p in firstPiece..lastPiece) {
                if (p !in 0 until totalPieces) return false
                if (!verifiedBitSet.get(p)) {
                    unverified.add(p)
                }
            }
        }
        if (unverified.isEmpty()) return true
        if (fallbackChecker == null) return false

        // Check fallback OUTSIDE lock.withLock to prevent deadlock with sessionLock!
        val newlyVerified = mutableListOf<Int>()
        for (p in unverified) {
            if (fallbackChecker.invoke(p)) {
                newlyVerified.add(p)
            } else {
                return false
            }
        }
        if (newlyVerified.isNotEmpty()) {
            markPiecesVerified(newlyVerified)
        }
        return true
    }

    fun awaitRange(
        startByte: Long,
        endByte: Long,
        fileOffset: Long,
        pieceLength: Int,
        timeoutMs: Long,
        fallbackChecker: ((Int) -> Boolean)? = null
    ): Boolean {
        if (isAllVerified) return true
        if (isRangeVerified(startByte, endByte, fileOffset, pieceLength, fallbackChecker)) return true
        if (totalPieces <= 0) return false

        val maxPiece = totalPieces - 1
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)

        while (true) {
            if (isAllVerified) return true
            val firstPiece = ((fileOffset + startByte) / pieceLength).toInt().coerceIn(0, maxPiece)
            val lastPiece = ((fileOffset + endByte) / pieceLength).toInt().coerceIn(0, maxPiece)

            val missing = mutableListOf<Int>()
            lock.withLock {
                for (p in firstPiece..lastPiece) {
                    if (p !in 0 until totalPieces) return false
                    if (!verifiedBitSet.get(p)) {
                        missing.add(p)
                    }
                }
            }
            if (missing.isEmpty()) return true

            // Check fallback OUTSIDE lock.withLock to prevent deadlock with sessionLock!
            if (fallbackChecker != null) {
                val found = mutableListOf<Int>()
                var allFound = true
                for (p in missing) {
                    if (fallbackChecker.invoke(p)) {
                        found.add(p)
                    } else {
                        allFound = false
                    }
                }
                if (found.isNotEmpty()) {
                    markPiecesVerified(found)
                }
                if (allFound) return true
            }

            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos <= 0) {
                return false
            }

            lock.withLock {
                // Re-verify with lock held before waiting to eliminate race condition
                var stillMissing = false
                for (p in firstPiece..lastPiece) {
                    if (p in 0 until totalPieces && !verifiedBitSet.get(p)) {
                        stillMissing = true
                        break
                    }
                }
                if (!stillMissing) return true

                try {
                    pieceCondition.awaitNanos(remainingNanos)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
    }

    fun verifiedPieceCount(): Int {
        if (isAllVerified) return totalPieces
        return lock.withLock { verifiedBitSet.cardinality() }
    }

    fun reset() {
        lock.withLock {
            isAllVerified = false
            verifiedBitSet.clear()
            pieceCondition.signalAll()
        }
    }
}
