package com.martinrevert.latorrentola.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.martinrevert.latorrentola.util.CastCodecChecker
import com.martinrevert.latorrentola.util.CodecWarning

import androidx.compose.runtime.LaunchedEffect

@Composable
fun CastCodecWarningObserver(
    player: Player?,
    streamUrl: String? = null,
    modifier: Modifier = Modifier
) {
    if (player == null) return
    val context = LocalContext.current
    var codecWarningState by remember { mutableStateOf<CodecWarning?>(null) }

    fun checkAndShowWarning(castDevice: CastDevice?, vFormat: Format?, aFormat: Format?) {
        if (castDevice == null) return
        codecWarningState = CastCodecChecker.checkCompatibility(castDevice, vFormat, aFormat)
    }

    LaunchedEffect(player, streamUrl) {
        val (vFormat, aFormat) = extractMediaFormats(player)
        if (vFormat == null && !streamUrl.isNullOrBlank()) {
            val (extractedVideo, extractedAudio) = CastCodecChecker.retrieveMediaFormats(streamUrl)
            val castContext = try { CastContext.getSharedInstance(context) } catch (_: Exception) { null }
            val currentSession = castContext?.sessionManager?.currentCastSession
            if (currentSession != null && currentSession.isConnected) {
                checkAndShowWarning(currentSession.castDevice, extractedVideo, extractedAudio)
            }
        }
    }

    DisposableEffect(player) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) {}
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                val (vFormat, aFormat) = extractMediaFormats(player)
                checkAndShowWarning(session.castDevice, vFormat, aFormat)
            }
            override fun onSessionStartFailed(session: CastSession, error: Int) {}
            override fun onSessionEnding(session: CastSession) {
                codecWarningState = null
            }
            override fun onSessionEnded(session: CastSession, error: Int) {
                codecWarningState = null
            }
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                val (vFormat, aFormat) = extractMediaFormats(player)
                checkAndShowWarning(session.castDevice, vFormat, aFormat)
            }
            override fun onSessionResumeFailed(session: CastSession, error: Int) {}
            override fun onSessionSuspended(session: CastSession, reason: Int) {}
        }

        var sessionManager: SessionManager? = null
        try {
            CastContext.getSharedInstance(context, mainExecutor)
                .addOnSuccessListener { castContext ->
                    val sm = castContext.sessionManager
                    sessionManager = sm
                    sm.addSessionManagerListener(listener, CastSession::class.java)

                    val currentSession = sm.currentCastSession
                    if (currentSession != null && currentSession.isConnected) {
                        val (vFormat, aFormat) = extractMediaFormats(player)
                        checkAndShowWarning(currentSession.castDevice, vFormat, aFormat)
                    }
                }
        } catch (_: Exception) {}

        onDispose {
            try {
                sessionManager?.removeSessionManagerListener(listener, CastSession::class.java)
            } catch (_: Exception) {}
        }
    }

    val warning = codecWarningState
    if (warning != null) {
        CodecWarningSnackbar(
            warning = warning,
            onDismiss = { codecWarningState = null },
            modifier = modifier
        )
    }
}

@Composable
fun CodecWarningSnackbar(
    warning: CodecWarning,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF261C14),
            border = BorderStroke(1.dp, Color(0xFFFFB300)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Color(0xFFFFB300),
                    modifier = Modifier
                        .size(32.dp)
                        .padding(end = 12.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Advertencia de compatibilidad de Cast",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFFFB300),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${warning.deviceName} (${warning.modelName}) puede no reproducir este contenido:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    warning.warnings.forEach { item ->
                        Text(
                            text = "• $item",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun extractMediaFormats(player: Player): Pair<Format?, Format?> {
    var videoFormat: Format? = null
    var audioFormat: Format? = null
    try {
        for (group in player.currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO && group.length > 0) {
                for (i in 0 until group.length) {
                    if (group.isTrackSelected(i)) {
                        videoFormat = group.getTrackFormat(i)
                        break
                    }
                }
                if (videoFormat == null) {
                    videoFormat = group.getTrackFormat(0)
                }
            }
            if (group.type == C.TRACK_TYPE_AUDIO && group.length > 0) {
                for (i in 0 until group.length) {
                    if (group.isTrackSelected(i)) {
                        audioFormat = group.getTrackFormat(i)
                        break
                    }
                }
                if (audioFormat == null) {
                    audioFormat = group.getTrackFormat(0)
                }
            }
        }
    } catch (_: Exception) {}
    return Pair(videoFormat, audioFormat)
}
