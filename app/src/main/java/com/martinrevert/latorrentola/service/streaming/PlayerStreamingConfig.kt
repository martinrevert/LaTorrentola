package com.martinrevert.latorrentola.service.streaming

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException

/**
 * Builds optimized Media3 player configurations tailored for low-spec TV hardware and partial streaming.
 */
@OptIn(UnstableApi::class)
object PlayerStreamingConfig {

    const val TV_MIN_BUFFER_MS = 30_000 // 30 seconds minimum forward buffer
    const val TV_MAX_BUFFER_MS = 120_000 // 120 seconds (2 minutes) maximum forward buffer
    const val TV_BUFFER_FOR_PLAYBACK_MS = 2_000 // 2 seconds startup cushion to prevent stutter on network jitter
    const val TV_BUFFER_AFTER_REBUFFER_MS = 3_000 // 3 seconds re-buffer cushion to prevent stutter on network jitter
    const val TV_TARGET_BUFFER_BYTES = 96 * 1024 * 1024 // 96 MB aggressive memory buffer for high-bitrate 1080p/4K

    const val MOBILE_MIN_BUFFER_MS = 20_000 // 20 seconds
    const val MOBILE_MAX_BUFFER_MS = 60_000 // 60 seconds (1 minute)
    const val MOBILE_BUFFER_FOR_PLAYBACK_MS = 1_500
    const val MOBILE_BUFFER_AFTER_REBUFFER_MS = 3_000
    const val MOBILE_TARGET_BUFFER_BYTES = 64 * 1024 * 1024 // 64 MB

