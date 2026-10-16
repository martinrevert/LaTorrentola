package com.martinrevert.latorrentola.util

import androidx.media3.common.Format
import com.google.android.gms.cast.CastDevice
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.YTS.Torrent
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class CastCodecCheckerTest {

    @Test
    fun `checkCompatibility with YTS torrent on legacy chromecast returns HEVC warning`() {
        val castDevice = mockk<CastDevice>()
        every { castDevice.modelName } returns "Chromecast"
        every { castDevice.friendlyName } returns "Living Room TV"

        val torrent = Torrent(
            videoCodec = "x265",
            quality = "1080p",
        )

        val warning = CastCodecChecker.checkCompatibility(castDevice, torrent)

        assertThat(warning).isNotNull()
        assertThat(warning?.deviceName).isEqualTo("Living Room TV")
        assertThat(warning?.modelName).isEqualTo("Chromecast")
        assertThat(warning?.warnings).contains("Códec de video x265 / HEVC 10-bit no soportado por este dispositivo.")
    }

    @Test
    fun `checkCompatibility fallback to Dispositivo Cast when friendlyName is blank`() {
        val castDevice = mockk<CastDevice>()
        every { castDevice.modelName } returns "Chromecast"
        every { castDevice.friendlyName } returns "  "

        val torrent = Torrent(
            videoCodec = "x265",
            quality = "1080p",
        )

        val warning = CastCodecChecker.checkCompatibility(castDevice, torrent)

        assertThat(warning).isNotNull()
        assertThat(warning?.deviceName).isEqualTo("Dispositivo Cast")
        assertThat(warning?.modelName).isEqualTo("Chromecast")
    }

    @Test
    fun `checkCompatibility with compatible format returns null`() {
        val castDevice = mockk<CastDevice>()
        every { castDevice.modelName } returns "Google TV"
        every { castDevice.friendlyName } returns "Bedroom TV"

        val videoFormat = Format.Builder()
            .setSampleMimeType("video/avc")
            .setWidth(1920)
            .setHeight(1080)
            .build()

        val warning = CastCodecChecker.checkCompatibility(castDevice, videoFormat, null)

        assertThat(warning).isNull()
    }
}
