@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.tv.material3.IconButton as TvIconButton
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.BuildConfig
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.utils.AutoPlayQualitySelectionMethod
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalInspectionMode
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.ExperimentalHazeApi
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.tv.material3.Button
import com.martinrevert.latorrentola.service.storage.StorageLocation
import com.martinrevert.latorrentola.service.storage.TorrentStorageResolver
import com.martinrevert.latorrentola.ui.media.formatMediaSize
import java.util.Locale

/** Collects settings state and presents the adaptive settings screen. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    userPhotoUrl: String?,
    userName: String?,
    userEmail: String?,
    themeMode: Int? = null,
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onExternalMediaClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val preferenceManager = remember(context) { PreferenceManager(context) }
    val currentObservedTheme by preferenceManager.themeFlow.collectAsState(initial = preferenceManager.getTheme())
    val uiState by viewModel.uiState.collectAsState()
    val isTv = remember(context) { context.isTvDevice() }
    val effectiveThemeMode = uiState.theme

    val content: @Composable () -> Unit = {
        SettingsScreenContent(
            uiState = uiState,
            userPhotoUrl = userPhotoUrl,
            userName = userName,
            userEmail = userEmail,
            isTv = isTv,
            onBackClick = onBackClick,
            onLogoutClick = onLogoutClick,
            onDownloadsClick = onDownloadsClick,
            onExternalMediaClick = onExternalMediaClick,
            onToggleVoiceSystem = { viewModel.toggleVoiceSystem(it) },
            onToggleVoiceSummary = { viewModel.toggleVoiceSummary(it) },
            onToggleVoiceTranslation = { viewModel.toggleVoiceTranslation(it) },
            onToggleVibrator = { viewModel.toggleVibrator(it) },
            onTogglePushEnabled = { viewModel.togglePushEnabled(it) },
            onSetTheme = { viewModel.setTheme(it) },
            onSetFilteredLanguages = { viewModel.setFilteredLanguages(it) },
            onSetMinimumRating = { viewModel.setMinimumRating(it) },
            onSetTorrentHandlingMode = { viewModel.setTorrentHandlingMode(it) },
            onSetAutoPlayQualitySelectionMethod = { viewModel.setAutoPlayQualitySelectionMethod(it) },
            onSetStorageLocation = { viewModel.setStorageLocation(it) },
            onSaveOpenSubtitlesCredentials = { username, password ->
                viewModel.saveOpenSubtitlesCredentials(username, password)
            },
            onClearOpenSubtitlesCredentials = { viewModel.clearOpenSubtitlesCredentials() }
        )
    }

    if (isTv) {
        TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
            content()
        }
    } else {
        LaTorrentolaTheme(themeMode = effectiveThemeMode) {
            content()
        }
    }
}

/** Renders account, appearance, filtering, and accessibility settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    uiState: SettingsUiState,
    userPhotoUrl: String?,
    userName: String?,
    userEmail: String?,
    isTv: Boolean,
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onExternalMediaClick: () -> Unit,
    onToggleVoiceSystem: (Boolean) -> Unit,
    onToggleVoiceSummary: (Boolean) -> Unit,
    onToggleVoiceTranslation: (Boolean) -> Unit,
    onToggleVibrator: (Boolean) -> Unit,
    onTogglePushEnabled: (Boolean) -> Unit,
    onSetTheme: (Int) -> Unit,
    onSetFilteredLanguages: (String) -> Unit,
    onSetMinimumRating: (Float) -> Unit,
    onSetTorrentHandlingMode: (TorrentHandlingMode) -> Unit,
    onSetAutoPlayQualitySelectionMethod: (AutoPlayQualitySelectionMethod) -> Unit,
    onSetStorageLocation: (String) -> Unit,
    onSaveOpenSubtitlesCredentials: (String, String) -> Unit,
    onClearOpenSubtitlesCredentials: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600 || isTv
    val scrollState = rememberScrollState()
    val hazeState = rememberHazeState()
    val isInspection = LocalInspectionMode.current
    val isPreAndroid12 = !isInspection && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
    val topBarContainerColor = if (isPreAndroid12) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    } else {
        Color.Transparent
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                modifier = Modifier.hazeGlass(input = HazeInput.Sources(hazeState)),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarContainerColor),
                title = {
                    if (isTv) {
                        androidx.tv.material3.Text(
                            text = stringResource(R.string.settings_title),
                            style = TvMaterialTheme.typography.titleLarge,
                            color = TvMaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                navigationIcon = {
                    if (isTv) {
                        TvIconButton(
                            onClick = onBackClick
                        ) {
                            androidx.tv.material3.Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_desc)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.focusHighlight(shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_desc)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .consumeWindowInsets(padding)
        ) {
        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = 64.dp + statusBarHeight + 16.dp,
                        bottom = 16.dp + navBarHeight,
                        start = 24.dp,
                        end = 24.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                // Left Column: User, Theme, Actions
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (userPhotoUrl != null || userName != null) {
                        UserSection(userPhotoUrl, userName, userEmail, isTv = isTv)
                        LogoutButton(isTv = isTv, onClick = onLogoutClick)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    SettingsSectionTitle(
                        title = stringResource(R.string.settings_theme),
                        isTv = isTv
                    )
                    ThemeSelector(selectedTheme = uiState.theme, isTv = isTv, onThemeSelected = onSetTheme)
                    DownloadsManagerButton(isTv = isTv, onClick = onDownloadsClick)
                    Spacer(modifier = Modifier.height(8.dp))
                    ExternalMediaManagerButton(
                        isTv = isTv,
                        storageLocation = uiState.storageLocation,
                        availableLocations = uiState.availableStorageLocations,
                        onClick = onExternalMediaClick
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    AppVersionInfo(isTv = isTv)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Right Column: Toggles and Language Filter
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsToggle(
                        title = stringResource(R.string.settings_tts),
                        checked = uiState.voiceSystem,
                        onCheckedChange = onToggleVoiceSystem
                    )
                    SettingsToggle(
                        title = stringResource(R.string.settings_voice_summary),
                        checked = uiState.voiceSummary,
                        onCheckedChange = onToggleVoiceSummary
                    )
                    SettingsToggle(
                        title = stringResource(R.string.settings_voice_translation),
                        checked = uiState.voiceTranslation,
                        enabled = uiState.voiceSummary,
                        onCheckedChange = onToggleVoiceTranslation
                    )

                    if (!isTv) {
                        SettingsToggle(
                            title = stringResource(R.string.settings_vibrator),
                            checked = uiState.vibrator,
                            onCheckedChange = onToggleVibrator
                        )
                        SettingsToggle(
                            title = stringResource(R.string.settings_push),
                            checked = uiState.pushEnabled,
                            onCheckedChange = onTogglePushEnabled
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    TorrentModeSetting(
                        selectedMode = uiState.torrentHandlingMode,
                        onModeSelected = onSetTorrentHandlingMode,
                        isTv = isTv
                    )
                    AutoPlayQualitySetting(
                        selectedMethod = uiState.autoPlayQualitySelectionMethod,
                        onMethodSelected = onSetAutoPlayQualitySelectionMethod,
                        isTv = isTv
                    )
                    OpenSubtitlesCredentialsSetting(
                        uiState = uiState,
                        isTv = isTv,
                        onSave = onSaveOpenSubtitlesCredentials,
                        onClear = onClearOpenSubtitlesCredentials
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    MinimumRatingSetting(
                        value = uiState.minimumRating,
                        isTv = isTv,
                        onValueChange = onSetMinimumRating
                    )

                    OutlinedTextField(
                        value = uiState.filteredLanguages,
                        onValueChange = onSetFilteredLanguages,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusHighlight(shape = OutlinedTextFieldDefaults.shape),
                        label = { Text(stringResource(R.string.filter_languages_label)) },
                        placeholder = { Text(stringResource(R.string.filter_languages_placeholder)) },
                        supportingText = { Text(stringResource(R.string.filter_languages_support)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .imePadding()
                    .padding(
                        top = 64.dp + statusBarHeight + 16.dp,
                        bottom = 16.dp + navBarHeight,
                        start = 16.dp,
                        end = 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (userPhotoUrl != null || userName != null) {
                    UserSection(userPhotoUrl, userName, userEmail, isTv = isTv)
                    LogoutButton(isTv = isTv, onClick = onLogoutClick)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                SettingsToggle(
                    title = stringResource(R.string.settings_tts),
                    checked = uiState.voiceSystem,
                    onCheckedChange = onToggleVoiceSystem
                )
                SettingsToggle(
                    title = stringResource(R.string.settings_voice_summary),
                    checked = uiState.voiceSummary,
                    onCheckedChange = onToggleVoiceSummary
                )
                SettingsToggle(
                    title = stringResource(R.string.settings_voice_translation),
                    checked = uiState.voiceTranslation,
                    enabled = uiState.voiceSummary,
                    onCheckedChange = onToggleVoiceTranslation
                )
                
                if (!isTv) {
                    SettingsToggle(
                        title = stringResource(R.string.settings_vibrator),
                        checked = uiState.vibrator,
                        onCheckedChange = onToggleVibrator
                    )
                    
                    SettingsToggle(
                        title = stringResource(R.string.settings_push),
                        checked = uiState.pushEnabled,
                        onCheckedChange = onTogglePushEnabled
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingsSectionTitle(
                    title = stringResource(R.string.settings_theme),
                    isTv = isTv
                )
                
                ThemeSelector(
                    selectedTheme = uiState.theme,
                    isTv = isTv,
                    onThemeSelected = onSetTheme
                )
                DownloadsManagerButton(isTv = isTv, onClick = onDownloadsClick)
                Spacer(modifier = Modifier.height(8.dp))
                ExternalMediaManagerButton(
                    isTv = isTv,
                    storageLocation = uiState.storageLocation,
                    availableLocations = uiState.availableStorageLocations,
                    onClick = onExternalMediaClick
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                TorrentModeSetting(
                    selectedMode = uiState.torrentHandlingMode,
                    onModeSelected = onSetTorrentHandlingMode,
                    isTv = isTv
                )
                AutoPlayQualitySetting(
                    selectedMethod = uiState.autoPlayQualitySelectionMethod,
                    onMethodSelected = onSetAutoPlayQualitySelectionMethod,
                    isTv = isTv
                )
                OpenSubtitlesCredentialsSetting(
                    uiState = uiState,
                    isTv = isTv,
                    onSave = onSaveOpenSubtitlesCredentials,
                    onClear = onClearOpenSubtitlesCredentials
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                MinimumRatingSetting(
                    value = uiState.minimumRating,
                    isTv = isTv,
                    onValueChange = onSetMinimumRating
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                OutlinedTextField(
                    value = uiState.filteredLanguages,
                    onValueChange = onSetFilteredLanguages,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusHighlight(shape = OutlinedTextFieldDefaults.shape),
                    label = { Text(stringResource(R.string.filter_languages_label)) },
                    placeholder = { Text(stringResource(R.string.filter_languages_placeholder)) },
                    supportingText = {
                        Text(stringResource(R.string.filter_languages_support))
                    },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
                )

                Spacer(modifier = Modifier.height(24.dp))
                
                AppVersionInfo(isTv = isTv)
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
}

/**
 * Opens the locally managed torrent jobs.
 *
 * @param isTv Whether to use the native TV button.
 * @param onClick Navigates to the download manager.
 */
