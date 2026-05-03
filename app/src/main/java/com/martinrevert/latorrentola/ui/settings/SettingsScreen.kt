package com.martinrevert.latorrentola.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
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
            SettingsToggle(
                title = "Vibrar ante eventos",
                checked = uiState.vibrator,
                onCheckedChange = { viewModel.toggleVibrator(it) }
            )
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
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
