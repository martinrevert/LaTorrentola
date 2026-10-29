@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.martinrevert.latorrentola.service.storage.TorrentStorageResolver
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text as TvText
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.IconButton as TvIconButton
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.SurfaceDefaults as TvSurfaceDefaults
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.service.storage.StorageLocation
import com.martinrevert.latorrentola.service.storage.remote.RemoteFileItem
import com.martinrevert.latorrentola.service.storage.remote.RemoteServerConfig
import com.martinrevert.latorrentola.service.storage.remote.RemoteServerType
import com.martinrevert.latorrentola.ui.player.LocalPlayerActivity
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice
import java.io.File
import java.util.Locale

/** Formats byte counts into human readable strings without framework dependencies. */
fun formatMediaSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return "%.1f %s".format(Locale.US, value, units[digitGroups])
}

/** Identifies playable media extensions. */
fun isPlayableMedia(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in setOf("mp4", "mkv", "avi", "webm", "ts", "mov", "m4v", "wmv", "flv", "vob", "mpg", "mpeg")
}

/**
 * Launches system settings for all-files access permission with sequential fallbacks.
 * Prevents ActivityNotFoundException on Android TV and custom vendor ROMs that lack
 * ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION.
 */
fun launchAllFilesAccessSettings(context: Context) {
    val packageName = context.packageName
    val attempts = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 1. Direct app-specific all files access screen
            add(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            })
            // 2. Generic all files access list
            add(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
        // 3. Application details settings (App Info)
        add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        })
        // 4. Manage applications settings
        add(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
        // 5. Root system settings
        add(Intent(Settings.ACTION_SETTINGS))
    }

    for (intent in attempts) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // Try next fallback in chain
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalMediaBrowserScreen(
    viewModel: ExternalMediaBrowserViewModel = hiltViewModel(),
    themeMode: Int? = null,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val preferenceManager = remember(context) { PreferenceManager(context) }
    val currentObservedTheme by preferenceManager.themeFlow.collectAsState(initial = preferenceManager.getTheme())
    val effectiveThemeMode = remember(context, themeMode, currentObservedTheme) {
        themeMode ?: currentObservedTheme
    }
    val uiState by viewModel.uiState.collectAsState()
    val isTv = remember(context) { context.isTvDevice() }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUsbLocations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExternalMediaEvent.PlayLocalFile -> {
                    com.martinrevert.latorrentola.service.TorrentDownloadService.pauseActiveTransfers(context)
                    com.martinrevert.latorrentola.service.PlaybackService.stopPlayback(context)
                    val intent = Intent(context, LocalPlayerActivity::class.java).apply {
                        putExtra(LocalPlayerActivity.EXTRA_FILE_PATH, event.file.absolutePath)
                        putExtra(LocalPlayerActivity.EXTRA_TITLE, event.title)
                        if (event.castImmediately) {
                            putExtra(LocalPlayerActivity.EXTRA_CAST_ENABLED, true)
                        }
                    }
                    context.startActivity(intent)
                }
                is ExternalMediaEvent.PlayRemoteStream -> {
                    com.martinrevert.latorrentola.service.TorrentDownloadService.pauseActiveTransfers(context)
                    com.martinrevert.latorrentola.service.PlaybackService.stopPlayback(context)
                    val intent = Intent(context, LocalPlayerActivity::class.java).apply {
                        putExtra(LocalPlayerActivity.EXTRA_STREAM_URL, event.streamUrl)
                        putExtra(LocalPlayerActivity.EXTRA_CAST_URL, event.castUrl)
                        putExtra(LocalPlayerActivity.EXTRA_TITLE, event.title)
                        putExtra(LocalPlayerActivity.EXTRA_REMOTE_SERVER_ID, event.serverId)
                        putExtra(LocalPlayerActivity.EXTRA_REMOTE_FILE_PATH, event.remotePath)
                        if (!event.serverJson.isNullOrBlank()) {
                            putExtra(LocalPlayerActivity.EXTRA_REMOTE_SERVER_JSON, event.serverJson)
                        }
                        if (event.castImmediately) {
                            putExtra(LocalPlayerActivity.EXTRA_CAST_ENABLED, true)
                        }
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    if (isTv) {
        TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
            TvExternalMediaContent(
                uiState = uiState,
                themeMode = effectiveThemeMode,
                onBackClick = onBackClick,
                onSelectTab = { viewModel.selectTab(it) },
                onRefreshUsb = { viewModel.refreshUsbLocations() },
                onSetActiveStorage = { viewModel.setActiveDownloadStorage(it) },
                onSelectUsbLocation = { viewModel.selectUsbLocation(it) },
                onNavigateUpUsb = { viewModel.navigateUpUsb() },
                onSelectServer = { viewModel.selectServer(it) },
                onNavigateUpRemote = { viewModel.navigateUpRemote() },
                onAddServerClick = { viewModel.openServerDialog(null) },
                onScanNetworkClick = { viewModel.scanNetworkShares() },
                onEditServerClick = { viewModel.openServerDialog(it) },
                onDeleteServerClick = { viewModel.deleteServer(it.id) },
                onFileItemClicked = { viewModel.onFileItemClicked(it) }
            )
        }
    } else {
        LaTorrentolaTheme(themeMode = effectiveThemeMode) {
            MobileExternalMediaContent(
                uiState = uiState,
                themeMode = effectiveThemeMode,
                onBackClick = onBackClick,
                onSelectTab = { viewModel.selectTab(it) },
                onRefreshUsb = { viewModel.refreshUsbLocations() },
                onSetActiveStorage = { viewModel.setActiveDownloadStorage(it) },
                onSelectUsbLocation = { viewModel.selectUsbLocation(it) },
                onNavigateUpUsb = { viewModel.navigateUpUsb() },
                onSelectServer = { viewModel.selectServer(it) },
                onNavigateUpRemote = { viewModel.navigateUpRemote() },
                onAddServerClick = { viewModel.openServerDialog(null) },
                onScanNetworkClick = { viewModel.scanNetworkShares() },
                onEditServerClick = { viewModel.openServerDialog(it) },
                onDeleteServerClick = { viewModel.deleteServer(it.id) },
                onFileItemClicked = { viewModel.onFileItemClicked(it) }
            )
        }
    }


    if (uiState.showServerDialog) {
        ServerConfigDialog(
            themeMode = effectiveThemeMode,
            editingServer = uiState.editingServer,
            onDismiss = { viewModel.dismissServerDialog() },
            onSave = { name, type, host, port, share, domain, username, password ->
                viewModel.saveServer(
                    id = uiState.editingServer?.id,
                    name = name,
                    type = type,
                    host = host,
                    port = port,
                    share = share,
                    domain = domain,
                    username = username,
                    password = password
                )
            }
        )
    }

    val authServer = uiState.authTargetServer
    if (uiState.showAuthDialog && authServer != null) {
        AuthenticationPromptDialog(
            themeMode = effectiveThemeMode,
            server = authServer,
            share = uiState.authTargetShare.orEmpty(),
            hasStoredCredentials = uiState.authTargetHasStoredCredentials,
            onClearCredentials = { viewModel.clearStoredCredentials(authServer.host) },
            onDismiss = { viewModel.dismissAuthDialog() },
            onSubmit = { username, password, domain, rememberCredentials ->
                viewModel.submitAuthentication(username, password, domain, rememberCredentials)
            }
        )
    }
}

@Composable
fun ActiveStorageBanner(
    uiState: ExternalMediaUiState,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    themeMode: Int? = null,
    onResetToInternal: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }

    val content: @Composable () -> Unit = {
        if (isTv) {
            TvSurface(
                modifier = modifier,
                shape = RoundedCornerShape(12.dp),
                colors = TvSurfaceDefaults.colors(
                    containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                    contentColor = TvMaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvIcon(
                    imageVector = Icons.Default.SaveAlt,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = TvMaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TvText(
                            text = stringResource(R.string.active_download_storage_title),
                            style = TvMaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TvMaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TvSurface(
                            shape = RoundedCornerShape(4.dp),
                            colors = TvSurfaceDefaults.colors(
                                containerColor = TvMaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                contentColor = TvMaterialTheme.colorScheme.primary
                            )
                        ) {
                            TvText(
                                text = uiState.activeStorageType,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = TvMaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TvMaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TvText(
                        text = uiState.activeStorageDisplayName.ifBlank { stringResource(R.string.settings_storage_internal) },
                        style = TvMaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TvMaterialTheme.colorScheme.onSurface
                    )
                    if (uiState.activeStorageExactPath.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TvIcon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = TvMaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            TvText(
                                text = stringResource(R.string.active_download_storage_exact_path, uiState.activeStorageExactPath),
                                style = TvMaterialTheme.typography.bodySmall,
                                color = TvMaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (uiState.activeStorageAvailableBytes > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        TvText(
                            text = stringResource(R.string.storage_space_available, formatMediaSize(uiState.activeStorageAvailableBytes)),
                            style = TvMaterialTheme.typography.bodySmall,
                            color = TvMaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    TvText(
                        text = stringResource(R.string.active_download_storage_subtitle),
                        style = TvMaterialTheme.typography.labelSmall,
                        color = TvMaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.activeStorageId != TorrentStorageResolver.LOCATION_INTERNAL && onResetToInternal != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TvOutlinedButton(
                            onClick = onResetToInternal,
                            modifier = Modifier.focusHighlight(shape = RoundedCornerShape(8.dp))
                        ) {
                            TvIcon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            TvText(stringResource(R.string.active_download_storage_reset_internal))
                        }
                    }
                }
            }
        }
    } else {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SaveAlt,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.active_download_storage_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = uiState.activeStorageType,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = uiState.activeStorageDisplayName.ifBlank { stringResource(R.string.settings_storage_internal) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (uiState.activeStorageExactPath.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.active_download_storage_exact_path, uiState.activeStorageExactPath),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (uiState.activeStorageAvailableBytes > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.storage_space_available, formatMediaSize(uiState.activeStorageAvailableBytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.active_download_storage_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.activeStorageId != TorrentStorageResolver.LOCATION_INTERNAL && onResetToInternal != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onResetToInternal,
                            modifier = Modifier.focusHighlight(shape = RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.active_download_storage_reset_internal))
                        }
                    }
                }
            }
        }
    }
    }

    if (isTv) {
        TvLaTorrentolaTheme(themeMode = effectiveThemeMode) { content() }
    } else {
        LaTorrentolaTheme(themeMode = effectiveThemeMode) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileExternalMediaContent(
    uiState: ExternalMediaUiState,
    themeMode: Int? = null,
    onBackClick: () -> Unit,
    onSelectTab: (ExternalMediaTab) -> Unit,
    onRefreshUsb: () -> Unit = {},
    onSetActiveStorage: (String) -> Unit,
    onSelectUsbLocation: (StorageLocation) -> Unit,
    onNavigateUpUsb: () -> Unit,
    onSelectServer: (RemoteServerConfig) -> Unit,
    onNavigateUpRemote: () -> Unit,
    onAddServerClick: () -> Unit,
    onScanNetworkClick: () -> Unit,
    onEditServerClick: (RemoteServerConfig) -> Unit,
    onDeleteServerClick: (RemoteServerConfig) -> Unit,
    onFileItemClicked: (RemoteFileItem) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.external_media_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_desc)
                        )
                    }
                },
                actions = {
                    if (uiState.selectedTab == ExternalMediaTab.USB) {
                        IconButton(onClick = onRefreshUsb) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.external_media_refresh_usb)
                            )
                        }
                    } else if (uiState.selectedTab == ExternalMediaTab.NETWORK && uiState.selectedServer == null) {
                        IconButton(onClick = onScanNetworkClick) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.external_media_scan_button)
                            )
                        }
                        IconButton(onClick = onAddServerClick) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.external_media_add_server)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ActiveStorageBanner(
                uiState = uiState,
                isTv = false,
                themeMode = themeMode,
                onResetToInternal = { onSetActiveStorage(TorrentStorageResolver.LOCATION_INTERNAL) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            PrimaryTabRow(
                selectedTabIndex = if (uiState.selectedTab == ExternalMediaTab.USB) 0 else 1
            ) {
                Tab(
                    selected = uiState.selectedTab == ExternalMediaTab.USB,
                    onClick = { onSelectTab(ExternalMediaTab.USB) },
                    text = { Text(stringResource(R.string.external_media_tab_usb)) },
                    icon = { Icon(Icons.Default.Usb, contentDescription = null) }
                )
                Tab(
                    selected = uiState.selectedTab == ExternalMediaTab.NETWORK,
                    onClick = { onSelectTab(ExternalMediaTab.NETWORK) },
                    text = { Text(stringResource(R.string.external_media_tab_network)) },
                    icon = { Icon(Icons.Default.Dns, contentDescription = null) }
                )
            }

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.errorMessage?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            when (uiState.selectedTab) {
                ExternalMediaTab.USB -> {
                    UsbMediaView(
                        uiState = uiState,
                        onSelectLocation = onSelectUsbLocation,
                        onSetActiveStorage = onSetActiveStorage,
                        onNavigateUp = onNavigateUpUsb,
                        onItemClick = onFileItemClicked
                    )
                }
                ExternalMediaTab.NETWORK -> {
                    NetworkMediaView(
                        uiState = uiState,
                        onSelectServer = onSelectServer,
                        onNavigateUp = onNavigateUpRemote,
                        onAddServerClick = onAddServerClick,
                        onScanNetworkClick = onScanNetworkClick,
                        onEditServerClick = onEditServerClick,
                        onDeleteServerClick = onDeleteServerClick,
                        onItemClick = onFileItemClicked
                    )
                }
            }
        }
    }
}

