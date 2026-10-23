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
}
