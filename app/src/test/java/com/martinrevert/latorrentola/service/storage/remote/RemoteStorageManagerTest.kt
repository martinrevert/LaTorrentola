package com.martinrevert.latorrentola.service.storage.remote

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RemoteStorageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val smbClient: SmbStorageClient = mockk(relaxed = true)
    private val nfsClient: NfsStorageClient = mockk(relaxed = true)
    private val serverRepository: RemoteServerRepository = mockk(relaxed = true)
    private val smbCredentialStore: SmbCredentialStore = mockk(relaxed = true)

    private lateinit var manager: RemoteStorageManager

    @Before
    fun setUp() {
        every { smbCredentialStore.load(any()) } returns null
        manager = RemoteStorageManager(context, smbClient, nfsClient, serverRepository, smbCredentialStore)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `listUsbFiles correctly maps files and directories`() = runTest {
        val root = tempFolder.newFolder("usb_drive")
        File(root, "movie1.mkv").writeBytes(ByteArray(1024))
        File(root, "movie2.mp4").writeBytes(ByteArray(2048))
        File(root, "Subfolder").mkdirs()

        val items = manager.listUsbFiles(root)

        // Directories first, then files
        assertThat(items).hasSize(3)
        assertThat(items[0].name).isEqualTo("Subfolder")
        assertThat(items[0].isDirectory).isTrue()
        assertThat(items[1].name).isEqualTo("movie1.mkv")
        assertThat(items[1].sizeBytes).isEqualTo(1024)
        assertThat(items[2].name).isEqualTo("movie2.mp4")
        assertThat(items[2].sizeBytes).isEqualTo(2048)
    }

    @Test
    fun `saveColocatedSubtitle for USB writes vtt alongside video file`() = runTest {
        val root = tempFolder.newFolder("usb_movie")
        val video = File(root, "Avatar.2009.1080p.mkv")
        video.writeBytes(ByteArray(512))

        val remoteItem = RemoteFileItem(
            path = video.absolutePath,
            name = video.name,
            isDirectory = false,
            sizeBytes = 512,
            serverConfigId = "usb"
        )

        val vttContent = "WEBVTT\n\n00:00:01.000 --> 00:00:04.000\nI see you."
        val success = manager.saveColocatedSubtitle(remoteItem, vttContent, "en")

        assertThat(success).isTrue()
        val expectedSubFile = File(root, "Avatar.2009.1080p.en.vtt")
        assertThat(expectedSubFile.exists()).isTrue()
        assertThat(expectedSubFile.readText()).isEqualTo(vttContent)
    }

    @Test
    fun `saveColocatedSubtitle for SMB calls smbClient writeSubtitle`() = runTest {
        val config = RemoteServerConfig(
            id = "server-smb",
            name = "NAS",
            type = RemoteServerType.SMB,
            host = "192.168.1.50",
            shareOrExport = "movies"
        )
        every { serverRepository.getServer("server-smb") } returns config
        coEvery { smbClient.writeSubtitle(any(), any(), any(), any()) } returns true

        val remoteItem = RemoteFileItem(
            path = "SciFi/Interstellar.2014.mkv",
            name = "Interstellar.2014.mkv",
            isDirectory = false,
            sizeBytes = 5_000_000_000L,
            serverConfigId = "server-smb"
        )

        val vttContent = "WEBVTT\n\n00:01:00.000 --> 00:01:05.000\nCooper, go!"
        val success = manager.saveColocatedSubtitle(remoteItem, vttContent, "es")

        assertThat(success).isTrue()
        coVerify {
            smbClient.writeSubtitle(
                config = config,
                directoryPath = "SciFi",
                fileName = "Interstellar.2014.es.vtt",
                vttContent = vttContent
            )
        }
    }

    @Test
    fun `listRemoteFiles with NFS delegates to nfsClient`() = runTest {
        val config = RemoteServerConfig(
            id = "server-nfs",
            name = "NAS NFS",
            type = RemoteServerType.NFS,
            host = "192.168.1.100",
            port = 2049,
            shareOrExport = "/volume1/media"
        )
        val expected = listOf(
            RemoteFileItem("movie.mkv", "movie.mkv", isDirectory = false, sizeBytes = 100L, serverConfigId = "server-nfs")
        )
        coEvery { nfsClient.listFiles(config, "movies") } returns expected

        val result = manager.listRemoteFiles(config, "movies")
        assertThat(result).isEqualTo(expected)
        coVerify { nfsClient.listFiles(config, "movies") }
    }

    @Test
    fun `saveColocatedSubtitle for NFS calls nfsClient writeSubtitle`() = runTest {
        val config = RemoteServerConfig(
            id = "server-nfs",
            name = "NAS NFS",
            type = RemoteServerType.NFS,
            host = "192.168.1.100",
            port = 2049,
            shareOrExport = "/volume1/media"
        )
        every { serverRepository.getServer("server-nfs") } returns config
        coEvery { nfsClient.writeSubtitle(any(), any(), any(), any()) } returns true

        val remoteItem = RemoteFileItem(
            path = "SciFi/Dune.2021.mkv",
            name = "Dune.2021.mkv",
            isDirectory = false,
            sizeBytes = 6_000_000_000L,
            serverConfigId = "server-nfs"
        )

        val vttContent = "WEBVTT\n\n00:01:00.000 --> 00:01:05.000\nFear is the mind-killer."
        val success = manager.saveColocatedSubtitle(remoteItem, vttContent, "es")

        assertThat(success).isTrue()
        coVerify {
            nfsClient.writeSubtitle(
                config = config,
                directoryPath = "SciFi",
                fileName = "Dune.2021.es.vtt",
                vttContent = vttContent
            )
        }
    }

    @Test
    fun `getServer returns cached server when not found in repository`() {
        val dynamicServer = RemoteServerConfig(
            id = "nfs-dynamic-1",
            name = "Scanned NFS",
            type = RemoteServerType.NFS,
            host = "192.168.86.29",
            port = 2049,
            shareOrExport = "/export_pelis"
        )
        every { serverRepository.getServer("nfs-dynamic-1") } returns null

        manager.registerServer(dynamicServer)

        val retrieved = manager.getServer("nfs-dynamic-1")
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.id).isEqualTo("nfs-dynamic-1")
        assertThat(retrieved?.host).isEqualTo("192.168.86.29")
        assertThat(retrieved?.shareOrExport).isEqualTo("/export_pelis")
    }

    @Test
    fun `resolveServerConfig enriches SMB server with stored credentials`() {
        val server = RemoteServerConfig(
            id = "smb-1",
            name = "NAS SMB",
            type = RemoteServerType.SMB,
            host = "192.168.86.29",
            port = 445,
            shareOrExport = "pelis"
        )
        every { smbCredentialStore.load("192.168.86.29") } returns SmbCredentials(
            host = "192.168.86.29",
            username = "admin",
            password = "secretPassword",
            domain = "WORKGROUP"
        )

        val resolved = manager.resolveServerConfig(server)
        assertThat(resolved.username).isEqualTo("admin")
        assertThat(resolved.password).isEqualTo("secretPassword")
        assertThat(resolved.domain).isEqualTo("WORKGROUP")
    }

    @Test
    fun `listRemoteFiles for SMB resolves credentials before invoking smbClient`() = runTest {
        val server = RemoteServerConfig(
            id = "smb-1",
            name = "NAS SMB",
            type = RemoteServerType.SMB,
            host = "192.168.86.29",
            port = 445,
            shareOrExport = "pelis"
        )
        every { smbCredentialStore.load("192.168.86.29") } returns SmbCredentials(
            host = "192.168.86.29",
            username = "admin",
            password = "secretPassword"
        )
        coEvery { smbClient.listFiles(any(), any()) } returns emptyList()

        manager.listRemoteFiles(server, "")

        coVerify {
            smbClient.listFiles(
                match { it.username == "admin" && it.password == "secretPassword" },
                ""
            )
        }
    }
}

