package com.martinrevert.latorrentola.ui.media

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.Build
import android.os.Environment
import com.martinrevert.latorrentola.service.storage.StorageEvent
import com.martinrevert.latorrentola.service.storage.StorageEvents
import com.martinrevert.latorrentola.service.storage.StorageLocation
import com.martinrevert.latorrentola.service.storage.TorrentStorageResolver
import com.martinrevert.latorrentola.service.storage.remote.NetworkStorageScanner
import com.martinrevert.latorrentola.service.storage.remote.RemoteFileItem
import com.martinrevert.latorrentola.service.storage.remote.RemoteMediaHttpServer
import com.martinrevert.latorrentola.service.storage.remote.RemoteServerConfig
import com.martinrevert.latorrentola.service.storage.remote.RemoteServerRepository
import com.martinrevert.latorrentola.service.storage.remote.RemoteServerType
import com.martinrevert.latorrentola.service.storage.remote.RemoteStorageManager
import com.martinrevert.latorrentola.service.storage.remote.SmbCredentialStore
import com.martinrevert.latorrentola.service.storage.remote.SmbCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.utils.PreferenceManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

enum class ExternalMediaTab {
    USB,
    NETWORK
}

data class ExternalMediaUiState(
    val selectedTab: ExternalMediaTab = ExternalMediaTab.USB,
    val usbLocations: List<StorageLocation> = emptyList(),
    val selectedUsbLocation: StorageLocation? = null,
    val currentUsbFolder: File? = null,
    val usbFiles: List<RemoteFileItem> = emptyList(),
    val remoteServers: List<RemoteServerConfig> = emptyList(),
    val selectedServer: RemoteServerConfig? = null,
    val currentRemotePath: String = "",
    val remoteFiles: List<RemoteFileItem> = emptyList(),
    val isLoading: Boolean = false,
    val isScanningNetwork: Boolean = false,
    val errorMessage: String? = null,
    val showServerDialog: Boolean = false,
    val editingServer: RemoteServerConfig? = null,
    val showAuthDialog: Boolean = false,
    val authTargetServer: RemoteServerConfig? = null,
    val authTargetShare: String? = null,
    val authTargetHasStoredCredentials: Boolean = false,
    val activeStorageId: String = TorrentStorageResolver.LOCATION_INTERNAL,
    val activeStorageDisplayName: String = "",
    val activeStorageExactPath: String = "",
    val activeStorageAvailableBytes: Long = 0L,
    val activeStorageType: String = "INTERNAL",
    val hasAllFilesAccess: Boolean = true
)

sealed interface ExternalMediaEvent {
    data class PlayLocalFile(
        val file: File,
        val title: String,
        val castImmediately: Boolean = false
    ) : ExternalMediaEvent

    data class PlayRemoteStream(
        val streamUrl: String,
        val castUrl: String,
        val title: String,
        val serverId: String,
        val remotePath: String,
        val castImmediately: Boolean = false,
        val serverJson: String? = null
    ) : ExternalMediaEvent
}

