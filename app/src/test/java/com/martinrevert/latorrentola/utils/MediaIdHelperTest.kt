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
    fun `forMovie returns unknown_movie when imdbId is missing`() {
        val mediaId = MediaIdHelper.forMovie(movieId = 12230)
        assertEquals("unknown_movie", mediaId)
        assertFalse(MediaIdHelper.isCanonical(mediaId))
    }

    @Test
    fun `forMovie generates canonical IMDb ID when imdbId is provided`() {
        val mediaId = MediaIdHelper.forMovie(imdbId = "tt0111161", movieId = 12230)
        assertEquals("tt0111161", mediaId)
        assertTrue(MediaIdHelper.isCanonical(mediaId))
        assertTrue(MediaIdHelper.isCanonicalMovieId(mediaId))
    }

    @Test
    fun `forMovie ignores infoHash even if provided`() {
        val fakeHash = "092ea1d9309572ad7caf0868c3a94217dad15769"
        val mediaId = MediaIdHelper.forMovie(
            imdbId = "tt0111161",
            movieId = 12230,
            title = "Inception"
        )
        assertEquals("tt0111161", mediaId)
        assertFalse(mediaId.contains(fakeHash))
    }

    @Test
    fun `forEpisode returns unknown_episode when imdbId is missing`() {
        val mediaId = MediaIdHelper.forEpisode(
            seriesId = 111803,
            seasonNumber = 2,
            episodeNumber = 5
        )
        assertEquals("unknown_episode", mediaId)
        assertFalse(MediaIdHelper.isCanonical(mediaId))
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
        assertFalse(MediaIdHelper.isCanonical("unknown_episode"))
    }

    @Test
    fun `parseCanonicalEpisode correctly extracts components from IMDb format`() {
        val parsed = MediaIdHelper.parseCanonicalEpisode("tt1118030_s2_e8")
        assertNotNull(parsed)
        assertEquals("tt1118030", parsed!!.imdbId)
        assertEquals(2, parsed.seasonNumber)
        assertEquals(8, parsed.episodeNumber)

        assertNull(MediaIdHelper.parseCanonicalEpisode("media_111803_s2_e8"))
        assertNull(MediaIdHelper.parseCanonicalEpisode("tmdb_111803_s2_e8"))
        assertNull(MediaIdHelper.parseCanonicalEpisode("12230"))
        assertNull(MediaIdHelper.parseCanonicalEpisode("invalid_id"))
    }

    @Test
    fun `normalizeImdbId handles raw digits and prefixes`() {
        assertEquals("tt0111161", MediaIdHelper.normalizeImdbId("0111161"))
        assertEquals("tt0111161", MediaIdHelper.normalizeImdbId("tt0111161"))
        assertEquals("tt0111161", MediaIdHelper.normalizeImdbId("TT0111161"))
        assertEquals("tt0111161", MediaIdHelper.normalizeImdbId(" TT0111161 "))
        assertNull(MediaIdHelper.normalizeImdbId(""))
        assertNull(MediaIdHelper.normalizeImdbId(null))
        assertNull(MediaIdHelper.normalizeImdbId("invalid"))
    }

    @Test
    fun `parseCanonicalEpisode handles series matching by IMDb ID`() {
        val parsedImdb = MediaIdHelper.parseCanonicalEpisode("tt11111111_s1_e2")
        assertNotNull(parsedImdb)
        assertEquals("tt11111111", parsedImdb!!.seriesIdentifier)
        assertEquals("tt11111111", parsedImdb.imdbId)
        assertEquals(1, parsedImdb.second)
        assertEquals(2, parsedImdb.third)
        assertTrue(parsedImdb.matchesSeries(targetImdbId = "tt11111111"))
        assertTrue(parsedImdb.matchesSeries(targetImdbId = "11111111"))
        assertTrue(parsedImdb.matchesSeries(targetImdbId = "TT11111111"))
        assertFalse(parsedImdb.matchesSeries(targetImdbId = "tt22222222"))
        assertFalse(parsedImdb.matchesSeries(targetImdbId = null))
    }

    @Test
    fun `findMovieProgress resolves progress exclusively via canonical IMDb ID`() {
        val progressImdb = PlaybackProgress(
            mediaId = "tt0111161",
            title = "The Shawshank Redemption",
            positionMs = 5000L,
            durationMs = 10000L
        )
        val map = mapOf("tt0111161" to progressImdb)

        // Movie with IMDb code matches canonical entry
        val movie1 = Movie(id = 278, imdbCode = "tt0111161", title = "The Shawshank Redemption")
        val found1 = MediaIdHelper.findMovieProgress(movie1, map)
        assertNotNull(found1)
        assertEquals("tt0111161", found1?.mediaId)

        // Movie without IMDb code returns null
        val movie2 = Movie(id = 550, imdbCode = null, title = "Fight Club")
        val found2 = MediaIdHelper.findMovieProgress(movie2, map)
        assertNull(found2)
    }

    @Test
    fun `findMovieProgress ignores episode records with identical IMDb ID if marked isEpisode`() {
        val progressEpisode = PlaybackProgress(
            mediaId = "tt0944947",
            title = "Pilot",
            positionMs = 1000L,
            durationMs = 2000L,
            isEpisode = true
        )
        val mapWithEpisode = mapOf("tt0944947" to progressEpisode)
        val movie = Movie(
            id = 9999,
            imdbCode = "tt0944947",
            title = "Pilot"
        )
        assertNull(MediaIdHelper.findMovieProgress(movie, mapWithEpisode))
    }

    @Test
    fun `buildWatchHistoryMap indexes canonical IMDb ID and handles case-insensitivity`() {
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
    fun `buildWatchHistoryMap indexes canonical episode ID and handles case-insensitivity`() {
        val progressEp = PlaybackProgress(
            mediaId = "TT0944947_s1_e2",
            title = "Game of Thrones S1E2",
            positionMs = 1000L,
            durationMs = 2000L,
            isEpisode = true
        )
        val map = MediaIdHelper.buildWatchHistoryMap(listOf(progressEp))
        assertTrue(map.containsKey("tt0944947_s1_e2"))
        assertEquals("TT0944947_s1_e2", map["tt0944947_s1_e2"]?.mediaId)
    }

    @Test
    fun `EpisodeComponents matchesSeries matches without seriesId`() {
        val parsed = MediaIdHelper.parseCanonicalEpisode("tt11111111_s1_e2")
        assertNotNull(parsed)
        assertTrue(parsed!!.matchesSeries("tt11111111"))
        assertTrue(parsed.matchesSeries("11111111"))
        assertFalse(parsed.matchesSeries("tt22222222"))
    }
}