    /**
     * Builds a LoadControl that buffers smoothly and recovers quickly from buffer underflows.
     */
    fun buildLoadControl(isTv: Boolean): LoadControl {
        val minBuffer = if (isTv) TV_MIN_BUFFER_MS else MOBILE_MIN_BUFFER_MS
        val maxBuffer = if (isTv) TV_MAX_BUFFER_MS else MOBILE_MAX_BUFFER_MS
        val forPlayback = if (isTv) TV_BUFFER_FOR_PLAYBACK_MS else MOBILE_BUFFER_FOR_PLAYBACK_MS
        val afterRebuffer = if (isTv) TV_BUFFER_AFTER_REBUFFER_MS else MOBILE_BUFFER_AFTER_REBUFFER_MS
        val targetBytes = if (isTv) TV_TARGET_BUFFER_BYTES else MOBILE_TARGET_BUFFER_BYTES

        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuffer, maxBuffer, forPlayback, afterRebuffer)
            .setTargetBufferBytes(targetBytes)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(10_000, true)
            .build()
    }

    /**
     * Builds an error handling policy that quickly retries transient loopback connection delays
     * without immediately declaring fatal player errors.
     */
    fun buildLoadErrorHandlingPolicy(): LoadErrorHandlingPolicy {
        return object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                // Quick retry for loopback HTTP socket stalls
                return 500L
            }

            override fun getMinimumLoadableRetryCount(dataType: Int): Int {
                return 6 // Allow more retries for partial torrent streams
            }
        }
    }

    const val HTTP_CONNECT_TIMEOUT_MS = 15_000
    const val HTTP_READ_TIMEOUT_MS = 35_000

    /**
     * Builds a DataSource.Factory with generous read timeouts for local torrent streaming,
     * and optional [com.martinrevert.latorrentola.service.storage.remote.RemoteMediaDataSource]
     * support for direct SMB/NFS playback without an HTTP proxy server.
     */
    fun buildDataSourceFactory(
        context: android.content.Context,
        remoteStorageManager: com.martinrevert.latorrentola.service.storage.remote.RemoteStorageManager? = null
    ): androidx.media3.datasource.DataSource.Factory {
        val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(HTTP_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(HTTP_READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)

        val defaultFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpFactory)
        if (remoteStorageManager != null) {
            return androidx.media3.datasource.DataSource.Factory {
                val base = defaultFactory.createDataSource()
                com.martinrevert.latorrentola.service.storage.remote.RemoteMediaDataSource(remoteStorageManager, base)
            }
        }
        return defaultFactory
    }

    /** Extension renderer mode: ON enables MediaCodec passthrough/decoding first, with FFmpeg as fallback. */
    const val AUDIO_EXTENSION_RENDERER_MODE = androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON

    /**
     * Builds a RenderersFactory that prioritizes direct digital audio passthrough (AC3, E-AC3, DTS, TrueHD)
     * and hardware decoders via [MediaCodecAudioRenderer], while keeping [FfmpegAudioRenderer] enabled as a robust
     * software fallback when hardware decoders cannot handle a format. Wraps the audio sink with
     * [Ac3TranscodingAudioSink] to support real-time surround transcoding (AAC 5.1, E-AC3, DTS -> AC3 5.1)
     * for SPDIF and HDMI ARC soundbars when direct bitstream playback is unsupported by the receiver.
     */
    fun buildRenderersFactory(
        context: android.content.Context,
        preferenceManager: com.martinrevert.latorrentola.utils.PreferenceManager? = null
    ): androidx.media3.exoplayer.RenderersFactory {
        val prefs = preferenceManager ?: com.martinrevert.latorrentola.utils.PreferenceManager(context)
        return object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            init {
                setExtensionRendererMode(AUDIO_EXTENSION_RENDERER_MODE)
            }

            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                val bufferSizeProvider = androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider.Builder()
                    .setPassthroughBufferDurationUs(250_000) // Bound passthrough buffer to 250ms to prevent AudioFlinger memory allocation errors (-12) on high-bitrate DTS-HD
                    .build()
                val audioOutputProvider = androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider.Builder(context)
                    .setAudioTrackBufferSizeProvider(bufferSizeProvider)
                    .build()
                val defaultSink = androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                    .setAudioOutputProvider(audioOutputProvider)
                    .build()
                return Ac3TranscodingAudioSink(defaultSink, context, prefs)
            }

            override fun buildAudioRenderers(
                context: android.content.Context,
                extensionRendererMode: Int,
                mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: androidx.media3.exoplayer.audio.AudioSink,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                val wrappedEventListener = object : androidx.media3.exoplayer.audio.AudioRendererEventListener by eventListener {
                    override fun onAudioInputFormatChanged(
                        format: androidx.media3.common.Format,
                        decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
                    ) {
                        if (audioSink is Ac3TranscodingAudioSink) {
                            audioSink.setSourceAudioFormat(format)
                        }
                        eventListener.onAudioInputFormatChanged(format, decoderReuseEvaluation)
                    }
                }

                super.buildAudioRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    wrappedEventListener,
                    out
                )

                // Replace standard MediaCodecAudioRenderer with one that synchronously notifies
                // Ac3TranscodingAudioSink on the playback thread before audioSink.configure(...) runs.
                val mediaCodecIndex = out.indexOfFirst {
                    it.javaClass == MediaCodecAudioRenderer::class.java
                }
                if (mediaCodecIndex >= 0) {
                    out[mediaCodecIndex] = object : MediaCodecAudioRenderer(
                        context,
                        codecAdapterFactory,
                        mediaCodecSelector,
                        enableDecoderFallback,
                        eventHandler,
                        wrappedEventListener,
                        audioSink
                    ) {
                        override fun shouldUseBypass(format: Format): Boolean {
                            if (prefs.getDecodeDtsHdToPcm() && Ac3TranscodingAudioSink.isDtsHdMime(format.sampleMimeType)) {
                                return false
                            }
                            return super.shouldUseBypass(format)
                        }

                        override fun supportsFormat(
                            mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                            format: Format
                        ): Int {
                            // When decodeDtsHdToPcm is enabled, reject DTS-HD in MediaCodecAudioRenderer to prevent
                            // Android TV audio HAL IEC 61937 HBR passthrough stalls and force FfmpegAudioRenderer to decode.
                            if (prefs.getDecodeDtsHdToPcm() && Ac3TranscodingAudioSink.isDtsHdMime(format.sampleMimeType)) {
                                return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE)
                            }
                            return super.supportsFormat(mediaCodecSelector, format)
                        }

                        override fun onInputFormatChanged(
                            formatHolder: FormatHolder
                        ): DecoderReuseEvaluation? {
                            formatHolder.format?.let { format ->
                                if (audioSink is Ac3TranscodingAudioSink) {
                                    audioSink.setSourceAudioFormat(format)
                                }
                            }
                            return super.onInputFormatChanged(formatHolder)
                        }
                    }
                }

                // Replace standard FfmpegAudioRenderer with one that synchronously notifies
                // Ac3TranscodingAudioSink on the playback thread before audioSink.configure(...) runs.
                val ffmpegIndex = out.indexOfFirst {
                    it.javaClass == FfmpegAudioRenderer::class.java
                }
                if (ffmpegIndex >= 0) {
                    out[ffmpegIndex] = object : FfmpegAudioRenderer(
                        eventHandler,
                        wrappedEventListener,
                        audioSink
                    ) {
                        override fun onInputFormatChanged(format: Format) {
                            if (audioSink is Ac3TranscodingAudioSink) {
                                audioSink.setSourceAudioFormat(format)
                            }
                            super.onInputFormatChanged(format)
                        }
                    }
                }
            }
        }
    }
}
