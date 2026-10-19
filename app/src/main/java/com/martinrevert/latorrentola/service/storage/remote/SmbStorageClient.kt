package com.martinrevert.latorrentola.service.storage.remote

import android.util.Log
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

    override suspend fun openInputStream(config: RemoteServerConfig, filePath: String, offsetBytes: Long): InputStream =
        withContext(Dispatchers.IO) {
            val connection = smbClient.connect(config.host, config.port)
            val auth = buildAuthContext(config)
            val session = authenticateSession(connection, auth)
            val cleanShare = config.shareOrExport.trim().trim('/', '\\')
            val share = session.connectShare(cleanShare) as DiskShare

            val cleanPath = filePath.trim().trim('/', '\\').replace('/', '\\')
            val file = share.openFile(
                cleanPath,
                EnumSet.of(AccessMask.FILE_READ_DATA),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )

            var currentOffset = offsetBytes

            // Wrap file to close resources when closed
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
                    val bytesRead = file.read(b, currentOffset, off, len)
                    if (bytesRead > 0) {
                        currentOffset += bytesRead
                        return bytesRead
                    }
                    return -1
                }

                override fun close() {
                    if (!isClosed) {
                        isClosed = true
                        try { file.close() } catch (_: Exception) {}
                        try { share.close() } catch (_: Exception) {}
                        try { session.close() } catch (_: Exception) {}
                        try { connection.close() } catch (_: Exception) {}
                    }
                }
            }
            java.io.BufferedInputStream(rawStream, 131072)
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
            Log.e(TAG, "Failed to write subtitle to SMB share: ${e.message}", e)
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
        val connection = smbClient.connect(config.host, config.port)
        return try {
            val auth = buildAuthContext(config)
            val session = authenticateSession(connection, auth)
            try {
                val cleanShare = config.shareOrExport.trim().trim('/', '\\')
                val share = session.connectShare(cleanShare) as DiskShare
                try {
                    block(share)
                } finally {
                    try { share.close() } catch (_: Exception) {}
                }
            } finally {
                try { session.close() } catch (_: Exception) {}
            }
        } finally {
            try { connection.close() } catch (_: Exception) {}
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
