package com.martinrevert.latorrentola.service.storage.remote

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import android.util.Log
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.runBlocking

/**
 * Custom Media3 [DataSource] enabling direct playback of remote media files (SMB / NFS)
 * in ExoPlayer without requiring an HTTP proxy server.
 *
 * Intercepts URIs with the [SCHEME_REMOTE] scheme (e.g. `remote://{serverId}?path={remotePath}`)
 * and delegates all other URIs transparently to the upstream [DataSource].
 */
@OptIn(UnstableApi::class)
class RemoteMediaDataSource(
    private val remoteStorageManager: RemoteStorageManager,
    private val upstreamDataSource: DataSource
) : BaseDataSource(/* isNetwork = */ true) {

    private var activeUpstream: DataSource? = null
    private var currentStream: InputStream? = null
    private var currentUri: Uri? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var isOpened = false

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        val uriStr = dataSpec.uri.toString()
        val scheme = dataSpec.uri.scheme ?: uriStr.substringBefore("://")

        if (scheme == SCHEME_REMOTE) {
            transferInitializing(dataSpec)
            activeUpstream = null

            val serverId = dataSpec.uri.host.takeUnless { it.isNullOrBlank() }
                ?: uriStr.substringAfter("://").substringBefore('?').substringBefore('/')
            val remotePath = dataSpec.uri.getQueryParameter(PARAM_PATH).takeUnless { it.isNullOrBlank() }
                ?: java.net.URLDecoder.decode(uriStr.substringAfter("$PARAM_PATH=", "").substringBefore('&'), "UTF-8")

            var serverConfig = remoteStorageManager.getServer(serverId)
            if (serverConfig == null) {
                val typeStr = dataSpec.uri.getQueryParameter(PARAM_TYPE)
                val host = dataSpec.uri.getQueryParameter(PARAM_HOST)
                if (!typeStr.isNullOrBlank() && !host.isNullOrBlank()) {
                    val type = try {
                        RemoteServerType.valueOf(typeStr)
                    } catch (_: Exception) {
                        RemoteServerType.NFS
                    }
                    val port = dataSpec.uri.getQueryParameter(PARAM_PORT)?.toIntOrNull()
                        ?: if (type == RemoteServerType.SMB) 445 else 2049
                    val share = dataSpec.uri.getQueryParameter(PARAM_SHARE).orEmpty()
                    val user = dataSpec.uri.getQueryParameter(PARAM_USER).takeUnless { it.isNullOrBlank() }
                    val pass = dataSpec.uri.getQueryParameter(PARAM_PASS).takeUnless { it.isNullOrBlank() }
                    serverConfig = RemoteServerConfig(
                        id = serverId,
                        name = serverId,
                        type = type,
                        host = host,
                        port = port,
                        shareOrExport = share,
                        username = user,
                        password = pass
                    )
                    remoteStorageManager.registerServer(serverConfig)
                }
            }

            val finalServerConfig = serverConfig
                ?: throw IOException("Servidor remoto no encontrado: $serverId")
            val client = remoteStorageManager.getClient(finalServerConfig.type)

            Log.i(TAG, "SCHEME_REMOTE: opening server=${finalServerConfig.host}:${finalServerConfig.port}/${finalServerConfig.shareOrExport}, path=$remotePath, pos=${dataSpec.position}, len=${dataSpec.length}")
            val totalSize = runBlocking { client.getFileSize(finalServerConfig, remotePath) }
            Log.i(TAG, "getFileSize for $remotePath: $totalSize bytes")
            val stream = runBlocking { client.openInputStream(finalServerConfig, remotePath, dataSpec.position) }
            Log.i(TAG, "openInputStream created stream: $stream")
            currentStream = stream

            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else if (totalSize > 0) {
                totalSize - dataSpec.position
            } else {
                C.LENGTH_UNSET.toLong()
            }

            Log.i(TAG, "Stream opened successfully, bytesRemaining=$bytesRemaining")
            isOpened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } else {
            Log.d(TAG, "Delegating non-remote URI ${dataSpec.uri} to upstream")
            activeUpstream = upstreamDataSource
            return upstreamDataSource.open(dataSpec)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val upstream = activeUpstream
        if (upstream != null) {
            return upstream.read(buffer, offset, length)
        }

        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val stream = currentStream ?: return C.RESULT_END_OF_INPUT
        val bytesToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            minOf(length.toLong(), bytesRemaining).toInt()
        } else {
            length
        }

        val bytesRead = stream.read(buffer, offset, bytesToRead)
        Log.d(TAG, "read(length=$length, bytesToRead=$bytesToRead) -> bytesRead=$bytesRead")
        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }

        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = activeUpstream?.uri ?: currentUri

    override fun close() {
        Log.i(TAG, "close() called, isOpened=$isOpened")
        val upstream = activeUpstream
        if (upstream != null) {
            upstream.close()
            activeUpstream = null
        } else if (isOpened) {
            try {
                currentStream?.close()
            } catch (_: Exception) {}
            currentStream = null
            isOpened = false
            transferEnded()
        }
    }

    companion object {
        private const val TAG = "RemoteMediaDataSource"
        const val SCHEME_REMOTE = "remote"
        const val PARAM_PATH = "path"
        const val PARAM_TYPE = "type"
        const val PARAM_HOST = "host"
        const val PARAM_PORT = "port"
        const val PARAM_SHARE = "share"
        const val PARAM_USER = "user"
        const val PARAM_PASS = "pass"

        /**
         * Builds a URI string targeting a remote file item on a configured server,
         * encoding essential connection parameters for stateless recovery.
         */
        fun buildRemoteUriString(server: RemoteServerConfig, remotePath: String): String {
            val fileName = remotePath.substringAfterLast('/').ifBlank { "video.mkv" }
            val encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8")
            val encodedPath = java.net.URLEncoder.encode(remotePath, "UTF-8")
            val encodedShare = java.net.URLEncoder.encode(server.shareOrExport, "UTF-8")
            val encodedUser = java.net.URLEncoder.encode(server.username ?: "", "UTF-8")
            val encodedPass = java.net.URLEncoder.encode(server.password ?: "", "UTF-8")
            return "$SCHEME_REMOTE://${server.id}/$encodedFileName?$PARAM_PATH=$encodedPath&$PARAM_TYPE=${server.type.name}&$PARAM_HOST=${server.host}&$PARAM_PORT=${server.port}&$PARAM_SHARE=$encodedShare&$PARAM_USER=$encodedUser&$PARAM_PASS=$encodedPass"
        }

        /**
         * Builds a URI string targeting a remote file item on a configured server.
         */
        fun buildRemoteUriString(serverId: String, remotePath: String): String {
            val fileName = remotePath.substringAfterLast('/').ifBlank { "video.mkv" }
            val encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8")
            val encodedPath = java.net.URLEncoder.encode(remotePath, "UTF-8")
            return "$SCHEME_REMOTE://$serverId/$encodedFileName?$PARAM_PATH=$encodedPath"
        }

        /**
         * Builds a URI targeting a remote file item on a configured server.
         */
        fun buildRemoteUri(serverId: String, remotePath: String): Uri {
            return Uri.parse(buildRemoteUriString(serverId, remotePath))
        }

        /**
         * Builds a URI targeting a remote file item on a configured server with connection parameters.
         */
        fun buildRemoteUri(server: RemoteServerConfig, remotePath: String): Uri {
            return Uri.parse(buildRemoteUriString(server, remotePath))
        }
    }
}
