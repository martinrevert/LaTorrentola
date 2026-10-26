package com.martinrevert.latorrentola.ui.media

import android.content.Context
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.service.storage.StorageLocation
import com.martinrevert.latorrentola.service.storage.TorrentStorageResolver
import com.martinrevert.latorrentola.service.storage.remote.NetworkStorageScanner
import com.martinrevert.latorrentola.service.storage.remote.RemoteFileItem
import com.martinrevert.latorrentola.service.storage.remote.RemoteMediaHttpServer
import com.martinrevert.latorrentola.service.storage.remote.RemoteServerConfig
import com.martinrevert.latorrentola.service.storage.remote.RemoteServerRepository
import com.martinrevert.latorrentola.service.storage.remote.RemoteServerType
import com.martinrevert.latorrentola.service.storage.remote.RemoteStorageManager
import com.martinrevert.latorrentola.utils.PreferenceManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ExternalMediaBrowserViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val storageManager: RemoteStorageManager = mockk(relaxed = true)
    private val serverRepository: RemoteServerRepository = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private val networkScanner: NetworkStorageScanner = mockk(relaxed = true)
    private val smbCredentialStore: com.martinrevert.latorrentola.service.storage.remote.SmbCredentialStore = mockk(relaxed = true)

    private val serversFlow = MutableStateFlow<List<RemoteServerConfig>>(emptyList())
    private val storageLocationFlow = MutableStateFlow(TorrentStorageResolver.LOCATION_INTERNAL)

    private lateinit var viewModel: ExternalMediaBrowserViewModel

    @Before
    fun setUp() {
        every { context.filesDir } returns tempFolder.root
        every { serverRepository.serversFlow } returns serversFlow
        every { serverRepository.getServers() } returns emptyList()
        every { serverRepository.getServer(any()) } returns null
        every { preferenceManager.storageLocationFlow } returns storageLocationFlow
        every { preferenceManager.getStorageLocation() } returns TorrentStorageResolver.LOCATION_INTERNAL
        every { smbCredentialStore.hasCredentials(any()) } returns false
        every { smbCredentialStore.load(any()) } returns null
        coEvery { networkScanner.scanNetwork() } returns emptyList()
        viewModel = ExternalMediaBrowserViewModel(context, storageManager, serverRepository, preferenceManager, networkScanner, smbCredentialStore)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `tab switching updates selected tab in state`() = runTest {
        assertThat(viewModel.uiState.value.selectedTab).isEqualTo(ExternalMediaTab.USB)

        viewModel.selectTab(ExternalMediaTab.NETWORK)
        assertThat(viewModel.uiState.value.selectedTab).isEqualTo(ExternalMediaTab.NETWORK)

        viewModel.selectTab(ExternalMediaTab.USB)
        assertThat(viewModel.uiState.value.selectedTab).isEqualTo(ExternalMediaTab.USB)
    }

    @Test
    fun `remote servers from repository are emitted to uiState`() = runTest {
        val server = RemoteServerConfig(
            id = "server-1",
            name = "NAS",
            type = RemoteServerType.SMB,
            host = "192.168.1.100",
            port = 445,
            shareOrExport = "Media"
        )

        serversFlow.value = listOf(server)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.remoteServers).hasSize(1)
        assertThat(viewModel.uiState.value.remoteServers.first().name).isEqualTo("NAS")
    }

    @Test
    fun `selectServer triggers browseRemotePath and updates state`() = runTest {
        val server = RemoteServerConfig(
            id = "server-1",
            name = "NAS",
            type = RemoteServerType.SMB,
            host = "192.168.1.100",
            port = 445,
            shareOrExport = "Media"
        )
        val fileItems = listOf(
            RemoteFileItem(
                path = "Movies",
                name = "Movies",
                isDirectory = true,
                sizeBytes = 0L,
                serverConfigId = "server-1"
            ),
            RemoteFileItem(
                path = "video.mp4",
                name = "video.mp4",
                isDirectory = false,
                sizeBytes = 1048576L,
                serverConfigId = "server-1"
            )
        )

        coEvery { storageManager.listRemoteFiles(server, "") } returns fileItems

        viewModel.selectServer(server)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedServer).isEqualTo(server)
        assertThat(viewModel.uiState.value.remoteFiles).isEqualTo(fileItems)
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `saveServer calls repository saveServer and closes dialog`() = runTest {
        viewModel.openServerDialog(null)
        assertThat(viewModel.uiState.value.showServerDialog).isTrue()

        viewModel.saveServer(
            id = null,
            name = "My PC",
            type = RemoteServerType.SMB,
            host = "192.168.1.50",
            port = 445,
            share = "Shared",
            domain = null,
            username = "user",
            password = "pass"
        )

        verify {
            serverRepository.saveServer(match {
                it.name == "My PC" && it.host == "192.168.1.50" && it.shareOrExport == "Shared"
            })
        }
        assertThat(viewModel.uiState.value.showServerDialog).isFalse()
    }

    @Test
    fun `saveServer with NFS saves correct server config`() = runTest {
        viewModel.saveServer(
            id = null,
            name = "NAS NFS",
            type = RemoteServerType.NFS,
            host = "192.168.1.100",
            port = 2049,
            share = "/volume1/media",
            domain = null,
            username = "0",
            password = null
        )

        verify {
            serverRepository.saveServer(match {
                it.type == RemoteServerType.NFS && it.port == 2049 && it.shareOrExport == "/volume1/media"
            })
        }
    }

    @Test
    fun `deleteServer invokes repository delete`() = runTest {
        viewModel.deleteServer("server-delete-id")
        verify { serverRepository.deleteServer("server-delete-id") }
    }

    @Test
    fun `onFileItemClicked directory navigates into folder`() = runTest {
        val dir = tempFolder.newFolder("usb_dir")
        val subDir = File(dir, "sub")
        subDir.mkdirs()

        val item = RemoteFileItem(
            path = subDir.absolutePath,
            name = "sub",
            isDirectory = true,
            serverConfigId = "usb"
        )
        coEvery { storageManager.listUsbFiles(subDir) } returns emptyList()

        viewModel.selectTab(ExternalMediaTab.USB)
        viewModel.onFileItemClicked(item)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.currentUsbFolder?.absolutePath).isEqualTo(subDir.absolutePath)
    }

    @Test
    fun `onFileItemClicked USB video file directly emits PlayLocalFile event`() = runTest {
        val root = tempFolder.newFolder("usb_root")
        val video = File(root, "Interstellar.mkv")
        video.writeBytes(ByteArray(1024))

        val item = RemoteFileItem(
            path = video.absolutePath,
            name = "Interstellar.mkv",
            isDirectory = false,
            sizeBytes = 1024L,
            serverConfigId = "usb"
        )

        viewModel.selectTab(ExternalMediaTab.USB)

        viewModel.events.test {
            viewModel.onFileItemClicked(item)
            val event = awaitItem()
            assertThat(event).isInstanceOf(ExternalMediaEvent.PlayLocalFile::class.java)
            val playLocal = event as ExternalMediaEvent.PlayLocalFile
            assertThat(playLocal.file.absolutePath).isEqualTo(video.absolutePath)
            assertThat(playLocal.title).isEqualTo("Interstellar.mkv")
        }
    }

    @Test
    fun `onFileItemClicked remote video file directly emits PlayRemoteStream event without dialog`() = runTest {
        val server = RemoteServerConfig(
            id = "server-smb",
            name = "NAS",
            type = RemoteServerType.SMB,
            host = "192.168.1.100",
            port = 445,
            shareOrExport = "Media"
        )
        val fileItem = RemoteFileItem(
            path = "Movies/Inception.mkv",
            name = "Inception.mkv",
            isDirectory = false,
            sizeBytes = 2048L,
            serverConfigId = "server-smb"
        )

        viewModel.selectTab(ExternalMediaTab.NETWORK)
        viewModel.selectServer(server)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onFileItemClicked(fileItem)
            val event = awaitItem()
            assertThat(event).isInstanceOf(ExternalMediaEvent.PlayRemoteStream::class.java)
            val playRemote = event as ExternalMediaEvent.PlayRemoteStream
            assertThat(playRemote.streamUrl).startsWith("remote://server-smb/Inception.mkv?path=Movies%2FInception.mkv")
            assertThat(playRemote.castUrl).isEmpty()
            assertThat(playRemote.title).isEqualTo("Inception.mkv")
            assertThat(playRemote.serverId).isEqualTo("server-smb")
            assertThat(playRemote.remotePath).isEqualTo("Movies/Inception.mkv")
            assertThat(playRemote.castImmediately).isFalse()
        }
    }

    @Test
    fun `navigateUpRemote from subfolder navigates back up`() = runTest {
        val server = RemoteServerConfig(
            id = "server-smb",
            name = "NAS",
            type = RemoteServerType.SMB,
            host = "192.168.1.100",
            port = 445,
            shareOrExport = "Media"
        )
        every { serverRepository.getServer(server.id) } returns server
        viewModel.selectServer(server)
        viewModel.browseRemotePath(server, "Movies/SciFi")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.currentRemotePath).isEqualTo("Movies/SciFi")

        viewModel.navigateUpRemote()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.currentRemotePath).isEqualTo("Movies")

        viewModel.navigateUpRemote()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.currentRemotePath).isEqualTo("")

        viewModel.navigateUpRemote()
        advanceUntilIdle()

        // When navigating up from root of a configured share, server selection is cleared
        assertThat(viewModel.uiState.value.selectedServer).isNull()
    }

    @Test
    fun `clicking entry point on host-level server navigates into that share`() = runTest {
        val hostOnlyServer = RemoteServerConfig(
            id = "server-nfs",
            name = "NAS",
            type = RemoteServerType.NFS,
            host = "192.168.86.29",
            port = 2049,
            shareOrExport = ""
        )
        val entryItem = RemoteFileItem(
            path = "/pelis",
            name = "pelis",
            isDirectory = true,
            sizeBytes = 0L,
            serverConfigId = hostOnlyServer.id
        )

        coEvery { storageManager.listRemoteFiles(any(), any()) } returns emptyList()

        viewModel.selectTab(ExternalMediaTab.NETWORK)
        viewModel.selectServer(hostOnlyServer)
        advanceUntilIdle()

        viewModel.onFileItemClicked(entryItem)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedServer?.shareOrExport).isEqualTo("/pelis")
        assertThat(viewModel.uiState.value.currentRemotePath).isEqualTo("")
    }

    @Test
    fun `auth error triggers showAuthDialog and submitAuthentication saves credentials and retries`() = runTest {
        val server = RemoteServerConfig(
            id = "server-auth",
            name = "Protected NAS",
            type = RemoteServerType.SMB,
            host = "192.168.1.50",
            port = 445,
            shareOrExport = "secure"
        )

        coEvery { storageManager.listRemoteFiles(server, "") } throws java.io.IOException("STATUS_LOGON_FAILURE")

        viewModel.selectTab(ExternalMediaTab.NETWORK)
        viewModel.selectServer(server)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showAuthDialog).isTrue()
        assertThat(viewModel.uiState.value.authTargetServer?.id).isEqualTo(server.id)

        coEvery { storageManager.listRemoteFiles(match { it.username == "admin" && it.password == "secret" }, "") } returns listOf(
            RemoteFileItem(path = "doc.pdf", name = "doc.pdf", isDirectory = false, sizeBytes = 100L, lastModified = 0L, serverConfigId = server.id)
        )

        viewModel.submitAuthentication("admin", "secret", "WORKGROUP", saveCredentials = true)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showAuthDialog).isFalse()
        verify { serverRepository.saveServer(match { it.username == "admin" && it.password == "secret" }) }
        verify {
            smbCredentialStore.save(
                match { it.host == "192.168.1.50" && it.username == "admin" && it.password == "secret" && it.domain == "WORKGROUP" }
            )
        }
        assertThat(viewModel.uiState.value.remoteFiles).hasSize(1)
    }

    @Test
    fun `smbj SecretKey getEncoded null exception triggers showAuthDialog and suppresses red error banner`() = runTest {
        val server = RemoteServerConfig(
            id = "server-smb-npe",
            name = "NAS SMB",
            type = RemoteServerType.SMB,
            host = "192.168.86.29",
            port = 445,
            shareOrExport = ""
        )

        coEvery { storageManager.listRemoteFiles(server, "") } throws NullPointerException(
            "Attempt to invoke interface method 'byte[] javax.crypto.SecretKey.getEncoded()' on a null object reference"
        )

        viewModel.selectServer(server)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showAuthDialog).isTrue()
        assertThat(viewModel.uiState.value.authTargetServer?.host).isEqualTo("192.168.86.29")
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `auth error with existing stored credentials populates authTargetHasStoredCredentials and prefilled username`() = runTest {
        val server = RemoteServerConfig(
            id = "server-auth-existing",
            name = "Protected NAS",
            type = RemoteServerType.SMB,
            host = "192.168.1.50",
            port = 445,
            shareOrExport = "secure"
        )

        every { smbCredentialStore.hasCredentials("192.168.1.50") } returns true
        every { smbCredentialStore.load("192.168.1.50") } returns com.martinrevert.latorrentola.service.storage.remote.SmbCredentials(
            host = "192.168.1.50",
            username = "savedUser",
            password = "wrongPassword",
            domain = "MYDOMAIN"
        )
        coEvery { storageManager.listRemoteFiles(server, "") } throws java.io.IOException("STATUS_LOGON_FAILURE")

        viewModel.selectServer(server)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showAuthDialog).isTrue()
        assertThat(viewModel.uiState.value.authTargetHasStoredCredentials).isTrue()
        assertThat(viewModel.uiState.value.authTargetServer?.username).isEqualTo("savedUser")
        assertThat(viewModel.uiState.value.authTargetServer?.password).isEqualTo("wrongPassword")
        assertThat(viewModel.uiState.value.authTargetServer?.domain).isEqualTo("MYDOMAIN")
    }

    @Test
    fun `openServerDialog enriches editingServer with stored credentials for SMB server`() = runTest {
        val server = RemoteServerConfig(
            id = "smb-1",
            name = "My SMB",
            type = RemoteServerType.SMB,
            host = "192.168.1.120",
            port = 445
        )
        every { smbCredentialStore.load("192.168.1.120") } returns com.martinrevert.latorrentola.service.storage.remote.SmbCredentials(
            host = "192.168.1.120",
            username = "admin",
            password = "storedPassword123"
        )

        viewModel.openServerDialog(server)

        val editing = viewModel.uiState.value.editingServer
        assertThat(editing).isNotNull()
        assertThat(editing?.username).isEqualTo("admin")
        assertThat(editing?.password).isEqualTo("storedPassword123")
    }

    @Test
    fun `clearStoredCredentials deletes credentials from keystore and updates uiState`() = runTest {
        val server = RemoteServerConfig(
            id = "server-to-clear",
            name = "NAS",
            type = RemoteServerType.SMB,
            host = "192.168.1.50",
            username = "savedUser",
            password = "secretPassword"
        )
        every { serverRepository.getServers() } returns listOf(server)

        viewModel.clearStoredCredentials("192.168.1.50")

        verify { smbCredentialStore.clear("192.168.1.50") }
        verify { serverRepository.saveServer(match { it.host == "192.168.1.50" && it.username == null && it.password == null }) }
        assertThat(viewModel.uiState.value.authTargetHasStoredCredentials).isFalse()
    }

    @Test
    fun `setActiveDownloadStorage with USB location persists selection via preferenceManager`() = runTest {
        viewModel.setActiveDownloadStorage("/storage/usb_drive")
        verify { preferenceManager.setStorageLocation("/storage/usb_drive") }
    }

    @Test
    fun `setActiveDownloadStorage with remote location resets to internal storage`() = runTest {
        viewModel.setActiveDownloadStorage("remote:nas-123")
        verify { preferenceManager.setStorageLocation(TorrentStorageResolver.LOCATION_INTERNAL) }
    }

    @Test
    fun `deleteServer deletes server from repository`() = runTest {
        viewModel.deleteServer("server-to-delete")
        verify { serverRepository.deleteServer("server-to-delete") }
    }

    @Test
    fun `scanNetworkShares merges discovered shares into remoteServers without duplicating saved servers`() = runTest {
        val savedServer = RemoteServerConfig(
            id = "saved-1",
            name = "NAS",
            type = RemoteServerType.SMB,
            host = "192.168.1.100",
            port = 445,
            shareOrExport = "Media"
        )
        val discoveredShare = RemoteServerConfig(
            id = "disc-1",
            name = "Public NFS (192.168.1.50/pelis)",
            type = RemoteServerType.NFS,
            host = "192.168.1.50",
            port = 2049,
            shareOrExport = "/pelis"
        )
        val duplicateDiscovered = RemoteServerConfig(
            id = "disc-dup",
            name = "Duplicate NAS",
            type = RemoteServerType.SMB,
            host = "192.168.1.100",
            port = 445,
            shareOrExport = "Media"
        )

        every { serverRepository.getServers() } returns listOf(savedServer)
        coEvery { networkScanner.scanNetwork() } returns listOf(discoveredShare, duplicateDiscovered)

        viewModel.scanNetworkShares()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isScanningNetwork).isFalse()
        assertThat(viewModel.uiState.value.remoteServers).hasSize(2)
        assertThat(viewModel.uiState.value.remoteServers.map { it.shareOrExport }).containsExactly("Media", "/pelis")
    }

    @Test
    fun `updateActiveStorageInfo computes activeStorageExactPath for internal storage and resets remote location`() = runTest {
        // Internal storage path
        assertThat(viewModel.uiState.value.activeStorageExactPath).contains("torrent_downloads")
        assertThat(viewModel.uiState.value.activeStorageType).isEqualTo("INTERNAL")

        // If a remote location is encountered, it must reset to internal storage
        storageLocationFlow.value = "remote:server-exact"
        advanceUntilIdle()

        verify { preferenceManager.setStorageLocation(TorrentStorageResolver.LOCATION_INTERNAL) }
        assertThat(viewModel.uiState.value.activeStorageType).isEqualTo("INTERNAL")
    }

    @Test
    fun `onFileItemClicked for network share emits direct remote URI without starting HTTP server`() = runTest {
        val server = RemoteServerConfig(
            id = "server-nas",
            name = "NAS",
            type = RemoteServerType.NFS,
            host = "192.168.1.100",
            port = 2049,
            shareOrExport = "/video"
        )
        val movieItem = RemoteFileItem(
            path = "movies/avatar.mkv",
            name = "avatar.mkv",
            isDirectory = false,
            sizeBytes = 2000L,
            serverConfigId = server.id
        )

        viewModel.selectTab(ExternalMediaTab.NETWORK)
        viewModel.selectServer(server)

        viewModel.events.test {
            viewModel.onFileItemClicked(movieItem)
            val event = awaitItem() as ExternalMediaEvent.PlayRemoteStream
            assertThat(event.streamUrl).startsWith("remote://server-nas/avatar.mkv?path=movies%2Favatar.mkv")
            assertThat(event.castUrl).isEmpty()
            assertThat(event.castImmediately).isFalse()
            assertThat(event.title).isEqualTo("avatar.mkv")
            assertThat(event.serverJson).isNotNull()
        }

        // Storage manager must NOT have started an HTTP stream server for ExoPlayer direct playback
        coVerify(exactly = 0) { storageManager.startRemoteStreamServer(any(), any(), any()) }
    }

    @Test
    fun `navigateUpUsb navigates up until root is reached`() = runTest {
        val rootDir = tempFolder.newFolder("usb_root")
        val subDir = File(rootDir, "sub_folder").apply { mkdirs() }
        val loc = StorageLocation(
            id = rootDir.absolutePath,
            displayName = "USB Drive",
            rootDirectory = rootDir,
            isRemovable = true,
            availableBytes = 1000L,
            totalBytes = 2000L
        )

        viewModel.selectUsbLocation(loc)
        viewModel.browseUsbDirectory(subDir)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.currentUsbFolder?.absolutePath).isEqualTo(subDir.absolutePath)

        viewModel.navigateUpUsb()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.currentUsbFolder?.absolutePath).isEqualTo(rootDir.absolutePath)

        // Calling again should not navigate higher than root
        viewModel.navigateUpUsb()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.currentUsbFolder?.absolutePath).isEqualTo(rootDir.absolutePath)
    }

    @Test
    fun `storage mount events reload USB locations`() = runTest {
        com.martinrevert.latorrentola.service.storage.StorageEvents.notifyMounted("/storage/new_usb")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.usbLocations).isNotEmpty()
    }

    @Test
    fun `selectTab USB reloads USB locations`() = runTest {
        viewModel.selectTab(ExternalMediaTab.NETWORK)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.selectedTab).isEqualTo(ExternalMediaTab.NETWORK)

        viewModel.selectTab(ExternalMediaTab.USB)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.selectedTab).isEqualTo(ExternalMediaTab.USB)
        assertThat(viewModel.uiState.value.usbLocations).isNotEmpty()
    }

    @Test
    fun `loadUsbLocations auto-selects removable USB drive when available and previously on internal`() = runTest {
        mockkObject(TorrentStorageResolver)
        try {
            val internalLoc = StorageLocation(
                id = TorrentStorageResolver.LOCATION_INTERNAL,
                displayName = "Internal",
                rootDirectory = tempFolder.newFolder("internal_test"),
                isRemovable = false,
                availableBytes = 5000L,
                totalBytes = 10000L
            )
            val usbLoc = StorageLocation(
                id = "/storage/usb1",
                displayName = "USB Drive",
                rootDirectory = tempFolder.newFolder("usb1_test"),
                isRemovable = true,
                availableBytes = 8000L,
                totalBytes = 16000L
            )

            every { TorrentStorageResolver.getAvailableStorageLocations(any()) } returns listOf(internalLoc, usbLoc)

            viewModel.loadUsbLocations()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.selectedUsbLocation?.id).isEqualTo(usbLoc.id)
            assertThat(viewModel.uiState.value.currentUsbFolder?.absolutePath).isEqualTo(usbLoc.rootDirectory.absolutePath)
        } finally {
            unmockkObject(TorrentStorageResolver)
        }
    }
}


