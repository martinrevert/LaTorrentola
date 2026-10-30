package com.martinrevert.latorrentola.service.storage.remote

import android.util.Log
import com.martinrevert.latorrentola.utils.AppLogger
import com.martinrevert.latorrentola.utils.mediaMimeType
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.UUID

/**
 * Lightweight HTTP streaming server proxying remote SMB / NFS files to localhost (ExoPlayer)
 * and LAN IP (Chromecast receiver) with full byte-range support.
 */
class RemoteMediaHttpServer(
    private val client: RemoteStorageClient,
    private val serverConfig: RemoteServerConfig,
    private val remoteFilePath: String,
    private val fileName: String,
    private val fileLength: Long,
    val accessToken: String = UUID.randomUUID().toString(),
    private val getSubtitleVtt: (() -> String?)? = null
) : NanoHTTPD("0.0.0.0", 0) {

    fun startServer(): Int {
        start(SOCKET_READ_TIMEOUT, false)
        return listeningPort
    }

    fun localUrl(): String {
        val encodedName = java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
        return "http://127.0.0.1:$listeningPort/$accessToken/$encodedName"
    }

    fun lanUrl(): String {
        val address = com.martinrevert.latorrentola.service.VerifiedTorrentHttpServer.getLanIpAddress() ?: "127.0.0.1"
        val encodedName = java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
        return "http://$address:$listeningPort/$accessToken/$encodedName"
    }

    fun lanSubtitleUrl(): String {
        val address = com.martinrevert.latorrentola.service.VerifiedTorrentHttpServer.getLanIpAddress() ?: "127.0.0.1"
        return "http://$address:$listeningPort/$accessToken/subtitles.vtt"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.orEmpty().removePrefix("/")
        val segments = uri.split("/")

        if (segments.isEmpty() || segments[0] != accessToken) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden")
        }

        if (session.method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "").apply {
                addCorsHeaders(this)
            }
        }

        // Subtitle request
        if (segments.size > 1 && segments[1].endsWith(".vtt", ignoreCase = true)) {
            val vtt = getSubtitleVtt?.invoke().orEmpty()
            val data = vtt.toByteArray(Charsets.UTF_8)
            return newFixedLengthResponse(
                Response.Status.OK,
                "text/vtt",
                ByteArrayInputStream(data),
                data.size.toLong()
            ).apply {
                addCorsHeaders(this)
            }
        }

        // Media streaming request
        val rangeHeader = session.headers["range"]
        return try {
            val mime = File(fileName).mediaMimeType()
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                serveRange(rangeHeader, mime)
            } else {
                serveFull(mime)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error serving media stream: ${e.message}", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message.orEmpty())
        }
    }

    private fun serveFull(mime: String): Response {
        val stream = runBlocking { client.openInputStream(serverConfig, remoteFilePath, 0L) }
        return newFixedLengthResponse(Response.Status.OK, mime, stream, fileLength).apply {
            addHeader("Accept-Ranges", "bytes")
            addHeader("Content-Length", fileLength.toString())
            addCorsHeaders(this)
        }
    }

    private fun serveRange(rangeHeader: String, mime: String): Response {
        val range = rangeHeader.removePrefix("bytes=").trim()
        val parts = range.split("-")
        val start = parts[0].toLongOrNull() ?: 0L
        val end = if (parts.size > 1 && parts[1].isNotBlank()) {
            parts[1].toLongOrNull() ?: (fileLength - 1)
        } else {
            fileLength - 1
        }.coerceAtMost(fileLength - 1)

        val contentLength = (end - start + 1).coerceAtLeast(0L)
        val stream = runBlocking { client.openInputStream(serverConfig, remoteFilePath, start) }
        val boundedStream = BoundedInputStream(stream, contentLength)

        return newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, boundedStream, contentLength).apply {
            addHeader("Accept-Ranges", "bytes")
            addHeader("Content-Range", "bytes $start-$end/$fileLength")
            addHeader("Content-Length", contentLength.toString())
            addCorsHeaders(this)
        }
    }

    private fun addCorsHeaders(response: Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, OPTIONS, HEAD")
        response.addHeader("Access-Control-Allow-Headers", "*")
        response.addHeader("Access-Control-Allow-Private-Network", "true")
    }

    private fun getLanIpAddress(): String? {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces()).asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isPointToPoint }
                .flatMap { Collections.list(it.inetAddresses).asSequence() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress }
                .map { it.hostAddress }
                .firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private class BoundedInputStream(
        private val delegate: InputStream,
        private val maxBytes: Long
    ) : InputStream() {
        private var bytesRemaining = maxBytes

        override fun read(): Int {
            if (bytesRemaining <= 0) return -1
            val b = delegate.read()
            if (b != -1) bytesRemaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (bytesRemaining <= 0) return -1
            val toRead = minOf(len.toLong(), bytesRemaining).toInt()
            val read = delegate.read(b, off, toRead)
            if (read != -1) bytesRemaining -= read
            return read
        }

        override fun available(): Int = minOf(delegate.available().toLong(), bytesRemaining).toInt()

        override fun close() {
            delegate.close()
        }
    }

    companion object {
        private const val TAG = "RemoteMediaHttpServer"
        private const val SOCKET_READ_TIMEOUT = 30_000
    }
}
