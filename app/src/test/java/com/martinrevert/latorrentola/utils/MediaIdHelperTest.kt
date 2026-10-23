package com.martinrevert.latorrentola.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIdHelperTest {

    @Test
    fun `forMovie generates canonical numeric string when movieId is valid`() {
        val mediaId = MediaIdHelper.forMovie(movieId = 12230)
        assertEquals("12230", mediaId)
        assertTrue(MediaIdHelper.isCanonical(mediaId))
        assertTrue(MediaIdHelper.isCanonicalMovieId(mediaId))
    }

    @Test
    fun `forMovie never uses infoHash even if provided`() {
        val fakeHash = "092ea1d9309572ad7caf0868c3a94217dad15769"
        val mediaId = MediaIdHelper.forMovie(
            movieId = 12230,
            infoHash = fakeHash,
            title = "Inception"
        )
        assertEquals("12230", mediaId)
        assertFalse(mediaId.contains(fakeHash))
    }

    @Test
    fun `forMovie falls back to cleaned title when movieId is 0 without using infoHash`() {
        val fakeHash = "092ea1d9309572ad7caf0868c3a94217dad15769"
        val mediaId = MediaIdHelper.forMovie(
            movieId = 0,
            infoHash = fakeHash,
            title = "Gladiator (2000).mkv"
        )
        assertEquals("Gladiator (2000)", mediaId)
        assertFalse(mediaId.contains(fakeHash))
    }

    @Test
    fun `forEpisode generates canonical mediaId adhering strictly to mnemonic`() {
        val mediaId = MediaIdHelper.forEpisode(
            seriesId = 111803,
            seasonNumber = 1,
            episodeNumber = 1
        )
        assertEquals("media_111803_s1_e1", mediaId)
        assertTrue(MediaIdHelper.isCanonical(mediaId))
        assertTrue(MediaIdHelper.isCanonicalEpisodeId(mediaId))
    }

    @Test
    fun `forEpisode never uses infoHash`() {
        val fakeHash = "092ea1d9309572ad7caf0868c3a94217dad15769"
        val mediaId = MediaIdHelper.forEpisode(
            seriesId = 111803,
            seasonNumber = 2,
            episodeNumber = 5,
            infoHash = fakeHash,
            title = "Severance S02E05"
        )
        assertEquals("media_111803_s2_e5", mediaId)
        assertFalse(mediaId.contains(fakeHash))
    }

    @Test
    fun `isInfoHash correctly identifies 40-character hex hashes`() {
        assertTrue(MediaIdHelper.isInfoHash("092ea1d9309572ad7caf0868c3a94217dad15769"))
        assertTrue(MediaIdHelper.isInfoHash("092EA1D9309572AD7CAF0868C3A94217DAD15769"))
        assertFalse(MediaIdHelper.isInfoHash("12230"))
        assertFalse(MediaIdHelper.isInfoHash("media_111803_s1_e1"))
        assertFalse(MediaIdHelper.isInfoHash("Inception"))
    }

    @Test
    fun `isCanonical validates both movie IDs and episode IDs`() {
        assertTrue(MediaIdHelper.isCanonical("12230"))
        assertTrue(MediaIdHelper.isCanonical("8742"))
        assertTrue(MediaIdHelper.isCanonical("media_111803_s1_e1"))
        assertTrue(MediaIdHelper.isCanonical("media_99_s10_e24"))

        assertFalse(MediaIdHelper.isCanonical("092ea1d9309572ad7caf0868c3a94217dad15769"))
        assertFalse(MediaIdHelper.isCanonical("Gladiator"))
        assertFalse(MediaIdHelper.isCanonical("Severance.S01E01.mkv"))
        assertFalse(MediaIdHelper.isCanonical("media_0_s1_e1"))
        assertFalse(MediaIdHelper.isCanonical("unknown_movie"))
    }

    @Test
    fun `parseCanonicalEpisode correctly extracts components`() {
        val parsed = MediaIdHelper.parseCanonicalEpisode("media_111803_s2_e8")
        assertNotNull(parsed)
        assertEquals(111803, parsed!!.first)
        assertEquals(2, parsed.second)
        assertEquals(8, parsed.third)

        assertNull(MediaIdHelper.parseCanonicalEpisode("12230"))
        assertNull(MediaIdHelper.parseCanonicalEpisode("invalid_id"))
    }
}
