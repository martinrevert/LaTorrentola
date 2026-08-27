package com.martinrevert.latorrentola.database

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.YTS.Cast
import com.martinrevert.latorrentola.model.YTS.Torrent
import org.junit.Before
import org.junit.Test
import java.util.Date

class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setUp() {
        converters = Converters()
    }

    @Test
    fun `test torrent list conversion`() {
        val torrents = listOf(
            Torrent(url = "url1", quality = "1080p", hash = "hash1"),
            Torrent(url = "url2", quality = "720p", hash = "hash2")
        )
        val json = converters.fromTorrentList(torrents)
        val result = converters.fromTorrentString(json)

        assertThat(result).hasSize(2)
        assertThat(result!![0].quality).isEqualTo("1080p")
        assertThat(result[1].hash).isEqualTo("hash2")
    }

    @Test
    fun `test string list conversion`() {
        val strings = listOf("Action", "Drama", "Sci-Fi")
        val json = converters.fromArrayList(strings)
        val result = converters.fromString(json)

        assertThat(result).containsExactly("Action", "Drama", "Sci-Fi").inOrder()
    }

    @Test
    fun `test cast list conversion`() {
        val cast = listOf(
            Cast(name = "Actor 1", characterName = "Hero"),
            Cast(name = "Actor 2", characterName = "Villain")
        )
        val json = converters.fromCastList(cast)
        val result = converters.fromCastString(json)

        assertThat(result).hasSize(2)
        assertThat(result!![0].name).isEqualTo("Actor 1")
        assertThat(result[1].characterName).isEqualTo("Villain")
    }

    @Test
    fun `test date conversion`() {
        val now = Date()
        val timestamp = converters.dateToTimestamp(now)
        val result = converters.fromTimestamp(timestamp)

        assertThat(result?.time).isEqualTo(now.time)
    }

    @Test
    fun `test null conversions`() {
        assertThat(converters.fromTorrentList(null)).isEqualTo("null") // Gson converts null to "null" string usually if not configured otherwise, let's check
        assertThat(converters.fromTorrentString(null)).isNull()
        assertThat(converters.fromArrayList(null)).isEqualTo("null")
        assertThat(converters.fromString(null)).isNull()
        assertThat(converters.fromTimestamp(null)).isNull()
        assertThat(converters.dateToTimestamp(null)).isNull()
    }
}