@Composable
private fun UsbMediaView(
    uiState: ExternalMediaUiState,
    onSelectLocation: (StorageLocation) -> Unit,
    onSetActiveStorage: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onItemClick: (RemoteFileItem) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        if (!uiState.hasAllFilesAccess) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderShared,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.usb_permission_banner_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.usb_permission_banner_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            launchAllFilesAccessSettings(context)
                        }
                    ) {
                        Text(stringResource(R.string.usb_permission_grant_button))
                    }
                }
            }
        }

        if (uiState.usbLocations.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.usbLocations) { loc ->
                    val isSelected = uiState.selectedUsbLocation?.id == loc.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectLocation(loc) },
                        label = { Text(loc.displayName) },
                        leadingIcon = {
                            Icon(
                                if (loc.isRemovable) Icons.Default.Usb else Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            uiState.selectedUsbLocation?.let { loc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (loc.isRemovable) stringResource(R.string.active_download_storage_usb_desc)
                            else stringResource(R.string.active_download_storage_internal_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (loc.availableBytes > 0) {
                            Text(
                                text = stringResource(R.string.storage_space_available, formatMediaSize(loc.availableBytes)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (uiState.activeStorageId == loc.id) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.active_download_storage_badge),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSetActiveStorage(loc.id) },
                            modifier = Modifier.focusHighlight(shape = ButtonDefaults.shape)
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.active_download_storage_set_button))
                        }
                    }
                }
            }
        }

        val canNavigateUp = uiState.currentUsbFolder != null &&
                uiState.selectedUsbLocation?.rootDirectory != null &&
                (try { uiState.currentUsbFolder.canonicalPath } catch (_: Throwable) { uiState.currentUsbFolder.absolutePath }) !=
                (try { uiState.selectedUsbLocation.rootDirectory.canonicalPath } catch (_: Throwable) { uiState.selectedUsbLocation.rootDirectory.absolutePath })

        if (canNavigateUp) {
            Surface(
                onClick = onNavigateUp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.external_media_parent_folder),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (uiState.usbFiles.isEmpty() && !uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.external_media_empty_usb),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.usbFiles) { item ->
                    MediaFileRow(item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

@Composable
private fun NetworkMediaView(
    uiState: ExternalMediaUiState,
    onSelectServer: (RemoteServerConfig) -> Unit,
    onNavigateUp: () -> Unit,
    onAddServerClick: () -> Unit,
    onScanNetworkClick: () -> Unit,
    onEditServerClick: (RemoteServerConfig) -> Unit,
    onDeleteServerClick: (RemoteServerConfig) -> Unit,
    onItemClick: (RemoteFileItem) -> Unit
) {
    if (uiState.selectedServer == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.isScanningNetwork) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.external_media_scanning_network),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (uiState.remoteServers.isEmpty() && !uiState.isLoading && !uiState.isScanningNetwork) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.external_media_empty_network_auto),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = onAddServerClick) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.external_media_add_server))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.remoteServers) { server ->
                        ServerCard(
                            server = server,
                            onClick = { onSelectServer(server) },
                            onEdit = { onEditServerClick(server) },
                            onDelete = { onDeleteServerClick(server) }
                        )
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateUp, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.selectedServer.name + if (uiState.currentRemotePath.isNotBlank()) " / ${uiState.currentRemotePath}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (uiState.remoteFiles.isEmpty() && !uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.external_media_empty_folder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.remoteFiles) { item ->
                        MediaFileRow(item = item, onClick = { onItemClick(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(
    server: RemoteServerConfig,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = server.type.name,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = "${server.host}:${server.port}${if (server.shareOrExport.isNotBlank()) "/${server.shareOrExport}" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.external_media_edit_server),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.external_media_delete_server),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.focusHighlight(shape = ButtonDefaults.shape)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.external_media_browse_action))
                }
            }
        }
    }
}

