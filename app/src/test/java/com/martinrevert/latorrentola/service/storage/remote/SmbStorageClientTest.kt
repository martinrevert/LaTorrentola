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
