package com.martinrevert.latorrentola.service.streaming

import android.content.Context
import android.media.AudioFormat
import androidx.annotation.OptIn
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
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(UnstableApi::class)
class Ac3TranscodingAudioSinkTest {

    private val delegateSink = mockk<AudioSink>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val preferenceManager = mockk<PreferenceManager>(relaxed = true)
    private val audioCapabilities = mockk<AudioCapabilities>(relaxed = true)
    private val mockEncoder = mockk<androidx.media3.decoder.ffmpeg.FfmpegAc3Encoder>(relaxed = true)

    private lateinit var transcodingSink: Ac3TranscodingAudioSink

    @Before
    fun setUp() {
        mockkStatic(AudioCapabilities::class)
        every { AudioCapabilities.getCapabilities(context) } returns audioCapabilities
        every { audioCapabilities.supportsEncoding(AudioFormat.ENCODING_AC3) } returns true

        transcodingSink = Ac3TranscodingAudioSink(
            delegate = delegateSink,
            context = context,
            preferenceManager = preferenceManager,
            encoderFactory = { _, _, _ -> mockEncoder }
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(AudioCapabilities::class)
    }

    @Test
    fun `when aac 2_0 stereo, never transcode even if setting is enabled`() {
        // GIVEN: User has enabled the setting
        every { preferenceManager.getConvertAac51ToAc3() } returns true

        val aacStereoFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(2) // 2.0 Stereo
            .setSampleRate(48000)
            .build()
        val rawPcmStereo = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(2)
            .setSampleRate(48000)
            .build()

        transcodingSink.setSourceAudioFormat(aacStereoFormat)

        // WHEN: configure is called
        transcodingSink.configure(rawPcmStereo, 0, null)

        // THEN: Delegate is configured with raw PCM stereo, NOT AC3!
        verify {
            delegateSink.configure(
                match { it.sampleMimeType == MimeTypes.AUDIO_RAW && it.channelCount == 2 },
                0,
                null
            )
        }
    }

    @Test
    fun `when aac 5_1 and setting enabled and receiver supports ac3, configure ac3 format on delegate`() {
        // GIVEN: User has enabled the setting and receiver supports AC3 passthrough
        every { preferenceManager.getConvertAac51ToAc3() } returns true
        every { audioCapabilities.supportsEncoding(AudioFormat.ENCODING_AC3) } returns true

        val aac51Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(6) // 5.1 Surround
            .setSampleRate(48000)
            .build()
        val rawPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()

        transcodingSink.setSourceAudioFormat(aac51Format)

        // WHEN: configure is called
        transcodingSink.configure(rawPcm51, 0, null)

        // THEN: Delegate is configured with AC3 5.1
        verify {
            delegateSink.configure(
                match { it.sampleMimeType == MimeTypes.AUDIO_AC3 && it.channelCount == 6 },
                0,
                null
            )
        }
    }

    @Test
    fun `when aac 5_1 but setting is disabled, never transcode`() {
        // GIVEN: User has disabled the setting
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
            .build()

        transcodingSink.setSourceAudioFormat(aac51Format)

        // WHEN: configure is called
        transcodingSink.configure(rawPcm51, 0, null)

        // THEN: Delegate receives original raw PCM
        verify {
            delegateSink.configure(
                match { it.sampleMimeType == MimeTypes.AUDIO_RAW && it.channelCount == 6 },
                0,
                null
            )
        }
    }

    @Test
    fun `when format is non-aac such as opus or flac, never transcode`() {
        every { preferenceManager.getConvertAac51ToAc3() } returns true

        val opus51Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_OPUS)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        val rawPcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()

        transcodingSink.setSourceAudioFormat(opus51Format)

        // WHEN: configure is called
        transcodingSink.configure(rawPcm51, 0, null)

        // THEN: Delegate receives original raw PCM
        verify {
            delegateSink.configure(
                match { it.sampleMimeType == MimeTypes.AUDIO_RAW && it.channelCount == 6 },
                0,
                null
            )
        }
    }
}
