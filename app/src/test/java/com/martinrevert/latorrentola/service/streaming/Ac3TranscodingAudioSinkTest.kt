package com.martinrevert.latorrentola.service.streaming

import android.content.Context
import android.media.AudioFormat
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.utils.PreferenceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(UnstableApi::class)
class Ac3TranscodingAudioSinkTest {

    private val delegateSink = mockk<AudioSink>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val preferenceManager = mockk<PreferenceManager>(relaxed = true)
    private val mockEncoder = mockk<androidx.media3.decoder.ffmpeg.FfmpegAc3Encoder>(relaxed = true)

    private lateinit var transcodingSink: Ac3TranscodingAudioSink

    @Before
    fun setUp() {
        // By default in tests, downstream delegate sink natively supports direct AC3 bitstream passthrough
        every {
            delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_AC3 })
        } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

        transcodingSink = Ac3TranscodingAudioSink(
            delegate = delegateSink,
            context = context,
            preferenceManager = preferenceManager,
            encoderFactory = { _, _, _, _ -> mockEncoder }
        )
    }

    @After
    fun tearDown() {
    }

    @Test
    fun `when aac 2_0 stereo, never transcode even if setting is enabled`() {
        every { preferenceManager.getConvertAac51ToAc3() } returns true

        val aacStereoFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(2)
            .setSampleRate(48000)
            .build()
        val rawPcmStereo = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(2)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(aacStereoFormat)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcmStereo).build())

        verify {
            delegateSink.configure(
                match<AudioSink.AudioSinkConfig> {
                    it.format.sampleMimeType == MimeTypes.AUDIO_RAW && it.format.channelCount == 2
                }
            )
        }
    }

    @Test
    fun `when aac 5_1 and setting enabled and receiver supports ac3, configure ac3 format via AudioSinkConfig`() {
        every { preferenceManager.getConvertAac51ToAc3() } returns true

        val aac51Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        val rawPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(aac51Format)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcm51).build())

        verify {
            delegateSink.configure(
                match<AudioSink.AudioSinkConfig> {
                    it.format.sampleMimeType == MimeTypes.AUDIO_AC3 &&
                        it.format.channelCount == 6 &&
                        it.format.sampleRate == 48000 &&
                        it.format.averageBitrate == 640_000
                }
            )
        }
    }

    @Test
    fun `when aac 5_1 but setting is disabled, never transcode`() {
        every { preferenceManager.getConvertAac51ToAc3() } returns false

        val aac51Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        val rawPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(aac51Format)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcm51).build())

        verify {
            delegateSink.configure(
                match<AudioSink.AudioSinkConfig> {
                    it.format.sampleMimeType == MimeTypes.AUDIO_RAW && it.format.channelCount == 6
                }
            )
        }
    }

    @Test
    fun `when format is non-transcoded such as opus flac ac3 or truehd, never transcode`() {
        every { preferenceManager.getConvertAac51ToAc3() } returns true
        every { preferenceManager.getConvertEac3ToAc3() } returns false
        every { preferenceManager.getConvertDtsToAc3() } returns false

        val nonTranscodedMimeTypes = listOf(
            MimeTypes.AUDIO_OPUS,
            MimeTypes.AUDIO_FLAC,
            MimeTypes.AUDIO_AC3,
            MimeTypes.AUDIO_TRUEHD,
            MimeTypes.AUDIO_VORBIS
        )

        for (mime in nonTranscodedMimeTypes) {
            val sourceFormat = Format.Builder()
                .setSampleMimeType(mime)
                .setChannelCount(6)
                .setSampleRate(48000)
                .build()
            val rawPcm51 = Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setChannelCount(6)
                .setSampleRate(48000)
                .setPcmEncoding(C.ENCODING_PCM_16BIT)
                .build()

            transcodingSink.setSourceAudioFormat(sourceFormat)
            transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcm51).build())

            verify {
                delegateSink.configure(
                    match<AudioSink.AudioSinkConfig> {
                        it.format.sampleMimeType == MimeTypes.AUDIO_RAW && it.format.channelCount == 6
                    }
                )
            }
        }
    }

    @Test
    fun `when eac3 5_1 and toggle enabled and receiver lacks direct eac3 but supports ac3, transcode to ac3`() {
        every { preferenceManager.getConvertEac3ToAc3() } returns true
        val eac3Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_E_AC3)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_E_AC3 }) } returns AudioSink.SINK_FORMAT_UNSUPPORTED
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_AC3 }) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

        val rawPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(eac3Format)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcm51).build())

        verify {
            delegateSink.configure(
                match<AudioSink.AudioSinkConfig> {
                    it.format.sampleMimeType == MimeTypes.AUDIO_AC3 &&
                        it.format.channelCount == 6 &&
                        it.format.sampleRate == 48000 &&
                        it.format.averageBitrate == 640_000
                }
            )
        }
    }

    @Test
    fun `when eac3 5_1 and toggle enabled but receiver natively supports direct eac3 passthrough, never transcode`() {
        every { preferenceManager.getConvertEac3ToAc3() } returns true
        val eac3Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_E_AC3)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_E_AC3 }) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

        val rawPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(eac3Format)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcm51).build())

        verify {
            delegateSink.configure(
                match<AudioSink.AudioSinkConfig> {
                    it.format.sampleMimeType == MimeTypes.AUDIO_RAW && it.format.channelCount == 6
                }
            )
        }
    }

    @Test
    fun `when dts 5_1 and toggle enabled and receiver lacks direct dts but supports ac3, transcode to ac3`() {
        every { preferenceManager.getConvertDtsToAc3() } returns true
        val dtsFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_DTS)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_DTS }) } returns AudioSink.SINK_FORMAT_UNSUPPORTED
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_AC3 }) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

        val rawPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(dtsFormat)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcm51).build())

        verify {
            delegateSink.configure(
                match<AudioSink.AudioSinkConfig> {
                    it.format.sampleMimeType == MimeTypes.AUDIO_AC3 &&
                        it.format.channelCount == 6 &&
                        it.format.sampleRate == 48000 &&
                        it.format.averageBitrate == 640_000
                }
            )
        }
    }

    @Test
    fun `when handleBuffer encounters downstream AudioTrack backpressure, drains pending AC3 frame without re-encoding or losing data`() {
        every { preferenceManager.getConvertAac51ToAc3() } returns true

        val aac51Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        val rawPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(aac51Format)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcm51).build())

        every { mockEncoder.encode(any(), any()) } answers {
            val inBuf = firstArg<ByteBuffer>()
            val outBuf = secondArg<ByteBuffer>()
            inBuf.position(inBuf.limit())
            val ac3Frame = ByteArray(2560) { 0x0B }
            outBuf.put(ac3Frame)
            2560
        }

        // Simulate AudioTrack full on first call (false), then accepting on retry (true)
        val outBufSlot = slot<ByteBuffer>()
        every { delegateSink.handleBuffer(capture(outBufSlot), any(), any()) } returnsMany listOf(false, true)

        val pcmBuffer = ByteBuffer.allocateDirect(18432).order(ByteOrder.LITTLE_ENDIAN)
        val firstAttempt = transcodingSink.handleBuffer(pcmBuffer, 100_000L, 1)
        assertThat(firstAttempt).isFalse()
        assertThat(transcodingSink.hasPendingData()).isTrue()
        assertThat(outBufSlot.captured.order()).isEqualTo(ByteOrder.LITTLE_ENDIAN)

        // Retry with same PCM buffer (just as DecoderAudioRenderer / MediaCodecAudioRenderer does)
        val secondAttempt = transcodingSink.handleBuffer(pcmBuffer, 100_000L, 1)
        assertThat(secondAttempt).isTrue()

        // Encoder must only be invoked ONCE; second call only drains pending AC3 output buffer
        verify(exactly = 1) { mockEncoder.encode(any(), any()) }
        verify(exactly = 2) { delegateSink.handleBuffer(any(), 100_000L, 1) }
    }

    @Test
    fun `when encode produces multiple AC3 syncframes, passes accurate encodedAccessUnitCount and resets cleanly on flush`() {
        every { preferenceManager.getConvertAac51ToAc3() } returns true

        val aac51Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        val rawPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(aac51Format)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcm51).build())

        // 2 AC3 syncframes at 48kHz / 640kbps = 5120 bytes
        every { mockEncoder.encode(any(), any()) } answers {
            val inBuf = firstArg<ByteBuffer>()
            val outBuf = secondArg<ByteBuffer>()
            inBuf.position(inBuf.limit())
            outBuf.put(ByteArray(5120))
            5120
        }
        every { delegateSink.handleBuffer(any(), any(), any()) } returns true

        val pcmBuffer = ByteBuffer.allocate(36864) // Non-direct buffer > 1 frame
        val handled = transcodingSink.handleBuffer(pcmBuffer, 200_000L, 1)
        assertThat(handled).isTrue()

        verify { delegateSink.handleBuffer(any(), 200_000L, 2) }

        // Flush on seek must reset encoder and never pass C.TIME_UNSET to delegate.handleBuffer
        transcodingSink.flush()
        verify { mockEncoder.reset() }
        verify { delegateSink.flush() }
        verify(exactly = 0) { delegateSink.handleBuffer(any(), C.TIME_UNSET, any()) }
    }

    @Test
    fun `when aac 5_1 transcoding is enabled, getFormatSupport reports float PCM as supported with transcoding so 16-bit PCM is preferred`() {
        every { preferenceManager.getConvertAac51ToAc3() } returns true
        every { delegateSink.getFormatSupport(any()) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

        val aac51Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        val floatPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .build()

        transcodingSink.setSourceAudioFormat(aac51Format)

        assertThat(transcodingSink.getFormatSupport(floatPcm51))
            .isEqualTo(AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING)
    }

    @Test
    fun `when handleDiscontinuity is called, resets encoder and clears buffered frames`() {
        every { preferenceManager.getConvertAac51ToAc3() } returns true

        val aac51Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        val rawPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(aac51Format)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcm51).build())

        transcodingSink.handleDiscontinuity()

        verify { mockEncoder.reset() }
        verify { delegateSink.handleDiscontinuity() }
    }

    @Test
    fun `when playToEndOfStream is called, flushes trailing encoder samples and delivers to delegate`() {
        every { preferenceManager.getConvertAac51ToAc3() } returns true

        val aac51Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        val rawPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(aac51Format)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcm51).build())

        every { mockEncoder.encode(any(), any()) } returns 0
        val pcmBuffer = ByteBuffer.allocateDirect(1000).order(ByteOrder.LITTLE_ENDIAN)
        transcodingSink.handleBuffer(pcmBuffer, 50_000L, 1)

        every { mockEncoder.flush(any()) } answers {
            val out = firstArg<ByteBuffer>()
            out.put(ByteArray(2560))
            2560
        }
        every { delegateSink.handleBuffer(any(), any(), any()) } returns true

        transcodingSink.playToEndOfStream()

        verify { mockEncoder.flush(any()) }
        verify { delegateSink.handleBuffer(any(), any(), any()) }
        verify { delegateSink.playToEndOfStream() }
    }

    @Test
    fun `when hasPendingAc3Output is true, isEnded returns false even if delegate isEnded is true`() {
        every { preferenceManager.getConvertAac51ToAc3() } returns true
        every { delegateSink.isEnded } returns true

        val aac51Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        val rawPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(aac51Format)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcm51).build())

        every { mockEncoder.encode(any(), any()) } answers {
            val out = secondArg<ByteBuffer>()
            out.put(ByteArray(2560))
            2560
        }
        every { delegateSink.handleBuffer(any(), any(), any()) } returns false

        val pcmBuffer = ByteBuffer.allocateDirect(18432).order(ByteOrder.LITTLE_ENDIAN)
        transcodingSink.handleBuffer(pcmBuffer, 100_000L, 1)

        assertThat(transcodingSink.isEnded).isFalse()
    }

    @Test
    fun `negative presentationTimeUs is preserved for preroll frames`() {
        every { preferenceManager.getConvertAac51ToAc3() } returns true

        val aac51Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        val rawPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(aac51Format)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcm51).build())

        every { mockEncoder.encode(any(), any()) } answers {
            val out = secondArg<ByteBuffer>()
            out.put(ByteArray(2560))
            2560
        }
        val ptsSlot = slot<Long>()
        every { delegateSink.handleBuffer(any(), capture(ptsSlot), any()) } returns true

        val pcmBuffer = ByteBuffer.allocateDirect(18432).order(ByteOrder.LITTLE_ENDIAN)
        transcodingSink.handleBuffer(pcmBuffer, -50_000L, 1)

        assertThat(ptsSlot.captured).isEqualTo(-50_000L)
    }

    @Test
    fun `when currentSourceFormat is null but setting is enabled, getFormatSupport returns supported with transcoding for multichannel format`() {
        every { preferenceManager.getConvertAac51ToAc3() } returns true
        every { delegateSink.getFormatSupport(any()) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

        // currentSourceFormat is NOT set (null)
        val floatPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .build()

        assertThat(transcodingSink.getFormatSupport(floatPcm51))
            .isEqualTo(AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING)
    }

    @Test
    fun `when eac3 2_0 stereo, never transcode even if setting enabled and receiver lacks direct eac3`() {
        every { preferenceManager.getConvertEac3ToAc3() } returns true
        val eac3Stereo = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_E_AC3)
            .setChannelCount(2)
            .setSampleRate(48000)
            .build()
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_E_AC3 }) } returns AudioSink.SINK_FORMAT_UNSUPPORTED
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_AC3 }) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

        val rawPcmStereo = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(2)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(eac3Stereo)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcmStereo).build())

        verify {
            delegateSink.configure(
                match<AudioSink.AudioSinkConfig> {
                    it.format.sampleMimeType == MimeTypes.AUDIO_RAW && it.format.channelCount == 2
                }
            )
        }
    }

    @Test
    fun `when dts 2_0 stereo, never transcode even if setting enabled and receiver lacks direct dts`() {
        every { preferenceManager.getConvertDtsToAc3() } returns true
        val dtsStereo = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_DTS)
            .setChannelCount(2)
            .setSampleRate(48000)
            .build()
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_DTS }) } returns AudioSink.SINK_FORMAT_UNSUPPORTED
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_AC3 }) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

        val rawPcmStereo = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(2)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(dtsStereo)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcmStereo).build())

        verify {
            delegateSink.configure(
                match<AudioSink.AudioSinkConfig> {
                    it.format.sampleMimeType == MimeTypes.AUDIO_RAW && it.format.channelCount == 2
                }
            )
        }
    }

    @Test
    fun `when input is 7_1 surround with 8 channels, encoderFactory is configured with 8 channels for native downmixing`() {
        every { preferenceManager.getConvertEac3ToAc3() } returns true
        val eac371Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_E_AC3)
            .setChannelCount(8)
            .setSampleRate(48000)
            .build()
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_E_AC3 }) } returns AudioSink.SINK_FORMAT_UNSUPPORTED
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_AC3 }) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

        var capturedEncoderChannels = 0
        val customSink = Ac3TranscodingAudioSink(
            delegate = delegateSink,
            preferenceManager = preferenceManager,
            encoderFactory = { _, ch, _, _ ->
                capturedEncoderChannels = ch
                mockEncoder
            }
        )

        val rawPcm71 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(8)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        customSink.setSourceAudioFormat(eac371Format)
        customSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcm71).build())

        assertThat(capturedEncoderChannels).isEqualTo(8)
        verify {
            delegateSink.configure(
                match<AudioSink.AudioSinkConfig> {
                    it.format.sampleMimeType == MimeTypes.AUDIO_AC3 && it.format.channelCount == 6
                }
            )
        }
    }

    @Test
    fun `when receiver lacks direct ac3 passthrough, getFormatSupport delegates directly without reporting transcoding`() {
        every { preferenceManager.getConvertAac51ToAc3() } returns true
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_AC3 }) } returns AudioSink.SINK_FORMAT_UNSUPPORTED
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_RAW }) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

        val aac51Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        val floatPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .build()

        transcodingSink.setSourceAudioFormat(aac51Format)

        assertThat(transcodingSink.getFormatSupport(floatPcm51))
            .isEqualTo(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY)
    }

    @Test
    fun `when eac3 5_1 and toggle enabled but receiver lacks direct ac3 passthrough, never transcode`() {
        every { preferenceManager.getConvertEac3ToAc3() } returns true
        val eac3Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_E_AC3)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_E_AC3 }) } returns AudioSink.SINK_FORMAT_UNSUPPORTED
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_AC3 }) } returns AudioSink.SINK_FORMAT_UNSUPPORTED

        val rawPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(eac3Format)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcm51).build())

        verify {
            delegateSink.configure(
                match<AudioSink.AudioSinkConfig> {
                    it.format.sampleMimeType == MimeTypes.AUDIO_RAW && it.format.channelCount == 6
                }
            )
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `when dts-hd format queried and decodeDtsHdToPcm enabled, returns SINK_FORMAT_UNSUPPORTED to prevent IEC 61937 HBR stall even if delegate supports it`() {
        every { preferenceManager.getDecodeDtsHdToPcm() } returns true
        val dtsHdMimes = listOf(
            MimeTypes.AUDIO_DTS_HD,
            "audio/vnd.dts.hd",
            "audio/dts-hd",
            MimeTypes.AUDIO_DTS_EXPRESS,
            MimeTypes.AUDIO_DTS_X
        )
        for (mime in dtsHdMimes) {
            val dtsHdFormat = Format.Builder()
                .setSampleMimeType(mime)
                .setChannelCount(6)
                .setSampleRate(48000)
                .build()
            every { delegateSink.getFormatSupport(match { it.sampleMimeType == mime }) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

            val support = transcodingSink.getFormatSupport(dtsHdFormat)
            assertThat(support).isEqualTo(AudioSink.SINK_FORMAT_UNSUPPORTED)
        }
    }

    @Test
    fun `when dts-hd format queried and decodeDtsHdToPcm disabled, delegates format support to downstream sink`() {
        every { preferenceManager.getDecodeDtsHdToPcm() } returns false
        val dtsHdFormat = Format.Builder()
            .setSampleMimeType("audio/vnd.dts.hd")
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == "audio/vnd.dts.hd" }) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

        val support = transcodingSink.getFormatSupport(dtsHdFormat)
        assertThat(support).isEqualTo(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY)
    }

    @Test
    fun `when dts core queried for format support, delegates to downstream sink`() {
        val dtsFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_DTS)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_DTS }) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

        val support = transcodingSink.getFormatSupport(dtsFormat)
        assertThat(support).isEqualTo(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY)
    }

    @Test
    fun `when dts-hd 5_1 and toggle enabled, transcodes decoded pcm to ac3`() {
        every { preferenceManager.getDecodeDtsHdToPcm() } returns true
        every { preferenceManager.getConvertDtsToAc3() } returns true
        val dtsHdFormat = Format.Builder()
            .setSampleMimeType("audio/vnd.dts.hd")
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        // Downstream delegate may report direct support via EDID, but sink should still transcode
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == "audio/vnd.dts.hd" }) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        every { delegateSink.getFormatSupport(match { it.sampleMimeType == MimeTypes.AUDIO_AC3 }) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

        val rawPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(dtsHdFormat)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcm51).build())

        verify {
            delegateSink.configure(
                match<AudioSink.AudioSinkConfig> {
                    it.format.sampleMimeType == MimeTypes.AUDIO_AC3 &&
                        it.format.channelCount == 6 &&
                        it.format.sampleRate == 48000 &&
                        it.format.averageBitrate == 640_000
                }
            )
        }
    }

    @Test
    fun `when dts-hd 7_1 and toggle disabled, passes decoded pcm directly to delegate as uncompressed multichannel pcm`() {
        every { preferenceManager.getConvertDtsToAc3() } returns false
        val dtsHdFormat = Format.Builder()
            .setSampleMimeType("audio/vnd.dts.hd")
            .setChannelCount(8)
            .setSampleRate(48000)
            .build()

        val rawPcm71 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(8)
            .setSampleRate(48000)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        transcodingSink.setSourceAudioFormat(dtsHdFormat)
        transcodingSink.configure(AudioSink.AudioSinkConfig.Builder(rawPcm71).build())

        verify {
            delegateSink.configure(
                match<AudioSink.AudioSinkConfig> {
                    it.format.sampleMimeType == MimeTypes.AUDIO_RAW && it.format.channelCount == 8
                }
            )
        }
    }
}
