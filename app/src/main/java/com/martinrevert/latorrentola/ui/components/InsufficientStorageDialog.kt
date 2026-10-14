package com.martinrevert.latorrentola.ui.components

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.utils.isTvDevice
import kotlinx.coroutines.delay

/**
 * Mandatory alert dialog informing the user that free disk space is less than 1 GB and blocking download until space is freed.
 *
 * @param availableBytes Currently available disk space on the Android device in bytes.
 * @param requiredBytes Required storage space in bytes.
 * @param onDismiss Callback triggered when the user clicks "Entendido" / "OK".
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun InsufficientStorageDialog(
    availableBytes: Long,
    requiredBytes: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val okButtonFocusRequester = remember { FocusRequester() }

    val formattedAvailable = remember(availableBytes) {
        if (availableBytes <= 0) "0 MB"
        else Formatter.formatFileSize(context, availableBytes)
    }

    BackHandler(onBack = onDismiss)

    if (isTv) {
        LaunchedEffect(Unit) {
            delay(100)
            try {
                okButtonFocusRequester.requestFocus()
            } catch (_: Exception) { }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            contentAlignment = Alignment.Center
        ) {
            val dialogBody: @Composable () -> Unit = {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.insufficient_storage_dialog_title),
                        style = if (isTv) androidx.tv.material3.MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = stringResource(R.string.insufficient_storage_dialog_message, formattedAvailable),
                        style = if (isTv) androidx.tv.material3.MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isTv) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.tv.material3.Button(
                                onClick = onDismiss,
                                modifier = Modifier.focusRequester(okButtonFocusRequester)
                            ) {
                                androidx.tv.material3.Text(text = stringResource(R.string.insufficient_storage_dialog_confirm))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Button(
                                onClick = onDismiss
                            ) {
                                Text(text = stringResource(R.string.insufficient_storage_dialog_confirm))
                            }
                        }
                    }
                }
            }

            if (isTv) {
                TvLaTorrentolaTheme {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 500.dp)
                            .padding(24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = SurfaceDefaults.colors(
                            containerColor = androidx.tv.material3.MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        dialogBody()
                    }
                }
            } else {
                LaTorrentolaTheme {
                    Card(
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .padding(24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        dialogBody()
                    }
                }
            }
        }
    }
}

@Preview(name = "Mobile Insufficient Storage Preview")
@Composable
private fun InsufficientStorageDialogMobilePreview() {
    LaTorrentolaTheme {
        InsufficientStorageDialog(
            availableBytes = 500_000_000L,
            requiredBytes = 1_000_000_000L,
            onDismiss = {}
        )
    }
}

@Preview(name = "TV Insufficient Storage Preview")
@Composable
private fun InsufficientStorageDialogTvPreview() {
    TvLaTorrentolaTheme {
        InsufficientStorageDialog(
            availableBytes = 250_000_000L,
            requiredBytes = 1_000_000_000L,
            onDismiss = {}
        )
    }
}
