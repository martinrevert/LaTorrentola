package com.martinrevert.latorrentola.service.storage.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native C NFS client (via sahlberg/libnfs JNI) implementing [RemoteStorageClient]
 * supporting NFSv3 and NFSv4 protocol operations without any third-party Java NFS dependency.
 */
@Singleton
class NfsStorageClient @Inject constructor() : RemoteStorageClient {

    private class NfsSession(
        val ctxPtr: Long,
        val server: String,
        val export: String,
        val lock: Any = Any()
    )

    private val sessionCache = ConcurrentHashMap<String, NfsSession>()
    private val sessionMutex = Mutex()

    private fun getOrCreateSession(config: RemoteServerConfig): NfsSession {
        if (!LibNfsNative.isAvailable) {
            throw IOException("Librería nativa libnfs no disponible en el sistema.")
        }
        val export = if (config.shareOrExport.startsWith("/")) config.shareOrExport else "/${config.shareOrExport}"
        val cacheKey = "${config.host}:${config.port}/$export"

        sessionCache[cacheKey]?.let { return it }

        val parsedUid = config.username?.substringBefore(':')?.toIntOrNull() ?: 1000
        val parsedGid = config.username?.substringAfter(':', "")?.toIntOrNull() ?: parsedUid

        // First attempt NFSv4
        var ctxPtr = LibNfsNative.nativeInitContext(nfsVersion = 4, uid = parsedUid, gid = parsedGid)
        if (ctxPtr == 0L) {
            throw IOException("No se pudo inicializar el contexto NFS")
        }

        var mountRes = LibNfsNative.nativeMount(ctxPtr, config.host, export)
        if (mountRes != 0) {
            // Log & retry with NFSv3 fallback
            val err4 = LibNfsNative.nativeGetErrorMessage(ctxPtr) ?: "Error $mountRes"
            LibNfsNative.nativeDestroyContext(ctxPtr)

            ctxPtr = LibNfsNative.nativeInitContext(nfsVersion = 3, uid = parsedUid, gid = parsedGid)
            if (ctxPtr == 0L) {
                throw IOException("No se pudo inicializar contexto NFSv3 de fallback: $err4")
            }
            mountRes = LibNfsNative.nativeMount(ctxPtr, config.host, export)
            if (mountRes != 0) {
                val err3 = LibNfsNative.nativeGetErrorMessage(ctxPtr) ?: "Error $mountRes"
                LibNfsNative.nativeDestroyContext(ctxPtr)
                throw IOException("Fallo al montar NFS en ${config.host}:$export (v4: $err4 | v3: $err3)")
            }
        }

        val session = NfsSession(ctxPtr, config.host, export)
        sessionCache[cacheKey] = session
        return session
    }

    override suspend fun listEntryPoints(config: RemoteServerConfig): List<String> =
        withContext(Dispatchers.IO) {
            if (LibNfsNative.isAvailable) {
                try {
                    val exports = LibNfsNative.nativeGetExports(config.host)
                    if (!exports.isNullOrEmpty()) {
                        return@withContext exports.toList().sorted()
                    }
                } catch (e: Exception) {
                    try { Log.w(TAG, "Native getExports failed for ${config.host}: ${e.message}") } catch (_: Throwable) {}
                }
            }
            emptyList()
        }

