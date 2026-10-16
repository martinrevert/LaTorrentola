@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Reusable dialog providing options to manually mark content as completed or uncompleted.
 *
 * Propagates user theme preferences and provides D-pad navigation trapping on TV devices.
 *
 * @param title Title text for the dialog (e.g. "Gestionar temporada").
 * @param option1Text Label for option 1 (e.g. "Marcar completada").
 * @param option2Text Label for option 2 (e.g. "Marcar no completada").
 * @param onOption1Selected Callback when option 1 is chosen.
 * @param onOption2Selected Callback when option 2 is chosen.
 * @param onDismissRequest Callback when dialog is dismissed/cancelled.
 * @param themeMode User's theme selection mode from [PreferenceManager], or null to resolve automatically.
 */
@Composable
fun WatchProgressOptionDialog(
    title: String,
    option1Text: String,
    option2Text: String,
    onOption1Selected: () -> Unit,
    onOption2Selected: () -> Unit,
    onDismissRequest: () -> Unit,
    themeMode: Int? = null
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }

    if (isTv) {
        TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
            val option1FocusRequester = remember { FocusRequester() }
            var isDialogReady by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                delay(350.milliseconds)
                isDialogReady = true
                try {
                    option1FocusRequester.requestFocus()
                } catch (_: Exception) {
                    // Ignore focus request errors if not yet attached
                }
            }

            Dialog(
                onDismissRequest = onDismissRequest,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                TvSurface(
                    shape = RoundedCornerShape(16.dp),
                    colors = androidx.tv.material3.SurfaceDefaults.colors(
                        containerColor = TvMaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f),
                        contentColor = TvMaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .width(420.dp)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = title,
                            style = TvMaterialTheme.typography.titleLarge,
                            color = TvMaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        TvButton(
                            onClick = {
                                if (isDialogReady) {
                                    onOption1Selected()
                                    onDismissRequest()
                                }
                            },
                            colors = TvButtonDefaults.colors(
                                containerColor = TvMaterialTheme.colorScheme.primary,
                                contentColor = TvMaterialTheme.colorScheme.onPrimary,
                                focusedContainerColor = TvMaterialTheme.colorScheme.primaryContainer,
                                focusedContentColor = TvMaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(option1FocusRequester)
                        ) {
                            Text(text = option1Text)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TvButton(
                            onClick = {
                                if (isDialogReady) {
                                    onOption2Selected()
                                    onDismissRequest()
                                }
                            },
                            colors = TvButtonDefaults.colors(
                                containerColor = TvMaterialTheme.colorScheme.primary,
                                contentColor = TvMaterialTheme.colorScheme.onPrimary,
                                focusedContainerColor = TvMaterialTheme.colorScheme.primaryContainer,
                                focusedContentColor = TvMaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = option2Text)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TvButton(
                            onClick = {
                                if (isDialogReady) {
                                    onDismissRequest()
                                }
                            },
                            colors = TvButtonDefaults.colors(
                                containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                                contentColor = TvMaterialTheme.colorScheme.onSurfaceVariant,
                                focusedContainerColor = TvMaterialTheme.colorScheme.secondaryContainer,
                                focusedContentColor = TvMaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    }
                }
            }
        }
    } else {
        LaTorrentolaTheme(themeMode = effectiveThemeMode) {
            AlertDialog(
                onDismissRequest = onDismissRequest,
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                onOption1Selected()
                                onDismissRequest()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(text = option1Text)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                onOption2Selected()
                                onDismissRequest()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(text = option2Text)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(R.string.cancel))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f)
            )
        }
    }
}
