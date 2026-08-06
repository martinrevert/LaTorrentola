package com.martinrevert.latorrentola.ui.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.PreferenceManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || 
               context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.focusHighlight(shape = androidx.compose.foundation.shape.CircleShape)
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
            
            if (!isTv) {
                SettingsToggle(
                    title = "Vibrar ante eventos",
                    checked = uiState.vibrator,
                    onCheckedChange = { viewModel.toggleVibrator(it) }
                )
            }
            
            SettingsToggle(
                title = "Notificaciones Push",
                checked = uiState.pushEnabled,
                onCheckedChange = { viewModel.togglePushEnabled(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(text = "Tema", style = MaterialTheme.typography.titleMedium)
            
            ThemeSelector(
                selectedTheme = uiState.theme,
                onThemeSelected = { viewModel.setTheme(it) }
            )
            
            // Add extra space at the bottom for TV overscan and comfort
            Spacer(modifier = Modifier.height(32.dp))
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

@Composable
fun SettingsToggle(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
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
