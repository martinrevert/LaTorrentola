package com.martinrevert.latorrentola.utils

import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeason
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvSeriesProgressCalculatorTest {

    @Test
    fun `calculateProgressPercent returns null when history is empty or seriesId invalid`() {
        val series = TmdbTvSummary(id = 100, name = "Breaking Bad", numberOfEpisodes = 62)
        assertNull(TvSeriesProgressCalculator.calculateProgressPercent(series, emptyMap()))

        val invalidSeries = TmdbTvSummary(id = 0, name = "Invalid", numberOfEpisodes = 10)
        val history = mapOf(
            MediaIdHelper.forEpisode(0, 1, 1) to PlaybackProgress(
                mediaId = MediaIdHelper.forEpisode(0, 1, 1),
                title = "Ep 1",
                positionMs = 1000L,
                durationMs = 1000L,
                timestamp = System.currentTimeMillis()
            )
        )
        assertNull(TvSeriesProgressCalculator.calculateProgressPercent(invalidSeries, history))
    }

    @Test
    fun `calculateProgressPercent returns null when series has no progress in history`() {
        val series = TmdbTvSummary(id = 100, name = "Breaking Bad", numberOfEpisodes = 10)
        val history = mapOf(
            MediaIdHelper.forEpisode(200, 1, 1) to PlaybackProgress(
                mediaId = MediaIdHelper.forEpisode(200, 1, 1),
                title = "Other Series S1E1",
                positionMs = 1000L,
                durationMs = 1000L,
                timestamp = System.currentTimeMillis()
            )
        )
        assertNull(TvSeriesProgressCalculator.calculateProgressPercent(series, history))
    }

    @Test
    fun `calculateProgressPercent accurately calculates 10 percent when 1 of 10 episodes is watched`() {
        val series = TmdbTvSummary(id = 500, name = "Test Series", numberOfEpisodes = 10)
        val history = mapOf(
            MediaIdHelper.forEpisode(500, 1, 1) to PlaybackProgress(
                mediaId = MediaIdHelper.forEpisode(500, 1, 1),
                title = "Ep 1",
                positionMs = 950L,
                durationMs = 1000L,
                timestamp = System.currentTimeMillis()
            )
        )

        val progress = TvSeriesProgressCalculator.calculateProgressPercent(series, history)
        assertEquals(10, progress)
    }

    @Test
    fun `calculateProgressPercent accurately calculates 100 percent when all episodes are watched`() {
        val series = TmdbTvSummary(id = 500, name = "Test Series", numberOfEpisodes = 3)
        val history = (1..3).associate { ep ->
            val id = MediaIdHelper.forEpisode(500, 1, ep)
            id to PlaybackProgress(
                mediaId = id,
                title = "Ep $ep",
                positionMs = 1000L,
                durationMs = 1000L,
                timestamp = System.currentTimeMillis()
            )
        }

        val progress = TvSeriesProgressCalculator.calculateProgressPercent(series, history)
        assertEquals(100, progress)
    }

    @Test
    fun `calculateProgressPercent weights partial episode playback properly`() {
        val series = TmdbTvSummary(id = 500, name = "Test Series", numberOfEpisodes = 10)
        val history = mapOf(
            MediaIdHelper.forEpisode(500, 1, 1) to PlaybackProgress(
                mediaId = MediaIdHelper.forEpisode(500, 1, 1),
                title = "Ep 1",
                positionMs = 500L,
                durationMs = 1000L,
                timestamp = System.currentTimeMillis()
            )
        )

        // 50% of 1 episode out of 10 = 5%
        val progress = TvSeriesProgressCalculator.calculateProgressPercent(series, history)
        assertEquals(5, progress)
    }

    @Test
    fun `calculateProgressPercent sums seasons episodeCount when numberOfEpisodes is null`() {
        val series = TmdbTvSummary(
            id = 500,
            name = "Test Series",
            numberOfEpisodes = null,
            seasons = listOf(
                TmdbTvSeason(id = 1, seasonNumber = 1, episodeCount = 5),
                TmdbTvSeason(id = 2, seasonNumber = 2, episodeCount = 5)
            )
        )
        val history = mapOf(
            MediaIdHelper.forEpisode(500, 1, 1) to PlaybackProgress(
                mediaId = MediaIdHelper.forEpisode(500, 1, 1),
                title = "Ep 1",
                positionMs = 1000L,
                durationMs = 1000L,
                timestamp = System.currentTimeMillis()
            )
        )

        // 1 of 10 total = 10%
        val progress = TvSeriesProgressCalculator.calculateProgressPercent(series, history)
        assertEquals(10, progress)
    }

    @Test
    fun `calculateProgressPercent does not return 100 percent when numberOfEpisodes and seasons are null and only one episode watched`() {
        val series = TmdbTvSummary(
            id = 500,
            name = "Catalog Feed Series",
            numberOfEpisodes = null,
            seasons = null,
            numberOfSeasons = 1
        )
        val history = mapOf(
            MediaIdHelper.forEpisode(500, 1, 1) to PlaybackProgress(
                mediaId = MediaIdHelper.forEpisode(500, 1, 1),
                title = "Ep 1",
                positionMs = 1000L,
                durationMs = 1000L,
                timestamp = System.currentTimeMillis()
            )
        )

        val progress = TvSeriesProgressCalculator.calculateProgressPercent(series, history)
        assertNotEquals(100, progress)
        assertEquals(10, progress)
    }

    @Test
    fun `calculateProgressPercent correctly parses non-canonical episode IDs and prevents key collisions`() {
        val series = TmdbTvSummary(id = 500, name = "Test Series", numberOfEpisodes = 10)
        val history = mapOf(
            "media_500_s1_e1_1080p" to PlaybackProgress(
                mediaId = "media_500_s1_e1_1080p",
                title = "Ep 1",
                positionMs = 1000L,
                durationMs = 1000L,
                timestamp = System.currentTimeMillis()
            ),
            "media_500_s1_e2_1080p" to PlaybackProgress(
                mediaId = "media_500_s1_e2_1080p",
                title = "Ep 2",
                positionMs = 1000L,
                durationMs = 1000L,
                timestamp = System.currentTimeMillis()
            )
        )

        val progress = TvSeriesProgressCalculator.calculateProgressPercent(series, history)
        // 2 distinct episodes out of 10 = 20% (if key collided into Pair(1,1), it would be 10%)
        assertEquals(20, progress)
    }

    @Test
    fun `calculateProgressPercent excludes Season 0 specials from series progress`() {
        val series = TmdbTvSummary(
            id = 500,
            name = "Test Series",
            numberOfEpisodes = 10,
            seasons = listOf(
                TmdbTvSeason(id = 0, seasonNumber = 0, episodeCount = 3),
                TmdbTvSeason(id = 1, seasonNumber = 1, episodeCount = 10)
            )
        )
        // Only Season 0 Special is watched
        val historySpecialOnly = mapOf(
            MediaIdHelper.forEpisode(500, 0, 1) to PlaybackProgress(
                mediaId = MediaIdHelper.forEpisode(500, 0, 1),
                title = "Special Ep 1",
                positionMs = 1000L,
                durationMs = 1000L,
                isEpisode = true
            )
        )
        assertNull(TvSeriesProgressCalculator.calculateProgressPercent(series, historySpecialOnly))

        // Season 0 Special + 1 regular episode watched
        val historyWithRegular = mapOf(
            MediaIdHelper.forEpisode(500, 0, 1) to PlaybackProgress(
                mediaId = MediaIdHelper.forEpisode(500, 0, 1),
                title = "Special Ep 1",
                positionMs = 1000L,
                durationMs = 1000L,
                isEpisode = true
            ),
            MediaIdHelper.forEpisode(500, 1, 1) to PlaybackProgress(
                mediaId = MediaIdHelper.forEpisode(500, 1, 1),
                title = "Ep 1",
                positionMs = 1000L,
                durationMs = 1000L,
                isEpisode = true
            )
        )
        // 1 regular episode out of 10 available = 10% (Season 0 is not counted)
        assertEquals(10, TvSeriesProgressCalculator.calculateProgressPercent(series, historyWithRegular))
    }

    @Test
    fun `calculateProgressPercent ignores future upcoming season and marks series complete based on available episodes`() {
        val series = TmdbTvSummary(
            id = 500,
            name = "Test Series",
            numberOfEpisodes = 20,
            seasons = listOf(
                TmdbTvSeason(id = 1, seasonNumber = 1, episodeCount = 10, airDate = "2024-01-01"),
                TmdbTvSeason(id = 2, seasonNumber = 2, episodeCount = 10, airDate = "2030-01-01") // Future unreleased season
            )
        )
        // Watch all 10 episodes of Season 1
        val history = (1..10).associate { ep ->
            val id = MediaIdHelper.forEpisode(500, 1, ep)
            id to PlaybackProgress(
                mediaId = id,
                title = "Ep $ep",
                positionMs = 1000L,
                durationMs = 1000L,
                isEpisode = true
            )
        }

        val progress = TvSeriesProgressCalculator.calculateProgressPercent(series, history)
        assertEquals(100, progress)
    }

    @Test
    fun `calculateProgressPercent does not falsely match movies or unrelated franchise titles`() {
        val series = TmdbTvSummary(id = 500, name = "Superman", numberOfEpisodes = 10)
        // History only has a movie titled "Superman Returns"
        val history = mapOf(
            "tt0348150" to PlaybackProgress(
                mediaId = "tt0348150",
                title = "Superman Returns",
                positionMs = 1000L,
                durationMs = 1000L,
                isEpisode = false
            )
        )

        assertNull(TvSeriesProgressCalculator.calculateProgressPercent(series, history))
    }

    @Test
    fun `calculateProgressPercent ignores Season 2 when airDate is null or blank and marks series 100 percent complete`() {
        val series = TmdbTvSummary(
            id = 600,
            name = "Severance",
            numberOfEpisodes = 18,
            seasons = listOf(
                TmdbTvSeason(id = 1, seasonNumber = 1, episodeCount = 9, airDate = "2022-02-18"),
                TmdbTvSeason(id = 2, seasonNumber = 2, episodeCount = 9, airDate = null) // Announced season without air date or torrents
            )
        )
        val history = (1..9).associate { ep ->
            val id = MediaIdHelper.forEpisode(600, 1, ep)
            id to PlaybackProgress(
                mediaId = id,
                title = "Severance - S01E0$ep",
                positionMs = 1000L,
                durationMs = 1000L,
                isEpisode = true
            )
        }

        val progress = TvSeriesProgressCalculator.calculateProgressPercent(series, history)
        assertEquals(100, progress)
        assertEquals(true, TvSeriesProgressCalculator.isSeriesWatched(series, history))
        assertEquals(false, TvSeriesProgressCalculator.isSeriesInProgress(series, history))
        assertEquals(false, TvSeriesProgressCalculator.isSeriesUnwatched(series, history))
    }

    @Test
    fun `calculateProgressPercent ignores Season 2 when episodeCount is 0 or empty chip`() {
        val series = TmdbTvSummary(
            id = 601,
            name = "Loki",
            numberOfEpisodes = 12,
            seasons = listOf(
                TmdbTvSeason(id = 1, seasonNumber = 1, episodeCount = 6, airDate = "2021-06-09"),
                TmdbTvSeason(id = 2, seasonNumber = 2, episodeCount = 0, airDate = null) // Empty season chip
            )
        )
        val history = (1..6).associate { ep ->
            val id = MediaIdHelper.forEpisode(601, 1, ep)
            id to PlaybackProgress(
                mediaId = id,
                title = "Loki - S01E0$ep",
                positionMs = 1000L,
                durationMs = 1000L,
                isEpisode = true
            )
        }

        val progress = TvSeriesProgressCalculator.calculateProgressPercent(series, history)
        assertEquals(100, progress)
        assertEquals(true, TvSeriesProgressCalculator.isSeriesWatched(series, history))
    }

    @Test
    fun `isSeriesInProgress is true when some episodes are watched and not completed`() {
        val series = TmdbTvSummary(
            id = 700,
            name = "In Progress Show",
            numberOfEpisodes = 10,
            seasons = listOf(
                TmdbTvSeason(id = 1, seasonNumber = 1, episodeCount = 10, airDate = "2023-01-01")
            )
        )
        val history = (1..5).associate { ep ->
            val id = MediaIdHelper.forEpisode(700, 1, ep)
            id to PlaybackProgress(
                mediaId = id,
                title = "Ep $ep",
                positionMs = 1000L,
                durationMs = 1000L,
                isEpisode = true
            )
        }

        assertEquals(50, TvSeriesProgressCalculator.calculateProgressPercent(series, history))
        assertEquals(false, TvSeriesProgressCalculator.isSeriesWatched(series, history))
        assertEquals(true, TvSeriesProgressCalculator.isSeriesInProgress(series, history))
        assertEquals(false, TvSeriesProgressCalculator.isSeriesUnwatched(series, history))
    }
}
