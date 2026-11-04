package com.martinrevert.latorrentola.utils

import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.user.PlaybackProgress
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
        assertFalse(MediaIdHelper.isCanonical(mediaId))
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
    fun `isCanonical validates canonical IMDb movie IDs and episode IDs only`() {
        assertTrue(MediaIdHelper.isCanonical("tt0111161"))
        assertTrue(MediaIdHelper.isCanonical("tt11280740_s1_e1"))

        assertFalse(MediaIdHelper.isCanonical("tmdb_12230"))
        assertFalse(MediaIdHelper.isCanonical("tmdb_111803_s1_e1"))
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

        val parsedTmdb = MediaIdHelper.parseCanonicalEpisode("tmdb_111803_s1_e2")
        assertNotNull(parsedTmdb)
        assertNull(parsedTmdb!!.imdbId)
        assertFalse(parsedTmdb.matchesSeries(seriesId = 0, targetImdbId = "tt111803"))
        assertTrue(parsedTmdb.matchesSeries(seriesId = 111803, targetImdbId = null))
    }

    @Test
    fun `findMovieProgress resolves progress via canonical IMDb ID, TMDB ID, numeric ID, and title`() {
        val progressImdb = PlaybackProgress(
            mediaId = "tt0111161",
            title = "The Shawshank Redemption",
            positionMs = 5000L,
            durationMs = 10000L
        )
        val progressTmdb = PlaybackProgress(
            mediaId = "tmdb_550",
            title = "Fight Club",
            positionMs = 3000L,
            durationMs = 9000L
        )
        val map = mapOf(
            "tt0111161" to progressImdb,
            "tmdb_550" to progressTmdb
        )

        // Movie with IMDb code matches canonical entry
        val movie1 = Movie(id = 278, imdbCode = "tt0111161", title = "The Shawshank Redemption")
        val found1 = MediaIdHelper.findMovieProgress(movie1, map)
        assertNotNull(found1)
        assertEquals("tt0111161", found1?.mediaId)

        // Movie with only TMDB ID matches tmdb_ entry
        val movie2 = Movie(id = 550, imdbCode = null, title = "Fight Club")
        val found2 = MediaIdHelper.findMovieProgress(movie2, map)
        assertNotNull(found2)
        assertEquals("tmdb_550", found2?.mediaId)
    }

    @Test
    fun `buildWatchHistoryMap creates dual-key lookup entries for movies and episodes`() {
        val progressEpisode = PlaybackProgress(
            mediaId = "tt0944947_s1_e1",
            title = "Game of Thrones - S01E01 - Winter Is Coming",
            positionMs = 1000L,
            durationMs = 2000L,
            isEpisode = true
        )
        val progressMovie = PlaybackProgress(
            mediaId = "tt0111161",
            title = "The Shawshank Redemption",
            positionMs = 5000L,
            durationMs = 10000L,
            isEpisode = false
        )

        val map = MediaIdHelper.buildWatchHistoryMap(listOf(progressEpisode, progressMovie))

        // Direct canonical lookup
        assertTrue(map.containsKey("tt0944947_s1_e1"))
        assertTrue(map.containsKey("tt0111161"))

        // Movie title lookups
        assertTrue(map.containsKey("The Shawshank Redemption"))
        assertTrue(map.containsKey("the shawshank redemption"))
        // Episode titles should not be indexed in flat title map to prevent collisions with movies
        assertFalse(map.containsKey("Game of Thrones - S01E01 - Winter Is Coming"))
    }

    @Test
    fun `findMovieProgress ignores episode records with identical titles`() {
        val progressEpisode = PlaybackProgress(
            mediaId = "tt0944947_s1_e1",
            title = "Pilot",
            positionMs = 1000L,
            durationMs = 2000L,
            isEpisode = true
        )
        val mapWithEpisode = mapOf("Pilot" to progressEpisode)
        val movie = Movie(
            id = 9999,
            imdbCode = null,
            title = "Pilot"
        )
        assertNull(MediaIdHelper.findMovieProgress(movie, mapWithEpisode))
    }

    @Test
    fun `buildWatchHistoryMap indexes normalized IMDb ID for case-insensitive resolution`() {
        val progressMovie = PlaybackProgress(
            mediaId = "TT0111161",
            title = "The Shawshank Redemption",
            positionMs = 5000L,
            durationMs = 10000L,
            isEpisode = false
        )
        val map = MediaIdHelper.buildWatchHistoryMap(listOf(progressMovie))

        // Normalized lowercase lookup
        assertTrue(map.containsKey("tt0111161"))
        val movie = Movie(id = 278, imdbCode = "tt0111161", title = "The Shawshank Redemption")
        val found = MediaIdHelper.findMovieProgress(movie, map)
        assertNotNull(found)
        assertEquals("TT0111161", found?.mediaId)
    }

    @Test
    fun `findMovieProgress returns null for unwatched movie`() {
        val progressMovie = PlaybackProgress(
            mediaId = "tt0111161",
            title = "The Shawshank Redemption",
            positionMs = 5000L,
            durationMs = 10000L,
            isEpisode = false
        )
        val map = MediaIdHelper.buildWatchHistoryMap(listOf(progressMovie))
        val unwatched = Movie(id = 99999, imdbCode = "tt9999999", title = "Unwatched Movie")
        assertNull(MediaIdHelper.findMovieProgress(unwatched, map))
    }

    @Test
    fun `forEpisode handles Season 0 specials canonically`() {
        val canonicalId = MediaIdHelper.forEpisode(
            imdbId = "tt0944947",
            seriesId = 1399,
            seasonNumber = 0,
            episodeNumber = 1,
            title = "Game of Thrones Special"
        )
        assertEquals("tt0944947_s0_e1", canonicalId)
        assertTrue(MediaIdHelper.isCanonical(canonicalId))
        assertTrue(MediaIdHelper.isCanonicalEpisodeId(canonicalId))

        val parsed = MediaIdHelper.parseCanonicalEpisode(canonicalId)
        assertNotNull(parsed)
        assertEquals(0, parsed?.second)
        assertEquals(1, parsed?.third)
        assertEquals("tt0944947", parsed?.imdbId)
    }

    @Test
    fun `parseCanonicalEpisode parses legacy and fallback Season 0 specials`() {
        val parsedTmdb = MediaIdHelper.parseCanonicalEpisode("tmdb_1399_s0_e2")
        assertNotNull(parsedTmdb)
        assertEquals(1399, parsedTmdb?.first)
        assertEquals(0, parsedTmdb?.second)
        assertEquals(2, parsedTmdb?.third)

        val parsedLegacy = MediaIdHelper.parseCanonicalEpisode("tv_1399_s0_e3")
        assertNotNull(parsedLegacy)
        assertEquals(1399, parsedLegacy?.first)
        assertEquals(0, parsedLegacy?.second)
        assertEquals(3, parsedLegacy?.third)
    }

    @Test
    fun `normalizeImdbId normalizes raw numeric and prefixed IMDb strings`() {
        assertEquals("tt0111161", MediaIdHelper.normalizeImdbId("0111161"))
        assertEquals("tt0111161", MediaIdHelper.normalizeImdbId("tt0111161"))
        assertEquals("tt0111161", MediaIdHelper.normalizeImdbId(" TT0111161 "))
        assertNull(MediaIdHelper.normalizeImdbId(""))
        assertNull(MediaIdHelper.normalizeImdbId(null))
    }
}
