package com.martinrevert.latorrentola.service.streaming

import android.content.Context
import android.media.AudioFormat
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegAc3Encoder
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import com.martinrevert.latorrentola.utils.PreferenceManager
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decorating [AudioSink] that conditionally transcodes multichannel AAC 5.1 audio streams
 * to Dolby Digital (AC3 5.1) bitstreams on the fly for SPDIF Optical and HDMI ARC surround passthrough.
 *
 * CRITICAL RULE:
 * - If [format] is AAC 5.1 (channels >= 6) and preference is enabled: transcode to AC3 5.1.
 * - If [format] is AAC 2.0 (channels <= 2) or any other format: DO NOT transcode, bypass transparently.
 */
@OptIn(UnstableApi::class)
class Ac3TranscodingAudioSink(
    private val delegate: AudioSink,
    private val context: Context,
    private val preferenceManager: PreferenceManager,
    private val encoderFactory: (sampleRate: Int, channelCount: Int, bitrate: Int) -> FfmpegAc3Encoder = { sr, ch, br ->
        FfmpegAc3Encoder(sr, ch, br)
    }
) : AudioSink by delegate {

    companion object {
        private const val TAG = "Ac3TranscodingSink"
        private const val AC3_BUFFER_CAPACITY = 65536 // 64 KB direct buffer for encoded AC3 frames
        private const val PCM_DIRECT_BUFFER_CAPACITY = 65536
    }

    private var currentSourceFormat: Format? = null
    private var isTranscodingActive = false
    private var encoder: FfmpegAc3Encoder? = null
    private var encodedOutputBuffer: ByteBuffer? = null
    private var directPcmBuffer: ByteBuffer? = null

    /**
     * Updates the source container/demuxer audio format to evaluate whether the current
     * audio track is multichannel AAC (5.1) vs stereo AAC (2.0).
     */
    fun setSourceAudioFormat(format: Format?) {
        this.currentSourceFormat = format
    }

    @Deprecated("Deprecated in Java", ReplaceWith("configure(inputFormat, specifiedBufferSize, outputChannels)"))
    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?
    ) {
        val settingEnabled = preferenceManager.getConvertAac51ToAc3()
        val sourceIsAac = isAacFormat(currentSourceFormat)
        val channelCount = currentSourceFormat?.channelCount ?: inputFormat.channelCount

        // STRICT MANDATE: Only transcode if AAC AND channelCount >= 6 (5.1).
        // If channelCount <= 2 (AAC 2.0 / stereo), NEVER transcode.
        val isAac51 = sourceIsAac && channelCount >= 6
        val capabilities = AudioCapabilities.getCapabilities(context)
        val receiverSupportsAc3 = capabilities.supportsEncoding(AudioFormat.ENCODING_AC3)

        if (settingEnabled && isAac51 && receiverSupportsAc3) {
            try {
                encoder?.release()
                encoder = encoderFactory(
                    inputFormat.sampleRate,
                    6,
                    640000
                )
                if (encodedOutputBuffer == null) {
                    encodedOutputBuffer = ByteBuffer.allocateDirect(AC3_BUFFER_CAPACITY)
                        .order(ByteOrder.nativeOrder())
                }

                val ac3Format = Format.Builder()
                    .setSampleMimeType(MimeTypes.AUDIO_AC3)
                    .setChannelCount(6)
                    .setSampleRate(inputFormat.sampleRate)
                    .build()

                delegate.configure(ac3Format, specifiedBufferSize, null)
                isTranscodingActive = true
                Log.d(TAG, "AAC 5.1 -> AC3 5.1 transcoding ACTIVATED (rate=${inputFormat.sampleRate}, channels=6)")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AC3 encoder, falling back to direct PCM output", e)
                cleanupEncoder()
                isTranscodingActive = false
            }
        } else {
            if (isTranscodingActive) {
                Log.d(TAG, "Transcoding DEACTIVATED (setting=$settingEnabled, isAac51=$isAac51, supportsAc3=$receiverSupportsAc3)")
            }
            cleanupEncoder()
            isTranscodingActive = false
        }

        delegate.configure(inputFormat, specifiedBufferSize, outputChannels)
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

        // Ensure direct buffer input for JNI
        val pcmInput = if (buffer.isDirect) {
            buffer
        } else {
            val directBuf = directPcmBuffer ?: ByteBuffer.allocateDirect(PCM_DIRECT_BUFFER_CAPACITY)
                .order(ByteOrder.nativeOrder()).also { directPcmBuffer = it }
            directBuf.clear()
            val toCopy = minOf(buffer.remaining(), directBuf.remaining())
            val oldLimit = buffer.limit()
            buffer.limit(buffer.position() + toCopy)
            directBuf.put(buffer)
            buffer.limit(oldLimit)
            directBuf.flip()
            directBuf
        }

        outBuf.clear()
        val bytesEncoded = enc.encode(pcmInput, outBuf)
        outBuf.flip()

        if (bytesEncoded > 0 && outBuf.hasRemaining()) {
            return delegate.handleBuffer(outBuf, presentationTimeUs, 1)
        }

        return true
    }

    override fun flush() {
        if (isTranscodingActive && encoder != null && encodedOutputBuffer != null) {
            val enc = encoder!!
            val outBuf = encodedOutputBuffer!!
            outBuf.clear()
            val flushed = enc.flush(outBuf)
            outBuf.flip()
            if (flushed > 0 && outBuf.hasRemaining()) {
                delegate.handleBuffer(outBuf, C.TIME_UNSET, 1)
            }
        }
        delegate.flush()
    }

    override fun reset() {
        cleanupEncoder()
        isTranscodingActive = false
        delegate.reset()
    }

    private fun cleanupEncoder() {
        encoder?.release()
        encoder = null
        encodedOutputBuffer = null
        directPcmBuffer = null
    }

    private fun isAacFormat(format: Format?): Boolean {
        val mime = format?.sampleMimeType ?: return false
        return mime.equals(MimeTypes.AUDIO_AAC, ignoreCase = true) ||
                mime.equals("audio/mp4a-latm", ignoreCase = true) ||
                mime.equals("audio/aac", ignoreCase = true) ||
                mime.equals("audio/x-aac", ignoreCase = true)
    }
}