@Composable
private fun DownloadsManagerButton(isTv: Boolean, onClick: () -> Unit) {
    if (isTv) {
        androidx.tv.material3.Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.tv.material3.ButtonDefaults.colors(
                containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                contentColor = TvMaterialTheme.colorScheme.onSurfaceVariant,
                focusedContainerColor = TvMaterialTheme.colorScheme.primaryContainer,
                focusedContentColor = TvMaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            androidx.tv.material3.Text(stringResource(R.string.torrent_downloads_title))
        }
    } else {
        androidx.compose.material3.OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .focusHighlight(shape = ButtonDefaults.shape)
        ) {
            Text(stringResource(R.string.torrent_downloads_title))
        }
    }
}

@Composable
private fun ExternalMediaManagerButton(
    isTv: Boolean,
    storageLocation: String,
    availableLocations: List<StorageLocation>,
    onClick: () -> Unit
) {
    val activeLabel = remember(storageLocation, availableLocations) {
        if (storageLocation == TorrentStorageResolver.LOCATION_INTERNAL || storageLocation.startsWith("remote:")) {
            val internalLoc = availableLocations.firstOrNull { it.id == TorrentStorageResolver.LOCATION_INTERNAL }
            val spaceText = internalLoc?.let { " (${formatMediaSize(it.availableBytes)} libres)" } ?: ""
            "Interno$spaceText"
        } else {
            val match = availableLocations.firstOrNull { it.id == storageLocation }
            val spaceText = match?.let { " (${formatMediaSize(it.availableBytes)} libres)" } ?: ""
            "${match?.displayName ?: "USB"}$spaceText"
        }
    }

    if (isTv) {
        androidx.tv.material3.Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .focusHighlight(shape = RoundedCornerShape(12.dp)),
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(TvMaterialTheme.shapes.medium),
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                contentColor = TvMaterialTheme.colorScheme.onSurfaceVariant,
                focusedContainerColor = TvMaterialTheme.colorScheme.primaryContainer,
                focusedContentColor = TvMaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.tv.material3.Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = TvMaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    androidx.tv.material3.Text(
                        text = stringResource(R.string.external_media_title),
                        style = TvMaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TvMaterialTheme.colorScheme.onSurface
                    )
                    androidx.tv.material3.Text(
                        text = stringResource(R.string.settings_storage_card_subtitle, activeLabel),
                        style = TvMaterialTheme.typography.bodySmall,
                        color = TvMaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.tv.material3.Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = TvMaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        OutlinedCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .focusHighlight(shape = RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.external_media_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.settings_storage_card_subtitle, activeLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Presents the mutually exclusive torrent launch behavior.
 *
 * @param selectedMode Currently stored handling mode.
 * @param onModeSelected Persists a user-selected mode.
 * @param isTv Whether this device is a television.
 */
@Composable
private fun TorrentModeSetting(
    selectedMode: TorrentHandlingMode,
    onModeSelected: (TorrentHandlingMode) -> Unit,
    isTv: Boolean
) {
    // On TV devices, Chromecast mode is not shown since TV devices use dedicated Cast functionality
    val options = if (isTv) listOf(
        Triple(TorrentHandlingMode.EXTERNAL_CLIENT, R.string.torrent_mode_external, R.string.torrent_mode_external_support),
        Triple(TorrentHandlingMode.LOCAL_PLAYBACK, R.string.torrent_mode_local, R.string.torrent_mode_local_support)
    ) else listOf(
        Triple(TorrentHandlingMode.EXTERNAL_CLIENT, R.string.torrent_mode_external, R.string.torrent_mode_external_support),
        Triple(TorrentHandlingMode.LOCAL_PLAYBACK, R.string.torrent_mode_local, R.string.torrent_mode_local_support),
        Triple(TorrentHandlingMode.CHROMECAST, R.string.torrent_mode_cast, R.string.torrent_mode_cast_support)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
    ) {
        SettingsSectionTitle(
            title = stringResource(R.string.torrent_mode_title),
            isTv = isTv
        )
        Spacer(modifier = Modifier.height(4.dp))
        options.forEach { (mode, title, support) ->
            val isSelected = selectedMode == mode
            if (isTv) {
                androidx.tv.material3.ListItem(
                    selected = isSelected,
                    enabled = true,
                    onClick = { onModeSelected(mode) },
                    headlineContent = {
                        androidx.tv.material3.Text(
                            text = stringResource(title),
                            style = TvMaterialTheme.typography.bodyLarge
                        )
                    },
                    supportingContent = {
                        androidx.tv.material3.Text(
                            text = stringResource(support),
                            style = TvMaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        androidx.tv.material3.RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusHighlight(shape = MaterialTheme.shapes.small)
                        .selectable(
                            selected = isSelected,
                            enabled = true,
                            onClick = { onModeSelected(mode) },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = stringResource(title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(support),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Edits OpenSubtitles credentials and reports whether an encrypted account is configured.
 *
 * @param uiState Current credential status and username.
 * @param isTv Whether to use TV-native action buttons.
 * @param onSave Encrypts and stores the entered account credentials.
 * @param onClear Removes saved credentials.
 */
@Composable
private fun OpenSubtitlesCredentialsSetting(
    uiState: SettingsUiState,
    isTv: Boolean,
    onSave: (String, String) -> Unit,
    onClear: () -> Unit
) {
    var username by remember(uiState.openSubtitlesUsername) {
        mutableStateOf(uiState.openSubtitlesUsername)
    }
    var password by remember(uiState.openSubtitlesPassword) {
        mutableStateOf(uiState.openSubtitlesPassword)
    }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsSectionTitle(
            title = stringResource(R.string.opensubtitles_title),
            isTv = isTv
        )
        if (isTv) {
            androidx.tv.material3.Text(
                text = stringResource(
                    if (uiState.openSubtitlesCredentialsConfigured) {
                        R.string.opensubtitles_configured
                    } else {
                        R.string.opensubtitles_support
                    }
                ),
                style = TvMaterialTheme.typography.bodySmall,
                color = TvMaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = stringResource(
                    if (uiState.openSubtitlesCredentialsConfigured) {
                        R.string.opensubtitles_configured
                    } else {
                        R.string.opensubtitles_support
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusHighlight(shape = OutlinedTextFieldDefaults.shape),
            label = { Text(stringResource(R.string.opensubtitles_username)) },
            singleLine = true
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusHighlight(shape = OutlinedTextFieldDefaults.shape),
            label = { Text(stringResource(R.string.opensubtitles_password)) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                val description = stringResource(if (passwordVisible) R.string.hide_password else R.string.show_password)
                if (isTv) {
                    TvIconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
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
            singleLine = true
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isTv) {
                androidx.tv.material3.Button(
                    onClick = {
                        onSave(username.trim(), password)
                    },
                    enabled = username.isNotBlank() && password.isNotBlank()
                ) {
                    androidx.tv.material3.Text(stringResource(R.string.opensubtitles_save))
                }
                if (uiState.openSubtitlesCredentialsConfigured) {
                    androidx.tv.material3.Button(
                        onClick = {
                            onClear()
                            username = ""
                            password = ""
                            passwordVisible = false
                        },
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                            contentColor = TvMaterialTheme.colorScheme.onSurfaceVariant,
                            focusedContainerColor = TvMaterialTheme.colorScheme.primaryContainer,
                            focusedContentColor = TvMaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        androidx.tv.material3.Text(stringResource(R.string.opensubtitles_clear))
                    }
                }
            } else {
                androidx.compose.material3.Button(
                    onClick = {
                        onSave(username.trim(), password)
                    },
                    enabled = username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.focusHighlight(shape = ButtonDefaults.shape)
                ) {
                    Text(stringResource(R.string.opensubtitles_save))
                }
                if (uiState.openSubtitlesCredentialsConfigured) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            onClear()
                            username = ""
                            password = ""
                            passwordVisible = false
                        },
                        modifier = Modifier.focusHighlight(shape = ButtonDefaults.shape)
                    ) {
                        Text(stringResource(R.string.opensubtitles_clear))
                    }
                }
            }
        }
    }
}

/** Displays the sign-out action using the appropriate device styling. */
@Composable
private fun LogoutButton(isTv: Boolean, onClick: () -> Unit) {
    if (isTv) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.tv.material3.ButtonDefaults.colors(
                containerColor = TvMaterialTheme.colorScheme.errorContainer,
                contentColor = TvMaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            androidx.tv.material3.Text(
                text = stringResource(R.string.logout_button),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    } else {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .focusHighlight(shape = ButtonDefaults.shape),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Text(stringResource(R.string.logout_button))
        }
    }
}

/** Displays the installed application version. */
@Composable
private fun AppVersionInfo(isTv: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isTv) {
            androidx.tv.material3.Text(
                text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                style = TvMaterialTheme.typography.labelMedium,
                color = TvMaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.tv.material3.Text(
                text = stringResource(R.string.app_build, BuildConfig.VERSION_CODE),
                style = TvMaterialTheme.typography.labelSmall,
                color = TvMaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        } else {
            Text(
                text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.app_build, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/** Section title with device-appropriate typography and colors. */
@Composable
private fun SettingsSectionTitle(title: String, isTv: Boolean) {
    if (isTv) {
        androidx.tv.material3.Text(
            text = title,
            style = TvMaterialTheme.typography.titleMedium,
            color = TvMaterialTheme.colorScheme.onSurface
        )
    } else {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Presents the signed-in user's profile and account information. */
@Composable
fun UserSection(
    photoUrl: String?,
    name: String?,
    email: String?,
    isTv: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = stringResource(R.string.user_profile_desc),
                modifier = Modifier
                    .size(if (isTv) 56.dp else 64.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(if (isTv) 56.dp else 64.dp),
                shape = CircleShape,
                color = if (isTv) TvMaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isTv) {
                        androidx.tv.material3.Text(
                            text = name?.firstOrNull()?.uppercase() ?: "?",
                            style = TvMaterialTheme.typography.headlineMedium,
                            color = TvMaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Text(
                            text = name?.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            if (isTv) {
                androidx.tv.material3.Text(
                    text = name ?: stringResource(com.martinrevert.latorrentola.R.string.default_user_name),
                    style = TvMaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TvMaterialTheme.colorScheme.onSurface
                )
                if (email != null) {
                    androidx.tv.material3.Text(
                        text = email,
                        style = TvMaterialTheme.typography.bodyMedium,
                        color = TvMaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = name ?: stringResource(com.martinrevert.latorrentola.R.string.default_user_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (email != null) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Allows selection between system, light, and dark appearance modes. */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun ThemeSelector(
    selectedTheme: Int,
    isTv: Boolean = false,
    onThemeSelected: (Int) -> Unit
) {
    val options = listOf(
        Triple(PreferenceManager.THEME_SYSTEM, stringResource(R.string.theme_system), Icons.Default.BrightnessAuto),
        Triple(PreferenceManager.THEME_LIGHT, stringResource(R.string.theme_light), Icons.Default.LightMode),
        Triple(PreferenceManager.THEME_DARK, stringResource(R.string.theme_dark), Icons.Default.DarkMode)
    )

    if (isTv) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (themeValue, label, icon) ->
                val isSelected = selectedTheme == themeValue
                androidx.tv.material3.FilterChip(
                    selected = isSelected,
                    onClick = { onThemeSelected(themeValue) },
                    modifier = Modifier.weight(1f),
                    colors = androidx.tv.material3.FilterChipDefaults.colors(
                        containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                        contentColor = TvMaterialTheme.colorScheme.onSurfaceVariant,
                        focusedContainerColor = TvMaterialTheme.colorScheme.primaryContainer,
                        focusedContentColor = TvMaterialTheme.colorScheme.onPrimaryContainer,
                        selectedContainerColor = TvMaterialTheme.colorScheme.primary,
                        selectedContentColor = TvMaterialTheme.colorScheme.onPrimary,
                        focusedSelectedContainerColor = TvMaterialTheme.colorScheme.primaryContainer,
                        focusedSelectedContentColor = TvMaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    leadingIcon = {
                        androidx.tv.material3.Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                ) {
                    androidx.tv.material3.Text(
                        text = label,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = TvMaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    } else {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEachIndexed { index, (themeValue, label, icon) ->
                val shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                val isSelected = selectedTheme == themeValue
                SegmentedButton(
                    shape = shape,
                    onClick = { onThemeSelected(themeValue) },
                    selected = isSelected,
                    icon = {
                        SegmentedButtonDefaults.Icon(active = isSelected) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                            )
                        }
                    },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                        activeBorderColor = MaterialTheme.colorScheme.outline,
                        inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.focusHighlight(shape = shape)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/** Presents the bounded minimum-rating preference as an adjustable control. */
@Composable
private fun MinimumRatingSetting(
    value: Float,
    isTv: Boolean = false,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSectionTitle(
            title = stringResource(R.string.minimum_imdb_rating),
            isTv = isTv
        )
        if (isTv) {
            androidx.tv.material3.Text(
                text = stringResource(R.string.minimum_imdb_rating_support),
                style = TvMaterialTheme.typography.bodySmall,
                color = TvMaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = stringResource(R.string.minimum_imdb_rating_support),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isTv) {
                TvIconButton(
                    onClick = {
                        val next = (kotlin.math.round((value - 0.1f) * 10.0f) / 10.0f).coerceAtLeast(PreferenceManager.MINIMUM_RATING_MIN)
                        onValueChange(next)
                    },
                    enabled = value > PreferenceManager.MINIMUM_RATING_MIN
                ) {
                    androidx.tv.material3.Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.decrease_minimum_rating)
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        val next = (kotlin.math.round((value - 0.1f) * 10.0f) / 10.0f).coerceAtLeast(PreferenceManager.MINIMUM_RATING_MIN)
                        onValueChange(next)
                    },
                    enabled = value > PreferenceManager.MINIMUM_RATING_MIN
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.decrease_minimum_rating)
                    )
                }
            }
            val formattedValue = String.format(Locale.US, "%.1f", value)
            if (isTv) {
                androidx.tv.material3.Text(
                    text = stringResource(R.string.minimum_imdb_rating_value, formattedValue),
                    style = TvMaterialTheme.typography.titleLarge,
                    color = TvMaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                Text(
                    text = stringResource(R.string.minimum_imdb_rating_value, formattedValue),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            if (isTv) {
                TvIconButton(
                    onClick = {
                        val next = (kotlin.math.round((value + 0.1f) * 10.0f) / 10.0f).coerceAtMost(PreferenceManager.MINIMUM_RATING_MAX)
                        onValueChange(next)
                    },
                    enabled = value < PreferenceManager.MINIMUM_RATING_MAX
                ) {
                    androidx.tv.material3.Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.increase_minimum_rating)
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        val next = (kotlin.math.round((value + 0.1f) * 10.0f) / 10.0f).coerceAtMost(PreferenceManager.MINIMUM_RATING_MAX)
                        onValueChange(next)
                    },
                    enabled = value < PreferenceManager.MINIMUM_RATING_MAX
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.increase_minimum_rating)
                    )
                }
            }
        }
    }
}

/** Renders a labeled settings toggle with device-appropriate focus behavior. */
@Composable
fun SettingsToggle(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }

    if (isTv) {
        androidx.tv.material3.ListItem(
            selected = false,
            enabled = enabled,
            onClick = { onCheckedChange(!checked) },
            headlineContent = {
                androidx.tv.material3.Text(
                    text = title,
                    style = androidx.tv.material3.MaterialTheme.typography.bodyLarge
                )
            },
            trailingContent = {
                androidx.tv.material3.Switch(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = enabled
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusHighlight(shape = MaterialTheme.shapes.small)
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

/**
 * Presents the auto-play quality/seed selection behavior when an episode ends.
 *
 * @param selectedMethod Currently configured auto-play method.
 * @param onMethodSelected Persists a user-selected method.
 * @param isTv Whether to use TV-appropriate selectable ListItems.
 */
@Composable
private fun AutoPlayQualitySetting(
    selectedMethod: AutoPlayQualitySelectionMethod,
    onMethodSelected: (AutoPlayQualitySelectionMethod) -> Unit,
    isTv: Boolean = false
) {
    val options = listOf(
        Triple(
            AutoPlayQualitySelectionMethod.OFF,
            stringResource(R.string.autoplay_method_off),
            stringResource(R.string.autoplay_method_off_desc)
        ),
        Triple(
            AutoPlayQualitySelectionMethod.BY_SEED_PEERS,
            stringResource(R.string.autoplay_method_seeds),
            stringResource(R.string.autoplay_method_seeds_desc)
        ),
        Triple(
            AutoPlayQualitySelectionMethod.BY_QUALITY,
            stringResource(R.string.autoplay_method_quality),
            stringResource(R.string.autoplay_method_quality_desc)
        ),
        Triple(
            AutoPlayQualitySelectionMethod.DO_NOTHING,
            stringResource(R.string.autoplay_method_do_nothing),
            stringResource(R.string.autoplay_method_do_nothing_desc)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
    ) {
        SettingsSectionTitle(
            title = stringResource(R.string.next_episode_autoplay_title),
            isTv = isTv
        )
        Spacer(modifier = Modifier.height(4.dp))
        options.forEach { (method, title, support) ->
            val isSelected = selectedMethod == method
            if (isTv) {
                androidx.tv.material3.ListItem(
                    selected = isSelected,
                    enabled = true,
                    onClick = { onMethodSelected(method) },
                    headlineContent = {
                        androidx.tv.material3.Text(
                            text = title,
                            style = TvMaterialTheme.typography.bodyLarge
                        )
                    },
                    supportingContent = {
                        androidx.tv.material3.Text(
                            text = support,
                            style = TvMaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        androidx.tv.material3.RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusHighlight(shape = MaterialTheme.shapes.small)
                        .selectable(
                            selected = isSelected,
                            enabled = true,
                            onClick = { onMethodSelected(method) },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = support,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** TV preview of the settings screen. */
@Preview(name = "TV Light", showBackground = true, device = "id:tv_720p", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "TV Dark", showBackground = true, device = "id:tv_720p", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SettingsScreenTvPreview() {
    val isDark = isSystemInDarkTheme()
    val previewTheme = if (isDark) PreferenceManager.THEME_DARK else PreferenceManager.THEME_LIGHT
    TvLaTorrentolaTheme(themeMode = previewTheme) {
        SettingsScreenContent(
            uiState = SettingsUiState(
                voiceSystem = true,
                voiceSummary = true,
                voiceTranslation = false,
                theme = previewTheme,
                filteredLanguages = "en, es"
            ),
            userPhotoUrl = null,
            userName = "Martin Revert",
            userEmail = "martin@example.com",
            isTv = true,
            onBackClick = {},
            onLogoutClick = {},
            onDownloadsClick = {},
            onExternalMediaClick = {},
            onToggleVoiceSystem = {},
            onToggleVoiceSummary = {},
            onToggleVoiceTranslation = {},
            onToggleVibrator = {},
            onTogglePushEnabled = {},
            onSetTheme = {},
            onSetFilteredLanguages = {},
            onSetMinimumRating = {},
            onSetTorrentHandlingMode = {},
            onSetAutoPlayQualitySelectionMethod = {},
            onSetStorageLocation = {},
            onSaveOpenSubtitlesCredentials = { _, _ -> },
            onClearOpenSubtitlesCredentials = {}
        )
    }
}

/** Light and dark previews of the settings screen across form factors. */
@com.martinrevert.latorrentola.ui.theme.LightDarkPreviews
@Composable
fun SettingsScreenPreview() {
    val isDark = isSystemInDarkTheme()
    val previewTheme = if (isDark) PreferenceManager.THEME_DARK else PreferenceManager.THEME_LIGHT
    LaTorrentolaTheme(themeMode = previewTheme) {
        SettingsScreenContent(
            uiState = SettingsUiState(
                voiceSystem = true,
                voiceSummary = true,
                voiceTranslation = false,
                theme = previewTheme,
                filteredLanguages = "en, es"
            ),
            userPhotoUrl = null,
            userName = "Martin Revert",
            userEmail = "martin@example.com",
            isTv = false,
            onBackClick = {},
            onLogoutClick = {},
            onDownloadsClick = {},
            onExternalMediaClick = {},
            onToggleVoiceSystem = {},
            onToggleVoiceSummary = {},
            onToggleVoiceTranslation = {},
            onToggleVibrator = {},
            onTogglePushEnabled = {},
            onSetTheme = {},
            onSetFilteredLanguages = {},
            onSetMinimumRating = {},
            onSetTorrentHandlingMode = {},
            onSetAutoPlayQualitySelectionMethod = {},
            onSetStorageLocation = {},
            onSaveOpenSubtitlesCredentials = { _, _ -> },
            onClearOpenSubtitlesCredentials = {}
        )
    }
}
