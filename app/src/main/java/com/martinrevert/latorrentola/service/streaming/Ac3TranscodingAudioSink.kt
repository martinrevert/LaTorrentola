package com.martinrevert.latorrentola.service.streaming

import android.content.Context
import android.util.Log
import com.martinrevert.latorrentola.utils.AppLogger
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegAc3Encoder
import androidx.media3.exoplayer.audio.AudioSink
import com.martinrevert.latorrentola.utils.PreferenceManager
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decorating [AudioSink] that conditionally transcodes multichannel AAC 5.1 audio streams
 * to Dolby Digital (AC3 5.1) bitstreams on the fly for SPDIF Optical and HDMI ARC surround passthrough.
 *
 * CRITICAL RULE:
 * - If source format is AAC 5.1 (channels >= 6), input is decoded PCM, and preference is enabled:
 *   transcode to AC3 5.1.
 * - If source format is AAC 2.0 (channels <= 2) or any other codec (Opus, FLAC, AC3, E-AC3, DTS, TrueHD, etc.):
 *   DO NOT transcode, bypass transparently to [delegate].
 */
@OptIn(UnstableApi::class)
class Ac3TranscodingAudioSink(
    private val delegate: AudioSink,
    private val preferenceManager: PreferenceManager,
    private val encoderFactory: (sampleRate: Int, channelCount: Int, bitrate: Int, inputFloat: Boolean) -> FfmpegAc3Encoder =
        { sr, ch, br, isFloat -> FfmpegAc3Encoder(sr, ch, br, isFloat) }
) : AudioSink by delegate {

    constructor(
        delegate: AudioSink,
        @Suppress("UNUSED_PARAMETER") context: Context,
        preferenceManager: PreferenceManager,
        encoderFactory: (sampleRate: Int, channelCount: Int, bitrate: Int, inputFloat: Boolean) -> FfmpegAc3Encoder =
            { sr, ch, br, isFloat -> FfmpegAc3Encoder(sr, ch, br, isFloat) }
    ) : this(
        delegate = delegate,
        preferenceManager = preferenceManager,
        encoderFactory = encoderFactory
    )

    constructor(
        delegate: AudioSink,
        @Suppress("UNUSED_PARAMETER") context: Context,
        preferenceManager: PreferenceManager,
        legacyEncoderFactory: (sampleRate: Int, channelCount: Int, bitrate: Int) -> FfmpegAc3Encoder
    ) : this(
        delegate = delegate,
        preferenceManager = preferenceManager,
        encoderFactory = { sr, ch, br, _ -> legacyEncoderFactory(sr, ch, br) }
    )

    companion object {
        private const val TAG = "Ac3TranscodingSink"
        private const val AC3_BITRATE = 640_000
        private const val AC3_CHANNELS = 6
        private const val AC3_SAMPLES_PER_FRAME = 1536
        private const val AC3_SYNCFRAME_SIZE_48KHZ_BYTES = 2560 // 1536 * 640000 / (48000 * 8)
        private const val AC3_BUFFER_CAPACITY = 65536 // 64 KB direct buffer for encoded AC3 frames
        private const val PCM_DIRECT_BUFFER_CAPACITY = 65536

        /**
         * Returns true if [mime] corresponds to High Bit Rate (HBR) DTS-HD (Master Audio / High Resolution / DTS:X).
         * Android TV / Google TV Audio HALs cannot route IEC 61937 HBR bursts across the internal SoC bridge to eARC,
         * causing the audio hardware clock to freeze and stopping video playback.
         */
        @Suppress("DEPRECATION")
        fun isDtsHdMime(mime: String?): Boolean {
            if (mime == null) return false
            return mime.equals(MimeTypes.AUDIO_DTS_HD, ignoreCase = true) ||
                    mime.equals(MimeTypes.AUDIO_DTS_EXPRESS, ignoreCase = true) ||
                    mime.equals(MimeTypes.AUDIO_DTS_X, ignoreCase = true) ||
                    mime.equals("audio/vnd.dts.hd", ignoreCase = true) ||
                    mime.equals("audio/dts-hd", ignoreCase = true) ||
                    mime.equals("audio/x-dts-hd", ignoreCase = true) ||
                    mime.equals("audio/vnd.dts.lbr", ignoreCase = true) ||
                    mime.equals("audio/dts-hd-ma", ignoreCase = true) ||
                    mime.equals("audio/dts-hd-hra", ignoreCase = true) ||
                    mime.equals("audio/vnd.dts.uhd", ignoreCase = true)
        }
    }

    @Volatile
    private var currentSourceFormat: Format? = null
    private var isTranscodingActive = false
    private var encoder: FfmpegAc3Encoder? = null
    private var encodedOutputBuffer: ByteBuffer? = null
    private var directPcmBuffer: ByteBuffer? = null

    private var activeInputSampleRate: Int = 48000
    private var activePcmBytesPerFrame: Int = 12 // 6 channels * 2 bytes (16-bit PCM)
    private var activeAc3SyncframeSizeBytes: Int = AC3_SYNCFRAME_SIZE_48KHZ_BYTES
    private var bufferedPcmFrames: Long = 0L

    private var hasPendingAc3Output = false
    private var pendingAc3PresentationTimeUs: Long = C.TIME_UNSET
    private var pendingAc3AccessUnitCount: Int = 1

    /**
     * Updates the source container/demuxer audio format to evaluate whether the current
     * audio track is multichannel AAC (5.1) vs stereo AAC (2.0) or another codec.
     */
    fun setSourceAudioFormat(format: Format?) {
        // Ignore raw PCM formats if accidentally passed as source format
        if (format != null && format.sampleMimeType == MimeTypes.AUDIO_RAW) {
            return
        }
        this.currentSourceFormat = format
    }

    override fun getFormatSupport(format: Format): @AudioSink.SinkFormatSupport Int {
        // When the user has enabled decodeDtsHdToPcm, disallow direct passthrough for DTS-HD (Master Audio, High Resolution, DTS:X).
        // Android TV / Google TV Audio HALs cannot route IEC 61937 High Bit Rate (HBR) bursts (up to 24.5 Mbps)
        // across the TV SoC internal bridge to the HDMI eARC transmitter. Even when an eARC AVR advertises
        // ENCODING_DTS_HD in its HDMI EDID, writing DTS-HD bursts to AudioTrack halts the hardware playback clock
        // (playback head position stays at 0), permanently stalling video rendering and freezing playback.
        // When enabled, declaring DTS-HD unsupported for direct sink bitstream bypass forces ExoPlayer to route
        // DTS-HD to FfmpegAudioRenderer, which decodes the lossless stream to pristine multichannel LPCM (or transcodes
        // to AC3 5.1 if convertDtsToAc3 is enabled), playing smoothly with zero stalls.
        if (preferenceManager.getDecodeDtsHdToPcm() && isDtsHdFormat(format)) {
            return AudioSink.SINK_FORMAT_UNSUPPORTED
        }

        // When surround -> AC3 transcoding is applicable, instruct FfmpegAudioRenderer and
        // MediaCodecAudioRenderer to prefer 16-bit integer PCM over 32-bit float PCM.
        if (format.sampleMimeType == MimeTypes.AUDIO_RAW &&
            format.pcmEncoding == C.ENCODING_PCM_FLOAT &&
            format.channelCount >= 6 &&
            delegate.getFormatSupport(buildAc3Format(format.sampleRate.takeIf { it > 0 } ?: 48000)) == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY &&
            (
                (preferenceManager.getConvertAac51ToAc3() && (currentSourceFormat == null || isAacFormat(currentSourceFormat))) ||
                (preferenceManager.getConvertEac3ToAc3() && isEac3Format(currentSourceFormat) &&
                    (currentSourceFormat == null || getFormatSupport(currentSourceFormat!!) != AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY)) ||
                (preferenceManager.getConvertDtsToAc3() && isDtsFormat(currentSourceFormat) &&
                    (currentSourceFormat == null || getFormatSupport(currentSourceFormat!!) != AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY))
            )
        ) {
            return AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
        }
        return delegate.getFormatSupport(format)
    }

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        val inputFormat = audioSinkConfig.format
        val isRawPcmInput = inputFormat.sampleMimeType == MimeTypes.AUDIO_RAW ||
                inputFormat.sampleMimeType == null
        val isSupportedPcmEncoding = inputFormat.pcmEncoding == C.ENCODING_PCM_16BIT ||
                inputFormat.pcmEncoding == C.ENCODING_PCM_FLOAT ||
                inputFormat.pcmEncoding == Format.NO_VALUE

        if (isRawPcmInput &&
            isSupportedPcmEncoding &&
            shouldTranscodeToAc3(currentSourceFormat, inputFormat.channelCount)
        ) {
            try {
                val isFloatPcm = inputFormat.pcmEncoding == C.ENCODING_PCM_FLOAT
                val inSampleRate = if (inputFormat.sampleRate > 0) inputFormat.sampleRate else 48000
                val inChannels = if (inputFormat.channelCount >= 6) inputFormat.channelCount else AC3_CHANNELS
                val ac3SampleRate = if (inSampleRate == 48000 || inSampleRate == 44100 || inSampleRate == 32000) {
                    inSampleRate
                } else {
                    48000
                }

                encoder?.release()
                encoder = encoderFactory(
                    inSampleRate,
                    inChannels,
                    AC3_BITRATE,
                    isFloatPcm
                )
                if (encodedOutputBuffer == null) {
                    encodedOutputBuffer = ByteBuffer.allocateDirect(AC3_BUFFER_CAPACITY)
                        .order(ByteOrder.LITTLE_ENDIAN)
                } else {
                    encodedOutputBuffer?.clear()
                    encodedOutputBuffer?.order(ByteOrder.LITTLE_ENDIAN)
                }

                val ac3Format = buildAc3Format(ac3SampleRate)

                val ac3Config = AudioSink.AudioSinkConfig.Builder(ac3Format)
                    .setTimeline(audioSinkConfig.timeline)
                    .setMediaPeriodId(audioSinkConfig.mediaPeriodId)
                    .build()

                delegate.configure(ac3Config)

                activeInputSampleRate = inSampleRate
                activePcmBytesPerFrame = inChannels * (if (isFloatPcm) 4 else 2)
                activeAc3SyncframeSizeBytes = maxOf(1, (AC3_SAMPLES_PER_FRAME * AC3_BITRATE) / (ac3SampleRate * 8))
                bufferedPcmFrames = 0L
                hasPendingAc3Output = false
                pendingAc3PresentationTimeUs = C.TIME_UNSET
                pendingAc3AccessUnitCount = 1
                isTranscodingActive = true
                Log.d(
                    TAG,
                    "Surround -> AC3 5.1 transcoding ACTIVATED (src=${currentSourceFormat?.sampleMimeType}, inRate=$inSampleRate, ac3Rate=$ac3SampleRate, inCh=$inChannels, float=$isFloatPcm)"
                )
                return
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to initialize AC3 transcoding, falling back to direct PCM output", e)
                cleanupEncoder()
                isTranscodingActive = false
            }
        } else {
            if (isTranscodingActive) {
                Log.d(TAG, "Transcoding DEACTIVATED (sourceMime=${currentSourceFormat?.sampleMimeType}, ch=${inputFormat.channelCount})")
            }
            cleanupEncoder()
            isTranscodingActive = false
        }

        delegate.configure(audioSinkConfig)
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        if (!isTranscodingActive || encoder == null) {
            return delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        val enc = encoder ?: return delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        val outBuf = encodedOutputBuffer ?: return delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)

        // 1. If a previous encoded AC3 frame was only partially written to the downstream AudioSink
        //    (delegate.handleBuffer returned false), drain that exact same ByteBuffer first!
        if (hasPendingAc3Output) {
            val drained = delegate.handleBuffer(
                outBuf,
                pendingAc3PresentationTimeUs,
                pendingAc3AccessUnitCount
            )
            if (!drained) {
                return false
            }
            hasPendingAc3Output = false
            // If the incoming PCM buffer was already consumed into outBuf on the previous call,
            // we can now report full completion to the upstream audio renderer.
            if (!buffer.hasRemaining()) {
                return true
            }
        }

        if (!buffer.hasRemaining()) {
            return true
        }

        val inputBytes = buffer.remaining()
        val inputPcmFrames = if (activePcmBytesPerFrame > 0) {
            (inputBytes / activePcmBytesPerFrame).toLong()
        } else {
            0L
        }
        val framesBeforeEncode = bufferedPcmFrames

        // 2. Ensure direct LITTLE_ENDIAN ByteBuffer input for JNI without truncating large buffers
        val pcmInput = if (buffer.isDirect) {
            buffer
        } else {
            var directBuf = directPcmBuffer
            if (directBuf == null || directBuf.capacity() < inputBytes) {
                directBuf = ByteBuffer.allocateDirect(maxOf(PCM_DIRECT_BUFFER_CAPACITY, inputBytes))
                    .order(ByteOrder.LITTLE_ENDIAN)
                directPcmBuffer = directBuf
            }
            directBuf.clear()
            directBuf.put(buffer)
            directBuf.flip()
            directBuf
        }

        outBuf.clear()
        val bytesEncoded = enc.encode(pcmInput, outBuf)
        outBuf.flip()

        if (bytesEncoded > 0 && outBuf.hasRemaining()) {
            val accessUnits = maxOf(1, bytesEncoded / activeAc3SyncframeSizeBytes)
            val bufferedDurationUs = if (activeInputSampleRate > 0) {
                (framesBeforeEncode * C.MICROS_PER_SECOND) / activeInputSampleRate
            } else {
                0L
            }
            val ac3PtsUs = if (presentationTimeUs != C.TIME_UNSET) {
                presentationTimeUs - bufferedDurationUs
            } else {
                presentationTimeUs
            }

            bufferedPcmFrames = maxOf(
                0L,
                framesBeforeEncode + inputPcmFrames - (accessUnits.toLong() * AC3_SAMPLES_PER_FRAME)
            )

            val handled = delegate.handleBuffer(outBuf, ac3PtsUs, accessUnits)
            if (!handled) {
                hasPendingAc3Output = true
                pendingAc3PresentationTimeUs = ac3PtsUs
                pendingAc3AccessUnitCount = accessUnits
                return false
            }
            return true
        } else {
            bufferedPcmFrames = framesBeforeEncode + inputPcmFrames
            return true
        }
    }

    override fun playToEndOfStream() {
        if (isTranscodingActive && hasPendingAc3Output) {
            val outBuf = encodedOutputBuffer
            if (outBuf != null) {
                val drained = delegate.handleBuffer(
                    outBuf,
                    pendingAc3PresentationTimeUs,
                    pendingAc3AccessUnitCount
                )
                if (!drained) {
                    return
                }
            }
            hasPendingAc3Output = false
        }
        if (isTranscodingActive && encoder != null && encodedOutputBuffer != null) {
            val enc = encoder!!
            val outBuf = encodedOutputBuffer!!
            outBuf.clear()
            val flushed = enc.flush(outBuf)
            outBuf.flip()
            if (flushed > 0 && outBuf.hasRemaining()) {
                val accessUnits = maxOf(1, flushed / activeAc3SyncframeSizeBytes)
                val handled = delegate.handleBuffer(outBuf, C.TIME_UNSET, accessUnits)
                if (!handled) {
                    hasPendingAc3Output = true
                    pendingAc3PresentationTimeUs = C.TIME_UNSET
                    pendingAc3AccessUnitCount = accessUnits
                    return
                }
            }
        }
        delegate.playToEndOfStream()
    }

    override fun hasPendingData(): Boolean {
        return (isTranscodingActive && hasPendingAc3Output) || delegate.hasPendingData()
    }

    override fun isEnded(): Boolean {
        return (!isTranscodingActive || !hasPendingAc3Output) && delegate.isEnded
    }

    override fun handleDiscontinuity() {
        if (isTranscodingActive) {
            encoder?.reset()
            encodedOutputBuffer?.clear()
            hasPendingAc3Output = false
            pendingAc3PresentationTimeUs = C.TIME_UNSET
            pendingAc3AccessUnitCount = 1
            bufferedPcmFrames = 0L
        }
        delegate.handleDiscontinuity()
    }

    override fun flush() {
        if (isTranscodingActive) {
            encoder?.reset()
            encodedOutputBuffer?.clear()
            hasPendingAc3Output = false
            pendingAc3PresentationTimeUs = C.TIME_UNSET
            pendingAc3AccessUnitCount = 1
            bufferedPcmFrames = 0L
        }
        delegate.flush()
    }

    override fun reset() {
        cleanupEncoder()
        isTranscodingActive = false
        currentSourceFormat = null
        delegate.reset()
    }

    private fun cleanupEncoder() {
        encoder?.release()
        encoder = null
        encodedOutputBuffer = null
        directPcmBuffer = null
        hasPendingAc3Output = false
        pendingAc3PresentationTimeUs = C.TIME_UNSET
        pendingAc3AccessUnitCount = 1
        bufferedPcmFrames = 0L
    }

    private fun buildAc3Format(sampleRate: Int): Format {
        return Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AC3)
            .setChannelCount(AC3_CHANNELS)
            .setSampleRate(sampleRate)
            .setAverageBitrate(AC3_BITRATE)
            .setPeakBitrate(AC3_BITRATE)
            .build()
    }

    private fun shouldTranscodeToAc3(sourceFormat: Format?, decodedChannelCount: Int): Boolean {
        val src = sourceFormat
        val effectiveChannels = if (decodedChannelCount != Format.NO_VALUE && decodedChannelCount > 0) {
            val sourceChannels = src?.channelCount ?: decodedChannelCount
            minOf(
                if (sourceChannels != Format.NO_VALUE && sourceChannels > 0) sourceChannels else decodedChannelCount,
                decodedChannelCount
            )
        } else {
            src?.channelCount ?: decodedChannelCount
        }

        // Surround transcoding only applies to multichannel tracks (5.1+). Never transcode stereo (2.0).
        if (effectiveChannels < 6) {
            return false
        }

        // 1. Evaluate candidate transcoding triggers based on user preferences and source codec
        val isCandidate = when {
            // Case A: Multichannel AAC 5.1+
            isAacFormat(src) -> {
                preferenceManager.getConvertAac51ToAc3()
            }
            // Case B: E-AC3 (DD+ / Atmos) when receiver lacks direct E-AC3 playback
            isEac3Format(src) -> {
                preferenceManager.getConvertEac3ToAc3() &&
                    src != null &&
                    getFormatSupport(src) != AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            }
            // Case C: DTS (Core / DTS-HD / DTS:X) when receiver lacks direct DTS playback
            isDtsFormat(src) -> {
                preferenceManager.getConvertDtsToAc3() &&
                    src != null &&
                    getFormatSupport(src) != AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            }
            else -> false
        }

        if (!isCandidate) {
            return false
        }

        // 2. Transcode ONLY IF the connected receiver/soundbar natively supports AC3 bitstream passthrough
        val candidateAc3Format = buildAc3Format(src?.sampleRate?.takeIf { it > 0 } ?: 48000)
        return delegate.getFormatSupport(candidateAc3Format) == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
    }

    private fun isAacFormat(format: Format?): Boolean {
        val mime = format?.sampleMimeType ?: return false
        return mime.equals(MimeTypes.AUDIO_AAC, ignoreCase = true) ||
                mime.equals("audio/mp4a-latm", ignoreCase = true) ||
                mime.equals("audio/aac", ignoreCase = true) ||
                mime.equals("audio/x-aac", ignoreCase = true)
    }

    private fun isEac3Format(format: Format?): Boolean {
        val mime = format?.sampleMimeType ?: return false
        return mime.equals(MimeTypes.AUDIO_E_AC3, ignoreCase = true) ||
                mime.equals(MimeTypes.AUDIO_E_AC3_JOC, ignoreCase = true) ||
                mime.equals("audio/e-ac3", ignoreCase = true) ||
                mime.equals("audio/eac3", ignoreCase = true) ||
                mime.equals("audio/eac3-joc", ignoreCase = true)
    }

    private fun isDtsHdFormat(format: Format?): Boolean {
        return isDtsHdMime(format?.sampleMimeType)
    }

    private fun isDtsFormat(format: Format?): Boolean {
        val mime = format?.sampleMimeType ?: return false
        return mime.equals(MimeTypes.AUDIO_DTS, ignoreCase = true) ||
                mime.equals("audio/vnd.dts", ignoreCase = true) ||
                mime.equals("audio/dts", ignoreCase = true) ||
                mime.equals("audio/x-dts", ignoreCase = true) ||
                isDtsHdMime(mime)
    }
}
