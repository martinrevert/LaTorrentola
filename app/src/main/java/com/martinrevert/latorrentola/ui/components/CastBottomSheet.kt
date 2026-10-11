package com.martinrevert.latorrentola.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent

/**
 * YouTube Music-style Cast Bottom Sheet for discovering devices,
 * controlling remote volume, and transferring playback back to phone ("Este dispositivo").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastBottomSheet(
    onDismissRequest: () -> Unit,
    mediaTitle: String = "",
    mediaSubtitle: String = ""
) {
    val context = LocalContext.current
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
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) { updateState() }
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) { updateState() }
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) { updateState() }
            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) { updateState() }
            override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo) { updateState() }
        }
        mediaRouter.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        updateState()
        onDispose {
            mediaRouter.removeCallback(callback)
        }
    }

    val isCasting = selectedRoute != null && !selectedRoute.isDefault
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isCasting && selectedRoute != null) {
                // "Reproduciendo" Header Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Reproduciendo",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (mediaTitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = mediaTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (mediaSubtitle.isNotBlank()) {
                                Text(
                                    text = mediaSubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Selected Route Control Row & Volume Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = routeIcon(selectedRoute),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedRoute.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (selectedRoute.volumeHandling == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE) {
                                    var sliderValue by remember(selectedRoute.volume) {
                                        mutableFloatStateOf(selectedRoute.volume.toFloat())
                                    }
                                    Slider(
                                        value = sliderValue,
                                        onValueChange = { newValue ->
                                            sliderValue = newValue
                                            selectedRoute.requestSetVolume(newValue.toInt())
                                        },
                                        valueRange = 0f..selectedRoute.volumeMax.toFloat(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                // "Sugerencias" Section (Includes "Este dispositivo" to transfer back to phone)
                item {
                    Text(
                        text = "Sugerencias",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }

                item {
                    CastDeviceRow(
                        title = "Este dispositivo",
                        subtitle = null,
                        icon = Icons.Default.PhoneAndroid,
                        onClick = {
                            mediaRouter.selectRoute(mediaRouter.defaultRoute)
                            onDismissRequest()
                        }
                    )
                }

                val suggestedRoutes = routes.filter { it.id != selectedRoute.id }
                items(suggestedRoutes) { route ->
                    CastDeviceRow(
                        title = route.name,
                        subtitle = route.description,
                        icon = routeIcon(route),
                        onClick = {
                            mediaRouter.selectRoute(route)
                            onDismissRequest()
                        }
                    )
                }
            } else {
                // Disconnected Mode: Show device list
                val suggested = routes.take(3)
                val others = routes.drop(3)

                if (suggested.isNotEmpty()) {
                    item {
                        Text(
                            text = "Sugerencias",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                    items(suggested) { route ->
                        CastDeviceRow(
                            title = route.name,
                            subtitle = route.description,
                            icon = routeIcon(route),
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
                            text = "Otros dispositivos",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                    items(others) { route ->
                        CastDeviceRow(
                            title = route.name,
                            subtitle = route.description,
                            icon = routeIcon(route),
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
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Buscando dispositivos...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CastDeviceRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
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

private fun routeIcon(route: MediaRouter.RouteInfo?): ImageVector {
    if (route == null) return Icons.Default.Tv
    return when {
        route.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER ||
        route.name.contains("Speaker", ignoreCase = true) ||
        route.name.contains("Altavoz", ignoreCase = true) -> Icons.Default.Speaker
        else -> Icons.Default.Tv
    }
}
