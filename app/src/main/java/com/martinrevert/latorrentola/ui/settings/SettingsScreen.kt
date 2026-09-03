package com.martinrevert.latorrentola.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    userPhotoUrl: String?,
    userName: String?,
    userEmail: String?,
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isRunningOnTv = remember(context) { context.isTvDevice() }
    
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.focusHighlight(shape = CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (userPhotoUrl != null || userName != null) {
                UserSection(userPhotoUrl, userName, userEmail)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            SettingsToggle(
                title = "Emitir guias por voz (TTS)",
                checked = uiState.voiceSystem,
                onCheckedChange = { viewModel.toggleVoiceSystem(it) }
            )
            SettingsToggle(
                title = "Sumario de peliculas por voz",
                checked = uiState.voiceSummary,
                onCheckedChange = { viewModel.toggleVoiceSummary(it) }
            )
            SettingsToggle(
                title = "Traducir sumario de peliculas",
                checked = uiState.voiceTranslation,
                enabled = uiState.voiceSummary,
                onCheckedChange = { viewModel.toggleVoiceTranslation(it) }
            )
            
            if (!isRunningOnTv) {
                SettingsToggle(
                    title = "Vibrar ante eventos",
                    checked = uiState.vibrator,
                    onCheckedChange = { viewModel.toggleVibrator(it) }
                )
                
                SettingsToggle(
                    title = "Notificaciones Push",
                    checked = uiState.pushEnabled,
                    onCheckedChange = { viewModel.togglePushEnabled(it) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(text = "Tema", style = MaterialTheme.typography.titleMedium)
            
            ThemeSelector(
                selectedTheme = uiState.theme,
                onThemeSelected = { viewModel.setTheme(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            OutlinedTextField(
                value = uiState.filteredLanguages,
                onValueChange = { viewModel.setFilteredLanguages(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusHighlight(shape = OutlinedTextFieldDefaults.shape),
                label = { Text("Filtrar Idiomas") },
                placeholder = { Text("cn, fr, hi...") },
                supportingText = {
                    Text("Excluir películas en estos idiomas (ej: cn, fr, hi)")
                },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isRunningOnTv) {
                androidx.tv.material3.Button(
                    onClick = onLogoutClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    androidx.tv.material3.Text(
                        text = "Cerrar Sesión",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Button(
                    onClick = onLogoutClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusHighlight(shape = ButtonDefaults.shape),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Cerrar Sesión")
                }
            }
            
            // Add extra space at the bottom for TV overscan and comfort
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

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
                contentDescription = "User profile",
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
                text = name ?: "Usuario",
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
@Composable
fun ThemeSelector(
    selectedTheme: Int,
    onThemeSelected: (Int) -> Unit
) {
    val options = listOf("Sistema", "Claro", "Oscuro")
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

@OptIn(ExperimentalTvMaterial3Api::class)
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
