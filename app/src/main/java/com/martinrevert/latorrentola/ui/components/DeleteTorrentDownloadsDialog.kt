package com.martinrevert.latorrentola.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice
import kotlinx.coroutines.delay

/**
 * Dialog prompting the user to delete downloaded torrent media files and subtitles upon playback completion.
 *
 * @param mediaTitle User-visible title of the finished movie or episode.
 * @param onConfirmDelete Callback triggered when the user confirms deleting the torrent content.
 * @param onKeep Callback triggered when the user decides to keep the downloaded files.
 * @param onDismiss Callback when the dialog is dismissed via back gesture.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DeleteTorrentDownloadsDialog(
    mediaTitle: String,
    onConfirmDelete: () -> Unit,
    onKeep: () -> Unit,
    onDismiss: () -> Unit = onKeep,
    themeMode: Int? = null
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }
    val deleteButtonFocusRequester = remember { FocusRequester() }

    BackHandler(onBack = onDismiss)

    if (isTv) {
        LaunchedEffect(Unit) {
            delay(100)
            try {
                deleteButtonFocusRequester.requestFocus()
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
                        text = stringResource(R.string.delete_torrent_dialog_title),
                        style = if (isTv) androidx.tv.material3.MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    if (mediaTitle.isNotBlank()) {
                        Text(
                            text = mediaTitle,
                            style = if (isTv) androidx.tv.material3.MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Text(
                        text = stringResource(R.string.delete_torrent_dialog_message),
                        style = if (isTv) androidx.tv.material3.MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isTv) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.tv.material3.Button(
                                onClick = onConfirmDelete,
                                modifier = Modifier.focusRequester(deleteButtonFocusRequester)
                            ) {
                                androidx.tv.material3.Text(text = stringResource(R.string.delete_torrent_dialog_confirm))
                            }

                            androidx.tv.material3.OutlinedButton(
                                onClick = onKeep
                            ) {
                                androidx.tv.material3.Text(text = stringResource(R.string.delete_torrent_dialog_keep))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onKeep) {
                                Text(
                                    text = stringResource(R.string.delete_torrent_dialog_keep),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.material3.Button(
                                onClick = onConfirmDelete,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text(text = stringResource(R.string.delete_torrent_dialog_confirm))
                            }
                        }
                    }
                }
            }

            if (isTv) {
                TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 500.dp)
                            .padding(24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = SurfaceDefaults.colors(
                            containerColor = androidx.tv.material3.MaterialTheme.colorScheme.surface,
                            contentColor = androidx.tv.material3.MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        dialogBody()
                    }
                }
            } else {
                LaTorrentolaTheme(themeMode = effectiveThemeMode) {
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

@Preview(name = "Mobile Delete Dialog Preview")
@Composable
private fun DeleteTorrentDownloadsDialogMobilePreview() {
    LaTorrentolaTheme {
        DeleteTorrentDownloadsDialog(
            mediaTitle = "Inception (2010)",
            onConfirmDelete = {},
            onKeep = {}
        )
    }
}

@Preview(name = "TV Delete Dialog Preview")
@Composable
private fun DeleteTorrentDownloadsDialogTvPreview() {
    TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        DeleteTorrentDownloadsDialog(
            mediaTitle = "Breaking Bad S01E01",
            onConfirmDelete = {},
            onKeep = {},
            themeMode = PreferenceManager.THEME_DARK
        )
    }
}
