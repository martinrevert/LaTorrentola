@file:OptIn(UnstableApi::class)

package com.martinrevert.latorrentola.ui.components

import androidx.annotation.OptIn
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.SurfaceDefaults as TvSurfaceDefaults
import androidx.tv.material3.Text as TvText
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * UI model representing a fatal local audio or video decoder failure in the player.
 *
 * @param isAudioError True when the failing renderer/track is audio; false when it is video.
 * @param codecSummary Optional human-readable codec description (e.g., "HEVC / H.265 10-bit").
 */
data class PlaybackDecodingError(
    val isAudioError: Boolean,
    val codecSummary: String?
)

private val MIME_REGEX = Regex("""\b((?:video|audio)/[A-Za-z0-9.+-]+)\b""", RegexOption.IGNORE_CASE)
private val CODEC_STRING_REGEX = Regex("""\b((?:hvc1|hev1|avc1|avc3|av01|vp09|dvhe|dvh1)\.[A-Za-z0-9.]+)\b""", RegexOption.IGNORE_CASE)

private val CONTAINER_MIMES = setOf(
    MimeTypes.VIDEO_MATROSKA,
    MimeTypes.VIDEO_MP4,
    MimeTypes.VIDEO_WEBM,
    MimeTypes.VIDEO_AVI,
    MimeTypes.VIDEO_MP2T,
    "video/quicktime",
    "video/3gpp",
    "audio/mp4",
    "audio/x-matroska",
    "audio/webm"
)

/**
 * Inspects a [PlaybackException] and returns a [PlaybackDecodingError] if the error corresponds
 * to a decoder initialization, capability, or runtime decoding failure; returns `null` otherwise
 * (for example, transient network or HTTP errors).
 */
@OptIn(UnstableApi::class)
fun resolvePlaybackDecodingError(error: PlaybackException): PlaybackDecodingError? {
    val isDecoderError = when (error.errorCode) {
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> true
        else -> false
    }
    if (!isDecoderError) return null

    val message = error.message.orEmpty()
    val exoError = error as? ExoPlaybackException
    val rendererFormat = if (exoError?.type == ExoPlaybackException.TYPE_RENDERER) {
        exoError.rendererFormat
    } else {
        null
    }
    val rendererName = if (exoError?.type == ExoPlaybackException.TYPE_RENDERER) {
        exoError.rendererName.orEmpty()
    } else {
        ""
    }

    val sampleMimeType = rendererFormat?.sampleMimeType
        ?: MIME_REGEX.findAll(message)
            .map { it.groupValues[1].lowercase() }
            .firstOrNull { it !in CONTAINER_MIMES }

    val isAudioError = when {
        sampleMimeType?.startsWith("audio/") == true -> true
        sampleMimeType?.startsWith("video/") == true -> false
        rendererName.contains("Audio", ignoreCase = true) -> true
        rendererName.contains("Video", ignoreCase = true) -> false
        message.contains("AudioRenderer", ignoreCase = true) || message.contains("AudioSink", ignoreCase = true) -> true
        else -> false
    }

    val codecs = rendererFormat?.codecs ?: CODEC_STRING_REGEX.find(message)?.groupValues?.get(1)
    val lumaBitdepth = rendererFormat?.colorInfo?.lumaBitdepth ?: -1
    val isTenBit = lumaBitdepth == 10 ||
        codecs?.startsWith("hvc1.2.", ignoreCase = true) == true ||
        codecs?.startsWith("hev1.2.", ignoreCase = true) == true ||
        codecs?.startsWith("vp09.02.", ignoreCase = true) == true ||
        (codecs?.contains(".10", ignoreCase = true) == true && codecs.startsWith("av01.", ignoreCase = true)) ||
        message.contains("10bit", ignoreCase = true) ||
        message.contains("10-bit", ignoreCase = true)

    val baseCodecLabel = when (sampleMimeType?.lowercase()) {
        MimeTypes.VIDEO_H265, "video/h265", "video/h.265" -> "HEVC / H.265"
        MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
        MimeTypes.VIDEO_AV1, "video/av1" -> "AV1"
        MimeTypes.VIDEO_VP9, "video/vp9" -> "VP9"
        MimeTypes.VIDEO_H264, "video/h264", "video/h.264" -> "AVC / H.264"
        MimeTypes.AUDIO_TRUEHD, "audio/truehd" -> "Dolby TrueHD"
        MimeTypes.AUDIO_DTS_HD, "audio/dts-hd" -> "DTS-HD"
        MimeTypes.AUDIO_DTS, "audio/dts" -> "DTS"
        MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Atmos (E-AC3)"
        MimeTypes.AUDIO_E_AC3, "audio/e-ac3" -> "Dolby Digital Plus (E-AC3)"
        MimeTypes.AUDIO_AC3 -> "Dolby Digital (AC3)"
        MimeTypes.AUDIO_AAC, "audio/aac" -> "AAC"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_OPUS -> "Opus"
        else -> when {
            codecs?.startsWith("hvc1", ignoreCase = true) == true ||
                codecs?.startsWith("hev1", ignoreCase = true) == true -> "HEVC / H.265"
            codecs?.startsWith("av01", ignoreCase = true) == true -> "AV1"
            codecs?.startsWith("vp09", ignoreCase = true) == true ||
                codecs?.startsWith("vp9", ignoreCase = true) == true -> "VP9"
            codecs?.startsWith("avc1", ignoreCase = true) == true ||
                codecs?.startsWith("avc3", ignoreCase = true) == true -> "AVC / H.264"
            codecs?.startsWith("dvhe", ignoreCase = true) == true ||
                codecs?.startsWith("dvh1", ignoreCase = true) == true -> "Dolby Vision"
            else -> null
        }
    }

    val codecSummary = when {
        baseCodecLabel != null && !isAudioError && isTenBit -> "$baseCodecLabel 10-bit"
        baseCodecLabel != null -> baseCodecLabel
        !isAudioError && isTenBit -> "10-bit"
        else -> null
    }

    return PlaybackDecodingError(
        isAudioError = isAudioError,
        codecSummary = codecSummary
    )
}