    override suspend fun listFiles(config: RemoteServerConfig, directoryPath: String): List<RemoteFileItem> =
        withContext(Dispatchers.IO) {
            if (config.shareOrExport.isBlank()) {
                val entryPoints = listEntryPoints(config)
                if (entryPoints.isNotEmpty()) {
                    return@withContext entryPoints.map { exportPath ->
                        RemoteFileItem(
                            path = exportPath,
                            name = exportPath.trimStart('/'),
                            isDirectory = true,
                            sizeBytes = 0L,
                            lastModified = 0L,
                            serverConfigId = config.id
                        )
                    }
                }
                return@withContext emptyList()
            }

            val session = sessionMutex.withLock { getOrCreateSession(config) }
            val cleanPath = normalizeNfsPath(directoryPath)

            val rawEntries = synchronized(session.lock) {
                LibNfsNative.nativeListEntries(session.ctxPtr, cleanPath)
            } ?: return@withContext emptyList()

            rawEntries.mapNotNull { entryStr ->
                // Formatted in JNI as "name|isDirectory|size"
                val parts = entryStr.split('|')
                if (parts.size >= 3) {
                    val name = parts[0]
                    val isDir = parts[1] == "1"
                    val size = parts[2].toLongOrNull() ?: 0L
                    val itemRelPath = if (directoryPath.isBlank() || directoryPath == "/") name else "${directoryPath.trimEnd('/')}/$name"
                    RemoteFileItem(
                        path = itemRelPath,
                        name = name,
                        isDirectory = isDir,
                        sizeBytes = size,
                        lastModified = 0L,
                        serverConfigId = config.id
                    )
                } else null
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }

    override suspend fun openInputStream(config: RemoteServerConfig, filePath: String, offsetBytes: Long): InputStream =
        withContext(Dispatchers.IO) {
            val export = if (config.shareOrExport.startsWith("/")) config.shareOrExport else "/${config.shareOrExport}"
            val cacheKey = "${config.host}:${config.port}/$export"
            val cleanPath = normalizeNfsPath(filePath, export)
            Log.i(TAG, "openInputStream config=${config.host}:$export, filePath=$filePath, cleanPath=$cleanPath, offset=$offsetBytes")

            var session = sessionMutex.withLock { getOrCreateSession(config) }
            var fh = synchronized(session.lock) {
                LibNfsNative.nativeOpen(session.ctxPtr, cleanPath)
            }
            if (fh == 0L) {
                // Connection may be stale or disconnected; invalidate and retry once with fresh session
                Log.w(TAG, "nativeOpen returned 0 for $cleanPath, retrying with fresh session")
                sessionCache.remove(cacheKey)?.let { oldSession ->
                    synchronized(oldSession.lock) {
                        LibNfsNative.nativeDestroyContext(oldSession.ctxPtr)
                    }
                }
                session = sessionMutex.withLock { getOrCreateSession(config) }
                fh = synchronized(session.lock) {
                    LibNfsNative.nativeOpen(session.ctxPtr, cleanPath)
                }
                if (fh == 0L) {
                    val err = synchronized(session.lock) {
                        LibNfsNative.nativeGetErrorMessage(session.ctxPtr)
                    } ?: "Error al abrir archivo"
                    Log.e(TAG, "Failed to open $cleanPath on NFS: $err")
                    throw IOException("No se pudo abrir $filePath en NFS: $err")
                }
            }

            Log.i(TAG, "Successfully opened NFS file handle fh=$fh for $cleanPath")
            var currentOffset = offsetBytes

            val rawStream = object : InputStream() {
                private var isClosed = false

                override fun read(): Int {
                    val b = ByteArray(1)
                    val readBytes = read(b, 0, 1)
                    return if (readBytes > 0) b[0].toInt() and 0xFF else -1
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (isClosed) return -1
                    if (len == 0) return 0

                    val tempBuf = if (off == 0 && len == b.size) b else ByteArray(len)
                    val bytesRead = synchronized(session.lock) {
                        if (isClosed) -1
                        else LibNfsNative.nativePread(session.ctxPtr, fh, currentOffset, len, tempBuf)
                    }

                    if (bytesRead > 0) {
                        if (tempBuf !== b) {
                            System.arraycopy(tempBuf, 0, b, off, bytesRead)
                        }
                        currentOffset += bytesRead
                        return bytesRead
                    }
                    return -1
                }

                override fun close() {
                    synchronized(session.lock) {
                        if (!isClosed) {
                            isClosed = true
                            LibNfsNative.nativeClose(session.ctxPtr, fh)
                            Log.i(TAG, "Closed NFS file handle fh=$fh for $cleanPath")
                        }
                    }
                }
            }
            java.io.BufferedInputStream(rawStream, 131072)
        }

    override suspend fun getFileSize(config: RemoteServerConfig, filePath: String): Long =
        withContext(Dispatchers.IO) {
            val export = if (config.shareOrExport.startsWith("/")) config.shareOrExport else "/${config.shareOrExport}"
            val session = sessionMutex.withLock { getOrCreateSession(config) }
            val cleanPath = normalizeNfsPath(filePath, export)
            val size = synchronized(session.lock) {
                LibNfsNative.nativeStatSize(session.ctxPtr, cleanPath)
            }
            Log.i(TAG, "getFileSize for $cleanPath -> $size bytes")
            if (size >= 0L) size else 0L
        }

    override suspend fun writeSubtitle(
        config: RemoteServerConfig,
        directoryPath: String,
        fileName: String,
        vttContent: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val session = sessionMutex.withLock { getOrCreateSession(config) }
            val fullRelPath = if (directoryPath.isBlank() || directoryPath == "/") fileName else "${directoryPath.trimEnd('/')}/$fileName"
            val cleanPath = normalizeNfsPath(fullRelPath)
            synchronized(session.lock) {
                LibNfsNative.nativeWriteFile(session.ctxPtr, cleanPath, vttContent.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            try { Log.e(TAG, "Failed to write subtitle to NFS export: ${e.message}", e) } catch (_: Throwable) {}
            false
        }
    }

    override suspend fun testConnection(config: RemoteServerConfig): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val session = sessionMutex.withLock { getOrCreateSession(config) }
                val rootEntries = synchronized(session.lock) {
                    LibNfsNative.nativeListEntries(session.ctxPtr, "/")
                }
                rootEntries != null
            } catch (e: Exception) {
                val export = if (config.shareOrExport.startsWith("/")) config.shareOrExport else "/${config.shareOrExport}"
                val cacheKey = "${config.host}:${config.port}/$export"
                sessionCache.remove(cacheKey)?.let { oldSession ->
                    synchronized(oldSession.lock) {
                        LibNfsNative.nativeDestroyContext(oldSession.ctxPtr)
                    }
                }
                try { Log.w(TAG, "NFS connection test failed for ${config.host}: ${e.message}") } catch (_: Throwable) {}
                false
            }
        }

    private fun normalizeNfsPath(path: String, export: String = ""): String {
        var trimmed = path.trim().replace('\\', '/')
        if (export.isNotBlank()) {
            val cleanExport = if (export.startsWith('/')) export else "/$export"
            if (trimmed.startsWith(cleanExport, ignoreCase = true)) {
                trimmed = trimmed.substring(cleanExport.length)
            } else if (trimmed.startsWith(cleanExport.trimStart('/'), ignoreCase = true)) {
                trimmed = trimmed.substring(cleanExport.trimStart('/').length)
            }
        }
        return if (trimmed.startsWith('/')) trimmed else "/$trimmed"
    }

    companion object {
        private const val TAG = "NfsStorageClient"
    }
}
