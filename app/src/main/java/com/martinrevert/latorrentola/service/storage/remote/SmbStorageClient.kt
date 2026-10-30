package com.martinrevert.latorrentola.service.storage.remote

import android.util.Log
import com.martinrevert.latorrentola.utils.AppLogger
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMB 2/3 client implementation using the pure Java/Kotlin smbj library.
 * Handles directory listing, remote streaming with byte offsets, and subtitle uploads.
 */
@Singleton
class SmbStorageClient @Inject constructor(
    private val smbCredentialStore: SmbCredentialStore
) : RemoteStorageClient {

    private val smbConfig = SmbConfig.builder()
        .withTimeout(15, TimeUnit.SECONDS)
        .withSoTimeout(25, TimeUnit.SECONDS)
        .withMultiProtocolNegotiate(true)
        .withDfsEnabled(true)
        .withSigningRequired(false)
        .withReadBufferSize(1024 * 1024)
        .withWriteBufferSize(1024 * 1024)
        .withDialects(
            SMB2Dialect.SMB_2_0_2,
            SMB2Dialect.SMB_2_1,
            SMB2Dialect.SMB_3_0,
            SMB2Dialect.SMB_3_0_2,
            SMB2Dialect.SMB_3_1_1
        )
        .build()

    private val smbClient = SMBClient(smbConfig)

    override suspend fun listEntryPoints(config: RemoteServerConfig): List<String> =
        withContext(Dispatchers.IO) {
            val found = linkedSetOf<String>()
            try {
                smbClient.connect(config.host, config.port).use { connection ->
                    val auth = buildAuthContext(config)
                    val session = authenticateSession(connection, auth)
                    try {
                        // 1. Primary: Query the server dynamically via MS-SRVS (SRVSVC NetShareEnumAll)
                        try {
                            val transport = com.rapid7.client.dcerpc.transport.SMBTransportFactories.SRVSVC.getTransport(session)
                            val serverService = com.rapid7.client.dcerpc.mssrvs.ServerService(transport)
                            val shares: List<String> = try {
                                val info1List = serverService.getShares1()
                                info1List.filter { info ->
                                    val type = info.type
                                    // Exclude print queues (type 1) and IPC (type 3)
                                    (type and 0x03) != 1 && (type and 0x03) != 3
                                }.map { it.netName }
                            } catch (_: Exception) {
                                try {
                                    serverService.getShares0().map { it.netName }
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                            for (rawName in shares) {
                                val name = rawName.trim()
                                // Exclude administrative/special shares ending in $ (e.g. C$, ADMIN$, IPC$, print$)
                                if (name.isNotEmpty() && !name.endsWith("$") && !name.equals("IPC$", ignoreCase = true)) {
                                    found.add(name)
                                }
                            }
                            Log.i(TAG, "SRVSVC dynamic enumeration discovered ${found.size} shares on ${config.host}: $found")
                        } catch (e: Exception) {
                            Log.w(TAG, "SRVSVC dynamic share enumeration failed on ${config.host}: ${e.message}")
                        }

                        // 2. Secondary/Fallback: If RPC found nothing (or server blocked named pipes), probe common names
                        if (found.isEmpty()) {
                            val candidates = listOf(
                                "public", "media", "video", "videos", "movies", "pelis",
                                "downloads", "descargas", "share", "shared", "data", "datos",
                                "home", "users", "storage", "nas", "disk", "sda1", "sdb1",
                                "volume1", "volume2"
                            )
                            for (candidate in candidates) {
                                try {
                                    val share = session.connectShare(candidate)
                                    if (share is DiskShare) {
                                        found.add(candidate)
                                    }
                                    share.close()
                                } catch (_: Exception) {}
                            }
                        }
                    } finally {
                        try { session.close() } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                try { Log.w(TAG, "Probing SMB shares failed on ${config.host}: ${e.message}") } catch (_: Throwable) {}
                throw e
            }
            found.sortedWith(String.CASE_INSENSITIVE_ORDER)
        }

    override suspend fun listFiles(config: RemoteServerConfig, directoryPath: String): List<RemoteFileItem> =
        withContext(Dispatchers.IO) {
            if (config.shareOrExport.isBlank()) {
                val shares = listEntryPoints(config)
                return@withContext shares.map { shareName ->
                    RemoteFileItem(
                        path = shareName,
                        name = shareName,
                        isDirectory = true,
                        sizeBytes = 0L,
                        lastModified = 0L,
                        serverConfigId = config.id
                    )
                }
            }

            withDiskShare(config) { share ->
                val cleanPath = directoryPath.trim().trim('/', '\\').replace('/', '\\')
                val entries = if (cleanPath.isEmpty()) share.list("") else share.list(cleanPath)

                entries.filter { entry ->
                    val name = entry.fileName
                    name != "." && name != ".." && !name.startsWith("._") &&
                        (entry.fileAttributes and FileAttributes.FILE_ATTRIBUTE_HIDDEN.value) == 0L
                }.map { entry ->
                    val isDir = (entry.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                    val itemPath = if (cleanPath.isEmpty()) entry.fileName else "$cleanPath\\${entry.fileName}"
                    RemoteFileItem(
                        path = itemPath.replace('\\', '/'),
                        name = entry.fileName,
                        isDirectory = isDir,
                        sizeBytes = if (isDir) 0L else entry.endOfFile,
                        lastModified = entry.changeTime.toEpochMillis(),
                        serverConfigId = config.id
                    )
                }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            }
        }

    private class SmbSession(
        val connection: com.hierynomus.smbj.connection.Connection,
        val session: com.hierynomus.smbj.session.Session,
        val share: DiskShare,
        val lock: Any = Any()
    ) {
        val isAlive: Boolean
            get() = try {
                connection.isConnected && session.connection.isConnected && share.isConnected
            } catch (_: Exception) {
                false
            }

        fun closeSilently() {
            try { share.close() } catch (_: Exception) {}
            try { session.close() } catch (_: Exception) {}
            try { connection.close() } catch (_: Exception) {}
        }
    }

    private val sessionCache = ConcurrentHashMap<String, SmbSession>()
    private val sessionLock = Any()

    private fun getOrCreateShare(config: RemoteServerConfig): SmbSession {
        val cleanShare = config.shareOrExport.trim().trim('/', '\\')
        val cacheKey = "${config.id}:${config.host}:${config.port}/$cleanShare"

        sessionCache[cacheKey]?.let { cached ->
            if (cached.isAlive) {
                return cached
            } else {
                sessionCache.remove(cacheKey)?.closeSilently()
            }
        }

        synchronized(sessionLock) {
            sessionCache[cacheKey]?.let { cached ->
                if (cached.isAlive) return cached
                sessionCache.remove(cacheKey)?.closeSilently()
            }

            val connection = smbClient.connect(config.host, config.port)
            val auth = buildAuthContext(config)
            val session = authenticateSession(connection, auth)
            val share = session.connectShare(cleanShare) as DiskShare
            val newSession = SmbSession(connection, session, share)
            sessionCache[cacheKey] = newSession
            return newSession
        }
    }

    override suspend fun openInputStream(config: RemoteServerConfig, filePath: String, offsetBytes: Long): InputStream =
        withContext(Dispatchers.IO) {
            val cleanShare = config.shareOrExport.trim().trim('/', '\\')
            val cacheKey = "${config.id}:${config.host}:${config.port}/$cleanShare"
            val cleanPath = filePath.trim().trim('/', '\\').replace('/', '\\')

            var smbSession = getOrCreateShare(config)
            var file: com.hierynomus.smbj.share.File? = null
            try {
                file = synchronized(smbSession.lock) {
                    smbSession.share.openFile(
                        cleanPath,
                        EnumSet.of(AccessMask.FILE_READ_DATA),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open $cleanPath on cached SMB share, retrying with fresh connection: ${e.message}")
                sessionCache.remove(cacheKey)?.closeSilently()
                smbSession = getOrCreateShare(config)
                file = synchronized(smbSession.lock) {
                    smbSession.share.openFile(
                        cleanPath,
                        EnumSet.of(AccessMask.FILE_READ_DATA),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                    )
                }
            }

            val finalFile = file ?: throw java.io.IOException("Could not open remote SMB file: $cleanPath")
            val totalSize = try {
                finalFile.fileInformation.standardInformation.endOfFile
            } catch (_: Exception) {
                -1L
            }

            SmbPrefetchInputStream(
                file = finalFile,
                startOffset = offsetBytes,
                totalSize = totalSize
            )
        }

    override suspend fun getFileSize(config: RemoteServerConfig, filePath: String): Long =
        withContext(Dispatchers.IO) {
            withDiskShare(config) { share ->
                val cleanPath = filePath.trim().trim('/', '\\').replace('/', '\\')
                val info = share.getFileInformation(cleanPath)
                info.standardInformation.endOfFile
            }
        }

    override suspend fun writeSubtitle(
        config: RemoteServerConfig,
        directoryPath: String,
        fileName: String,
        vttContent: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            withDiskShare(config) { share ->
                val cleanDir = directoryPath.trim().trim('/', '\\').replace('/', '\\')
                val fullPath = if (cleanDir.isEmpty()) fileName else "$cleanDir\\$fileName"

                val subFile = share.openFile(
                    fullPath,
                    EnumSet.of(AccessMask.FILE_WRITE_DATA, AccessMask.FILE_READ_ATTRIBUTES),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    null
                )
                subFile.use { f ->
                    f.outputStream.use { out ->
                        out.write(vttContent.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }
                true
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to write subtitle to SMB share: ${e.message}", e)
            false
        }
    }

    override suspend fun testConnection(config: RemoteServerConfig): Boolean =
        withContext(Dispatchers.IO) {
            try {
                withDiskShare(config) {
                    true
                }
            } catch (e: Exception) {
                Log.w(TAG, "SMB connection test failed for ${config.host}: ${e.message}")
                false
            }
        }

    private fun <T> withDiskShare(config: RemoteServerConfig, block: (DiskShare) -> T): T {
        val smbSession = getOrCreateShare(config)
        return try {
            synchronized(smbSession.lock) {
                block(smbSession.share)
            }
        } catch (e: Exception) {
            val cleanShare = config.shareOrExport.trim().trim('/', '\\')
            val cacheKey = "${config.id}:${config.host}:${config.port}/$cleanShare"
            sessionCache.remove(cacheKey)?.closeSilently()
            val freshSession = getOrCreateShare(config)
            synchronized(freshSession.lock) {
                block(freshSession.share)
            }
        }
    }

    private fun buildAuthContext(config: RemoteServerConfig): AuthenticationContext {
        var username = config.username.orEmpty()
        var password = config.password.orEmpty()
        var domain = config.domain.orEmpty()

        if (username.isBlank() || password.isBlank()) {
            val stored = smbCredentialStore.load(config.host)
            if (stored != null && stored.username.isNotBlank() && stored.password.isNotBlank()) {
                if (username.isBlank()) username = stored.username
                if (password.isBlank()) password = stored.password
                if (domain.isBlank()) domain = stored.domain.orEmpty()
            }
        }

        return if (username.isBlank()) {
            AuthenticationContext.anonymous()
        } else {
            AuthenticationContext(username, password.toCharArray(), domain)
        }
    }

    private fun authenticateSession(
        connection: com.hierynomus.smbj.connection.Connection,
        auth: AuthenticationContext
    ): com.hierynomus.smbj.session.Session {
        return try {
            connection.authenticate(auth)
        } catch (npe: NullPointerException) {
            if (npe.message?.contains("SecretKey.getEncoded") == true ||
                npe.stackTrace.any { it.methodName.contains("deriveKey", ignoreCase = true) }
            ) {
                throw java.io.IOException(
                    "STATUS_LOGON_FAILURE: SMB server rejected guest/anonymous session (null session key)",
                    npe
                )
            } else {
                throw npe
            }
        }
    }

    companion object {
        private const val TAG = "SmbStorageClient"
    }
}
