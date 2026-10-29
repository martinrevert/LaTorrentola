/*
 * Copyright (C) 2026 LaTorrentola Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.decoder.ffmpeg

import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Real-time hardware/software AC3 (Dolby Digital 5.1) encoder powered by native FFmpeg.
 *
 * Encodes 16-bit interleaved multi-channel PCM samples into standard AC-3 bitstream frames
 * at 640 kbps, suitable for SPDIF Optical and HDMI ARC surround passthrough.
 */
@UnstableApi
class FfmpegAc3Encoder(
    val sampleRate: Int = 48000,
    val channelCount: Int = 6,
    val bitrate: Int = 640000,
    val inputFloat: Boolean = false
) {
    private var nativeContext: Long = 0

    init {
        if (!FfmpegLibrary.isAvailable()) {
            throw IllegalStateException("FFmpeg native library is not loaded")
        }
        nativeContext = ffmpegAc3EncoderInit(sampleRate, channelCount, bitrate, inputFloat)
        if (nativeContext == 0L) {
            throw IllegalStateException("Failed to initialize FFmpeg AC3 encoder")
        }
    }

    /**
     * Feeds interleaved PCM samples into the encoder and writes any completed
     * AC3 frames to [outputAc3].
     *
     * @param inputPcm Direct [ByteBuffer] containing raw PCM audio samples.
     * @param outputAc3 Direct [ByteBuffer] receiving encoded AC3 bitstream packets.
     * @return Total number of encoded AC3 bytes written into [outputAc3].
     */
    fun encode(inputPcm: ByteBuffer, outputAc3: ByteBuffer): Int {
        if (nativeContext == 0L || !inputPcm.hasRemaining() || !outputAc3.hasRemaining()) return 0
        val inOffset = inputPcm.position()
        val inLength = inputPcm.remaining()
        val outOffset = outputAc3.position()
        val outCapacity = outputAc3.remaining()

        val bytesWritten = ffmpegAc3EncoderEncode(
            nativeContext, inputPcm, inOffset, inLength, outputAc3, outOffset, outCapacity
        )
        if (bytesWritten > 0) {
            inputPcm.position(inputPcm.limit()) // All input consumed into native accumulator
            outputAc3.position(outOffset + bytesWritten)
        } else {
            inputPcm.position(inputPcm.limit())
        }
        return bytesWritten
    }

    /**
     * Discards any buffered partial PCM samples and resets internal encoder state (e.g. on seek).
     */
    fun reset() {
        if (nativeContext != 0L) {
            ffmpegAc3EncoderReset(nativeContext)
        }
    }

    /**
     * Flushes any remaining samples inside the encoder into [outputAc3].
     */
    fun flush(outputAc3: ByteBuffer): Int {
        if (nativeContext == 0L || !outputAc3.hasRemaining()) return 0
        val outOffset = outputAc3.position()
        val outCapacity = outputAc3.remaining()

        val bytesWritten = ffmpegAc3EncoderFlush(nativeContext, outputAc3, outOffset, outCapacity)
        if (bytesWritten > 0) {
            outputAc3.position(outOffset + bytesWritten)
        }
        return bytesWritten
    }

    /**
     * Releases native encoder, resampler, and buffer resources.
     */
    fun release() {
        if (nativeContext != 0L) {
            ffmpegAc3EncoderRelease(nativeContext)
            nativeContext = 0L
        }
    }

    protected fun finalize() {
        release()
    }

    private external fun ffmpegAc3EncoderInit(
        sampleRate: Int,
        channelCount: Int,
        bitrate: Int,
        inputFloat: Boolean
    ): Long
    private external fun ffmpegAc3EncoderEncode(
        context: Long, input: ByteBuffer, inputOffset: Int, inputLength: Int,
        output: ByteBuffer, outputOffset: Int, outputCapacity: Int
    ): Int
    private external fun ffmpegAc3EncoderReset(context: Long)
    private external fun ffmpegAc3EncoderFlush(
        context: Long, output: ByteBuffer, outputOffset: Int, outputCapacity: Int
    ): Int
    private external fun ffmpegAc3EncoderRelease(context: Long)
}
