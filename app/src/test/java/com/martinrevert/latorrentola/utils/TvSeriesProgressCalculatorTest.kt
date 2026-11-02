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
}
