package com.martinrevert.latorrentola.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.SurfaceDefaults as TvSurfaceDefaults
import androidx.tv.material3.Text as TvText
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.update.AppUpdateManifest
import com.martinrevert.latorrentola.network.ApkDownloadState
import com.martinrevert.latorrentola.network.AppUpdateErrorType
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/** Canonical Streaming Red accent used for progress indicators. */
private val StreamingRed = Color(0xFFE50914)

/**
 * Non-dismissible mandatory update dialog blocking application usage until the user downloads,
 * verifies, and installs the new release APK.
 *
 * Adapts to Handheld Material 3, narrow foldable viewports (< 360dp), and Android TV 10-foot UI
 * with D-pad focus trapping.
 */
@Composable
fun AppUpdateDialog(
    manifest: AppUpdateManifest,
    downloadState: ApkDownloadState,
    canInstallPackages: Boolean,
    onStartDownload: () -> Unit,
    onInstallApk: (File) -> Unit,
    onRequestInstallPermission: () -> Unit,
    themeMode: Int? = null
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }

    Dialog(
        onDismissRequest = { /* Mandatory update cannot be dismissed */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        if (isTv) {
            TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TvMaterialTheme.colorScheme.scrim.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    AppUpdateDialogTvCard(
                        manifest = manifest,
                        downloadState = downloadState,
                        canInstallPackages = canInstallPackages,
                        onStartDownload = onStartDownload,
                        onInstallApk = onInstallApk,
                        onRequestInstallPermission = onRequestInstallPermission
                    )
                }
            }
        } else {
            LaTorrentolaTheme(themeMode = effectiveThemeMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    AppUpdateDialogMobileCard(
                        manifest = manifest,
                        downloadState = downloadState,
                        canInstallPackages = canInstallPackages,
                        onStartDownload = onStartDownload,
                        onInstallApk = onInstallApk,
                        onRequestInstallPermission = onRequestInstallPermission
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppUpdateDialogTvCard(
    manifest: AppUpdateManifest,
    downloadState: ApkDownloadState,
    canInstallPackages: Boolean,
    onStartDownload: () -> Unit,
    onInstallApk: (File) -> Unit,
    onRequestInstallPermission: () -> Unit,
    actionButtonFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    val versionDisplay = manifest.versionName.ifBlank { "v${manifest.versionCode}" }

    val hasActionButton = downloadState is ApkDownloadState.Idle ||
        downloadState is ApkDownloadState.ReadyToInstall ||
        downloadState is ApkDownloadState.Error

    LaunchedEffect(downloadState::class, canInstallPackages) {
        if (hasActionButton) {
            delay(100.milliseconds)
            try {
                actionButtonFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    TvSurface(
        modifier = Modifier
            .widthIn(max = 540.dp)
            .padding(24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = TvSurfaceDefaults.colors(
            containerColor = TvMaterialTheme.colorScheme.surface,
            contentColor = TvMaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TvText(
                text = stringResource(R.string.app_update_required_title),
                style = TvMaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TvMaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            TvText(
                text = stringResource(R.string.app_update_required_message, versionDisplay),
                style = TvMaterialTheme.typography.bodyLarge,
                color = TvMaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            when (downloadState) {
                is ApkDownloadState.Idle -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    TvButton(
                        onClick = onStartDownload,
                        modifier = Modifier.focusRequester(actionButtonFocusRequester)
                    ) {
                        TvText(text = stringResource(R.string.app_update_action_download))
                    }
                }

                is ApkDownloadState.Downloading -> {
                    val downloadedMb = downloadState.bytesDownloaded.toFloat() / (1024f * 1024f)
                    val totalMb = (downloadState.totalBytes.coerceAtLeast(1L)).toFloat() / (1024f * 1024f)
                    val fraction = (downloadState.progressPercent / 100f).coerceIn(0f, 1f)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(TvMaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(8.dp)
                                    .background(StreamingRed)
                            )
                        }
                        TvText(
                            text = stringResource(
                                R.string.app_update_downloading_progress,
                                downloadState.progressPercent,
                                downloadedMb,
                                totalMb
                            ),
                            style = TvMaterialTheme.typography.bodyMedium,
                            color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is ApkDownloadState.VerifyingChecksum -> {
                    TvText(
                        text = stringResource(R.string.app_update_verifying_sha256),
                        style = TvMaterialTheme.typography.bodyMedium,
                        color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                is ApkDownloadState.ReadyToInstall -> {
                    TvText(
                        text = stringResource(
                            if (canInstallPackages) {
                                R.string.app_update_ready_to_install_message
                            } else {
                                R.string.app_update_permission_required_message
                            }
                        ),
                        style = TvMaterialTheme.typography.bodyMedium,
                        color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TvButton(
                        onClick = {
                            if (canInstallPackages) {
                                onInstallApk(downloadState.apkFile)
                            } else {
                                onRequestInstallPermission()
                            }
                        },
                        modifier = Modifier.focusRequester(actionButtonFocusRequester)
                    ) {
                        TvText(
                            text = stringResource(
                                if (canInstallPackages) {
                                    R.string.app_update_action_install
                                } else {
                                    R.string.app_update_action_grant_permission
                                }
                            )
                        )
                    }
                }

                is ApkDownloadState.Error -> {
                    val errorMsgRes = when (downloadState.errorType) {
                        AppUpdateErrorType.CHECKSUM_MISMATCH -> R.string.app_update_error_checksum
                        AppUpdateErrorType.NETWORK_OR_IO -> R.string.app_update_error_download
                    }
                    TvText(
                        text = stringResource(errorMsgRes),
                        style = TvMaterialTheme.typography.bodyMedium,
                        color = TvMaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TvButton(
                        onClick = onStartDownload,
                        modifier = Modifier.focusRequester(actionButtonFocusRequester)
                    ) {
                        TvText(text = stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppUpdateDialogMobileCard(
    manifest: AppUpdateManifest,
    downloadState: ApkDownloadState,
    canInstallPackages: Boolean,
    onStartDownload: () -> Unit,
    onInstallApk: (File) -> Unit,
    onRequestInstallPermission: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isConstrainedWidth = configuration.screenWidthDp < 360
    val outerPadding = if (isConstrainedWidth) 12.dp else 24.dp
    val innerPadding = if (isConstrainedWidth) 16.dp else 24.dp
    val versionDisplay = manifest.versionName.ifBlank { "v${manifest.versionCode}" }

    Card(
        modifier = Modifier
            .widthIn(max = 440.dp)
            .padding(outerPadding),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.app_update_required_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.app_update_required_message, versionDisplay),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            when (downloadState) {
                is ApkDownloadState.Idle -> {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onStartDownload,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.app_update_action_download),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                is ApkDownloadState.Downloading -> {
                    val downloadedMb = downloadState.bytesDownloaded.toFloat() / (1024f * 1024f)
                    val totalMb = (downloadState.totalBytes.coerceAtLeast(1L)).toFloat() / (1024f * 1024f)
                    val fraction = (downloadState.progressPercent / 100f).coerceIn(0f, 1f)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = StreamingRed,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            text = stringResource(
                                R.string.app_update_downloading_progress,
                                downloadState.progressPercent,
                                downloadedMb,
                                totalMb
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is ApkDownloadState.VerifyingChecksum -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = StreamingRed,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.app_update_verifying_sha256),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is ApkDownloadState.ReadyToInstall -> {
                    Text(
                        text = stringResource(
                            if (canInstallPackages) {
                                R.string.app_update_ready_to_install_message
                            } else {
                                R.string.app_update_permission_required_message
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = {
                            if (canInstallPackages) {
                                onInstallApk(downloadState.apkFile)
                            } else {
                                onRequestInstallPermission()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(
                                if (canInstallPackages) {
                                    R.string.app_update_action_install
                                } else {
                                    R.string.app_update_action_grant_permission
                                }
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                is ApkDownloadState.Error -> {
                    val errorMsgRes = when (downloadState.errorType) {
                        AppUpdateErrorType.CHECKSUM_MISMATCH -> R.string.app_update_error_checksum
                        AppUpdateErrorType.NETWORK_OR_IO -> R.string.app_update_error_download
                    }
                    Text(
                        text = stringResource(errorMsgRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onStartDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.retry),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Mobile Idle Dark", showBackground = true, widthDp = 412)
@Preview(name = "Foldable Cover Idle", showBackground = true, widthDp = 320)
@Composable
private fun AppUpdateDialogMobileIdlePreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        AppUpdateDialogMobileCard(
            manifest = AppUpdateManifest(
                versionCode = 50200,
                versionName = "3.0.20200-main-release"
            ),
            downloadState = ApkDownloadState.Idle,
            canInstallPackages = true,
            onStartDownload = {},
            onInstallApk = {},
            onRequestInstallPermission = {}
        )
    }
}

@Preview(name = "Mobile Idle Light", showBackground = true, widthDp = 412)
@Composable
private fun AppUpdateDialogMobileLightPreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_LIGHT) {
        AppUpdateDialogMobileCard(
            manifest = AppUpdateManifest(
                versionCode = 50200,
                versionName = "3.0.20200-main-release"
            ),
            downloadState = ApkDownloadState.Idle,
            canInstallPackages = true,
            onStartDownload = {},
            onInstallApk = {},
            onRequestInstallPermission = {}
        )
    }
}

@Preview(name = "Mobile Downloading", showBackground = true, widthDp = 340)
@Composable
private fun AppUpdateDialogMobileDownloadingPreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        AppUpdateDialogMobileCard(
            manifest = AppUpdateManifest(
                versionCode = 50200,
                versionName = "3.0.20200-main-release"
            ),
            downloadState = ApkDownloadState.Downloading(
                bytesDownloaded = 42L * 1024L * 1024L,
                totalBytes = 84L * 1024L * 1024L,
                progressPercent = 50
            ),
            canInstallPackages = true,
            onStartDownload = {},
            onInstallApk = {},
            onRequestInstallPermission = {}
        )
    }
}

@Preview(name = "TV Idle", showBackground = true, widthDp = 960, heightDp = 540)
@Composable
private fun AppUpdateDialogTvPreview() {
    TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        AppUpdateDialogTvCard(
            manifest = AppUpdateManifest(
                versionCode = 50200,
                versionName = "3.0.20200-main-release"
            ),
            downloadState = ApkDownloadState.Idle,
            canInstallPackages = true,
            onStartDownload = {},
            onInstallApk = {},
            onRequestInstallPermission = {}
        )
    }
}
