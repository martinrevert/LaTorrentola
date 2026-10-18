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

    const val TV_MIN_BUFFER_MS = 30_000
    const val TV_MAX_BUFFER_MS = 90_000
    const val TV_BUFFER_FOR_PLAYBACK_MS = 2_500
    const val TV_BUFFER_AFTER_REBUFFER_MS = 5_000
    const val TV_TARGET_BUFFER_BYTES = 48 * 1024 * 1024 // 48 MB

    const val MOBILE_MIN_BUFFER_MS = 20_000
    const val MOBILE_MAX_BUFFER_MS = 60_000
    const val MOBILE_BUFFER_FOR_PLAYBACK_MS = 1_500
    const val MOBILE_BUFFER_AFTER_REBUFFER_MS = 3_000
    const val MOBILE_TARGET_BUFFER_BYTES = 32 * 1024 * 1024 // 32 MB

    /**
     * Builds a LoadControl that buffers further ahead to absorb disk I/O latency on Android TV eMMC.
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
     * preventing premature SocketTimeoutExceptions while pieces verify in the background.
     */
    fun buildDataSourceFactory(context: android.content.Context): androidx.media3.datasource.DataSource.Factory {
        val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(HTTP_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(HTTP_READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)

        return androidx.media3.datasource.DefaultDataSource.Factory(context, httpFactory)
    }
}
