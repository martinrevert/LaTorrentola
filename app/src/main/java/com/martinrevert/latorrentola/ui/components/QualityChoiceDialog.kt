package com.martinrevert.latorrentola.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent

@Composable
fun QualityChoiceDialog(
    onQualitySelected: (String) -> Unit,
    onDismiss: () -> Unit,
    releases: List<EztvTorrent>,
    isLoading: Boolean,
    error: String?
) {
    val qualities = if (isLoading) {
        emptyList()
    } else {
        releases
            .map { it.title }
            .map { extractQuality(it) }
            .filterNotNull()
            .distinct()
            .sortedBy { it.replace("p", "").toIntOrNull() ?: Int.MAX_VALUE }
    }

    var selectedQuality by remember(qualities) { mutableStateOf<String?>(qualities.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Quality for Next Episode",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        },
        text = {
            if (isLoading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(256.dp)
                        .height(128.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .wrapContentSize()
                            .align(Alignment.Center)
                    )
                }
            } else if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            } else if (qualities.isEmpty()) {
                Text(
                    text = "No qualities found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .width(256.dp)
                        .height(256.dp)
                ) {
                    items(qualities) { quality ->
                        QualityOptionRow(
                            quality = quality,
                            selected = selectedQuality == quality,
                            onClick = { selectedQuality = quality }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedQuality?.let { onQualitySelected(it) }
                },
                enabled = selectedQuality != null && !isLoading && qualities.isNotEmpty()
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun QualityOptionRow(
    quality: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(8.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                unselectedColor = MaterialTheme.colorScheme.onBackground.copy(0.2f),
                selectedColor = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = quality,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
    }
}

private fun extractQuality(title: String): String? {
    val lower = title.lowercase()
    return when {
        lower.contains("2160p") || lower.contains("4k") -> "2160p"
        lower.contains("1080p") -> "1080p"
        lower.contains("720p") -> "720p"
        lower.contains("hdtv") -> "HDTV"
        else -> null
    }
}
