package com.martinrevert.latorrentola.service

import android.util.Log
import com.martinrevert.latorrentola.utils.AppLogger
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
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
    val accessToken: String = UUID.randomUUID().toString(),
    private val onRangeRequested: ((Long, Long) -> Unit)? = null,
    private val awaitRangeVerified: ((Long, Long, Long) -> Boolean)? = null,
    private val isRangeVerified: (Long, Long) -> Boolean
) : NanoHTTPD("0.0.0.0", 0) {

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
    fun localUrl(): String {
        val encodedName = java.net.URLEncoder.encode(mediaFile.name, "UTF-8").replace("+", "%20")
        return "http://127.0.0.1:$listeningPort/$accessToken/$encodedName"
    }

    /** Returns the URL reachable by another device on the same local network. */
    fun lanUrl(): String {
        val address = getLanIpAddress()
            ?: error("No local-network address is available for Cast")

        val encodedName = java.net.URLEncoder.encode(mediaFile.name, "UTF-8").replace("+", "%20")
        return "http://$address:$listeningPort/$accessToken/$encodedName"
    }

    /**
     * Answers a single HTTP range request or subtitle file request without reading any unverified bytes.
     *
     * @param session Incoming request and headers.
     * @return Bounded file response, range response, subtitle file, or an explicit unavailable/error status.
     */
    override fun serve(session: IHTTPSession): Response {
        Log.d(
            TAG,
            "HTTP ${session.method} ${session.uri} | Range: ${session.headers["range"]} | Origin: ${session.headers["origin"]}"
        )

        // Handle CORS preflight OPTIONS requests for Cast receivers
        if (session.method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "").apply {
                addCorsHeaders(this, session)
            }
        }

        val uri = session.uri.trim('/')

        // Handle LAN subtitle requests for Cast receivers
        // Supports both /{accessToken}/subtitles/{base64Path}/{filename} and /{accessToken}/{filename}/subtitles/{base64Path}/{filename}
        if (uri.startsWith("$accessToken/subtitles/") || (uri.startsWith(accessToken) && uri.contains("/subtitles/"))) {
            val parts = uri.substringAfter("/subtitles/").split('/')
            var subFile: File? = null

            if (parts.isNotEmpty()) {
                val base64Path = parts[0]
                val decodedPath = try {
                    String(
                        android.util.Base64.decode(
                            base64Path,
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
                        )
                    )
                } catch (_: Exception) {
                    try {
                        String(android.util.Base64.decode(base64Path, android.util.Base64.DEFAULT))
                    } catch (_: Exception) {
                        null
                    }
                }
                if (decodedPath != null) {
                    val candidate = File(decodedPath)
                    if (candidate.exists() && candidate.isFile && (candidate.name.endsWith(
                            ".vtt",
                            true
                        ) || candidate.name.endsWith(".srt", true))
                    ) {
                        subFile = candidate
                    }
                }
            }

            if (subFile == null) {
                // Fallback search
                val fileName = parts.lastOrNull() ?: ""
                val parent = mediaFile.parentFile
                val possibleLocations = listOfNotNull(
                    parent?.let { File(it, "opensubtitles/$fileName") },
                    parent?.let { File(it, fileName) },
                    parent?.parentFile?.let { File(it, "opensubtitles/$fileName") },
                    File(fileName)
                )
                subFile = possibleLocations.firstOrNull { it.exists() && it.isFile }
            }

            if (subFile != null) {
                return try {
                    val rawText = try {
                        subFile.readText(Charsets.UTF_8)
                    } catch (_: Exception) {
                        subFile.readText(Charsets.ISO_8859_1)
                    }
                    val vttText = convertSrtToVtt(rawText)
                    val vttBytes = vttText.toByteArray(Charsets.UTF_8)
                    val bis = java.io.ByteArrayInputStream(vttBytes)
                    Log.d(TAG, "Serving subtitle VTT for $uri (${vttBytes.size} bytes)")
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "text/vtt; charset=utf-8",
                        bis,
                        vttBytes.size.toLong()
                    ).apply {
                        addHeader("Accept-Ranges", "bytes")
                        addCorsHeaders(this, session)
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error serving subtitle VTT: ${e.message}", e)
                    newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR,
                        MIME_PLAINTEXT,
                        e.message.orEmpty()
                    ).apply { addCorsHeaders(this, session) }
                }
            } else {
                Log.w(TAG, "Subtitle file not found for URI $uri")
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "Subtitle file not found"
                ).apply { addCorsHeaders(this, session) }
            }
        }

        val isMediaUri = uri.contains(accessToken) && !uri.contains("/subtitles/")
        if (!isMediaUri) {
            Log.w(TAG, "URI not matched: $uri (expected token: $accessToken)")
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Not found"
            ).apply { addCorsHeaders(this, session) }
        }

        val rawRange = parseRange(session.headers["range"], expectedLength)
            ?: if (session.headers.containsKey("range")) {
                Log.w(TAG, "Invalid Range requested: ${session.headers["range"]}")
                return newFixedLengthResponse(
                    Response.Status.RANGE_NOT_SATISFIABLE,
                    MIME_PLAINTEXT,
                    ""
                ).apply { addCorsHeaders(this, session) }
            } else {
                0L to (expectedLength - 1L)
            }

        val (rawStart, rawEnd) = rawRange
        val activeFile = mediaFile

        val start = rawStart.coerceIn(0L, maxOf(0L, expectedLength - 1L))
        val end = rawEnd.coerceIn(start, maxOf(start, expectedLength - 1L))
        val length = maxOf(0L, end - start + 1L)
        val httpMimeType = getResponseMimeType()

        val isRangeRequest = session.headers.containsKey("range")
        val status = if (isRangeRequest) Response.Status.PARTIAL_CONTENT else Response.Status.OK

        if (session.method == Method.HEAD) {
            Log.d(TAG, "HEAD request for $uri -> $httpMimeType length=$length range=$start-$end")
            return newFixedLengthResponse(status, httpMimeType, null, length).apply {
                addHeader("Accept-Ranges", "bytes")
                addHeader("Content-Length", length.toString())
                if (isRangeRequest) {
                    addHeader("Content-Range", "bytes $start-$end/$expectedLength")
                }
                addCorsHeaders(this, session)
            }
        }
        if (expectedLength <= 0L) {
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Invalid media length"
            ).apply { addCorsHeaders(this, session) }
        }

        // Check if file is ready on disk. If file exists and has data, skip wait loop completely.
        if (!activeFile.isFile || activeFile.length() < 1000L) {
            var fileWaitAttempts = 0
            while ((!activeFile.isFile || activeFile.length() < 1000L) && fileWaitAttempts < 20) {
                try {
                    Thread.sleep(100)
                } catch (_: InterruptedException) {
                }
                fileWaitAttempts++
            }
        }

        if (!activeFile.isFile || activeFile.length() == 0L) {
            AppLogger.e(TAG, "File not ready on disk for $uri")
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "File not ready on disk"
            ).apply { addCorsHeaders(this, session) }
        }

        Log.d(
            TAG,
            "Streaming GET $uri -> range $start-$end / $expectedLength (MIME: $httpMimeType, file: ${activeFile.name})"
        )
        val body = VerifiedRangeInputStream(activeFile, start, end, ::awaitVerified)
        val response = newFixedLengthResponse(status, httpMimeType, body, length)
        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Content-Length", length.toString())
        response.addHeader("Cache-Control", "no-store")
        if (status == Response.Status.PARTIAL_CONTENT) {
            response.addHeader("Content-Range", "bytes $start-$end/$expectedLength")
        }
        addCorsHeaders(response, session)
        return response
    }

    private fun addCorsHeaders(response: Response, session: IHTTPSession? = null) {
        val origin = session?.headers?.get("origin")
        val safeOrigin = if (!origin.isNullOrBlank() && !origin.equals("null", ignoreCase = true)) origin else "*"
        response.addHeader("Access-Control-Allow-Origin", safeOrigin)
        response.addHeader("Access-Control-Allow-Methods", "GET, OPTIONS, HEAD")
        val requestedHeaders = session?.headers?.get("access-control-request-headers")
        if (!requestedHeaders.isNullOrBlank()) {
            response.addHeader("Access-Control-Allow-Headers", requestedHeaders)
        } else {
            response.addHeader(
                "Access-Control-Allow-Headers",
                "Origin, Accept, Content-Type, Authorization, Range, Access-Control-Request-Private-Network"
            )
        }
        response.addHeader(
            "Access-Control-Expose-Headers",
            "Content-Length, Content-Range, Accept-Ranges, Access-Control-Allow-Private-Network"
        )
        response.addHeader("Access-Control-Allow-Private-Network", "true")
        response.addHeader("Access-Control-Max-Age", "86400")
    }

    /**
     * Waits for every byte in a receiver-requested range to become verified before serving.
     * Delegates to awaitRangeVerified if provided, or blocks for up to RANGE_WAIT_TIMEOUT_MS
     * to allow libtorrent to download the required pieces without aborting the client socket.
     *
     * @param start Inclusive byte offset.
     * @param end Inclusive byte offset.
     * @return Whether all pieces covering the interval verified before timeout.
     */
    private fun awaitVerified(start: Long, end: Long): Boolean {
        onRangeRequested?.invoke(start, end)
        if (awaitRangeVerified != null) {
            return awaitRangeVerified.invoke(start, end, RANGE_WAIT_TIMEOUT_MS)
        }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RANGE_WAIT_TIMEOUT_MS)
        while (System.nanoTime() < deadline) {
            if (isRangeVerified(start, end)) return true
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return isRangeVerified(start, end)
    }

    private fun convertSrtToVtt(srtText: String): String {
        return com.martinrevert.latorrentola.network.OpenSubtitleFormatConverter.toWebVtt(srtText)
    }

    private fun getResponseMimeType(): String {
        val mime = mediaFile.mediaMimeType()
        val ext = mediaFile.extension.lowercase(java.util.Locale.ROOT)
        return when {
            mime.contains("webm", ignoreCase = true) || ext == "webm" -> "video/webm"
            mime.contains("matroska", ignoreCase = true) || ext == "mkv" -> "video/x-matroska"
            mime.contains("avi", ignoreCase = true) || ext == "avi" -> "video/x-msvideo"
            else -> "video/mp4"
        }
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
        return if (start !in 0 until length || end < start) null else start to end
    }

    /**
     * Streams only bounded chunks after their backing torrent pieces have been hash-verified.
     * Uses RandomAccessFile to seek to arbitrary offsets without allocating or skipping intermediate bytes.
     *
     * @param file Backing media file on disk.
     * @param start Inclusive absolute offset of the HTTP range.
     * @param end Inclusive absolute offset of the HTTP range.
     * @param waitForRangeVerified Waits until complete torrent pieces cover a byte interval.
     */
    private class VerifiedRangeInputStream(
        private val file: File,
        private val start: Long,
        private val end: Long,
        private val waitForRangeVerified: (Long, Long) -> Boolean
    ) : InputStream() {
        private var currentOffset = start
        private var verifiedThrough = start - 1L
        private var raf: RandomAccessFile? = null

        private fun getOrOpenRaf(): RandomAccessFile {
            var r = raf
            if (r == null) {
                r = RandomAccessFile(file, "r")
                raf = r
            }
            return r
        }

        /** Reads one byte after verifying the bounded piece range containing it. */
        override fun read(): Int {
            if (currentOffset > end) return -1
            ensureVerified()
            var r = getOrOpenRaf()
            r.seek(currentOffset)
            var value = r.read()
            if (value < 0 && currentOffset <= end) {
                for (retry in 1..10) {
                    try { Thread.sleep(50) } catch (_: InterruptedException) { break }
                    if (retry == 5) {
                        try { raf?.close() } catch (_: Exception) {}
                        raf = null
                    }
                    r = getOrOpenRaf()
                    r.seek(currentOffset)
                    value = r.read()
                    if (value >= 0) break
                }
            }
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
            var r = getOrOpenRaf()
            r.seek(currentOffset)
            var count = r.read(buffer, offset, safeLength)
            if (count < 0 && currentOffset <= end) {
                for (retry in 1..10) {
                    try { Thread.sleep(50) } catch (_: InterruptedException) { break }
                    if (retry == 5) {
                        try { raf?.close() } catch (_: Exception) {}
                        raf = null
                    }
                    r = getOrOpenRaf()
                    r.seek(currentOffset)
                    count = r.read(buffer, offset, safeLength)
                    if (count > 0) break
                }
            }
            if (count > 0) currentOffset += count
            return count
        }

        /** Waits for the next bounded byte interval before exposing it to the socket. */
        private fun ensureVerified() {
            if (currentOffset <= verifiedThrough) return
            val nextChunkEnd =
                minOf(end, currentOffset + VerifiedTorrentHttpServer.VERIFIED_CHUNK_BYTES - 1L)
            if (!waitForRangeVerified(currentOffset, nextChunkEnd)) {
                Log.w(
                    TAG,
                    "Range $currentOffset-$nextChunkEnd not verified within timeout (${RANGE_WAIT_TIMEOUT_MS}ms). Closing stream chunk; client will retry."
                )
                throw IOException("Torrent pieces did not verify before the stream timed out")
            }
            verifiedThrough = nextChunkEnd
        }

        override fun close() {
            try {
                raf?.close()
            } catch (_: Exception) {}
            raf = null
        }
    }

    companion object {
        const val TAG = "VerifiedTorrentHttpServer"

        /** NanoHTTPD socket timeout used while a receiver requests media. */
        const val SOCKET_READ_TIMEOUT = 45_000

        /** Maximum timeout in ms to wait for a requested piece range to verify before failing. */
        const val RANGE_WAIT_TIMEOUT_MS = 30_000L

        /** Maximum byte interval verified before the response stream advances. */
        const val VERIFIED_CHUNK_BYTES = 256L * 1024L

        fun getLanIpAddress(): String? {
            return try {
                val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())

                val primaryIp = interfaces.asSequence()
                    .filter { it.isUp && !it.isLoopback }
                    .filter { network ->
                        val name = network.name.lowercase(java.util.Locale.ROOT)
                        !name.contains("p2p") && !name.contains("tun") && !name.contains("tap") && !name.contains("dummy")
                    }
                    .sortedBy { network ->
                        val name = network.name.lowercase(java.util.Locale.ROOT)
                        when {
                            name.startsWith("wlan") || name.contains("wlan") -> 0
                            name.startsWith("eth") || name.contains("eth") -> 1
                            else -> 2
                        }
                    }
                    .flatMap { java.util.Collections.list(it.inetAddresses).asSequence() }
                    .filterIsInstance<java.net.Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress && it.isSiteLocalAddress }
                    ?.hostAddress

                if (primaryIp != null) return primaryIp

                interfaces.asSequence()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { java.util.Collections.list(it.inetAddresses).asSequence() }
                    .filterIsInstance<java.net.Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    ?.hostAddress
            } catch (_: Exception) {
                null
            }
        }
    }
}