/**
 * Modal alert dialog displayed when ExoPlayer encounters a fatal video or audio decoding error,
 * preventing the player screen from silently stalling on an idle surface.
 */
@Composable
fun PlaybackErrorDialog(
    error: PlaybackDecodingError,
    onDismiss: () -> Unit,
    onChangeAudioTrack: (() -> Unit)? = null,
    themeMode: Int? = null
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        if (isTv) {
            TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TvMaterialTheme.colorScheme.scrim.copy(alpha = 0.85f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    PlaybackErrorDialogTvCard(
                        error = error,
                        onDismiss = onDismiss,
                        onChangeAudioTrack = onChangeAudioTrack
                    )
                }
            }
        } else {
            LaTorrentolaTheme(themeMode = effectiveThemeMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    PlaybackErrorDialogMobileCard(
                        error = error,
                        onDismiss = onDismiss,
                        onChangeAudioTrack = onChangeAudioTrack
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlaybackErrorDialogTvCard(
    error: PlaybackDecodingError,
    onDismiss: () -> Unit,
    onChangeAudioTrack: (() -> Unit)? = null,
    closeButtonFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    val title = if (error.isAudioError) {
        stringResource(R.string.player_decoding_error_audio_title)
    } else {
        stringResource(R.string.player_decoding_error_video_title)
    }
    val message = when {
        error.isAudioError && !error.codecSummary.isNullOrBlank() ->
            stringResource(R.string.player_decoding_error_audio_message_with_codec, error.codecSummary)
        error.isAudioError ->
            stringResource(R.string.player_decoding_error_audio_message_generic)
        !error.codecSummary.isNullOrBlank() ->
            stringResource(R.string.player_decoding_error_video_message_with_codec, error.codecSummary)
        else ->
            stringResource(R.string.player_decoding_error_video_message_generic)
    }

    LaunchedEffect(Unit) {
        delay(100.milliseconds)
        try {
            closeButtonFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    TvSurface(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .padding(24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = TvSurfaceDefaults.colors(
            containerColor = TvMaterialTheme.colorScheme.surface,
            contentColor = TvMaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TvText(
                text = title,
                style = TvMaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TvMaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            TvText(
                text = message,
                style = TvMaterialTheme.typography.bodyLarge,
                color = TvMaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (error.isAudioError && onChangeAudioTrack != null) {
                    TvButton(
                        onClick = onChangeAudioTrack,
                        modifier = Modifier.focusRequester(closeButtonFocusRequester)
                    ) {
                        TvText(text = stringResource(R.string.player_decoding_error_change_audio))
                    }
                    TvButton(onClick = onDismiss) {
                        TvText(text = stringResource(R.string.player_decoding_error_close))
                    }
                } else {
                    TvButton(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(closeButtonFocusRequester)
                    ) {
                        TvText(text = stringResource(R.string.player_decoding_error_close))
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlaybackErrorDialogMobileCard(
    error: PlaybackDecodingError,
    onDismiss: () -> Unit,
    onChangeAudioTrack: (() -> Unit)? = null
) {
    val title = if (error.isAudioError) {
        stringResource(R.string.player_decoding_error_audio_title)
    } else {
        stringResource(R.string.player_decoding_error_video_title)
    }
    val message = when {
        error.isAudioError && !error.codecSummary.isNullOrBlank() ->
            stringResource(R.string.player_decoding_error_audio_message_with_codec, error.codecSummary)
        error.isAudioError ->
            stringResource(R.string.player_decoding_error_audio_message_generic)
        !error.codecSummary.isNullOrBlank() ->
            stringResource(R.string.player_decoding_error_video_message_with_codec, error.codecSummary)
        else ->
            stringResource(R.string.player_decoding_error_video_message_generic)
    }

    Card(
        modifier = Modifier
            .widthIn(max = 480.dp)
            .padding(24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (error.isAudioError && onChangeAudioTrack != null) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.player_decoding_error_close))
                    }
                    Button(onClick = onChangeAudioTrack) {
                        Text(text = stringResource(R.string.player_decoding_error_change_audio))
                    }
                } else {
                    Button(onClick = onDismiss) {
                        Text(text = stringResource(R.string.player_decoding_error_close))
                    }
                }
            }
        }
    }
}

@Preview(name = "Mobile Light - Playback Decoding Error")
@Composable
private fun PlaybackErrorDialogMobileLightPreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_LIGHT) {
        PlaybackErrorDialogMobileCard(
            error = PlaybackDecodingError(isAudioError = false, codecSummary = "HEVC / H.265 10-bit"),
            onDismiss = {}
        )
    }
}

@Preview(name = "Mobile Dark - Playback Decoding Error")
@Composable
private fun PlaybackErrorDialogMobileDarkPreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        PlaybackErrorDialogMobileCard(
            error = PlaybackDecodingError(isAudioError = false, codecSummary = "HEVC / H.265 10-bit"),
            onDismiss = {}
        )
    }
}

@Preview(name = "TV Light - Playback Decoding Error", widthDp = 960, heightDp = 540)
@Composable
private fun PlaybackErrorDialogTvLightPreview() {
    TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_LIGHT) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvMaterialTheme.colorScheme.scrim),
            contentAlignment = Alignment.Center
        ) {
            PlaybackErrorDialogTvCard(
                error = PlaybackDecodingError(isAudioError = false, codecSummary = "HEVC / H.265 10-bit"),
                onDismiss = {}
            )
        }
    }
}

@Preview(name = "TV Dark - Playback Decoding Error", widthDp = 960, heightDp = 540)
@Composable
private fun PlaybackErrorDialogTvDarkPreview() {
    TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvMaterialTheme.colorScheme.scrim),
            contentAlignment = Alignment.Center
        ) {
            PlaybackErrorDialogTvCard(
                error = PlaybackDecodingError(isAudioError = false, codecSummary = "HEVC / H.265 10-bit"),
                onDismiss = {}
            )
        }
    }
}
