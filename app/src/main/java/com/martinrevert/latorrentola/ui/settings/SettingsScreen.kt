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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class, ExperimentalHazeApi::class)
/** Collects settings state and presents the adaptive settings screen. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    userPhotoUrl: String?,
    userName: String?,
    userEmail: String?,
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onDownloadsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    
    SettingsScreenContent(
        uiState = uiState,
        userPhotoUrl = userPhotoUrl,
        userName = userName,
        userEmail = userEmail,
        isTv = isTv,
        onBackClick = onBackClick,
        onLogoutClick = onLogoutClick,
        onDownloadsClick = onDownloadsClick,
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
        onSaveOpenSubtitlesCredentials = { username, password ->
            viewModel.saveOpenSubtitlesCredentials(username, password)
        },
        onClearOpenSubtitlesCredentials = { viewModel.clearOpenSubtitlesCredentials() }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
/** Renders account, appearance, filtering, and accessibility settings. */
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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.focusHighlight(shape = CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(
                            R.string.back_desc))
                    }
                }
            )
        }
    ) { padding ->
        val unusedPadding = padding
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .consumeWindowInsets(unusedPadding)
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
                        UserSection(userPhotoUrl, userName, userEmail)
                        LogoutButton(isTv = isTv, onClick = onLogoutClick)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    Text(text = stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                    ThemeSelector(selectedTheme = uiState.theme, onThemeSelected = onSetTheme)
                    DownloadsManagerButton(isTv = isTv, onClick = onDownloadsClick)

                    Spacer(modifier = Modifier.weight(1f))

                    AppVersionInfo()
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
                        onMethodSelected = onSetAutoPlayQualitySelectionMethod
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
                    UserSection(userPhotoUrl, userName, userEmail)
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

                Text(text = stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                
                ThemeSelector(
                    selectedTheme = uiState.theme,
                    onThemeSelected = onSetTheme
                )
                DownloadsManagerButton(isTv = isTv, onClick = onDownloadsClick)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                TorrentModeSetting(
                    selectedMode = uiState.torrentHandlingMode,
                    onModeSelected = onSetTorrentHandlingMode,
                    isTv = isTv
                )
                AutoPlayQualitySetting(
                    selectedMethod = uiState.autoPlayQualitySelectionMethod,
                    onMethodSelected = onSetAutoPlayQualitySelectionMethod
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
                
                AppVersionInfo()
                
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
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
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
        Text(
            text = stringResource(R.string.torrent_mode_title),
            style = MaterialTheme.typography.titleMedium
        )
        options.forEach { (mode, title, support) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusHighlight(shape = MaterialTheme.shapes.small)
                    .selectable(
                        selected = selectedMode == mode,
                        enabled = true,
                        onClick = { onModeSelected(mode) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedMode == mode,
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

/**
 * Edits OpenSubtitles credentials and reports whether an encrypted account is configured.
 *
 * @param uiState Current credential status and username.
 * @param isTv Whether to use TV-native action buttons.
 * @param onSave Encrypts and stores the entered account credentials.
 * @param onClear Removes saved credentials.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun OpenSubtitlesCredentialsSetting(
    uiState: SettingsUiState,
    isTv: Boolean,
    onSave: (String, String) -> Unit,
    onClear: () -> Unit
) {
    var username by remember(uiState.openSubtitlesUsername) {
        mutableStateOf(uiState.openSubtitlesUsername)
    }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.opensubtitles_title),
            style = MaterialTheme.typography.titleMedium
        )
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
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
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
                        password = ""
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
                        },
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        androidx.tv.material3.Text(stringResource(R.string.opensubtitles_clear))
                    }
                }
            } else {
                androidx.compose.material3.Button(
                    onClick = {
                        onSave(username.trim(), password)
                        password = ""
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
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
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
private fun AppVersionInfo() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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



/** Presents the signed-in user's profile and account information. */
@Composable
fun UserSection(
    photoUrl: String?,
    name: String?,
    email: String?
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
                    .size(64.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = name?.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            Text(
                text = name ?: stringResource(com.martinrevert.latorrentola.R.string.default_user_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
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

@OptIn(ExperimentalMaterial3Api::class)
/** Allows selection between system, light, and dark appearance modes. */
@Composable
fun ThemeSelector(
    selectedTheme: Int,
    onThemeSelected: (Int) -> Unit
) {
    val options = listOf(
        stringResource(R.string.theme_system),
        stringResource(R.string.theme_light),
        stringResource(R.string.theme_dark)
    )
    val themeValues = listOf(
        PreferenceManager.THEME_SYSTEM,
        PreferenceManager.THEME_LIGHT,
        PreferenceManager.THEME_DARK
    )

    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth()
    ) {
        options.forEachIndexed { index, label ->
            val shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            SegmentedButton(
                shape = shape,
                onClick = { onThemeSelected(themeValues[index]) },
                selected = selectedTheme == themeValues[index],
                modifier = Modifier.focusHighlight(shape = shape)
            ) {
                Text(label)
            }
        }
    }
}

/** Presents the bounded minimum-rating preference as an adjustable control. */
@Composable
private fun MinimumRatingSetting(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.minimum_imdb_rating),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.minimum_imdb_rating_support),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val next = (Math.round((value - 0.1f) * 10.0f) / 10.0f).coerceAtLeast(PreferenceManager.MINIMUM_RATING_MIN)
                    onValueChange(next)
                },
                enabled = value > PreferenceManager.MINIMUM_RATING_MIN
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = stringResource(R.string.decrease_minimum_rating)
                )
            }
            val formattedValue = String.format(Locale.US, "%.1f", value)
            Text(
                text = stringResource(R.string.minimum_imdb_rating_value, formattedValue),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            IconButton(
                onClick = {
                    val next = (Math.round((value + 0.1f) * 10.0f) / 10.0f).coerceAtMost(PreferenceManager.MINIMUM_RATING_MAX)
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

@OptIn(ExperimentalTvMaterial3Api::class)
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
 */
@Composable
private fun AutoPlayQualitySetting(
    selectedMethod: AutoPlayQualitySelectionMethod,
    onMethodSelected: (AutoPlayQualitySelectionMethod) -> Unit
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
        Text(
            text = stringResource(R.string.next_episode_autoplay_title),
            style = MaterialTheme.typography.titleMedium
        )
        options.forEach { (method, title, support) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusHighlight(shape = MaterialTheme.shapes.small)
                    .selectable(
                        selected = selectedMethod == method,
                        enabled = true,
                        onClick = { onMethodSelected(method) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedMethod == method,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge
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

@Preview(name = "TV Light", showBackground = true, device = "id:tv_720p", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "TV Dark", showBackground = true, device = "id:tv_720p", uiMode = Configuration.UI_MODE_NIGHT_YES)
/** TV preview of the settings screen. */
@Composable
fun SettingsScreenTvPreview() {
    LaTorrentolaTheme {
        SettingsScreenContent(
            uiState = SettingsUiState(
                voiceSystem = true,
                voiceSummary = true,
                voiceTranslation = false,
                filteredLanguages = "en, es"
            ),
            userPhotoUrl = null,
            userName = "Martin Revert",
            userEmail = "martin@example.com",
            isTv = true,
            onBackClick = {},
            onLogoutClick = {},
            onDownloadsClick = {},
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
            onSaveOpenSubtitlesCredentials = { _, _ -> },
            onClearOpenSubtitlesCredentials = {}
        )
    }
}

@com.martinrevert.latorrentola.ui.theme.LightDarkPreviews
/** Light and dark previews of the settings screen across form factors. */
@Composable
fun SettingsScreenPreview() {
    LaTorrentolaTheme {
        SettingsScreenContent(
            uiState = SettingsUiState(
                voiceSystem = true,
                voiceSummary = true,
                voiceTranslation = false,
                filteredLanguages = "en, es"
            ),
            userPhotoUrl = null,
            userName = "Martin Revert",
            userEmail = "martin@example.com",
            isTv = false,
            onBackClick = {},
            onLogoutClick = {},
            onDownloadsClick = {},
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
            onSaveOpenSubtitlesCredentials = { _, _ -> },
            onClearOpenSubtitlesCredentials = {}
        )
    }
}
