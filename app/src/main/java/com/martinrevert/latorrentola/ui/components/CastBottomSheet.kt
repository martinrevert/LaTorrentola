@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.mediarouter.media.MediaRouter
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ClickableSurfaceDefaults as TvClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.IconButton as TvIconButton
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.SurfaceDefaults as TvSurfaceDefaults
import androidx.tv.material3.Text as TvText
import coil3.compose.AsyncImage
import com.google.android.gms.cast.CastDevice
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.LocalNetworkPermissionHelper
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Immutable UI representation of a discovered Cast route for rendering and previews. */
internal data class CastRouteUiModel(
    val id: String,
    val name: String,
    val subtitle: String?,
    val isSpeaker: Boolean,
    val isConnecting: Boolean = false,
    val volume: Int = 0,
    val volumeMax: Int = 1,
    val hasVariableVolume: Boolean = false
)

/**
 * YouTube Music-style Cast Bottom Sheet for discovering devices,
 * controlling remote volume, mini-player preview, and transferring playback back to phone.
 *
 * Enforces Android 17 (API 37) `ACCESS_LOCAL_NETWORK` permission gating prior to registering
 * `MediaRouter` discovery callbacks, preventing the Android 17 `MediaRouter2` (#581018323)
 * stale empty-route state when runtime permission is granted mid-session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastBottomSheet(
    onDismissRequest: () -> Unit,
    mediaTitle: String = "",
    mediaSubtitle: String = "",
    posterUrl: String? = null,
    player: Player? = null,
    themeMode: Int? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isTv = remember(context) { context.isTvDevice() }
    val preferenceManager = remember(context) { PreferenceManager(context) }
    val effectiveThemeMode = remember(preferenceManager, themeMode) {
        themeMode ?: preferenceManager.getTheme()
    }
    val mediaRouter = remember(context) { MediaRouter.getInstance(context) }
    val selector = remember { LocalNetworkPermissionHelper.buildCastMediaRouteSelector() }

    var hasLocalNetworkPermission by remember {
        mutableStateOf(LocalNetworkPermissionHelper.hasLocalNetworkPermission(context))
    }
    var hasRequestedPermissionBefore by remember {
        mutableStateOf(preferenceManager.getLocalNetworkPermissionRequested())
    }
    var shouldShowRationale by remember {
        mutableStateOf(LocalNetworkPermissionHelper.shouldShowRationale(context))
    }
    var discoveryRestartKey by remember { mutableIntStateOf(0) }
    var isRestartingDiscovery by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val grantedNow = granted || LocalNetworkPermissionHelper.hasLocalNetworkPermission(context)
        preferenceManager.setLocalNetworkPermissionRequested(!grantedNow)
        hasRequestedPermissionBefore = !grantedNow
        shouldShowRationale = LocalNetworkPermissionHelper.shouldShowRationale(context)
        val wasMissing = !hasLocalNetworkPermission
        hasLocalNetworkPermission = grantedNow
        if (grantedNow && wasMissing) {
            discoveryRestartKey++
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val grantedNow = LocalNetworkPermissionHelper.hasLocalNetworkPermission(context)
                if (grantedNow) {
                    preferenceManager.setLocalNetworkPermissionRequested(false)
                    hasRequestedPermissionBefore = false
                }
                shouldShowRationale = LocalNetworkPermissionHelper.shouldShowRationale(context)
                if (grantedNow != hasLocalNetworkPermission) {
                    hasLocalNetworkPermission = grantedNow
                    if (grantedNow) {
                        discoveryRestartKey++
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(discoveryRestartKey) {
        if (discoveryRestartKey > 0) {
            // Brief gap guarantees MediaRoute2Provider drops global callback count to 0
            // before re-registering with active scan (mitigates Android 17 Issue #581018323).
            isRestartingDiscovery = true
            delay(150.milliseconds)
            isRestartingDiscovery = false
        }
    }

    var routes by remember { mutableStateOf(emptyList<MediaRouter.RouteInfo>()) }
    var selectedRoute by remember { mutableStateOf(mediaRouter.selectedRoute) }

    fun updateState() {
        routes = mediaRouter.routes.filter { it.matchesSelector(selector) && !it.isDefault }
        selectedRoute = mediaRouter.selectedRoute
    }

    DisposableEffect(hasLocalNetworkPermission, isRestartingDiscovery) {
        if (!hasLocalNetworkPermission || isRestartingDiscovery) {
            routes = emptyList()
            selectedRoute = mediaRouter.selectedRoute
            return@DisposableEffect onDispose {}
        }

        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                updateState()
            }

            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                updateState()
            }

            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                updateState()
            }

            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
                updateState()
            }

            override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
                updateState()
            }
        }
        val flags = LocalNetworkPermissionHelper.resolveDiscoveryCallbackFlags(
            hasPermission = true,
            activeScan = true
        )
        mediaRouter.addCallback(selector, callback, flags)
        updateState()
        onDispose {
            mediaRouter.removeCallback(callback)
        }
    }

    var isPlayerPlaying by remember(player) { mutableStateOf(player?.isPlaying == true) }
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlayerPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val isCasting = !selectedRoute.isDefault
    var showRescanHint by remember { mutableStateOf(false) }
    LaunchedEffect(hasLocalNetworkPermission, isRestartingDiscovery, routes.isEmpty(), isCasting) {
        if (hasLocalNetworkPermission && !isRestartingDiscovery && !isCasting && routes.isEmpty()) {
            showRescanHint = false
            delay(8.seconds)
            showRescanHint = true
        } else {
            showRescanHint = false
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var connectingRouteId by remember { mutableStateOf<String?>(null) }

    val selectedRouteUi = remember(selectedRoute, connectingRouteId) {
        if (selectedRoute.isDefault) null else selectedRoute.toUiModel(connectingRouteId)
    }
    val routeUiModels = remember(routes, connectingRouteId) {
        routes.map { it.toUiModel(connectingRouteId) }
    }

    val shouldOpenAppSettings = LocalNetworkPermissionHelper.shouldOpenAppSettings(
        hasRequestedBefore = hasRequestedPermissionBefore,
        shouldShowRationale = shouldShowRationale
    )

    val sheetBody: @Composable () -> Unit = {
        CastBottomSheetContent(
            isTv = isTv,
            hasLocalNetworkPermission = hasLocalNetworkPermission,
            shouldOpenAppSettings = shouldOpenAppSettings,
            showRescanHint = showRescanHint,
            isCasting = isCasting,
            selectedRoute = selectedRouteUi,
            routes = routeUiModels,
            mediaTitle = mediaTitle,
            mediaSubtitle = mediaSubtitle,
            posterUrl = posterUrl,
            hasPlayerControls = player != null,
            isPlayerPlaying = isPlayerPlaying,
            onTogglePlayPause = {
                player?.let { activePlayer ->
                    if (isPlayerPlaying) activePlayer.pause() else activePlayer.play()
                }
            },
            onSetVolume = { targetVol ->
                selectedRoute.requestSetVolume(targetVol)
            },
            onStopStreaming = {
                mediaRouter.selectRoute(mediaRouter.defaultRoute)
                onDismissRequest()
            },
            onSelectRoute = { routeUi ->
                routes.firstOrNull { it.id == routeUi.id }?.let { targetRoute ->
                    connectingRouteId = targetRoute.id
                    onDismissRequest()
                    mediaRouter.selectRoute(targetRoute)
                }
            },
            onRequestLocalNetworkPermission = {
                if (shouldOpenAppSettings) {
                    LocalNetworkPermissionHelper.openAppSettings(context)
                } else {
                    permissionLauncher.launch(LocalNetworkPermissionHelper.PERMISSION_ACCESS_LOCAL_NETWORK)
                }
            },
            onRescanDevices = {
                showRescanHint = false
                discoveryRestartKey++
            }
        )
    }

    if (isTv) {
        TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                sheetBody()
            }
        }
    } else {
        LaTorrentolaTheme(themeMode = effectiveThemeMode) {
            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                sheetBody()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun CastBottomSheetContent(
    isTv: Boolean,
    hasLocalNetworkPermission: Boolean,
    shouldOpenAppSettings: Boolean,
    showRescanHint: Boolean,
    isCasting: Boolean,
    selectedRoute: CastRouteUiModel?,
    routes: List<CastRouteUiModel>,
    mediaTitle: String,
    mediaSubtitle: String,
    posterUrl: String?,
    hasPlayerControls: Boolean,
    isPlayerPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSetVolume: (Int) -> Unit,
    onStopStreaming: () -> Unit,
    onSelectRoute: (CastRouteUiModel) -> Unit,
    onRequestLocalNetworkPermission: () -> Unit,
    onRescanDevices: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isCasting && selectedRoute != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cast_bottom_sheet_playing_header),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (mediaTitle.isNotBlank() || hasPlayerControls) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!posterUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = posterUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(48.dp)
                                            .height(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .width(48.dp)
                                            .height(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Movie,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mediaTitle.ifBlank { stringResource(R.string.cast_bottom_sheet_title) },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sub = if (mediaSubtitle.isNotBlank()) mediaSubtitle else selectedRoute.name
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (hasPlayerControls) {
                                    if (isTv) {
                                        TvIconButton(
                                            onClick = onTogglePlayPause,
                                            modifier = Modifier.focusHighlight(shape = RoundedCornerShape(12.dp))
                                        ) {
                                            TvIcon(
                                                imageVector = if (isPlayerPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlayerPlaying) stringResource(R.string.pause_desc) else stringResource(R.string.play_desc),
                                                tint = TvMaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(onClick = onTogglePlayPause) {
                                            Icon(
                                                imageVector = if (isPlayerPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlayerPlaying) stringResource(R.string.pause_desc) else stringResource(R.string.play_desc),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CastConnected,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = selectedRoute.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isPlayerPlaying) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (!selectedRoute.subtitle.isNullOrBlank()) {
                                Text(
                                    text = selectedRoute.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (selectedRoute.hasVariableVolume) {
                        val haptic = LocalHapticFeedback.current
                        val currentVol = selectedRoute.volume
                        val maxVol = selectedRoute.volumeMax.coerceAtLeast(1)
                        val volPct = ((currentVol.toFloat() / maxVol) * 100).toInt()
                        var sliderValue by remember(currentVol) {
                            mutableFloatStateOf(currentVol.toFloat())
                        }
                        var lastHapticStep by remember { mutableIntStateOf(currentVol) }
                        var preMuteVolume by remember { mutableIntStateOf(if (currentVol > 0) currentVol else maxVol / 2) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val muteIcon = when {
                                currentVol == 0 -> Icons.AutoMirrored.Filled.VolumeOff
                                volPct < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                                else -> Icons.AutoMirrored.Filled.VolumeUp
                            }
                            val muteDescription = if (currentVol == 0) {
                                stringResource(R.string.cast_unmute)
                            } else {
                                stringResource(R.string.cast_mute)
                            }
                            val toggleMute = {
                                if (currentVol > 0) {
                                    preMuteVolume = currentVol
                                    onSetVolume(0)
                                } else {
                                    onSetVolume(preMuteVolume.coerceAtLeast(1))
                                }
                            }
                            if (isTv) {
                                TvIconButton(
                                    onClick = toggleMute,
                                    modifier = Modifier.focusHighlight(shape = RoundedCornerShape(12.dp))
                                ) {
                                    TvIcon(
                                        imageVector = muteIcon,
                                        contentDescription = muteDescription,
                                        tint = TvMaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                IconButton(onClick = toggleMute) {
                                    Icon(
                                        imageVector = muteIcon,
                                        contentDescription = muteDescription,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            val volumeStep = (maxVol / 20f).coerceAtLeast(1f)
                            Slider(
                                value = sliderValue,
                                onValueChange = { newValue ->
                                    sliderValue = newValue
                                    val targetVol = newValue.toInt()
                                    if (targetVol != lastHapticStep) {
                                        lastHapticStep = targetVol
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onSetVolume(targetVol)
                                },
                                valueRange = 0f..maxVol.toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (isTv) {
                                            Modifier
                                                .focusHighlight(shape = RoundedCornerShape(12.dp))
                                                .onKeyEvent { keyEvent ->
                                                    if (keyEvent.type != KeyEventType.KeyDown) {
                                                        return@onKeyEvent false
                                                    }
                                                    val delta = when (keyEvent.key) {
                                                        Key.DirectionLeft -> -volumeStep
                                                        Key.DirectionRight -> volumeStep
                                                        else -> return@onKeyEvent false
                                                    }
                                                    val updated = (sliderValue + delta).coerceIn(0f, maxVol.toFloat())
                                                    sliderValue = updated
                                                    val targetVol = updated.roundToInt()
                                                    if (targetVol != lastHapticStep) {
                                                        lastHapticStep = targetVol
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                    onSetVolume(targetVol)
                                                    true
                                                }
                                        } else {
                                            Modifier
                                        }
                                    )
                            )

                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$volPct%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(42.dp)
                            )
                        }
                    }
                }
            }

            item {
                if (isTv) {
                    TvOutlinedButton(
                        onClick = onStopStreaming,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .focusHighlight(shape = RoundedCornerShape(12.dp))
                    ) {
                        TvIcon(
                            imageVector = Icons.AutoMirrored.Filled.StopScreenShare,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TvText(
                            text = stringResource(R.string.cast_stop_streaming),
                            style = TvMaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onStopStreaming,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.StopScreenShare,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.cast_stop_streaming),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            val otherRoutes = routes.filter { it.id != selectedRoute.id }
            if (otherRoutes.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.cast_bottom_sheet_other_devices),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
                items(otherRoutes, key = { it.id }) { route ->
                    CastDeviceRow(
                        isTv = isTv,
                        route = route,
                        onClick = { onSelectRoute(route) }
                    )
                }
            }
        } else if (!hasLocalNetworkPermission) {
            item(key = "cast_local_network_permission_card") {
                LocalNetworkCastPermissionCard(
                    isTv = isTv,
                    shouldOpenAppSettings = shouldOpenAppSettings,
                    onGrantClick = onRequestLocalNetworkPermission
                )
            }
        } else {
            val suggested = routes.take(3)
            val others = routes.drop(3)

            if (suggested.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.cast_bottom_sheet_suggestions),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
                items(suggested, key = { it.id }) { route ->
                    CastDeviceRow(
                        isTv = isTv,
                        route = route,
                        onClick = { onSelectRoute(route) }
                    )
                }
                if (others.isNotEmpty()) {
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            if (others.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.cast_bottom_sheet_other_devices),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
                items(others, key = { it.id }) { route ->
                    CastDeviceRow(
                        isTv = isTv,
                        route = route,
                        onClick = { onSelectRoute(route) }
                    )
                }
            }

            if (routes.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (showRescanHint) {
                            if (isTv) {
                                TvIcon(
                                    imageVector = Icons.Default.WifiFind,
                                    contentDescription = null,
                                    tint = TvMaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.WifiFind,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = if (isTv) TvMaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        val emptyText = if (showRescanHint) {
                            stringResource(R.string.cast_no_devices_found_hint)
                        } else {
                            stringResource(R.string.cast_searching_devices)
                        }
                        if (isTv) {
                            TvText(
                                text = emptyText,
                                style = TvMaterialTheme.typography.bodyLarge,
                                color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = emptyText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (showRescanHint) {
                            Spacer(modifier = Modifier.height(16.dp))
                            if (isTv) {
                                TvOutlinedButton(
                                    onClick = onRescanDevices,
                                    modifier = Modifier.focusHighlight(shape = RoundedCornerShape(10.dp))
                                ) {
                                    TvIcon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TvText(text = stringResource(R.string.cast_rescan_devices))
                                }
                            } else {
                                OutlinedButton(onClick = onRescanDevices) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = stringResource(R.string.cast_rescan_devices))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LocalNetworkCastPermissionCard(
    isTv: Boolean,
    shouldOpenAppSettings: Boolean,
    onGrantClick: () -> Unit
) {
    val buttonLabel = if (shouldOpenAppSettings) {
        stringResource(R.string.local_network_permission_settings_button)
    } else {
        stringResource(R.string.local_network_permission_grant_button)
    }

    if (isTv) {
        TvSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            shape = RoundedCornerShape(14.dp),
            colors = TvSurfaceDefaults.colors(
                containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                contentColor = TvMaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TvIcon(
                    imageVector = Icons.Default.WifiFind,
                    contentDescription = null,
                    tint = TvMaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                TvText(
                    text = stringResource(R.string.local_network_permission_cast_title),
                    style = TvMaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TvMaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                TvText(
                    text = stringResource(R.string.local_network_permission_cast_desc),
                    style = TvMaterialTheme.typography.bodyMedium,
                    color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                TvButton(
                    onClick = onGrantClick,
                    modifier = Modifier.focusHighlight(shape = RoundedCornerShape(10.dp))
                ) {
                    TvText(text = buttonLabel)
                }
            }
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.WifiFind,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.local_network_permission_cast_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.local_network_permission_cast_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onGrantClick) {
                    Text(text = buttonLabel)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CastDeviceRow(
    isTv: Boolean,
    route: CastRouteUiModel,
    onClick: () -> Unit
) {
    val icon: ImageVector = if (route.isSpeaker) Icons.Default.Speaker else Icons.Default.Tv
    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .focusHighlight(shape = RoundedCornerShape(12.dp)),
            shape = TvClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
            colors = TvClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = TvMaterialTheme.colorScheme.onSurface,
                focusedContainerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                focusedContentColor = TvMaterialTheme.colorScheme.onSurface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (route.isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = TvMaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    TvIcon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TvMaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    TvText(
                        text = route.name,
                        style = TvMaterialTheme.typography.bodyLarge,
                        color = TvMaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    if (route.isConnecting) {
                        TvText(
                            text = stringResource(R.string.cast_connecting_device),
                            style = TvMaterialTheme.typography.bodySmall,
                            color = TvMaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (!route.subtitle.isNullOrBlank()) {
                        TvText(
                            text = route.subtitle,
                            style = TvMaterialTheme.typography.bodyMedium,
                            color = TvMaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    } else {
        Surface(
            onClick = onClick,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (route.isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = route.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    if (route.isConnecting) {
                        Text(
                            text = stringResource(R.string.cast_connecting_device),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (!route.subtitle.isNullOrBlank()) {
                        Text(
                            text = route.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun MediaRouter.RouteInfo.toUiModel(connectingRouteId: String?): CastRouteUiModel {
    val castDevice = CastDevice.getFromBundle(extras)
    val isSpeaker = deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER ||
        name.contains("Speaker", ignoreCase = true) ||
        name.contains("Altavoz", ignoreCase = true)
    return CastRouteUiModel(
        id = id,
        name = name,
        subtitle = castDevice?.modelName ?: description,
        isSpeaker = isSpeaker,
        isConnecting = connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING || connectingRouteId == id,
        volume = volume,
        volumeMax = volumeMax,
        hasVariableVolume = volumeHandling == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE
    )
}

@Preview(name = "Cast Sheet Permission Mobile Light", showBackground = true)
@Composable
private fun CastBottomSheetPermissionMobileLightPreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_LIGHT) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            CastBottomSheetContent(
                isTv = false,
                hasLocalNetworkPermission = false,
                shouldOpenAppSettings = false,
                showRescanHint = false,
                isCasting = false,
                selectedRoute = null,
                routes = emptyList(),
                mediaTitle = "",
                mediaSubtitle = "",
                posterUrl = null,
                hasPlayerControls = false,
                isPlayerPlaying = false,
                onTogglePlayPause = {},
                onSetVolume = {},
                onStopStreaming = {},
                onSelectRoute = {},
                onRequestLocalNetworkPermission = {},
                onRescanDevices = {}
            )
        }
    }
}

@Preview(name = "Cast Sheet Devices Mobile Dark", showBackground = true)
@Composable
private fun CastBottomSheetDevicesMobileDarkPreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            CastBottomSheetContent(
                isTv = false,
                hasLocalNetworkPermission = true,
                shouldOpenAppSettings = false,
                showRescanHint = false,
                isCasting = false,
                selectedRoute = null,
                routes = listOf(
                    CastRouteUiModel("tv1", "Living Room TV", "Chromecast Ultra", isSpeaker = false),
                    CastRouteUiModel("spk1", "Kitchen Speaker", "Google Nest Audio", isSpeaker = true)
                ),
                mediaTitle = "",
                mediaSubtitle = "",
                posterUrl = null,
                hasPlayerControls = false,
                isPlayerPlaying = false,
                onTogglePlayPause = {},
                onSetVolume = {},
                onStopStreaming = {},
                onSelectRoute = {},
                onRequestLocalNetworkPermission = {},
                onRescanDevices = {}
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Preview(name = "Cast Sheet Permission TV Light", showBackground = true, device = "id:tv_1080p")
@Composable
private fun CastBottomSheetPermissionTvLightPreview() {
    TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_LIGHT) {
        TvSurface(
            colors = TvSurfaceDefaults.colors(
                containerColor = TvMaterialTheme.colorScheme.surface,
                contentColor = TvMaterialTheme.colorScheme.onSurface
            )
        ) {
            CastBottomSheetContent(
                isTv = true,
                hasLocalNetworkPermission = false,
                shouldOpenAppSettings = true,
                showRescanHint = false,
                isCasting = false,
                selectedRoute = null,
                routes = emptyList(),
                mediaTitle = "",
                mediaSubtitle = "",
                posterUrl = null,
                hasPlayerControls = false,
                isPlayerPlaying = false,
                onTogglePlayPause = {},
                onSetVolume = {},
                onStopStreaming = {},
                onSelectRoute = {},
                onRequestLocalNetworkPermission = {},
                onRescanDevices = {}
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Preview(name = "Cast Sheet Connected TV Dark", showBackground = true, device = "id:tv_1080p")
@Composable
private fun CastBottomSheetConnectedTvDarkPreview() {
    TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        TvSurface(
            colors = TvSurfaceDefaults.colors(
                containerColor = TvMaterialTheme.colorScheme.surface,
                contentColor = TvMaterialTheme.colorScheme.onSurface
            )
        ) {
            CastBottomSheetContent(
                isTv = true,
                hasLocalNetworkPermission = true,
                shouldOpenAppSettings = false,
                showRescanHint = false,
                isCasting = true,
                selectedRoute = CastRouteUiModel(
                    id = "tv1",
                    name = "Bedroom OLED",
                    subtitle = "Google TV Streamer",
                    isSpeaker = false,
                    volume = 12,
                    volumeMax = 20,
                    hasVariableVolume = true
                ),
                routes = listOf(
                    CastRouteUiModel("tv2", "Living Room TV", "Chromecast", isSpeaker = false)
                ),
                mediaTitle = "Interstellar",
                mediaSubtitle = "2014 • 4K HDR",
                posterUrl = null,
                hasPlayerControls = true,
                isPlayerPlaying = true,
                onTogglePlayPause = {},
                onSetVolume = {},
                onStopStreaming = {},
                onSelectRoute = {},
                onRequestLocalNetworkPermission = {},
                onRescanDevices = {}
            )
        }
    }
}
