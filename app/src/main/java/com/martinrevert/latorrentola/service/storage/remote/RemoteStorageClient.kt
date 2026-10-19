package com.martinrevert.latorrentola.service.storage.remote

import java.io.InputStream

/**
 * Common abstraction for interacting with remote network storage servers (SMB / NFS).
 */
interface RemoteStorageClient {

    /**
     * Enumerates available entry points (shares or exported directories) exposed by the host.
     */
    suspend fun listEntryPoints(config: RemoteServerConfig): List<String> = emptyList()

    /**
     * Lists files and folders located at [directoryPath] on the target share.
     */
    suspend fun listFiles(config: RemoteServerConfig, directoryPath: String): List<RemoteFileItem>

    /**
     * Opens a readable [InputStream] for [filePath] starting at [offsetBytes].
     */
    suspend fun openInputStream(config: RemoteServerConfig, filePath: String, offsetBytes: Long = 0L): InputStream

    /**
     * Retrieves the file size in bytes for [filePath].
     */
    suspend fun getFileSize(config: RemoteServerConfig, filePath: String): Long

    /**
     * Writes subtitle [vttContent] directly to [fileName] within [directoryPath] on the remote share.
     */
    suspend fun writeSubtitle(config: RemoteServerConfig, directoryPath: String, fileName: String, vttContent: String): Boolean

    /**
     * Tests connectivity and authentication to the server.
     */
    suspend fun testConnection(config: RemoteServerConfig): Boolean
}
