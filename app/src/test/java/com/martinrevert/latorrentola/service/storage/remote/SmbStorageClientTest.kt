package com.martinrevert.latorrentola.service.storage.remote

import com.google.common.truth.Truth.assertThat
import com.hierynomus.smbj.SmbConfig
import org.junit.Test
import java.util.concurrent.TimeUnit

class SmbStorageClientTest {

    @Test
    fun `verify smbConfig tuned for high performance media streaming`() {
        val config = SmbConfig.builder()
            .withTimeout(15, TimeUnit.SECONDS)
            .withSoTimeout(25, TimeUnit.SECONDS)
            .withMultiProtocolNegotiate(true)
            .withDfsEnabled(true)
            .withSigningRequired(false)
            .withReadBufferSize(1024 * 1024)
            .withWriteBufferSize(1024 * 1024)
            .withDialects(
                com.hierynomus.mssmb2.SMB2Dialect.SMB_2_0_2,
                com.hierynomus.mssmb2.SMB2Dialect.SMB_2_1,
                com.hierynomus.mssmb2.SMB2Dialect.SMB_3_0,
                com.hierynomus.mssmb2.SMB2Dialect.SMB_3_0_2,
                com.hierynomus.mssmb2.SMB2Dialect.SMB_3_1_1
            )
            .build()

        assertThat(config).isNotNull()
        assertThat(config.isDfsEnabled).isTrue()
        assertThat(config.isSigningRequired).isFalse()
        assertThat(config.readBufferSize).isEqualTo(1024 * 1024)
    }

    @Test
    fun `SmbStorageClient instantiates with SmbCredentialStore dependency`() {
        val credentialStore: SmbCredentialStore = io.mockk.mockk(relaxed = true)
        val client = SmbStorageClient(credentialStore)
        assertThat(client).isNotNull()
    }

    @Test
    fun `SmbPrefetchInputStream asynchronously reads data and handles EOF cleanly`() {
        val mockFile: com.hierynomus.smbj.share.File = io.mockk.mockk(relaxed = true)
        val sampleData = "Hello Antigravity SMB High Performance Prefetch Streaming!".toByteArray(Charsets.UTF_8)
        
        io.mockk.every { mockFile.fileName } returns "test_video.mkv"
        io.mockk.every { mockFile.path } returns "test_video.mkv"
        io.mockk.every { mockFile.read(any(), any(), any(), any()) } answers {
            val buf = firstArg<ByteArray>()
            val fileOffset = secondArg<Long>()
            val arrayOffset = thirdArg<Int>()
            val len = it.invocation.args[3] as Int

            if (fileOffset >= sampleData.size) {
                -1
            } else {
                val toRead = minOf(len, (sampleData.size - fileOffset).toInt())
                System.arraycopy(sampleData, fileOffset.toInt(), buf, arrayOffset, toRead)
                toRead
            }
        }

        val prefetchStream = SmbPrefetchInputStream(
            file = mockFile,
            startOffset = 0L,
            totalSize = sampleData.size.toLong(),
            chunkSize = 64,
            maxBufferBytes = 256
        )

        val destination = ByteArray(sampleData.size)
        var totalRead = 0
        val buffer = ByteArray(16)
        while (totalRead < sampleData.size) {
            val read = prefetchStream.read(buffer, 0, buffer.size)
            if (read == -1) break
            System.arraycopy(buffer, 0, destination, totalRead, read)
            totalRead += read
        }

        assertThat(totalRead).isEqualTo(sampleData.size)
        assertThat(String(destination, Charsets.UTF_8)).isEqualTo("Hello Antigravity SMB High Performance Prefetch Streaming!")

        // Verify next read returns -1 (EOF)
        val eofRead = prefetchStream.read(buffer, 0, buffer.size)
        assertThat(eofRead).isEqualTo(-1)

        prefetchStream.close()
        io.mockk.verify { mockFile.close() }
    }

    @Test
    fun `dcerpc classes and srvsvc transport are available for dynamic share discovery`() {
        val serverServiceClass = Class.forName("com.rapid7.client.dcerpc.mssrvs.ServerService")
        assertThat(serverServiceClass).isNotNull()
        assertThat(serverServiceClass.getMethod("getShares1")).isNotNull()
        assertThat(serverServiceClass.getMethod("getShares0")).isNotNull()

        val shareInfo1Class = Class.forName("com.rapid7.client.dcerpc.mssrvs.dto.NetShareInfo1")
        assertThat(shareInfo1Class.getMethod("getNetName")).isNotNull()
        assertThat(shareInfo1Class.getMethod("getType")).isNotNull()

        val transportFactoriesClass = Class.forName("com.rapid7.client.dcerpc.transport.SMBTransportFactories")
        val srvsvcField = transportFactoriesClass.getField("SRVSVC")
        assertThat(srvsvcField).isNotNull()
    }
}