@Composable
private fun MediaFileRow(
    item: RemoteFileItem,
    onClick: () -> Unit
) {
    val isPlayable = !item.isDirectory && isPlayableMedia(item.name)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlayable) MaterialTheme.colorScheme.surfaceContainerHighest
            else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    item.isDirectory -> Icons.Default.Folder
                    isPlayable -> Icons.Default.PlayCircle
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                },
                contentDescription = null,
                tint = when {
                    item.isDirectory -> MaterialTheme.colorScheme.primary
                    isPlayable -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isPlayable || item.isDirectory) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.isDirectory && item.sizeBytes > 0) {
                    Text(
                        text = formatMediaSize(item.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (item.isDirectory) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvExternalMediaContent(
    uiState: ExternalMediaUiState,
    themeMode: Int? = null,
    onBackClick: () -> Unit,
    onSelectTab: (ExternalMediaTab) -> Unit,
    onRefreshUsb: () -> Unit = {},
    onSetActiveStorage: (String) -> Unit,
    onSelectUsbLocation: (StorageLocation) -> Unit,
    onNavigateUpUsb: () -> Unit,
    onSelectServer: (RemoteServerConfig) -> Unit,
    onNavigateUpRemote: () -> Unit,
    onAddServerClick: () -> Unit,
    onScanNetworkClick: () -> Unit,
    onEditServerClick: (RemoteServerConfig) -> Unit,
    onDeleteServerClick: (RemoteServerConfig) -> Unit,
    onFileItemClicked: (RemoteFileItem) -> Unit
) {
    TvSurface(
        modifier = Modifier.fillMaxSize(),
        colors = TvSurfaceDefaults.colors(
            containerColor = TvMaterialTheme.colorScheme.background,
            contentColor = TvMaterialTheme.colorScheme.onBackground
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvIconButton(
                    onClick = onBackClick,
                    modifier = Modifier.focusHighlight(shape = RoundedCornerShape(12.dp))
                ) {
                    TvIcon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_desc)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                TvText(
                    text = stringResource(R.string.external_media_title),
                    style = TvMaterialTheme.typography.headlineMedium,
                    color = TvMaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.weight(1f))
                if (uiState.selectedTab == ExternalMediaTab.USB) {
                    TvIconButton(
                        onClick = onRefreshUsb,
                        modifier = Modifier.focusHighlight(shape = RoundedCornerShape(12.dp))
                    ) {
                        TvIcon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.external_media_refresh_usb)
                        )
                    }
                } else if (uiState.selectedTab == ExternalMediaTab.NETWORK && uiState.selectedServer == null) {
                    TvIconButton(
                        onClick = onScanNetworkClick,
                        modifier = Modifier.focusHighlight(shape = RoundedCornerShape(12.dp))
                    ) {
                        TvIcon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.external_media_scan_button)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TvButton(
                        onClick = onAddServerClick,
                        modifier = Modifier.focusHighlight(shape = RoundedCornerShape(12.dp))
                    ) {
                        TvIcon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        TvText(stringResource(R.string.external_media_add_server))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val isUsbTab = uiState.selectedTab == ExternalMediaTab.USB
                TvButton(
                    onClick = { onSelectTab(ExternalMediaTab.USB) },
                    modifier = Modifier.focusHighlight(shape = RoundedCornerShape(12.dp)),
                    colors = TvButtonDefaults.colors(
                        containerColor = if (isUsbTab) TvMaterialTheme.colorScheme.primaryContainer
                            else TvMaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isUsbTab) TvMaterialTheme.colorScheme.onPrimaryContainer
                            else TvMaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    TvIcon(Icons.Default.Usb, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    TvText(stringResource(R.string.external_media_tab_usb))
                }
                val isNetworkTab = uiState.selectedTab == ExternalMediaTab.NETWORK
                TvButton(
                    onClick = { onSelectTab(ExternalMediaTab.NETWORK) },
                    modifier = Modifier.focusHighlight(shape = RoundedCornerShape(12.dp)),
                    colors = TvButtonDefaults.colors(
                        containerColor = if (isNetworkTab) TvMaterialTheme.colorScheme.primaryContainer
                            else TvMaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isNetworkTab) TvMaterialTheme.colorScheme.onPrimaryContainer
                            else TvMaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    TvIcon(Icons.Default.Dns, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    TvText(stringResource(R.string.external_media_tab_network))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (uiState.selectedTab) {
                ExternalMediaTab.USB -> {
                    TvUsbMediaView(
                        uiState = uiState,
                        onSelectLocation = onSelectUsbLocation,
                        onSetActiveStorage = onSetActiveStorage,
                        onNavigateUp = onNavigateUpUsb,
                        onItemClick = onFileItemClicked
                    )
                }
                ExternalMediaTab.NETWORK -> {
                    TvNetworkMediaView(
                        uiState = uiState,
                        onSelectServer = onSelectServer,
                        onNavigateUp = onNavigateUpRemote,
                        onAddServerClick = onAddServerClick,
                        onScanNetworkClick = onScanNetworkClick,
                        onEditServerClick = onEditServerClick,
                        onDeleteServerClick = onDeleteServerClick,
                        onItemClick = onFileItemClicked
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvUsbMediaView(
    uiState: ExternalMediaUiState,
    onSelectLocation: (StorageLocation) -> Unit,
    onSetActiveStorage: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onItemClick: (RemoteFileItem) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        if (!uiState.hasAllFilesAccess) {
            TvSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(10.dp),
                colors = TvSurfaceDefaults.colors(
                    containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                    contentColor = TvMaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvIcon(
                        imageVector = Icons.Default.FolderShared,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = TvMaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        TvText(
                            text = stringResource(R.string.usb_permission_banner_title),
                            style = TvMaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TvMaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        TvText(
                            text = stringResource(R.string.usb_permission_banner_desc),
                            style = TvMaterialTheme.typography.bodySmall,
                            color = TvMaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    TvButton(
                        onClick = {
                            launchAllFilesAccessSettings(context)
                        },
                        modifier = Modifier.focusHighlight(shape = RoundedCornerShape(8.dp))
                    ) {
                        TvText(stringResource(R.string.usb_permission_grant_button))
                    }
                }
            }
        }

        if (uiState.usbLocations.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(uiState.usbLocations, key = { it.id }) { loc ->
                    val isSelected = uiState.selectedUsbLocation?.id == loc.id
                    val isActiveDownload = uiState.activeStorageId == loc.id
                    TvCard(
                        onClick = { onSelectLocation(loc) },
                        modifier = Modifier
                            .focusHighlight(shape = RoundedCornerShape(10.dp))
                            .widthIn(min = 200.dp, max = 300.dp),
                        shape = TvCardDefaults.shape(shape = RoundedCornerShape(10.dp)),
                        scale = TvCardDefaults.scale(focusedScale = 1.05f),
                        colors = TvCardDefaults.colors(
                            containerColor = if (isSelected) TvMaterialTheme.colorScheme.primaryContainer
                                else TvMaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) TvMaterialTheme.colorScheme.onPrimaryContainer
                                else TvMaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TvIcon(
                                    imageVector = if (loc.isRemovable) Icons.Default.Usb else Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isSelected) TvMaterialTheme.colorScheme.primary
                                        else TvMaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TvText(
                                    text = loc.displayName,
                                    style = TvMaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (loc.availableBytes > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                TvText(
                                    text = stringResource(R.string.storage_space_available, formatMediaSize(loc.availableBytes)),
                                    style = TvMaterialTheme.typography.bodySmall,
                                    color = if (isSelected) TvMaterialTheme.colorScheme.onPrimaryContainer
                                        else TvMaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isActiveDownload) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TvIcon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = TvMaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    TvText(
                                        text = stringResource(R.string.active_download_storage_badge),
                                        style = TvMaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TvMaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            uiState.selectedUsbLocation?.let { loc ->
                val isCurrentActive = uiState.activeStorageId == loc.id
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        TvText(
                            text = if (isCurrentActive) {
                                stringResource(R.string.active_download_storage_subtitle)
                            } else {
                                if (loc.isRemovable) stringResource(R.string.active_download_storage_usb_desc)
                                else stringResource(R.string.active_download_storage_internal_desc)
                            },
                            style = TvMaterialTheme.typography.bodySmall,
                            color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    if (isCurrentActive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TvSurface(
                                shape = RoundedCornerShape(8.dp),
                                colors = TvSurfaceDefaults.colors(
                                    containerColor = TvMaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    contentColor = TvMaterialTheme.colorScheme.primary
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TvIcon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = TvMaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    TvText(
                                        stringResource(R.string.active_download_storage_badge),
                                        style = TvMaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TvMaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (loc.id != TorrentStorageResolver.LOCATION_INTERNAL) {
                                Spacer(modifier = Modifier.width(8.dp))
                                TvOutlinedButton(
                                    onClick = { onSetActiveStorage(TorrentStorageResolver.LOCATION_INTERNAL) },
                                    modifier = Modifier.focusHighlight(shape = RoundedCornerShape(8.dp))
                                ) {
                                    TvIcon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    TvText(stringResource(R.string.active_download_storage_reset_internal))
                                }
                            }
                        }
                    } else {
                        TvButton(
                            onClick = { onSetActiveStorage(loc.id) },
                            modifier = Modifier.focusHighlight(shape = RoundedCornerShape(8.dp))
                        ) {
                            TvIcon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            TvText(stringResource(R.string.active_download_storage_set_button))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        val canNavigateUp = uiState.currentUsbFolder != null &&
                uiState.selectedUsbLocation?.rootDirectory != null &&
                (try { uiState.currentUsbFolder.canonicalPath } catch (_: Throwable) { uiState.currentUsbFolder.absolutePath }) !=
                (try { uiState.selectedUsbLocation.rootDirectory.canonicalPath } catch (_: Throwable) { uiState.selectedUsbLocation.rootDirectory.absolutePath })

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canNavigateUp) {
                TvButton(
                    onClick = onNavigateUp,
                    modifier = Modifier.focusHighlight(shape = RoundedCornerShape(8.dp))
                ) {
                    TvIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    TvText(stringResource(R.string.external_media_parent_folder))
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            uiState.currentUsbFolder?.let { folder ->
                TvText(
                    text = folder.absolutePath,
                    style = TvMaterialTheme.typography.labelSmall,
                    color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = TvMaterialTheme.colorScheme.primary
                    )
                }
            } else if (uiState.usbFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TvText(
                        text = stringResource(R.string.external_media_empty_usb),
                        style = TvMaterialTheme.typography.bodyLarge,
                        color = TvMaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.usbFiles, key = { it.path }) { item ->
                        TvMediaFileRow(item = item, onClick = { onItemClick(item) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvNetworkMediaView(
    uiState: ExternalMediaUiState,
    onSelectServer: (RemoteServerConfig) -> Unit,
    onNavigateUp: () -> Unit,
    onAddServerClick: () -> Unit,
    onScanNetworkClick: () -> Unit,
    onEditServerClick: (RemoteServerConfig) -> Unit,
    onDeleteServerClick: (RemoteServerConfig) -> Unit,
    onItemClick: (RemoteFileItem) -> Unit
) {
    if (uiState.selectedServer == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.isScanningNetwork) {
                TvSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = TvSurfaceDefaults.colors(
                        containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                        contentColor = TvMaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvIcon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = TvMaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TvText(
                            text = stringResource(R.string.external_media_scanning_network),
                            style = TvMaterialTheme.typography.bodySmall,
                            color = TvMaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (uiState.remoteServers.isEmpty() && !uiState.isLoading && !uiState.isScanningNetwork) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TvText(
                            text = stringResource(R.string.external_media_empty_network_auto),
                            style = TvMaterialTheme.typography.bodyMedium,
                            color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TvButton(
                            onClick = onAddServerClick,
                            modifier = Modifier.focusHighlight(shape = RoundedCornerShape(8.dp))
                        ) {
                            TvIcon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            TvText(stringResource(R.string.external_media_add_server))
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.remoteServers, key = { it.id }) { server ->
                            TvServerCard(
                                server = server,
                                onClick = { onSelectServer(server) },
                                onEdit = { onEditServerClick(server) },
                                onDelete = { onDeleteServerClick(server) }
                            )
                        }
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvButton(
                    onClick = onNavigateUp,
                    modifier = Modifier
                        .weight(1f)
                        .focusHighlight(shape = RoundedCornerShape(8.dp))
                ) {
                    TvIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    TvText(
                        text = uiState.selectedServer.name + if (uiState.currentRemotePath.isNotBlank()) " / ${uiState.currentRemotePath}" else ""
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.remoteFiles, key = { it.path }) { item ->
                        TvMediaFileRow(item = item, onClick = { onItemClick(item) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvServerCard(
    server: RemoteServerConfig,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    TvSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = TvSurfaceDefaults.colors(
            containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
            contentColor = TvMaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvIcon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = TvMaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TvText(
                            text = server.name,
                            style = TvMaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TvMaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TvSurface(
                            shape = RoundedCornerShape(4.dp),
                            colors = TvSurfaceDefaults.colors(
                                containerColor = TvMaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                contentColor = TvMaterialTheme.colorScheme.primary
                            )
                        ) {
                            TvText(
                                text = server.type.name,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = TvMaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TvMaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    TvText(
                        text = "${server.host}:${server.port}${if (server.shareOrExport.isNotBlank()) "/${server.shareOrExport}" else ""}",
                        style = TvMaterialTheme.typography.bodySmall,
                        color = TvMaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvButton(
                    onClick = onClick,
                    modifier = Modifier.focusHighlight(shape = RoundedCornerShape(8.dp))
                ) {
                    TvIcon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    TvText(stringResource(R.string.external_media_browse_action))
                }

                Spacer(modifier = Modifier.weight(1f))

                TvIconButton(
                    onClick = onEdit,
                    modifier = Modifier.focusHighlight(shape = RoundedCornerShape(8.dp))
                ) {
                    TvIcon(Icons.Default.Edit, contentDescription = stringResource(R.string.external_media_edit_server))
                }

                TvIconButton(
                    onClick = onDelete,
                    modifier = Modifier.focusHighlight(shape = RoundedCornerShape(8.dp))
                ) {
                    TvIcon(Icons.Default.Delete, contentDescription = stringResource(R.string.external_media_delete_server))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvMediaFileRow(
    item: RemoteFileItem,
    onClick: () -> Unit
) {
    val isPlayable = !item.isDirectory && isPlayableMedia(item.name)
    TvCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight(shape = RoundedCornerShape(8.dp)),
        shape = TvCardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        scale = TvCardDefaults.scale(focusedScale = 1.02f),
        colors = TvCardDefaults.colors(
            containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
            contentColor = TvMaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = TvMaterialTheme.colorScheme.surface,
            focusedContentColor = TvMaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvIcon(
                imageVector = when {
                    item.isDirectory -> Icons.Default.Folder
                    isPlayable -> Icons.Default.PlayCircle
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                },
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = when {
                    item.isDirectory -> TvMaterialTheme.colorScheme.primary
                    isPlayable -> TvMaterialTheme.colorScheme.tertiary
                    else -> TvMaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                TvText(
                    text = item.name,
                    style = TvMaterialTheme.typography.bodyLarge,
                    color = TvMaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.isDirectory && item.sizeBytes > 0) {
                    TvText(
                        text = formatMediaSize(item.sizeBytes),
                        style = TvMaterialTheme.typography.bodySmall,
                        color = TvMaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (item.isDirectory) {
                TvIcon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TvMaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ServerConfigDialog(
    themeMode: Int? = null,
    editingServer: RemoteServerConfig? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        type: RemoteServerType,
        host: String,
        port: Int,
        share: String,
        domain: String?,
        username: String?,
        password: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val preferenceManager = remember(context) { PreferenceManager(context) }
    val currentObservedTheme by preferenceManager.themeFlow.collectAsState(initial = preferenceManager.getTheme())
    val effectiveThemeMode = remember(context, themeMode, currentObservedTheme) {
        themeMode ?: currentObservedTheme
    }
    val isTv = remember(context) { context.isTvDevice() }

    var selectedType by remember { mutableStateOf(editingServer?.type ?: RemoteServerType.SMB) }
    var name by remember { mutableStateOf(editingServer?.name.orEmpty()) }
    var host by remember { mutableStateOf(editingServer?.host.orEmpty()) }
    var port by remember {
        mutableStateOf((editingServer?.port ?: if (selectedType == RemoteServerType.SMB) 445 else 2049).toString())
    }
    var share by remember { mutableStateOf(editingServer?.shareOrExport.orEmpty()) }
    var domain by remember { mutableStateOf(editingServer?.domain.orEmpty()) }
    var username by remember { mutableStateOf(editingServer?.username.orEmpty()) }
    var password by remember { mutableStateOf(editingServer?.password.orEmpty()) }
    var passwordVisible by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = !isTv)
    ) {
        val content: @Composable () -> Unit = {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth(if (isTv) 0.65f else 0.92f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (editingServer != null) stringResource(R.string.external_media_edit_server)
                        else stringResource(R.string.external_media_add_server),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Protocol Selector (SMB vs NFS)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedType == RemoteServerType.SMB,
                            onClick = {
                                selectedType = RemoteServerType.SMB
                                if (port == "2049") port = "445"
                            },
                            label = { Text("SMB / Samba") }
                        )
                        FilterChip(
                            selected = selectedType == RemoteServerType.NFS,
                            onClick = {
                                selectedType = RemoteServerType.NFS
                                if (port == "445") port = "2049"
                            },
                            label = { Text("NFS v3") }
                        )
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.external_media_server_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(stringResource(R.string.external_media_server_host)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text(stringResource(R.string.external_media_server_port)) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = share,
                            onValueChange = { share = it },
                            label = {
                                Text(
                                    if (selectedType == RemoteServerType.NFS) stringResource(R.string.external_media_server_share_nfs_optional)
                                    else stringResource(R.string.external_media_server_share_optional)
                                )
                            },
                            modifier = Modifier.weight(1.5f)
                        )
                    }

                    if (selectedType == RemoteServerType.SMB) {
                        OutlinedTextField(
                            value = domain,
                            onValueChange = { domain = it },
                            label = { Text(stringResource(R.string.external_media_server_domain)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.external_media_server_user)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (selectedType == RemoteServerType.SMB) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.external_media_server_pass)) },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                                val description = stringResource(if (passwordVisible) R.string.hide_password else R.string.show_password)
                                if (isTv) {
                                    TvIconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        TvIcon(
                                            imageVector = image,
                                            contentDescription = description,
                                            tint = TvMaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = image,
                                            contentDescription = description,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val portInt = port.toIntOrNull() ?: if (selectedType == RemoteServerType.SMB) 445 else 2049
                                onSave(name, selectedType, host, portInt, share, domain, username, password)
                            },
                            enabled = host.isNotBlank()
                        ) {
                            Text(stringResource(R.string.external_media_save))
                        }
                    }
                }
            }
        }

        if (isTv) {
            TvLaTorrentolaTheme(themeMode = effectiveThemeMode) { content() }
        } else {
            LaTorrentolaTheme(themeMode = effectiveThemeMode) { content() }
        }
    }
}

@Composable
fun AuthenticationPromptDialog(
    themeMode: Int? = null,
    server: RemoteServerConfig,
    share: String,
    hasStoredCredentials: Boolean = false,
    onClearCredentials: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSubmit: (username: String?, password: String?, domain: String?, rememberCredentials: Boolean) -> Unit
) {
    val context = LocalContext.current
    val preferenceManager = remember(context) { PreferenceManager(context) }
    val currentObservedTheme by preferenceManager.themeFlow.collectAsState(initial = preferenceManager.getTheme())
    val effectiveThemeMode = remember(context, themeMode, currentObservedTheme) {
        themeMode ?: currentObservedTheme
    }
    val isTv = remember(context) { context.isTvDevice() }

    var username by remember(server) { mutableStateOf(server.username.orEmpty()) }
    var password by remember(server) { mutableStateOf(server.password.orEmpty()) }
    var domain by remember(server) { mutableStateOf(server.domain.orEmpty()) }
    var rememberCredentials by remember { mutableStateOf(true) }
    var storedCredentialsPresent by remember(hasStoredCredentials) { mutableStateOf(hasStoredCredentials) }
    var passwordVisible by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = !isTv)
    ) {
        val content: @Composable () -> Unit = {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth(if (isTv) 0.60f else 0.92f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.external_media_auth_required),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.external_media_auth_subtitle,
                            share.ifBlank { server.name },
                            server.host
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (server.type == RemoteServerType.SMB) {
                        OutlinedTextField(
                            value = domain,
                            onValueChange = { domain = it },
                            label = { Text(stringResource(R.string.external_media_server_domain)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.external_media_server_user)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (server.type == RemoteServerType.SMB) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.external_media_server_pass)) },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                                val description = stringResource(if (passwordVisible) R.string.hide_password else R.string.show_password)
                                if (isTv) {
                                    TvIconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        TvIcon(
                                            imageVector = image,
                                            contentDescription = description,
                                            tint = TvMaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = image,
                                            contentDescription = description,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = rememberCredentials,
                            onCheckedChange = { rememberCredentials = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.external_media_auth_remember),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (storedCredentialsPresent) {
                        OutlinedButton(
                            onClick = {
                                onClearCredentials?.invoke()
                                storedCredentialsPresent = false
                                username = ""
                                password = ""
                                domain = ""
                                passwordVisible = false
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.external_media_auth_clear_credentials),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onSubmit(username, password, domain, rememberCredentials)
                            }
                        ) {
                            Text(stringResource(R.string.external_media_auth_connect))
                        }
                    }
                }
            }
        }

        if (isTv) {
            TvLaTorrentolaTheme(themeMode = effectiveThemeMode) { content() }
        } else {
            LaTorrentolaTheme(themeMode = effectiveThemeMode) { content() }
        }
    }
}

private val previewMockUiState = ExternalMediaUiState(
    selectedTab = ExternalMediaTab.USB,
    activeStorageId = "internal",
    activeStorageDisplayName = "Almacenamiento interno",
    activeStorageAvailableBytes = 15_200_000_000L,
    activeStorageType = "INTERNAL",
    usbLocations = listOf(
        StorageLocation(
            id = "internal",
            displayName = "Almacenamiento interno",
            rootDirectory = File("/storage/emulated/0"),
            isRemovable = false,
            availableBytes = 15_200_000_000L,
            totalBytes = 64_000_000_000L
        ),
        StorageLocation(
            id = "/storage/USB_DRIVE",
            displayName = "SanDisk USB (128 GB)",
            rootDirectory = File("/storage/USB_DRIVE"),
            isRemovable = true,
            availableBytes = 85_000_000_000L,
            totalBytes = 128_000_000_000L
        )
    ),
    remoteServers = listOf(
        RemoteServerConfig(
            id = "srv-1",
            name = "NAS Pelis",
            type = RemoteServerType.SMB,
            host = "192.168.1.100",
            port = 445,
            shareOrExport = "movies"
        ),
        RemoteServerConfig(
            id = "srv-2",
            name = "NFS Share",
            type = RemoteServerType.NFS,
            host = "192.168.1.50",
            port = 2049,
            shareOrExport = "/exports/video"
        )
    )
)

@Preview(name = "Mobile Light", showBackground = true)
@Composable
private fun ExternalMediaBrowserScreenMobileLightPreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_LIGHT) {
        MobileExternalMediaContent(
            uiState = previewMockUiState,
            onBackClick = {},
            onSelectTab = {},
            onSetActiveStorage = {},
            onSelectUsbLocation = {},
            onNavigateUpUsb = {},
            onSelectServer = {},
            onNavigateUpRemote = {},
            onAddServerClick = {},
            onScanNetworkClick = {},
            onEditServerClick = {},
            onDeleteServerClick = {},
            onFileItemClicked = {}
        )
    }
}

@Preview(name = "Mobile Dark", showBackground = true)
@Composable
private fun ExternalMediaBrowserScreenMobileDarkPreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        MobileExternalMediaContent(
            uiState = previewMockUiState,
            onBackClick = {},
            onSelectTab = {},
            onSetActiveStorage = {},
            onSelectUsbLocation = {},
            onNavigateUpUsb = {},
            onSelectServer = {},
            onNavigateUpRemote = {},
            onAddServerClick = {},
            onScanNetworkClick = {},
            onEditServerClick = {},
            onDeleteServerClick = {},
            onFileItemClicked = {}
        )
    }
}

@Preview(name = "TV Light", showBackground = true, device = "id:tv_1080p")
@Composable
private fun ExternalMediaBrowserScreenTvLightPreview() {
    TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_LIGHT) {
        TvExternalMediaContent(
            uiState = previewMockUiState,
            themeMode = PreferenceManager.THEME_LIGHT,
            onBackClick = {},
            onSelectTab = {},
            onSetActiveStorage = {},
            onSelectUsbLocation = {},
            onNavigateUpUsb = {},
            onSelectServer = {},
            onNavigateUpRemote = {},
            onAddServerClick = {},
            onScanNetworkClick = {},
            onEditServerClick = {},
            onDeleteServerClick = {},
            onFileItemClicked = {}
        )
    }
}

@Preview(name = "TV Dark", showBackground = true, device = "id:tv_1080p")
@Composable
private fun ExternalMediaBrowserScreenTvDarkPreview() {
    TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        TvExternalMediaContent(
            uiState = previewMockUiState,
            themeMode = PreferenceManager.THEME_DARK,
            onBackClick = {},
            onSelectTab = {},
            onSetActiveStorage = {},
            onSelectUsbLocation = {},
            onNavigateUpUsb = {},
            onSelectServer = {},
            onNavigateUpRemote = {},
            onAddServerClick = {},
            onScanNetworkClick = {},
            onEditServerClick = {},
            onDeleteServerClick = {},
            onFileItemClicked = {}
        )
    }
}

@Preview(name = "Storage Banner Mobile Light", showBackground = true)
@Composable
private fun ActiveStorageBannerMobileLightPreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_LIGHT) {
        ActiveStorageBanner(
            uiState = previewMockUiState,
            isTv = false,
            themeMode = PreferenceManager.THEME_LIGHT
        )
    }
}

@Preview(name = "Storage Banner Mobile Dark", showBackground = true)
@Composable
private fun ActiveStorageBannerMobileDarkPreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        ActiveStorageBanner(
            uiState = previewMockUiState,
            isTv = false,
            themeMode = PreferenceManager.THEME_DARK
        )
    }
}

@Preview(name = "Storage Banner TV Light", showBackground = true, device = "id:tv_1080p")
@Composable
private fun ActiveStorageBannerTvLightPreview() {
    TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_LIGHT) {
        ActiveStorageBanner(
            uiState = previewMockUiState,
            isTv = true,
            themeMode = PreferenceManager.THEME_LIGHT
        )
    }
}

@Preview(name = "Storage Banner TV Dark", showBackground = true, device = "id:tv_1080p")
@Composable
private fun ActiveStorageBannerTvDarkPreview() {
    TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        ActiveStorageBanner(
            uiState = previewMockUiState,
            isTv = true,
            themeMode = PreferenceManager.THEME_DARK
        )
    }
}

@Preview(name = "Server Dialog Mobile Light", showBackground = true)
@Composable
private fun ServerConfigDialogMobileLightPreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_LIGHT) {
        ServerConfigDialog(
            themeMode = PreferenceManager.THEME_LIGHT,
            editingServer = previewMockUiState.remoteServers.firstOrNull(),
            onDismiss = {},
            onSave = { _, _, _, _, _, _, _, _ -> }
        )
    }
}

@Preview(name = "Server Dialog Mobile Dark", showBackground = true)
@Composable
private fun ServerConfigDialogMobileDarkPreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        ServerConfigDialog(
            themeMode = PreferenceManager.THEME_DARK,
            editingServer = previewMockUiState.remoteServers.firstOrNull(),
            onDismiss = {},
            onSave = { _, _, _, _, _, _, _, _ -> }
        )
    }
}

@Preview(name = "Server Dialog TV Light", showBackground = true, device = "id:tv_1080p")
@Composable
private fun ServerConfigDialogTvLightPreview() {
    TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_LIGHT) {
        ServerConfigDialog(
            themeMode = PreferenceManager.THEME_LIGHT,
            editingServer = previewMockUiState.remoteServers.firstOrNull(),
            onDismiss = {},
            onSave = { _, _, _, _, _, _, _, _ -> }
        )
    }
}

@Preview(name = "Server Dialog TV Dark", showBackground = true, device = "id:tv_1080p")
@Composable
private fun ServerConfigDialogTvDarkPreview() {
    TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        ServerConfigDialog(
            themeMode = PreferenceManager.THEME_DARK,
            editingServer = previewMockUiState.remoteServers.firstOrNull(),
            onDismiss = {},
            onSave = { _, _, _, _, _, _, _, _ -> }
        )
    }
}

@Preview(name = "Auth Dialog Mobile Light", showBackground = true)
@Composable
private fun AuthPromptDialogMobileLightPreview() {
    val server = previewMockUiState.remoteServers.first()
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_LIGHT) {
        AuthenticationPromptDialog(
            themeMode = PreferenceManager.THEME_LIGHT,
            server = server,
            share = "movies",
            hasStoredCredentials = true,
            onClearCredentials = {},
            onDismiss = {},
            onSubmit = { _, _, _, _ -> }
        )
    }
}

@Preview(name = "Auth Dialog Mobile Dark", showBackground = true)
@Composable
private fun AuthPromptDialogMobileDarkPreview() {
    val server = previewMockUiState.remoteServers.first()
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        AuthenticationPromptDialog(
            themeMode = PreferenceManager.THEME_DARK,
            server = server,
            share = "movies",
            hasStoredCredentials = false,
            onClearCredentials = {},
            onDismiss = {},
            onSubmit = { _, _, _, _ -> }
        )
    }
}

@Preview(name = "Auth Dialog TV Light", showBackground = true, device = "id:tv_1080p")
@Composable
private fun AuthPromptDialogTvLightPreview() {
    val server = previewMockUiState.remoteServers.first()
    TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_LIGHT) {
        AuthenticationPromptDialog(
            themeMode = PreferenceManager.THEME_LIGHT,
            server = server,
            share = "movies",
            hasStoredCredentials = true,
            onClearCredentials = {},
            onDismiss = {},
            onSubmit = { _, _, _, _ -> }
        )
    }
}

@Preview(name = "Auth Dialog TV Dark", showBackground = true, device = "id:tv_1080p")
@Composable
private fun AuthPromptDialogTvDarkPreview() {
    val server = previewMockUiState.remoteServers.first()
    TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        AuthenticationPromptDialog(
            themeMode = PreferenceManager.THEME_DARK,
            server = server,
            share = "movies",
            hasStoredCredentials = false,
            onClearCredentials = {},
            onDismiss = {},
            onSubmit = { _, _, _, _ -> }
        )
    }
}



