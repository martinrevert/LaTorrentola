package com.martinrevert.latorrentola.service.storage.remote

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.util.ArrayDeque

/**
 * High-performance asynchronous prefetching input stream for SMB (smbj) file streaming.
 *
 * Solves the single-threaded stop-and-wait bottleneck by reading ahead from the remote SMB server
 * in large chunks (e.g. 1 MB) using a background worker thread into a memory-pooled buffer.
 *
 * Consumer reads (ExoPlayer's DataSource and NanoHTTPD Cast proxy) return immediately from RAM
 * without blocking on synchronous network round-trips.
 */
class SmbPrefetchInputStream(
    private val file: com.hierynomus.smbj.share.File,
    private val startOffset: Long,
    private val totalSize: Long = -1L,
    private val chunkSize: Int = 1024 * 1024, // 1 MB per SMB read request
    maxBufferBytes: Int = 16 * 1024 * 1024 // 16 MB ring buffer
) : InputStream() {

    private class Chunk(
        val data: ByteArray,
        var offset: Int,
        val length: Int
    )

    private val maxChunks = (maxBufferBytes / chunkSize).coerceAtLeast(4)
    private val lock = Any()

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun Any.waitCompat(timeoutMs: Long = 0L) {
        if (timeoutMs > 0) {
            (this as java.lang.Object).wait(timeoutMs)
        } else {
            (this as java.lang.Object).wait()
        }
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun Any.notifyAllCompat() {
        (this as java.lang.Object).notifyAll()
    }

    private val readyChunks = ArrayDeque<Chunk>()
    private val freeBuffers = ArrayDeque<ByteArray>()

    @Volatile
    private var isClosed = false

    @Volatile
    private var isEof = false

    @Volatile
    private var readError: IOException? = null

    private var producerOffset = startOffset
    private var currentChunk: Chunk? = null

    private val workerThread = Thread({
        prefetchLoop()
    }, "SmbPrefetchWorker-${file.path.substringAfterLast('\\')}").apply {
        isDaemon = true
        priority = Thread.NORM_PRIORITY + 1
        start()
    }

    private fun prefetchLoop() {
        try {
            while (!isClosed && !isEof) {
                var buffer: ByteArray? = null
                synchronized(lock) {
                    while (!isClosed && readyChunks.size >= maxChunks) {
                        lock.waitCompat()
                    }
                    if (isClosed) return
                    buffer = if (freeBuffers.isNotEmpty()) freeBuffers.removeFirst() else ByteArray(chunkSize)
                }

                val buf = buffer ?: ByteArray(chunkSize)
                val bytesToRead = if (totalSize > 0 && producerOffset + chunkSize > totalSize) {
                    (totalSize - producerOffset).toInt()
                } else {
                    chunkSize
                }

                if (bytesToRead <= 0) {
                    synchronized(lock) {
                        isEof = true
                        freeBuffers.addLast(buf)
                        lock.notifyAllCompat()
                    }
                    break
                }

                val bytesRead = try {
                    file.read(buf, producerOffset, 0, bytesToRead)
                } catch (e: Exception) {
                    synchronized(lock) {
                        if (!isClosed) {
                            readError = IOException("SMB prefetch read error at offset $producerOffset: ${e.message}", e)
                        }
                        freeBuffers.addLast(buf)
                        lock.notifyAllCompat()
                    }
                    break
                }

                if (bytesRead > 0) {
                    producerOffset += bytesRead
                    synchronized(lock) {
                        if (!isClosed) {
                            readyChunks.addLast(Chunk(buf, 0, bytesRead))
                            lock.notifyAllCompat()
                        } else {
                            freeBuffers.addLast(buf)
                        }
                    }
                } else {
                    synchronized(lock) {
                        isEof = true
                        freeBuffers.addLast(buf)
                        lock.notifyAllCompat()
                    }
                    break
                }
            }
        } catch (_: InterruptedException) {
            // Worker thread interrupted on stream closure
        } finally {
            synchronized(lock) {
                isEof = true
                lock.notifyAllCompat()
            }
        }
    }

    override fun read(): Int {
        val single = ByteArray(1)
        val n = read(single, 0, 1)
        return if (n > 0) single[0].toInt() and 0xFF else -1
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (isClosed) return -1

        synchronized(lock) {
            while (currentChunk == null && readyChunks.isEmpty() && !isEof && readError == null && !isClosed) {
                try {
                    lock.waitCompat(1000)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return -1
                }
            }

            if (isClosed) return -1
            readError?.let { throw it }

            if (currentChunk == null && readyChunks.isEmpty()) {
                if (isEof) return -1
            }

            if (currentChunk == null && readyChunks.isNotEmpty()) {
                currentChunk = readyChunks.removeFirst()
                lock.notifyAllCompat()
            }

            val chunk = currentChunk ?: return if (isEof) -1 else 0
            val availableInChunk = chunk.length - chunk.offset
            val toCopy = minOf(len, availableInChunk)

            System.arraycopy(chunk.data, chunk.offset, b, off, toCopy)
            chunk.offset += toCopy

            if (chunk.offset >= chunk.length) {
                freeBuffers.addLast(chunk.data)
                currentChunk = null
                lock.notifyAllCompat()
            }

            return toCopy
        }
    }

    override fun available(): Int {
        synchronized(lock) {
            val fromCurrent = currentChunk?.let { it.length - it.offset } ?: 0
            val fromReady = readyChunks.sumOf { it.length - it.offset }
            return fromCurrent + fromReady
        }
    }

    override fun close() {
        synchronized(lock) {
            if (isClosed) return
            isClosed = true
            lock.notifyAllCompat()
        }
        workerThread.interrupt()
        try {
            file.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing SMB file handle: ${e.message}")
        }
        synchronized(lock) {
            readyChunks.clear()
            freeBuffers.clear()
            currentChunk = null
        }
    }

    companion object {
        private const val TAG = "SmbPrefetchStream"
    }
}
