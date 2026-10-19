package com.martinrevert.latorrentola.service.storage.remote

import android.content.Context
import android.util.Log
import com.martinrevert.latorrentola.service.storage.TorrentStorageResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates external USB storage and remote network storage (SMB/NFS) browsing,
 * streaming playback session lifecycle, and co-located subtitle downloads.
 */
@Singleton
class RemoteStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smbClient: SmbStorageClient,
    private val nfsClient: NfsStorageClient,
    private val serverRepository: RemoteServerRepository,
    private val smbCredentialStore: SmbCredentialStore
) {
    /** Active streaming proxy servers keyed by playback session ID or URL. */
    private val activeServers = ConcurrentHashMap<String, RemoteMediaHttpServer>()

    /** Returns the underlying storage client matching the server protocol. */
    fun getClient(type: RemoteServerType): RemoteStorageClient = when (type) {
        RemoteServerType.SMB -> smbClient
        RemoteServerType.NFS -> nfsClient
    }

    /**
     * Enriches server configuration with securely stored credentials from the Keystore if present.
     */
    fun resolveServerConfig(config: RemoteServerConfig): RemoteServerConfig {
        if (config.type == RemoteServerType.SMB) {
            val stored = smbCredentialStore.load(config.host)
            if (stored != null && stored.username.isNotBlank() && stored.password.isNotBlank()) {
                return config.copy(
                    username = if (!config.username.isNullOrBlank()) config.username else stored.username,
                    password = if (!config.password.isNullOrBlank()) config.password else stored.password,
                    domain = if (!config.domain.isNullOrBlank()) config.domain else stored.domain
                )
            }
        }
        return config
    }

    /**
     * Enumerates video files on local USB and external mounts.
     */
    suspend fun listUsbFiles(directory: File): List<RemoteFileItem> = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.isDirectory) return@withContext emptyList()

        val files = directory.listFiles() ?: return@withContext emptyList()
        files.filter { !it.name.startsWith(".") }
            .map { file ->
                RemoteFileItem(
                    path = file.absolutePath,
                    name = file.name,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isDirectory) 0L else file.length(),
                    lastModified = file.lastModified(),
                    serverConfigId = "usb"
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    /**
     * Enumerates files on a remote server share (SMB or NFS).
     */
    suspend fun listRemoteFiles(config: RemoteServerConfig, directoryPath: String = ""): List<RemoteFileItem> {
        val resolved = resolveServerConfig(config)
        return getClient(resolved.type).listFiles(resolved, directoryPath)
    }

    /**
     * Starts an HTTP streaming proxy for a remote file so it can be streamed by ExoPlayer and Chromecast.
     */
    suspend fun startRemoteStreamServer(
        config: RemoteServerConfig,
        fileItem: RemoteFileItem,
        getSubtitleVtt: (() -> String?)? = null
    ): RemoteMediaHttpServer = withContext(Dispatchers.IO) {
        val resolved = resolveServerConfig(config)
        val client = getClient(resolved.type)
        val actualLength = if (fileItem.sizeBytes > 0L) {
            fileItem.sizeBytes
        } else {
            try {
                client.getFileSize(resolved, fileItem.path)
            } catch (_: Exception) {
                0L
            }
        }
        val server = RemoteMediaHttpServer(
            client = client,
            serverConfig = resolved,
            remoteFilePath = fileItem.path,
            fileName = fileItem.name,
            fileLength = actualLength,
            getSubtitleVtt = getSubtitleVtt
        )
        server.startServer()
        activeServers[server.accessToken] = server
        server
    }

    /**
     * Stops an active streaming server.
     */
    fun stopStreamServer(accessToken: String) {
        activeServers.remove(accessToken)?.stop()
    }

    private val serverCache = ConcurrentHashMap<String, RemoteServerConfig>()

    /**
     * Registers a server configuration into active runtime memory (e.g. dynamically scanned network shares).
     */
    fun registerServer(config: RemoteServerConfig) {
        serverCache[config.id] = config
    }

    /**
     * Retrieves server configuration by ID from runtime cache or persistent repository.
     */
    fun getServer(id: String): RemoteServerConfig? {
        val config = serverCache[id] ?: serverRepository.getServer(id)
        return config?.let { resolveServerConfig(it) }
    }

    /**
     * Writes subtitle content (.vtt) co-located alongside the video file.
     * For USB: writes directly to disk alongside the video.
     * For SMB/NFS: writes to the remote share/export alongside the video.
     */
    suspend fun saveColocatedSubtitle(
        source: RemoteFileItem,
        vttContent: String,
        languageCode: String
    ): Boolean = withContext(Dispatchers.IO) {
        val baseName = source.name.substringBeforeLast('.')
        val subFileName = "$baseName.$languageCode.vtt"

        if (source.serverConfigId == "usb") {
            try {
                val videoFile = File(source.path)
                val parentDir = videoFile.parentFile ?: return@withContext false
                val subFile = File(parentDir, subFileName)
                FileOutputStream(subFile).use { out ->
                    out.write(vttContent.toByteArray(Charsets.UTF_8))
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save local USB subtitle: ${e.message}", e)
                false
            }
        } else {
            val serverConfig = getServer(source.serverConfigId) ?: return@withContext false
            val parentPath = source.path.substringBeforeLast('/', "")
            getClient(serverConfig.type).writeSubtitle(serverConfig, parentPath, subFileName, vttContent)
        }
    }

    companion object {
        private const val TAG = "RemoteStorageManager"
    }
}
