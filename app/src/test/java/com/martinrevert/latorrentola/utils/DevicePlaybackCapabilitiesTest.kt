package com.martinrevert.latorrentola.utils

import android.content.Context
import android.media.AudioFormat
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.torrent.PlaybackCompatibility
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(UnstableApi::class)
class DevicePlaybackCapabilitiesTest {

    private val context = mockk<Context>(relaxed = true)
    private val mockAudioCaps = mockk<AudioCapabilities>(relaxed = true)
    private lateinit var capabilities: DevicePlaybackCapabilities

    @Before
    fun setUp() {
        mockkStatic(AudioCapabilities::class)
        every { AudioCapabilities.getCapabilities(any(), any(), any(), any()) } returns mockAudioCaps
        capabilities = DevicePlaybackCapabilities(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `evaluateRelease returns NATIVE for AAC audio regardless of surround capabilities`() {
        every { mockAudioCaps.supportsEncoding(any()) } returns false

        val result = capabilities.evaluateRelease(
            videoCodec = "x264",
            resolutionLabel = "1080p",
            audioCodec = "AAC 2.0"
        )

        assertThat(result).isEqualTo(PlaybackCompatibility.NATIVE)
    }

    @Test
    fun `evaluateRelease returns NATIVE when DTS is supported by audio capabilities`() {
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_DTS) } returns true
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_DTS_HD) } returns false

        val result = capabilities.evaluateRelease(
            videoCodec = "x264",
            resolutionLabel = "1080p",
            audioCodec = "DTS 5.1"
        )

        assertThat(result).isEqualTo(PlaybackCompatibility.NATIVE)
    }

    @Test
    fun `evaluateRelease returns NATIVE when DTS-HD is supported by audio capabilities`() {
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_DTS) } returns false
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_DTS_HD) } returns true

        val result = capabilities.evaluateRelease(
            videoCodec = "x265",
            resolutionLabel = "1080p",
            audioCodec = "DTS-HD MA 7.1"
        )

        assertThat(result).isEqualTo(PlaybackCompatibility.NATIVE)
    }

    @Test
    fun `evaluateRelease returns AUDIO_REQUIRES_PASSTHROUGH_OR_EXTERNAL when DTS is unsupported`() {
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_DTS) } returns false
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_DTS_HD) } returns false

        val result = capabilities.evaluateRelease(
            videoCodec = "x264",
            resolutionLabel = "1080p",
            audioCodec = "DTS 5.1"
        )

        assertThat(result).isEqualTo(PlaybackCompatibility.AUDIO_REQUIRES_PASSTHROUGH_OR_EXTERNAL)
    }

    @Test
    fun `evaluateRelease returns NATIVE when Dolby TrueHD is supported`() {
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_DOLBY_TRUEHD) } returns true

        val result = capabilities.evaluateRelease(
            videoCodec = "x265",
            resolutionLabel = "1080p",
            audioCodec = "TrueHD 7.1"
        )

        assertThat(result).isEqualTo(PlaybackCompatibility.NATIVE)
    }

    @Test
    fun `evaluateRelease returns AUDIO_REQUIRES_PASSTHROUGH_OR_EXTERNAL when Dolby TrueHD is unsupported`() {
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_DOLBY_TRUEHD) } returns false

        val result = capabilities.evaluateRelease(
            videoCodec = "x265",
            resolutionLabel = "1080p",
            audioCodec = "TrueHD Atmos"
        )

        assertThat(result).isEqualTo(PlaybackCompatibility.AUDIO_REQUIRES_PASSTHROUGH_OR_EXTERNAL)
    }

    @Test
    fun `evaluateRelease returns NATIVE when streaming Dolby Atmos with E-AC-3 JOC is supported`() {
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_E_AC3_JOC) } returns true
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_E_AC3) } returns false
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_AC4) } returns false
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_DOLBY_TRUEHD) } returns false

        val result = capabilities.evaluateRelease(
            videoCodec = "x265",
            resolutionLabel = "1080p",
            audioCodec = "E-AC-3 Atmos"
        )

        assertThat(result).isEqualTo(PlaybackCompatibility.NATIVE)
    }

    @Test
    fun `evaluateRelease returns NATIVE when streaming Dolby Atmos with AC-4 is supported`() {
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_E_AC3_JOC) } returns false
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_E_AC3) } returns false
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_AC4) } returns true
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_DOLBY_TRUEHD) } returns false

        val result = capabilities.evaluateRelease(
            videoCodec = "x265",
            resolutionLabel = "1080p",
            audioCodec = "AC4 Atmos"
        )

        assertThat(result).isEqualTo(PlaybackCompatibility.NATIVE)
    }

    @Test
    fun `evaluateRelease returns AUDIO_REQUIRES_PASSTHROUGH_OR_EXTERNAL when Dolby Atmos is unsupported`() {
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_E_AC3_JOC) } returns false
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_E_AC3) } returns false
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_AC4) } returns false
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_DOLBY_TRUEHD) } returns false

        val result = capabilities.evaluateRelease(
            videoCodec = "x265",
            resolutionLabel = "1080p",
            audioCodec = "DDP 5.1 Atmos"
        )

        assertThat(result).isEqualTo(PlaybackCompatibility.AUDIO_REQUIRES_PASSTHROUGH_OR_EXTERNAL)
    }

    @Test
    fun `evaluateRelease returns NATIVE for Dolby Digital Plus when E-AC-3 is supported`() {
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_E_AC3) } returns true

        val result = capabilities.evaluateRelease(
            videoCodec = "x264",
            resolutionLabel = "1080p",
            audioCodec = "DDP 5.1"
        )

        assertThat(result).isEqualTo(PlaybackCompatibility.NATIVE)
    }

    @Test
    fun `evaluateRelease returns NATIVE for AC-4 broadcast audio when AC4 is supported`() {
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_AC4) } returns true
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_AC3) } returns false
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_E_AC3) } returns false
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_E_AC3_JOC) } returns false

        val result = capabilities.evaluateRelease(
            videoCodec = "x265",
            resolutionLabel = "1080p",
            audioCodec = "AC-4 5.1"
        )

        assertThat(result).isEqualTo(PlaybackCompatibility.NATIVE)
    }

    @Test
    fun `evaluateRelease returns AUDIO_REQUIRES_PASSTHROUGH_OR_EXTERNAL for AC3 when unhandled by sink`() {
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_AC3) } returns false
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_E_AC3) } returns false
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_E_AC3_JOC) } returns false
        every { mockAudioCaps.supportsEncoding(AudioFormat.ENCODING_AC4) } returns false

        val result = capabilities.evaluateRelease(
            videoCodec = "x264",
            resolutionLabel = "1080p",
            audioCodec = "AC3 5.1"
        )

        assertThat(result).isEqualTo(PlaybackCompatibility.AUDIO_REQUIRES_PASSTHROUGH_OR_EXTERNAL)
    }
}
