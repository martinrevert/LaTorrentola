package com.martinrevert.latorrentola.service.storage.remote

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class NetworkStorageScannerTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var smbClient: SmbStorageClient
    private lateinit var nfsClient: NfsStorageClient
    private lateinit var scanner: NetworkStorageScanner

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        connectivityManager = mockk(relaxed = true)
        every { connectivityManager.activeNetwork } returns null

        context = mockk(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager

        smbClient = mockk(relaxed = true)
        nfsClient = mockk(relaxed = true)
        scanner = NetworkStorageScanner(context, smbClient, nfsClient)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `scanNetwork discovers native NFS broadcast hosts, filters virtual subnets and expands exports`() = runTest {
        mockkObject(LibNfsNative)
        every { LibNfsNative.isAvailable } returns true
        every { LibNfsNative.findLocalServersSynchronized() } returns arrayOf(
            "192.168.1.50",
            "192.168.1.50", // duplicate should be deduplicated
            "192.168.49.1", // Wi-Fi Direct virtual subnet should be filtered
            "127.0.0.1"     // Loopback should be filtered
        )
        coEvery {
            nfsClient.listEntryPoints(match { it.host == "192.168.1.50" })
        } returns listOf("/export/movies", "/export/series")

        val spyScanner = io.mockk.spyk(scanner)
        every { spyScanner.getActiveNetworkIpv4() } returns null

        val result = spyScanner.scanNetwork()

        assertThat(result).hasSize(2)
        assertThat(result.map { it.host }).containsExactly("192.168.1.50", "192.168.1.50")
        assertThat(result.map { it.shareOrExport }).containsExactly("/export/movies", "/export/series")
    }

    @Test
    fun `scanNetwork serializes concurrent invocations safely`() = runTest {
        mockkObject(LibNfsNative)
        every { LibNfsNative.isAvailable } returns true
        every { LibNfsNative.findLocalServersSynchronized() } returns arrayOf("192.168.1.100")
        coEvery { nfsClient.listEntryPoints(any()) } returns listOf("/volume1/video")

        val spyScanner = io.mockk.spyk(scanner)
        every { spyScanner.getActiveNetworkIpv4() } returns null

        val results = listOf(
            async { spyScanner.scanNetwork() },
            async { spyScanner.scanNetwork() }
        ).awaitAll()

        assertThat(results[0]).hasSize(1)
        assertThat(results[1]).hasSize(1)
        assertThat(results[0].first().shareOrExport).isEqualTo("/volume1/video")
    }
}
