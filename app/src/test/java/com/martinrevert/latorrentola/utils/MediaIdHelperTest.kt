package com.martinrevert.latorrentola.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIdHelperTest {

    @Test
    fun `forMovie generates deterministic tmdb string when movieId is valid without imdbId`() {
        val mediaId = MediaIdHelper.forMovie(movieId = 12230)
        assertEquals("tmdb_12230", mediaId)
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
        assertEquals("tmdb_12230", mediaId)
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
    fun `forEpisode generates deterministic tmdb mediaId when imdbId is not yet resolved`() {
        val mediaId = MediaIdHelper.forEpisode(
            seriesId = 111803,
            seasonNumber = 1,
            episodeNumber = 1
        )
        assertEquals("tmdb_111803_s1_e1", mediaId)
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
        assertEquals("tmdb_111803_s2_e5", mediaId)
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
        assertTrue(MediaIdHelper.isCanonical("tt0111161"))
        assertTrue(MediaIdHelper.isCanonical("tt11280740_s1_e1"))
        assertTrue(MediaIdHelper.isCanonical("tmdb_12230"))
        assertTrue(MediaIdHelper.isCanonical("tmdb_111803_s1_e1"))

        assertFalse(MediaIdHelper.isCanonical("media_111803_s1_e1"))
        assertFalse(MediaIdHelper.isCanonical("media_99_s10_e24"))
        assertFalse(MediaIdHelper.isCanonical("12230"))
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

    @Test
    fun `forMovie generates canonical IMDb ID when imdbId is provided`() {
        val mediaId = MediaIdHelper.forMovie(imdbId = "tt0111161", movieId = 12230)
        assertEquals("tt0111161", mediaId)
        assertTrue(MediaIdHelper.isCanonical(mediaId))
        assertTrue(MediaIdHelper.isCanonicalMovieId(mediaId))
    }

    @Test
    fun `forEpisode generates canonical IMDb episode ID when imdbId is provided`() {
        val mediaId = MediaIdHelper.forEpisode(
            imdbId = "tt11111111",
            seriesId = 111803,
            seasonNumber = 1,
            episodeNumber = 2
        )
        assertEquals("tt11111111_s1_e2", mediaId)
        assertTrue(MediaIdHelper.isCanonical(mediaId))
        assertTrue(MediaIdHelper.isCanonicalEpisodeId(mediaId))
    }

    @Test
    fun `normalizeImdbId handles raw digits and prefixes`() {
        assertEquals("tt0111161", MediaIdHelper.normalizeImdbId("0111161"))
        assertEquals("tt0111161", MediaIdHelper.normalizeImdbId("tt0111161"))
        assertEquals("tt0111161", MediaIdHelper.normalizeImdbId("TT0111161"))
        assertNull(MediaIdHelper.normalizeImdbId(""))
        assertNull(MediaIdHelper.normalizeImdbId("invalid"))
    }

    @Test
    fun `parseCanonicalEpisode handles both IMDb and legacy series mnemonic`() {
        val parsedImdb = MediaIdHelper.parseCanonicalEpisode("tt11111111_s1_e2")
        assertNotNull(parsedImdb)
        assertEquals("tt11111111", parsedImdb!!.seriesIdentifier)
        assertEquals("tt11111111", parsedImdb.imdbId)
        assertEquals(1, parsedImdb.second)
        assertEquals(2, parsedImdb.third)
        assertTrue(parsedImdb.matchesSeries(seriesId = 0, targetImdbId = "tt11111111"))
        assertTrue(parsedImdb.matchesSeries(seriesId = 0, targetImdbId = "11111111"))
        assertTrue(parsedImdb.matchesSeries(seriesId = 0, targetImdbId = "TT11111111"))
        assertFalse(parsedImdb.matchesSeries(seriesId = 0, targetImdbId = "tt22222222"))
        assertFalse(parsedImdb.matchesSeries(seriesId = 9999, targetImdbId = null))

        val parsedLegacy = MediaIdHelper.parseCanonicalEpisode("media_1399_s3_e4")
        assertNotNull(parsedLegacy)
        assertEquals(1399, parsedLegacy!!.first)
        assertEquals("1399", parsedLegacy.seriesIdentifier)
        assertEquals(3, parsedLegacy.second)
        assertEquals(4, parsedLegacy.third)
        assertTrue(parsedLegacy.matchesSeries(seriesId = 1399, targetImdbId = null))
        assertFalse(parsedLegacy.matchesSeries(seriesId = 5555, targetImdbId = null))
    }
}
