@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.martinrevert.latorrentola.ui.components

import android.content.ComponentName
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.martinrevert.latorrentola.utils.MediaControllerHelper
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.service.PlaybackService
import com.martinrevert.latorrentola.ui.player.LocalPlayerActivity
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Floating Picture-in-Picture (PIP) video overlay that stays rendered across app screens,
 * styled similarly to YouTube's in-app mini-player with draggable controls and fullscreen expansion.
 */
@Composable
fun FloatingVideoPipOverlay(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // MediaController connection
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val controllerFuture = MediaControllerHelper.buildControllerAsync(context)
        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            MediaControllerHelper.releaseFuture(controllerFuture)
            mediaController = null
        }
    }

    // MediaRouter state for Cast detection
    val mediaRouter = remember { MediaRouter.getInstance(context) }
    val selector = remember {
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
    }
    var isCasting by remember { mutableStateOf(!mediaRouter.selectedRoute.isDefault) }

    DisposableEffect(Unit) {
        val callback = object : MediaRouter.Callback() {
            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
                isCasting = !route.isDefault
            }
            override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
                isCasting = !router.selectedRoute.isDefault
            }
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                isCasting = !router.selectedRoute.isDefault
            }
        }
        mediaRouter.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        isCasting = !mediaRouter.selectedRoute.isDefault
        onDispose {
            mediaRouter.removeCallback(callback)
        }
    }

    // Active playback properties
    var currentItem by remember { mutableStateOf<MediaItem?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }

    val controller = mediaController
    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose {}
        fun update() {
            currentItem = controller.currentMediaItem
            isPlaying = controller.isPlaying
            playbackState = controller.playbackState
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { update() }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { update() }
            override fun onPlaybackStateChanged(state: Int) { update() }
        }
        controller.addListener(listener)
        update()
        onDispose {
            controller.removeListener(listener)
        }
    }

    // Should the PIP be displayed?
    val isPlayerForeground = LocalPlayerActivity.isPlayerForeground
    val shouldShow = !isCasting &&
        !isPlayerForeground &&
        currentItem != null &&
        playbackState != Player.STATE_IDLE &&
        playbackState != Player.STATE_ENDED

    if (!shouldShow || controller == null) {
        return
    }

    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    val pipWidth = 200.dp
    val pipHeight = 112.dp
    val pipWidthPx = with(density) { pipWidth.toPx() }
    val pipHeightPx = with(density) { pipHeight.toPx() }

    // Floating drag offset (clamped to screen boundaries)
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var isExpanding by remember { mutableStateOf(false) }

    LaunchedEffect(isPlayerForeground) {
        if (!isPlayerForeground) {
            isExpanding = false
        }
    }

    // Controls visibility state
    var showControls by remember { mutableStateOf(false) }
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3500)
            showControls = false
        }
    }

    val title = currentItem?.mediaMetadata?.title?.toString().orEmpty()

    val expandToFullScreen: () -> Unit = {
        isExpanding = true
        val item = currentItem
        val intent = Intent(context, LocalPlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(LocalPlayerActivity.EXTRA_FROM_PIP, true)
            putExtra(LocalPlayerActivity.EXTRA_CAST_ENABLED, false)
            if (item != null) {
                putExtra(LocalPlayerActivity.EXTRA_TITLE, item.mediaMetadata.title?.toString())
                putExtra(LocalPlayerActivity.EXTRA_POSTER_URL, item.mediaMetadata.artworkUri?.toString())
                putExtra(LocalPlayerActivity.EXTRA_MEDIA_ID, item.mediaId)
                item.mediaMetadata.extras?.let { extras ->
                    extras.getString(LocalPlayerActivity.EXTRA_STREAM_URL)?.takeIf(String::isNotBlank)?.let {
                        putExtra(LocalPlayerActivity.EXTRA_STREAM_URL, it)
                    }
                    extras.getString(LocalPlayerActivity.EXTRA_FILE_PATH)?.takeIf(String::isNotBlank)?.let {
                        putExtra(LocalPlayerActivity.EXTRA_FILE_PATH, it)
                    }
                    extras.getString(LocalPlayerActivity.EXTRA_INFO_HASH)?.takeIf(String::isNotBlank)?.let {
                        putExtra(LocalPlayerActivity.EXTRA_INFO_HASH, it)
                    }
                    val mId = extras.getInt(LocalPlayerActivity.EXTRA_MOVIE_ID, 0)
                    if (mId > 0) putExtra(LocalPlayerActivity.EXTRA_MOVIE_ID, mId)
                    val sId = extras.getInt(LocalPlayerActivity.EXTRA_SERIES_ID, 0)
                    if (sId > 0) putExtra(LocalPlayerActivity.EXTRA_SERIES_ID, sId)
                    val sNum = extras.getInt(LocalPlayerActivity.EXTRA_SEASON_NUMBER, 0)
                    if (sNum > 0) putExtra(LocalPlayerActivity.EXTRA_SEASON_NUMBER, sNum)
                    val eNum = extras.getInt(LocalPlayerActivity.EXTRA_EPISODE_NUMBER, 0)
                    if (eNum > 0) putExtra(LocalPlayerActivity.EXTRA_EPISODE_NUMBER, eNum)
                    extras.getString(LocalPlayerActivity.EXTRA_MIME_TYPE)?.takeIf(String::isNotBlank)?.let {
                        putExtra(LocalPlayerActivity.EXTRA_MIME_TYPE, it)
                    }
                }
                item.localConfiguration?.uri?.toString()?.takeIf(String::isNotBlank)?.let {
                    putExtra(LocalPlayerActivity.EXTRA_STREAM_URL, it)
                }
                item.localConfiguration?.mimeType?.takeIf(String::isNotBlank)?.let {
                    putExtra(LocalPlayerActivity.EXTRA_MIME_TYPE, it)
                }
            }
        }
        context.startActivity(intent)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(end = 16.dp, bottom = 28.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Card(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(width = pipWidth, height = pipHeight)
                .shadow(10.dp, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newX = offsetX + dragAmount.x
                        val newY = offsetY + dragAmount.y
                        // Clamp drag to screen
                        offsetX = newX.coerceIn(-(screenWidthPx - pipWidthPx - with(density) { 32.dp.toPx() }), 0f)
                        offsetY = newY.coerceIn(-(screenHeightPx - pipHeightPx - with(density) { 60.dp.toPx() }), 0f)
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showControls = !showControls
                },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                // Video rendering surface via TextureView
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                    if (!isExpanding && !isPlayerForeground) {
                                        controller.setVideoTextureView(this@apply)
                                    }
                                }
                                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                    if (!isExpanding && !isPlayerForeground) {
                                        controller.clearVideoTextureView(this@apply)
                                    }
                                    return true
                                }
                                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                            }
                            if (isAvailable && !isExpanding && !isPlayerForeground) {
                                controller.setVideoTextureView(this)
                            }
                        }
                    },
                    update = { textureView ->
                        if (!isExpanding && !isPlayerForeground) {
                            if (textureView.isAvailable) {
                                controller.setVideoTextureView(textureView)
                            }
                        }
                    },
                    onRelease = { textureView ->
                        if (!isPlayerForeground) {
                            controller.clearVideoTextureView(textureView)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Buffering indicator
                if (playbackState == Player.STATE_BUFFERING) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp
                    )
                }

                // Controls overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.7f),
                                        Color.Black.copy(alpha = 0.3f),
                                        Color.Black.copy(alpha = 0.75f)
                                    )
                                )
                            )
                    ) {
                        // Top action row (Expand on left, Close on right)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = expandToFullScreen,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_fullscreen),
                                    contentDescription = stringResource(R.string.pip_expand_desc),
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    controller.stop()
                                    controller.clearMediaItems()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.pip_close_desc),
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Center Play/Pause button
                        IconButton(
                            onClick = {
                                val shouldPause = isPlaying
                                isPlaying = !shouldPause
                                if (shouldPause) controller.pause() else controller.play()
                            },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(if (isPlaying) R.string.pause_desc else R.string.play_desc),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Bottom Title Bar
                        if (title.isNotBlank()) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