@HiltViewModel
class ExternalMediaBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: RemoteStorageManager,
    private val serverRepository: RemoteServerRepository,
    private val preferenceManager: PreferenceManager,
    private val networkScanner: NetworkStorageScanner,
    private val smbCredentialStore: SmbCredentialStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExternalMediaUiState())
    val uiState: StateFlow<ExternalMediaUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ExternalMediaEvent>()
    val events: SharedFlow<ExternalMediaEvent> = _events.asSharedFlow()

    private var storageVolumeCallback: Any? = null

    init {
        loadUsbLocations()
        observeRemoteServers()
        observeStorageLocation()
        scanNetworkShares()
        observeStorageEvents()
        registerStorageVolumeCallback()
    }

    private fun registerStorageVolumeCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? android.os.storage.StorageManager
            val cb = object : android.os.storage.StorageManager.StorageVolumeCallback() {
                override fun onStateChanged(volume: android.os.storage.StorageVolume) {
                    android.util.Log.i(
                        "ExternalMediaBrowser",
                        "StorageVolumeCallback state changed: ${volume.directory} (state=${volume.state})"
                    )
                    loadUsbLocations()
                    if (volume.state == Environment.MEDIA_MOUNTED) {
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(1200)
                            loadUsbLocations()
                        }
                    }
                }
            }
            try {
                sm?.registerStorageVolumeCallback(context.mainExecutor, cb)
                storageVolumeCallback = cb
            } catch (e: Exception) {
                android.util.Log.w("ExternalMediaBrowser", "Could not register StorageVolumeCallback", e)
            }
        }
    }

    private fun observeStorageEvents() {
        viewModelScope.launch {
            StorageEvents.events.collect { event ->
                loadUsbLocations()
                if (event is StorageEvent.Mounted) {
                    kotlinx.coroutines.delay(1200)
                    loadUsbLocations()
                    kotlinx.coroutines.delay(1300)
                    loadUsbLocations()
                }
            }
        }
    }

    fun selectTab(tab: ExternalMediaTab) {
        _uiState.update { it.copy(selectedTab = tab, errorMessage = null) }
        if (tab == ExternalMediaTab.USB) {
            loadUsbLocations()
        } else if (tab == ExternalMediaTab.NETWORK) {
            scanNetworkShares()
        }
    }

    private var isUserExplicitSelection = false

    fun refreshUsbLocations() {
        loadUsbLocations()
    }

    fun loadUsbLocations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val locations = try {
                TorrentStorageResolver.getAvailableStorageLocations(context)
            } catch (e: Exception) {
                emptyList()
            }
            val hasRemovable = locations.any { it.isRemovable }
            val currentSelected = _uiState.value.selectedUsbLocation
            val stillAvailable = locations.firstOrNull { it.id == currentSelected?.id }
            if (currentSelected?.isRemovable == true && stillAvailable == null) {
                isUserExplicitSelection = false
            }
            val target = when {
                isUserExplicitSelection && stillAvailable != null -> stillAvailable
                hasRemovable -> locations.firstOrNull { it.isRemovable } ?: locations.firstOrNull()
                stillAvailable != null -> stillAvailable
                else -> locations.firstOrNull()
            }

            val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    Environment.isExternalStorageManager()
                } catch (_: Throwable) {
                    true
                }
            } else {
                true
            }

            _uiState.update {
                it.copy(
                    usbLocations = locations,
                    selectedUsbLocation = target,
                    currentUsbFolder = target?.rootDirectory,
                    isLoading = false,
                    hasAllFilesAccess = hasAllFilesAccess
                )
            }
            updateActiveStorageInfo(preferenceManager.getStorageLocation(), _uiState.value.remoteServers, locations)
            target?.rootDirectory?.let { browseUsbDirectory(it) }
        }
    }

    private fun observeStorageLocation() {
        viewModelScope.launch {
            combine(
                preferenceManager.storageLocationFlow,
                serverRepository.serversFlow
            ) { locationId, servers ->
                locationId to servers
            }.collect { (locationId, servers) ->
                updateActiveStorageInfo(locationId, servers, _uiState.value.usbLocations)
            }
        }
    }

    private fun updateActiveStorageInfo(
        locationId: String,
        servers: List<RemoteServerConfig>,
        usbLocations: List<StorageLocation>
    ) {
        if (locationId.startsWith("remote:")) {
            // Network shares cannot be used as torrent download targets. Reset to internal storage.
            preferenceManager.setStorageLocation(TorrentStorageResolver.LOCATION_INTERNAL)
            val free = TorrentStorageResolver.getAvailableStorageBytes(context, TorrentStorageResolver.LOCATION_INTERNAL)
            val exactPath = File(context.filesDir, TorrentStorageResolver.TORRENT_DIRECTORY).absolutePath
            _uiState.update {
                it.copy(
                    activeStorageId = TorrentStorageResolver.LOCATION_INTERNAL,
                    activeStorageDisplayName = context.getString(R.string.settings_storage_internal),
                    activeStorageExactPath = exactPath,
                    activeStorageAvailableBytes = free,
                    activeStorageType = "INTERNAL"
                )
            }
            return
        }

        if (locationId == TorrentStorageResolver.LOCATION_INTERNAL) {
            val free = TorrentStorageResolver.getAvailableStorageBytes(context, TorrentStorageResolver.LOCATION_INTERNAL)
            val exactPath = File(context.filesDir, TorrentStorageResolver.TORRENT_DIRECTORY).absolutePath
            _uiState.update {
                it.copy(
                    activeStorageId = TorrentStorageResolver.LOCATION_INTERNAL,
                    activeStorageDisplayName = context.getString(R.string.settings_storage_internal),
                    activeStorageExactPath = exactPath,
                    activeStorageAvailableBytes = free,
                    activeStorageType = "INTERNAL"
                )
            }
        } else {
            val usb = usbLocations.firstOrNull { it.id == locationId }
            if (usb != null) {
                val exactPath = File(usb.downloadDirectory, TorrentStorageResolver.TORRENT_DIRECTORY).absolutePath
                _uiState.update {
                    it.copy(
                        activeStorageId = locationId,
                        activeStorageDisplayName = usb.displayName,
                        activeStorageExactPath = exactPath,
                        activeStorageAvailableBytes = usb.availableBytes,
                        activeStorageType = if (usb.isRemovable) "USB" else "INTERNAL"
                    )
                }
            } else {
                // If a previously selected USB volume was unplugged, fallback to internal storage
                preferenceManager.setStorageLocation(TorrentStorageResolver.LOCATION_INTERNAL)
                val free = TorrentStorageResolver.getAvailableStorageBytes(context, TorrentStorageResolver.LOCATION_INTERNAL)
                val exactPath = File(context.filesDir, TorrentStorageResolver.TORRENT_DIRECTORY).absolutePath
                _uiState.update {
                    it.copy(
                        activeStorageId = TorrentStorageResolver.LOCATION_INTERNAL,
                        activeStorageDisplayName = context.getString(R.string.settings_storage_internal),
                        activeStorageExactPath = exactPath,
                        activeStorageAvailableBytes = free,
                        activeStorageType = "INTERNAL"
                    )
                }
            }
        }
    }

    fun setActiveDownloadStorage(locationId: String) {
        if (locationId.startsWith("remote:") || locationId.isBlank()) {
            preferenceManager.setStorageLocation(TorrentStorageResolver.LOCATION_INTERNAL)
            return
        }
        preferenceManager.setStorageLocation(locationId)
    }

    fun selectUsbLocation(location: StorageLocation) {
        isUserExplicitSelection = true
        _uiState.update {
            it.copy(
                selectedUsbLocation = location,
                currentUsbFolder = location.rootDirectory,
                errorMessage = null
            )
        }
        browseUsbDirectory(location.rootDirectory)
    }

    fun browseUsbDirectory(directory: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, currentUsbFolder = directory) }
            try {
                val items = storageManager.listUsbFiles(directory)
                _uiState.update { it.copy(usbFiles = items, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: e.message ?: "Failed to list files"
                    )
                }
            }
        }
    }

    fun navigateUpUsb() {
        val current = _uiState.value.currentUsbFolder ?: return
        val root = _uiState.value.selectedUsbLocation?.rootDirectory ?: return
        val currentCanon = try { current.canonicalPath } catch (_: Throwable) { current.absolutePath }
        val rootCanon = try { root.canonicalPath } catch (_: Throwable) { root.absolutePath }
        if (currentCanon != rootCanon && current.parentFile != null) {
            browseUsbDirectory(current.parentFile!!)
        }
    }

    fun scanNetworkShares() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningNetwork = true) }
            try {
                val discovered = networkScanner.scanNetwork()
                for (disc in discovered) {
                    storageManager.registerServer(disc)
                }
                _uiState.update { current ->
                    val saved = serverRepository.getServers()
                    val merged = mutableListOf<RemoteServerConfig>()
                    merged.addAll(saved)
                    for (disc in discovered) {
                        val alreadySaved = saved.any {
                            it.host.equals(disc.host, ignoreCase = true) &&
                            it.type == disc.type &&
                            it.shareOrExport.trim('/').equals(disc.shareOrExport.trim('/'), ignoreCase = true)
                        }
                        if (!alreadySaved) {
                            merged.add(disc)
                        }
                    }
                    current.copy(isScanningNetwork = false, remoteServers = merged)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanningNetwork = false) }
            }
        }
    }

    private fun observeRemoteServers() {
        viewModelScope.launch {
            serverRepository.serversFlow.collect { servers ->
                _uiState.update { current ->
                    val discoveredOnly = current.remoteServers.filter { disc ->
                        servers.none { saved ->
                            saved.host.equals(disc.host, ignoreCase = true) &&
                            saved.type == disc.type &&
                            saved.shareOrExport.trim('/').equals(disc.shareOrExport.trim('/'), ignoreCase = true)
                        }
                    }
                    current.copy(remoteServers = servers + discoveredOnly)
                }
            }
        }
    }

    fun selectServer(server: RemoteServerConfig) {
        val configured = serverRepository.getServer(server.id)
        val targetServer = configured ?: server
        storageManager.registerServer(targetServer)
        _uiState.update {
            it.copy(
                selectedServer = targetServer,
                currentRemotePath = "",
                errorMessage = null
            )
        }
        browseRemotePath(targetServer, "")
    }

    fun browseRemotePath(server: RemoteServerConfig, path: String) {
        storageManager.registerServer(server)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    selectedServer = server,
                    currentRemotePath = path
                )
            }
            try {
                val files = storageManager.listRemoteFiles(server, path)
                _uiState.update { it.copy(remoteFiles = files, isLoading = false) }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.message ?: "Connection failed"
                val isSmbAuthStatus = (e as? com.hierynomus.mssmb2.SMBApiException)?.status?.let {
                    it.name.contains("LOGON", ignoreCase = true) ||
                    it.name.contains("ACCESS", ignoreCase = true) ||
                    it.name.contains("DENIED", ignoreCase = true) ||
                    it.name.contains("ACCOUNT", ignoreCase = true) ||
                    it.name.contains("PASSWORD", ignoreCase = true)
                } == true
                val causeSmbAuthStatus = (e.cause as? com.hierynomus.mssmb2.SMBApiException)?.status?.let {
                    it.name.contains("LOGON", ignoreCase = true) ||
                    it.name.contains("ACCESS", ignoreCase = true) ||
                    it.name.contains("DENIED", ignoreCase = true) ||
                    it.name.contains("ACCOUNT", ignoreCase = true) ||
                    it.name.contains("PASSWORD", ignoreCase = true)
                } == true

                val isSmbKeyDerivationError = (server.type == RemoteServerType.SMB) && (
                    errorMsg.contains("SecretKey.getEncoded", ignoreCase = true) ||
                    errorMsg.contains("deriveKey", ignoreCase = true) ||
                    errorMsg.contains("NullPointerException", ignoreCase = true) ||
                    e is NullPointerException ||
                    e.cause is NullPointerException
                )

                val isAuthError = isSmbAuthStatus || causeSmbAuthStatus || isSmbKeyDerivationError ||
                        errorMsg.contains("STATUS_LOGON_FAILURE", ignoreCase = true) ||
                        errorMsg.contains("STATUS_ACCESS_DENIED", ignoreCase = true) ||
                        errorMsg.contains("STATUS_LOGON_TYPE_NOT_GRANTED", ignoreCase = true) ||
                        errorMsg.contains("STATUS_WRONG_PASSWORD", ignoreCase = true) ||
                        errorMsg.contains("Authentication failed", ignoreCase = true) ||
                        errorMsg.contains("Logon failure", ignoreCase = true) ||
                        errorMsg.contains("Permiso denegado", ignoreCase = true) ||
                        errorMsg.contains("state: 13", ignoreCase = true) ||
                        errorMsg.contains("Permission denied", ignoreCase = true) ||
                        errorMsg.contains("EACCES", ignoreCase = true)

                val hasStored = if (isAuthError) smbCredentialStore.hasCredentials(server.host) else false
                val stored = if (hasStored) smbCredentialStore.load(server.host) else null
                val targetServer = if (isAuthError && stored != null) {
                    server.copy(
                        username = if (!server.username.isNullOrBlank()) server.username else stored.username,
                        password = if (!server.password.isNullOrBlank()) server.password else stored.password,
                        domain = if (!server.domain.isNullOrBlank()) server.domain else stored.domain
                    )
                } else {
                    server
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = if (isAuthError) null else errorMsg,
                        showAuthDialog = isAuthError,
                        authTargetServer = if (isAuthError) targetServer else null,
                        authTargetShare = if (isAuthError) (server.shareOrExport.ifBlank { path }) else null,
                        authTargetHasStoredCredentials = hasStored
                    )
                }
            }
        }
    }

    fun navigateUpRemote() {
        val server = _uiState.value.selectedServer ?: return
        val currentPath = _uiState.value.currentRemotePath
        if (currentPath.isBlank()) {
            val configured = serverRepository.getServer(server.id)
            if (configured != null && configured.shareOrExport.isBlank() && server.shareOrExport.isNotBlank()) {
                val hostOnlyServer = server.copy(shareOrExport = "")
                _uiState.update { it.copy(selectedServer = hostOnlyServer, currentRemotePath = "") }
                browseRemotePath(hostOnlyServer, "")
            } else {
                _uiState.update { it.copy(selectedServer = null, remoteFiles = emptyList()) }
            }
            return
        }
        val normalized = currentPath.trimEnd('/').substringBeforeLast('/', "")
        browseRemotePath(server, normalized)
    }

    fun openServerDialog(server: RemoteServerConfig? = null) {
        val targetServer = if (server != null && server.type == RemoteServerType.SMB) {
            val stored = smbCredentialStore.load(server.host)
            if (stored != null) {
                server.copy(
                    username = if (!server.username.isNullOrBlank()) server.username else stored.username,
                    password = if (!server.password.isNullOrBlank()) server.password else stored.password,
                    domain = if (!server.domain.isNullOrBlank()) server.domain else stored.domain
                )
            } else {
                server
            }
        } else {
            server
        }
        _uiState.update { it.copy(showServerDialog = true, editingServer = targetServer) }
    }

    fun dismissServerDialog() {
        _uiState.update { it.copy(showServerDialog = false, editingServer = null) }
    }

    fun dismissAuthDialog() {
        _uiState.update {
            it.copy(
                showAuthDialog = false,
                authTargetServer = null,
                authTargetShare = null,
                authTargetHasStoredCredentials = false
            )
        }
    }

    fun submitAuthentication(
        username: String?,
        password: String?,
        domain: String?,
        saveCredentials: Boolean
    ) {
        val server = _uiState.value.authTargetServer ?: return
        val path = _uiState.value.currentRemotePath
        val cleanUser = username?.trim()?.ifBlank { null }
        val cleanPass = password?.ifBlank { null }
        val cleanDomain = domain?.trim()?.ifBlank { null }

        if (saveCredentials && cleanUser != null && cleanPass != null) {
            smbCredentialStore.save(
                SmbCredentials(
                    host = server.host,
                    username = cleanUser,
                    password = cleanPass,
                    domain = cleanDomain
                )
            )
        }

        val updatedServer = server.copy(
            username = cleanUser,
            password = cleanPass,
            domain = cleanDomain
        )
        if (saveCredentials) {
            serverRepository.saveServer(updatedServer)
        }
        storageManager.registerServer(updatedServer)

        _uiState.update {
            it.copy(
                showAuthDialog = false,
                authTargetServer = null,
                authTargetShare = null,
                authTargetHasStoredCredentials = false,
                selectedServer = updatedServer
            )
        }
        browseRemotePath(updatedServer, path)
    }

    fun clearStoredCredentials(host: String) {
        smbCredentialStore.clear(host)
        val updatedServers = serverRepository.getServers().map { server ->
            if (server.host.equals(host, ignoreCase = true)) {
                val updated = server.copy(username = null, password = null, domain = null)
                serverRepository.saveServer(updated)
                storageManager.registerServer(updated)
                updated
            } else {
                server
            }
        }
        _uiState.update {
            it.copy(
                authTargetHasStoredCredentials = false,
                remoteServers = updatedServers,
                selectedServer = if (it.selectedServer?.host?.equals(host, ignoreCase = true) == true) {
                    it.selectedServer.copy(username = null, password = null, domain = null)
                } else {
                    it.selectedServer
                }
            )
        }
    }

    fun saveServer(
        id: String?,
        name: String,
        type: RemoteServerType,
        host: String,
        port: Int,
        share: String,
        domain: String?,
        username: String?,
        password: String?
    ) {
        val cleanHost = host.trim()
        val cleanUser = username?.trim()?.ifBlank { null }
        val cleanPass = password?.ifBlank { null }
        val cleanDomain = domain?.trim()?.ifBlank { null }

        if (type == RemoteServerType.SMB && cleanUser != null && cleanPass != null) {
            smbCredentialStore.save(
                SmbCredentials(
                    host = cleanHost,
                    username = cleanUser,
                    password = cleanPass,
                    domain = cleanDomain
                )
            )
        }

        val config = RemoteServerConfig(
            id = id ?: UUID.randomUUID().toString(),
            name = name.ifBlank { host },
            type = type,
            host = cleanHost,
            port = port,
            shareOrExport = share.trim(),
            domain = cleanDomain,
            username = cleanUser,
            password = cleanPass
        )
        serverRepository.saveServer(config)
        dismissServerDialog()
    }

    fun deleteServer(id: String) {
        val server = serverRepository.getServer(id)
        if (server?.type == RemoteServerType.SMB) {
            smbCredentialStore.clear(server.host)
        }
        serverRepository.deleteServer(id)
        if (preferenceManager.getStorageLocation() == "remote:$id") {
            preferenceManager.setStorageLocation(TorrentStorageResolver.LOCATION_INTERNAL)
        }
        _uiState.update { current ->
            val updated = current.remoteServers.filterNot { it.id == id }
            val updatedSelected = if (current.selectedServer?.id == id) null else current.selectedServer
            val updatedFiles = if (current.selectedServer?.id == id) emptyList() else current.remoteFiles
            current.copy(
                remoteServers = updated,
                selectedServer = updatedSelected,
                remoteFiles = updatedFiles
            )
        }
    }

    fun onFileItemClicked(item: RemoteFileItem) {
        if (item.isDirectory) {
            if (_uiState.value.selectedTab == ExternalMediaTab.USB) {
                browseUsbDirectory(File(item.path))
            } else {
                val server = _uiState.value.selectedServer ?: return
                if (server.shareOrExport.isBlank()) {
                    val updatedServer = server.copy(shareOrExport = item.path)
                    _uiState.update { it.copy(selectedServer = updatedServer, currentRemotePath = "") }
                    browseRemotePath(updatedServer, "")
                } else {
                    browseRemotePath(server, item.path)
                }
            }
        } else {
            if (!isPlayableMedia(item.name)) return
            playMediaItem(item)
        }
    }

    fun playMediaItem(item: RemoteFileItem) {
        if (_uiState.value.selectedTab == ExternalMediaTab.USB) {
            val file = File(item.path)
            viewModelScope.launch {
                _events.emit(ExternalMediaEvent.PlayLocalFile(file, item.name, castImmediately = false))
            }
        } else {
            val server = _uiState.value.selectedServer
                ?: _uiState.value.remoteServers.firstOrNull { it.id == item.serverConfigId }
                ?: serverRepository.getServer(item.serverConfigId)
                ?: return
            storageManager.registerServer(server)
            val directUriString = com.martinrevert.latorrentola.service.storage.remote.RemoteMediaDataSource.buildRemoteUriString(
                server,
                item.path
            )
            val serverJsonStr = try {
                com.google.gson.Gson().toJson(server)
            } catch (_: Exception) {
                null
            }
            viewModelScope.launch {
                _events.emit(
                    ExternalMediaEvent.PlayRemoteStream(
                        streamUrl = directUriString,
                        castUrl = "",
                        title = item.name,
                        serverId = server.id,
                        remotePath = item.path,
                        castImmediately = false,
                        serverJson = serverJsonStr
                    )
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? android.os.storage.StorageManager
            (storageVolumeCallback as? android.os.storage.StorageManager.StorageVolumeCallback)?.let {
                try {
                    sm?.unregisterStorageVolumeCallback(it)
                } catch (_: Exception) {}
            }
        }
    }
}
