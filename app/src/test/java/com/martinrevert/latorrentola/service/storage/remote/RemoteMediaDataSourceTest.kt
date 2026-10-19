package com.martinrevert.latorrentola.service.storage.remote

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

@OptIn(UnstableApi::class)
class RemoteMediaDataSourceTest {

    private val remoteStorageManager: RemoteStorageManager = mockk(relaxed = true)
    private val upstreamDataSource: DataSource = mockk(relaxed = true)
    private val remoteClient: RemoteStorageClient = mockk(relaxed = true)

    private lateinit var dataSource: RemoteMediaDataSource

    private val testServer = RemoteServerConfig(
        id = "srv_test",
        name = "My NAS",
        type = RemoteServerType.NFS,
        host = "192.168.1.100",
        port = 2049,
        shareOrExport = "/media"
    )

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        dataSource = RemoteMediaDataSource(remoteStorageManager, upstreamDataSource)
        every { remoteStorageManager.getServer("srv_test") } returns testServer
        every { remoteStorageManager.getClient(RemoteServerType.NFS) } returns remoteClient
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `buildRemoteUriString creates valid remote URI string with serverId and encoded path`() {
        val uriStr = RemoteMediaDataSource.buildRemoteUriString("srv_test", "movies/sample.mkv")
        assertThat(uriStr).isEqualTo("remote://srv_test/sample.mkv?path=movies%2Fsample.mkv")
    }

    @Test
    fun `open with remote scheme loads from RemoteStorageClient and reads data`() = runTest {
        val testData = "Hello Media Stream from NFS!".toByteArray(Charsets.UTF_8)
        coEvery { remoteClient.getFileSize(testServer, "movies/sample.mkv") } returns testData.size.toLong()
        coEvery { remoteClient.openInputStream(testServer, "movies/sample.mkv", 0L) } returns ByteArrayInputStream(testData)

        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "remote"
        every { uri.host } returns "srv_test"
        every { uri.getQueryParameter("path") } returns "movies/sample.mkv"
        every { uri.toString() } returns "remote://srv_test?path=movies%2Fsample.mkv"

        val dataSpec = DataSpec(uri)

        val bytesAvailable = dataSource.open(dataSpec)
        assertThat(bytesAvailable).isEqualTo(testData.size.toLong())

        val buffer = ByteArray(testData.size)
        val bytesRead = dataSource.read(buffer, 0, buffer.size)

        assertThat(bytesRead).isEqualTo(testData.size)
        assertThat(String(buffer)).isEqualTo("Hello Media Stream from NFS!")

        dataSource.close()
    }

    @Test
    fun `open with non-remote scheme delegates to upstream data source`() {
        val httpUri = mockk<Uri>(relaxed = true)
        every { httpUri.scheme } returns "http"
        every { httpUri.toString() } returns "http://example.com/video.mp4"

        val dataSpec = DataSpec(httpUri)
        every { upstreamDataSource.open(dataSpec) } returns 1024L
        every { upstreamDataSource.read(any(), any(), any()) } returns 512

        val result = dataSource.open(dataSpec)
        assertThat(result).isEqualTo(1024L)

        val buf = ByteArray(512)
        val read = dataSource.read(buf, 0, 512)
        assertThat(read).isEqualTo(512)

        dataSource.close()
        verify { upstreamDataSource.close() }
    }

    @Test(expected = IOException::class)
    fun `open with unknown serverId throws IOException`() {
        every { remoteStorageManager.getServer("unknown_id") } returns null
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "remote"
        every { uri.host } returns "unknown_id"
        every { uri.getQueryParameter("path") } returns "test.mkv"
        every { uri.getQueryParameter("type") } returns null
        every { uri.getQueryParameter("host") } returns null
        every { uri.toString() } returns "remote://unknown_id?path=test.mkv"

        val dataSpec = DataSpec(uri)
        dataSource.open(dataSpec)
    }

    @Test
    fun `open with unknown serverId but complete URI params recovers server and registers it`() = runTest {
        val testData = "Media content from dynamic share".toByteArray(Charsets.UTF_8)
        coEvery { remoteClient.getFileSize(any(), "movies/sample.mkv") } returns testData.size.toLong()
        coEvery { remoteClient.openInputStream(any(), "movies/sample.mkv", 0L) } returns ByteArrayInputStream(testData)

        every { remoteStorageManager.getServer("dynamic_nfs") } returns null

        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "remote"
        every { uri.host } returns "dynamic_nfs"
        every { uri.getQueryParameter("path") } returns "movies/sample.mkv"
        every { uri.getQueryParameter("type") } returns "NFS"
        every { uri.getQueryParameter("host") } returns "192.168.86.29"
        every { uri.getQueryParameter("port") } returns "2049"
        every { uri.getQueryParameter("share") } returns "/export_pelis"
        every { uri.toString() } returns "remote://dynamic_nfs?path=movies%2Fsample.mkv&type=NFS&host=192.168.86.29&port=2049&share=%2Fexport_pelis"

        val dataSpec = DataSpec(uri)
        val bytes = dataSource.open(dataSpec)
        assertThat(bytes).isEqualTo(testData.size.toLong())

        verify {
            remoteStorageManager.registerServer(match {
                it.id == "dynamic_nfs" && it.host == "192.168.86.29" && it.shareOrExport == "/export_pelis" && it.type == RemoteServerType.NFS
            })
        }

        dataSource.close()
    }
}
