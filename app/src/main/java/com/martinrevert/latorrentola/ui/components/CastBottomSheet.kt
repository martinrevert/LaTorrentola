@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import coil3.compose.AsyncImage
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.CastMediaControlIntent
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice

/**
 * YouTube Music-style Cast Bottom Sheet for discovering devices,
 * controlling remote volume, mini-player preview, and transferring playback back to phone.
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
    val isTv = remember(context) { context.isTvDevice() }
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }
    val mediaRouter = remember { MediaRouter.getInstance(context) }
    val selector = remember {
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
    }

    var routes by remember { mutableStateOf(emptyList<MediaRouter.RouteInfo>()) }
    var selectedRoute by remember { mutableStateOf(mediaRouter.selectedRoute) }

    fun updateState() {
        routes = mediaRouter.routes.filter { it.matchesSelector(selector) && !it.isDefault }
        selectedRoute = mediaRouter.selectedRoute
    }

    DisposableEffect(Unit) {
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
        mediaRouter.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sheetBody: @Composable () -> Unit = {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isCasting) {
                // "Reproduciendo" Header Section
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

                        // Mini-Player preview card if media info is present
                        if (mediaTitle.isNotBlank() || player != null) {
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
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
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
                                            text = mediaTitle.ifBlank { "Media" },
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

                                    if (player != null) {
                                        IconButton(
                                            onClick = {
                                                if (isPlayerPlaying) player.pause() else player.play()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayerPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlayerPlaying) "Pause" else "Play",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Active Route Control Row & Volume Slider
                        val castDevice = remember(selectedRoute) { CastDevice.getFromBundle(selectedRoute.extras) }
                        val hardwareModel = castDevice?.modelName

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
                                Text(
                                    text = selectedRoute.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!hardwareModel.isNullOrBlank()) {
                                    Text(
                                        text = hardwareModel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (selectedRoute.volumeHandling == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE) {
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
                                IconButton(
                                    onClick = {
                                        if (currentVol > 0) {
                                            preMuteVolume = currentVol
                                            selectedRoute.requestSetVolume(0)
                                        } else {
                                            selectedRoute.requestSetVolume(preMuteVolume.coerceAtLeast(1))
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = when {
                                            currentVol == 0 -> Icons.AutoMirrored.Filled.VolumeOff
                                            volPct < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                                            else -> Icons.AutoMirrored.Filled.VolumeUp
                                        },
                                        contentDescription = if (currentVol == 0) stringResource(R.string.cast_unmute) else stringResource(R.string.cast_mute),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Slider(
                                    value = sliderValue,
                                    onValueChange = { newValue ->
                                        sliderValue = newValue
                                        val targetVol = newValue.toInt()
                                        if (targetVol != lastHapticStep) {
                                            lastHapticStep = targetVol
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        selectedRoute.requestSetVolume(targetVol)
                                    },
                                    valueRange = 0f..maxVol.toFloat(),
                                    modifier = Modifier.weight(1f)
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

                // Explicit Disconnect / Play on phone button
                item {
                    OutlinedButton(
                        onClick = {
                            mediaRouter.selectRoute(mediaRouter.defaultRoute)
                            onDismissRequest()
                        },
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

                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                val otherRoutes = routes.filter { it.id != selectedRoute.id }
                if (otherRoutes.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.cast_bottom_sheet_other_devices),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                    items(otherRoutes) { route ->
                        val dev = remember(route) { CastDevice.getFromBundle(route.extras) }
                        CastDeviceRow(
                            title = route.name,
                            subtitle = dev?.modelName ?: route.description,
                            icon = routeIcon(route),
                            isConnecting = route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING,
                            onClick = {
                                mediaRouter.selectRoute(route)
                                onDismissRequest()
                            }
                        )
                    }
                }
            } else {
                // Disconnected Mode: Show device list
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
                    items(suggested) { route ->
                        val dev = remember(route) { CastDevice.getFromBundle(route.extras) }
                        CastDeviceRow(
                            title = route.name,
                            subtitle = dev?.modelName ?: route.description,
                            icon = routeIcon(route),
                            isConnecting = route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING,
                            onClick = {
                                mediaRouter.selectRoute(route)
                                onDismissRequest()
                            }
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
                    items(others) { route ->
                        val dev = remember(route) { CastDevice.getFromBundle(route.extras) }
                        CastDeviceRow(
                            title = route.name,
                            subtitle = dev?.modelName ?: route.description,
                            icon = routeIcon(route),
                            isConnecting = route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING,
                            onClick = {
                                mediaRouter.selectRoute(route)
                                onDismissRequest()
                            }
                        )
                    }
                }

                if (routes.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.cast_searching_devices),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        if (isTv) {
            TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
                sheetBody()
            }
        } else {
            LaTorrentolaTheme(themeMode = effectiveThemeMode) {
                sheetBody()
            }
        }
    }
}

@Composable
private fun CastDeviceRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    isConnecting: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isConnecting) {
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
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun routeIcon(route: MediaRouter.RouteInfo?): ImageVector {
    if (route == null) return Icons.Default.Tv
    return when {
        route.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER ||
                route.name.contains("Speaker", ignoreCase = true) ||
                route.name.contains("Altavoz", ignoreCase = true) -> Icons.Default.Speaker

        else -> Icons.Default.Tv
    }
}
