package com.martinrevert.latorrentola.service.storage.remote

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

/** Supported network storage protocol. */
@Serializable
enum class RemoteServerType {
    @SerializedName("SMB")
    SMB,
    @SerializedName("NFS")
    NFS
}

/**
 * Configuration and credentials for connecting to a remote network share (SMB / NFS).
 *
 * @property id Unique persistent identifier.
 * @property name User-visible label for the server.
 * @property type Protocol used to communicate with the server.
 * @property host Hostname or IP address of the server.
 * @property port Network port (default 445 for SMB, 2049 for NFS).
 * @property shareOrExport Share name (SMB) or exported directory path (NFS).
 * @property domain Optional domain/workgroup for Windows SMB authentication.
 * @property username Account username, if authentication is required.
 * @property password Account password, if authentication is required.
 */
@Serializable
data class RemoteServerConfig(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: RemoteServerType,
    @SerializedName("host") val host: String,
    @SerializedName("port") val port: Int = if (type == RemoteServerType.SMB) 445 else 2049,
    @SerializedName("shareOrExport") val shareOrExport: String = "",
    @SerializedName("domain") val domain: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("password") val password: String? = null
)

/**
 * Metadata for a file or directory located on a remote network share or external storage.
 *
 * @property path Relative path within the share/mount.
 * @property name Display name of the file or directory.
 * @property isDirectory True if this entry is a folder.
 * @property sizeBytes File size in bytes (0 for directories).
 * @property lastModified Last modification timestamp in milliseconds.
 * @property serverConfigId Owning server ID.
 */
@Serializable
data class RemoteFileItem(
    @SerializedName("path") val path: String,
    @SerializedName("name") val name: String,
    @SerializedName("isDirectory") val isDirectory: Boolean,
    @SerializedName("sizeBytes") val sizeBytes: Long = 0L,
    @SerializedName("lastModified") val lastModified: Long = 0L,
    @SerializedName("serverConfigId") val serverConfigId: String
)
