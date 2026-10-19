package com.martinrevert.latorrentola.service.storage.remote

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class NfsStorageClientTest {

    private lateinit var client: NfsStorageClient

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any()) } returns 0
        client = NfsStorageClient()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `testConnection returns false for unreachable host gracefully without crashing`() = runTest {
        val config = RemoteServerConfig(
            id = "nfs-dummy",
            name = "Dummy NFS",
            type = RemoteServerType.NFS,
            host = "192.0.2.1", // TEST-NET-1, unreachable
            port = 2049,
            shareOrExport = "/export/test"
        )

        val connected = client.testConnection(config)
        assertThat(connected).isFalse()
    }

    @Test
    fun `listFiles throws exception on unreachable host so UI can surface error`() = runTest {
        val config = RemoteServerConfig(
            id = "nfs-dummy",
            name = "Dummy NFS",
            type = RemoteServerType.NFS,
            host = "192.0.2.1",
            port = 2049,
            shareOrExport = "/export/test"
        )

        var errorCaught = false
        try {
            client.listFiles(config, "")
        } catch (_: Exception) {
            errorCaught = true
        }
        assertThat(errorCaught).isTrue()
    }
}
