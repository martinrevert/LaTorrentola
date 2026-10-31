package com.martinrevert.latorrentola.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class PreferenceManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    @Before
    fun setUp() {
        every { context.getSharedPreferences("latorrentola_prefs", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.putFloat(any(), any()) } returns editor
    }

    @Test
    fun `getTorrentHandlingMode should migrate legacy CHROMECAST mode to LOCAL_PLAYBACK`() {
        every { sharedPreferences.getString("torrent_handling_mode", null) } returns "CHROMECAST"

        val preferenceManager = PreferenceManager(context)
        val mode = preferenceManager.getTorrentHandlingMode()

        assertThat(mode).isEqualTo(TorrentHandlingMode.LOCAL_PLAYBACK)
        verify { editor.putString("torrent_handling_mode", TorrentHandlingMode.LOCAL_PLAYBACK.name) }
    }

    @Test
    fun `getTorrentHandlingMode should return EXTERNAL_CLIENT when configured`() {
        every { sharedPreferences.getString("torrent_handling_mode", null) } returns TorrentHandlingMode.EXTERNAL_CLIENT.name

        val preferenceManager = PreferenceManager(context)
        val mode = preferenceManager.getTorrentHandlingMode()

        assertThat(mode).isEqualTo(TorrentHandlingMode.EXTERNAL_CLIENT)
    }

    @Test
    fun `getTorrentHandlingMode should return LOCAL_PLAYBACK when null`() {
        every { sharedPreferences.getString("torrent_handling_mode", null) } returns null

        val preferenceManager = PreferenceManager(context)
        val mode = preferenceManager.getTorrentHandlingMode()

        assertThat(mode).isEqualTo(TorrentHandlingMode.LOCAL_PLAYBACK)
    }

    @Test
    fun `areSubtitlesEnabled should return true by default and persist state`() {
        every { sharedPreferences.getBoolean("subtitles_enabled", true) } returns true

        val preferenceManager = PreferenceManager(context)
        assertThat(preferenceManager.areSubtitlesEnabled()).isTrue()

        preferenceManager.setSubtitlesEnabled(false)
        verify { editor.putBoolean("subtitles_enabled", false) }
    }

    @Test
    fun `audio transcoding toggles persist state to sharedPreferences`() {
        every { sharedPreferences.getBoolean("convert_aac51_to_ac3", false) } returns false
        every { sharedPreferences.getBoolean("convert_eac3_to_ac3", false) } returns false
        every { sharedPreferences.getBoolean("convert_dts_to_ac3", false) } returns false

        val preferenceManager = PreferenceManager(context)
        assertThat(preferenceManager.getConvertAac51ToAc3()).isFalse()
        assertThat(preferenceManager.getConvertEac3ToAc3()).isFalse()
        assertThat(preferenceManager.getConvertDtsToAc3()).isFalse()

        preferenceManager.setConvertAac51ToAc3(true)
        verify { editor.putBoolean("convert_aac51_to_ac3", true) }

        preferenceManager.setConvertEac3ToAc3(true)
        verify { editor.putBoolean("convert_eac3_to_ac3", true) }

        preferenceManager.setConvertDtsToAc3(true)
        verify { editor.putBoolean("convert_dts_to_ac3", true) }
    }
}
