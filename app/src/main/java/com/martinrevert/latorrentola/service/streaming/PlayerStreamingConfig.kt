package com.martinrevert.latorrentola.service.streaming

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException

/**
 * Builds optimized Media3 player configurations tailored for low-spec TV hardware and partial streaming.
 */
@OptIn(UnstableApi::class)
object PlayerStreamingConfig {

    const val TV_MIN_BUFFER_MS = 15_000
    const val TV_MAX_BUFFER_MS = 45_000
    const val TV_BUFFER_FOR_PLAYBACK_MS = 2_000
    const val TV_BUFFER_AFTER_REBUFFER_MS = 2_500
    const val TV_TARGET_BUFFER_BYTES = 48 * 1024 * 1024 // 48 MB
 
    const val MOBILE_MIN_BUFFER_MS = 15_000
    const val MOBILE_MAX_BUFFER_MS = 30_000
    const val MOBILE_BUFFER_FOR_PLAYBACK_MS = 1_500
    const val MOBILE_BUFFER_AFTER_REBUFFER_MS = 2_000
    const val MOBILE_TARGET_BUFFER_BYTES = 32 * 1024 * 1024 // 32 MB

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

    /**
     * Builds a RenderersFactory that prefers software extension decoders (such as FFmpeg)
     * when hardware decoders are unavailable or fail to support the audio format (e.g. AC3, DTS, TrueHD),
     * and wraps the audio sink with [Ac3TranscodingAudioSink] to support real-time AAC 5.1 -> AC3 5.1
     * passthrough for SPDIF/ARC soundbars when enabled.
     */
    fun buildRenderersFactory(
        context: android.content.Context,
        preferenceManager: com.martinrevert.latorrentola.utils.PreferenceManager? = null
    ): androidx.media3.exoplayer.RenderersFactory {
        val prefs = preferenceManager ?: com.martinrevert.latorrentola.utils.PreferenceManager(context)
        return object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            init {
                setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
            }

            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                val defaultSink = androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
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
            }
        }
    }
}
