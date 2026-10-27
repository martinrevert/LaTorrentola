@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.martinrevert.latorrentola.utils

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodecList
import android.media.MediaFormat
import androidx.media3.exoplayer.audio.AudioCapabilities
import com.martinrevert.latorrentola.model.torrent.PlaybackCompatibility
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inspects local device video and audio decoding hardware capabilities to inform users
 * whether a release can be decoded natively or requires HDMI AVR passthrough / external playback.
 */
@Singleton
class DevicePlaybackCapabilities @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val supportedVideoMimeTypes: Set<String> by lazy { querySupportedVideoMimes() }
    private val supports4kVideo: Boolean by lazy { query4kVideoSupport() }
    private val supportsDolbyVision: Boolean by lazy { queryDolbyVisionSupport() }
    private val nativeAudioMimes: Set<String> by lazy { queryNativeAudioMimes() }

    /**
     * Evaluates playback compatibility for a specific torrent release.
     *
     * @param videoCodec Video codec name (e.g. "x265", "x264", "AV1").
     * @param resolutionLabel Resolution text (e.g. "4K", "2160p", "1080p", "720p").
     * @param visualTags Visual enhancement tags (e.g. ["HDR10", "DV"]).
     * @param audioCodec Audio codec name (e.g. "AAC", "DTS-HD MA 5.1", "TrueHD Atmos", "DDP 5.1").
     * @return Evaluated [PlaybackCompatibility].
     */
    fun evaluateRelease(
        videoCodec: String?,
        resolutionLabel: String,
        visualTags: List<String> = emptyList(),
        audioCodec: String
    ): PlaybackCompatibility {
        // 1. Check Video Resolution (4K / 2160p)
        val is4k = resolutionLabel.contains("4K", ignoreCase = true) ||
                resolutionLabel.contains("2160", ignoreCase = true)
        if (is4k && !supports4kVideo) {
            return PlaybackCompatibility.VIDEO_UNSUPPORTED
        }

        // 2. Check Video Codec (AV1)
        val isAv1 = videoCodec?.contains("AV1", ignoreCase = true) == true
        if (isAv1 && !supportedVideoMimeTypes.contains(MediaFormat.MIMETYPE_VIDEO_AV1)) {
            // If AV1 hardware decoder is absent on 4K, video will stutter or fail
            if (is4k) return PlaybackCompatibility.VIDEO_UNSUPPORTED
        }

        // 3. Check Audio Codec Compatibility
        val audioUpper = audioCodec.uppercase()
        val isAac = audioUpper.contains("AAC")
        if (isAac) {
            return PlaybackCompatibility.NATIVE
        }

        val isDts = audioUpper.contains("DTS")
        val isTrueHd = audioUpper.contains("TRUEHD") || audioUpper.contains("ATMOS")
        val isDdpOrAc3 = audioUpper.contains("DDP") || audioUpper.contains("EAC3") ||
                audioUpper.contains("AC3") || audioUpper.contains("DD5.1") || audioUpper.contains("DD 5.1")

        if (isDts) {
            val hasDts = hasDtsSupport()
            if (!hasDts) return PlaybackCompatibility.AUDIO_REQUIRES_PASSTHROUGH_OR_EXTERNAL
        }

        if (isTrueHd) {
            val hasTrueHd = hasTrueHdSupport()
            if (!hasTrueHd) return PlaybackCompatibility.AUDIO_REQUIRES_PASSTHROUGH_OR_EXTERNAL
        }

        if (isDdpOrAc3) {
            val hasDdp = hasDdpOrAc3Support()
            if (!hasDdp) return PlaybackCompatibility.AUDIO_REQUIRES_PASSTHROUGH_OR_EXTERNAL
        }

        return PlaybackCompatibility.NATIVE
    }

    private fun hasDtsSupport(): Boolean {
        if (nativeAudioMimes.contains("audio/vnd.dts") || nativeAudioMimes.contains("audio/vnd.dts.hd")) {
            return true
        }
        val audioCaps = AudioCapabilities.getCapabilities(context)
        return audioCaps.supportsEncoding(AudioFormat.ENCODING_DTS) ||
                audioCaps.supportsEncoding(AudioFormat.ENCODING_DTS_HD)
    }

    private fun hasTrueHdSupport(): Boolean {
        val audioCaps = AudioCapabilities.getCapabilities(context)
        return audioCaps.supportsEncoding(AudioFormat.ENCODING_DOLBY_TRUEHD)
    }

    private fun hasDdpOrAc3Support(): Boolean {
        if (nativeAudioMimes.contains("audio/ac3") || nativeAudioMimes.contains("audio/eac3")) {
            return true
        }
        val audioCaps = AudioCapabilities.getCapabilities(context)
        return audioCaps.supportsEncoding(AudioFormat.ENCODING_AC3) ||
                audioCaps.supportsEncoding(AudioFormat.ENCODING_E_AC3)
    }

    private fun querySupportedVideoMimes(): Set<String> {
        val supported = mutableSetOf<String>()
        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            for (info in codecList.codecInfos) {
                if (info.isEncoder) continue
                for (type in info.supportedTypes) {
                    if (type.startsWith("video/")) {
                        supported.add(type.lowercase())
                    }
                }
            }
        } catch (_: Exception) {}
        return supported
    }

    private fun query4kVideoSupport(): Boolean {
        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            for (info in codecList.codecInfos) {
                if (info.isEncoder) continue
                for (type in info.supportedTypes) {
                    if (type.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) ||
                        type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) ||
                        type.equals(MediaFormat.MIMETYPE_VIDEO_VP9, ignoreCase = true)
                    ) {
                        val capabilities = info.getCapabilitiesForType(type)
                        val videoCaps = capabilities?.videoCapabilities
                        if (videoCaps != null && videoCaps.areSizeAndRateSupported(3840, 2160, 24.0)) {
                            return true
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun queryDolbyVisionSupport(): Boolean {
        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            for (info in codecList.codecInfos) {
                if (info.isEncoder) continue
                for (type in info.supportedTypes) {
                    if (type.equals(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION, ignoreCase = true)) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun queryNativeAudioMimes(): Set<String> {
        val supported = mutableSetOf<String>()
        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            for (info in codecList.codecInfos) {
                if (info.isEncoder) continue
                for (type in info.supportedTypes) {
                    if (type.startsWith("audio/")) {
                        supported.add(type.lowercase())
                    }
                }
            }
        } catch (_: Exception) {}
        return supported
    }
}
