package com.martinrevert.latorrentola.service

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.martinrevert.latorrentola.utils.mediaMimeType

/**
 * Serves one torrent file with HTTP byte-range support and verifies every requested piece first.
 *
 * @property mediaFile Selected media file backed by libtorrent storage.
 * @property expectedLength Metadata length of the selected file.
 * @property isRangeVerified Returns true only when all pieces overlapping a byte range passed hash checks.
 */
class VerifiedTorrentHttpServer(
    private val mediaFile: File,
    private val expectedLength: Long,
    private val isRangeVerified: (Long, Long) -> Boolean
) : NanoHTTPD("0.0.0.0", 0) {

    /** Random endpoint path component preventing unauthenticated access to app downloads. */
    private val accessToken = UUID.randomUUID().toString()

    /**
     * Starts listening for local-player and Cast receiver media requests.
     *
     * @return TCP port selected for this server.
     */
    fun startServer(): Int {
        start(SOCKET_READ_TIMEOUT, false)
        return listeningPort
    }

    /** Returns the localhost URL for the embedded Media3 player. */
    fun localUrl(): String = "http://127.0.0.1:$listeningPort/$accessToken"

    /** Returns the URL reachable by another device on the same local network. */
    fun lanUrl(): String {
        val address = Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filter(NetworkInterface::isUp)
            .sortedBy { network ->
                when {
                    network.name.startsWith("wlan", ignoreCase = true) -> 0
                    network.name.startsWith("eth", ignoreCase = true) -> 1
                    else -> 2
                }
            }
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress && !it.isLoopbackAddress }
            ?.hostAddress
            ?: error("No local-network address is available for Cast")
        return "http://$address:$listeningPort/$accessToken"
    }

    /**
     * Answers a single HTTP range request without reading any unverified bytes.
     *
     * @param session Incoming request and headers.
     * @return Bounded file response, range response, or an explicit unavailable/error status.
     */
    override fun serve(session: IHTTPSession): Response {
        if (session.uri.trim('/') != accessToken) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }

        val range = parseRange(session.headers["range"], expectedLength)
            ?: if (session.headers.containsKey("range")) {
                return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
            } else {
                0L to (expectedLength - 1L)
            }
        val (start, end) = range
        val length = end - start + 1L
        val initialVerifiedEnd = minOf(end, start + VERIFIED_CHUNK_BYTES - 1L)
        if (session.method == Method.HEAD) {
            return newFixedLengthResponse(Response.Status.OK, mediaFile.mediaMimeType(), null, length).apply {
                addHeader("Accept-Ranges", "bytes")
                addHeader("Content-Length", length.toString())
                addHeader("Content-Range", "bytes $start-$end/$expectedLength")
            }
        }
        if (
            expectedLength <= 0L ||
            !awaitVerified(start, initialVerifiedEnd)
        ) {
            return newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "Buffering")
                .apply { addHeader("Retry-After", "2") }
        }

        if (!mediaFile.isFile || mediaFile.length() < initialVerifiedEnd + 1L) {
            return newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "Buffering")
                .apply { addHeader("Retry-After", "2") }
        }

        val input = FileInputStream(mediaFile)
        skipFully(input, start)
        val body = VerifiedRangeInputStream(input, start, end, ::awaitVerified)
        val status = if (session.headers.containsKey("range")) {
            Response.Status.PARTIAL_CONTENT
        } else {
            Response.Status.OK
        }
        val response = newFixedLengthResponse(status, mediaFile.mediaMimeType(), body, length)
        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Content-Length", length.toString())
        response.addHeader("Cache-Control", "no-store")
        if (status == Response.Status.PARTIAL_CONTENT) {
            response.addHeader("Content-Range", "bytes $start-$end/$expectedLength")
        }
        return response
    }

    /**
     * Waits briefly for every byte in a receiver-requested range to become verified.
     *
     * @param start Inclusive byte offset.
     * @param end Inclusive byte offset.
     * @return Whether all pieces covering the interval verified before timeout.
     */
    private fun awaitVerified(start: Long, end: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (System.nanoTime() < deadline) {
            if (isRangeVerified(start, end)) return true
            try {
                Thread.sleep(250)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return isRangeVerified(start, end)
    }

    /**
     * Parses a single HTTP byte range and clamps an open-ended range to the media length.
     *
     * @param header Range request header, or `null` for the complete representation.
     * @param length Total media length in bytes.
     * @return Inclusive start/end offsets, or `null` for an invalid or unsupported range.
     */
    private fun parseRange(header: String?, length: Long): Pair<Long, Long>? {
        if (header == null) return 0L to (length - 1L)
        if (length <= 0L || !header.startsWith("bytes=") || header.contains(',')) return null
        val components = header.removePrefix("bytes=").split('-', limit = 2)
        if (components.size != 2) return null
        val requestedStart = components[0].toLongOrNull()
        val requestedEnd = components[1].toLongOrNull()
        val start: Long
        val end: Long
        if (requestedStart == null) {
            val suffixLength = requestedEnd?.takeIf { it > 0 } ?: return null
            start = (length - suffixLength).coerceAtLeast(0)
            end = length - 1L
        } else {
            start = requestedStart
            end = (requestedEnd ?: (length - 1L)).coerceAtMost(length - 1L)
        }
        return if (start < 0 || start >= length || end < start) null else start to end
    }

    /**
     * Advances a file stream exactly to [offset], failing if the backing file ends early.
     *
     * @param input Media file stream.
     * @param offset Number of bytes to advance.
     */
    private fun skipFully(input: InputStream, offset: Long) {
        var remaining = offset
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() == -1) error("Media file ended before the requested range")
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    /**
     * Streams only bounded chunks after their backing torrent pieces have been hash-verified.
     *
     * @param input Backing media file stream positioned at the selected range start.
     * @param start Inclusive absolute offset of the HTTP range.
     * @param end Inclusive absolute offset of the HTTP range.
     * @param waitForRangeVerified Waits until complete torrent pieces cover a byte interval.
     */
    private class VerifiedRangeInputStream(
        input: InputStream,
        private val start: Long,
        private val end: Long,
        private val waitForRangeVerified: (Long, Long) -> Boolean
    ) : FilterInputStream(input) {
        private var currentOffset = start
        private var verifiedThrough =
            minOf(end, start + VerifiedTorrentHttpServer.VERIFIED_CHUNK_BYTES - 1L)

        /** Reads one byte after verifying the bounded piece range containing it. */
        override fun read(): Int {
            if (currentOffset > end) return -1
            ensureVerified()
            val value = super.read()
            if (value >= 0) currentOffset++
            return value
        }

        /** Reads only from the current verified chunk and never crosses into an unchecked range. */
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (currentOffset > end) return -1
            ensureVerified()
            val safeLength = minOf(
                length.toLong(),
                verifiedThrough - currentOffset + 1L,
                end - currentOffset + 1L
            ).toInt()
            val count = super.read(buffer, offset, safeLength)
            if (count > 0) currentOffset += count
            return count
        }

        /** Waits for the next bounded byte interval before exposing it to the socket. */
        private fun ensureVerified() {
            if (currentOffset <= verifiedThrough) return
            val nextChunkEnd =
                minOf(end, currentOffset + VerifiedTorrentHttpServer.VERIFIED_CHUNK_BYTES - 1L)
            if (!waitForRangeVerified(currentOffset, nextChunkEnd)) {
                throw IOException("Torrent pieces did not verify before the stream timed out")
            }
            verifiedThrough = nextChunkEnd
        }
    }

    private companion object {
        /** NanoHTTPD socket timeout used while a receiver requests media. */
        const val SOCKET_READ_TIMEOUT = 5_000

        /** Maximum byte interval verified before the response stream advances. */
        const val VERIFIED_CHUNK_BYTES = 1024L * 1024L
    }
}
