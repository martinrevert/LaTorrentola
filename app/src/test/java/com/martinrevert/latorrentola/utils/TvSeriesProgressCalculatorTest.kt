package com.martinrevert.latorrentola.utils

import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeason
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import java.time.LocalDate
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

    @Test
    fun `series with unconsumed Season 0 specials, 4 completed seasons, and upcoming Season 5 is marked WATCHED`() {
        val series = TmdbTvSummary(
            id = 800,
            name = "Epic Show",
            numberOfEpisodes = 48,
            numberOfSeasons = 5,
            status = "Returning Series",
            inProduction = true,
            nextEpisodeToAir = TmdbTvEpisode(
                id = 9991,
                seasonNumber = 5,
                episodeNumber = 1,
                airDate = "2026-12-01"
            ),
            seasons = listOf(
                TmdbTvSeason(id = 80, seasonNumber = 0, episodeCount = 6, airDate = "2019-01-01"), // S0 Specials unconsumed
                TmdbTvSeason(id = 81, seasonNumber = 1, episodeCount = 10, airDate = "2019-06-01"),
                TmdbTvSeason(id = 82, seasonNumber = 2, episodeCount = 10, airDate = "2020-06-01"),
                TmdbTvSeason(id = 83, seasonNumber = 3, episodeCount = 10, airDate = "2021-06-01"),
                TmdbTvSeason(id = 84, seasonNumber = 4, episodeCount = 10, airDate = "2022-06-01"),
                TmdbTvSeason(id = 85, seasonNumber = 5, episodeCount = 8, airDate = "2026-12-01") // Upcoming season
            )
        )

        // User watched all 40 episodes of seasons 1 through 4 (and did not watch S0 specials)
        val history = mutableMapOf<String, PlaybackProgress>()
        for (s in 1..4) {
            for (e in 1..10) {
                val mediaId = MediaIdHelper.forEpisode(800, s, e)
                history[mediaId] = PlaybackProgress(
                    mediaId = mediaId,
                    title = "Epic Show S0${s}E%02d".format(e),
                    positionMs = 2000L,
                    durationMs = 2000L,
                    isEpisode = true
                )
            }
        }

        val progress = TvSeriesProgressCalculator.calculateProgressPercent(series, history)
        assertEquals(100, progress)
        assertEquals(true, TvSeriesProgressCalculator.isSeriesWatched(series, history))
        assertEquals(false, TvSeriesProgressCalculator.isSeriesInProgress(series, history))
        assertEquals(false, TvSeriesProgressCalculator.isSeriesUnwatched(series, history))
    }

    @Test
    fun `series with Season 0 specials, 4 completed seasons, and upcoming Season 5 with year-only air date is marked WATCHED`() {
        val series = TmdbTvSummary(
            id = 801,
            name = "Epic Show 2",
            numberOfEpisodes = 48,
            numberOfSeasons = 5,
            status = "Returning Series",
            inProduction = true,
            lastEpisodeToAir = TmdbTvEpisode(
                id = 9990,
                seasonNumber = 0, // A special was the most recent episode to air!
                episodeNumber = 1,
                airDate = "2024-12-01"
            ),
            seasons = listOf(
                TmdbTvSeason(id = 80, seasonNumber = 0, episodeCount = 3, airDate = "2024-12-01"),
                TmdbTvSeason(id = 81, seasonNumber = 1, episodeCount = 10, airDate = "2020-01-01"),
                TmdbTvSeason(id = 82, seasonNumber = 2, episodeCount = 10, airDate = "2021-01-01"),
                TmdbTvSeason(id = 83, seasonNumber = 3, episodeCount = 10, airDate = "2022-01-01"),
                TmdbTvSeason(id = 84, seasonNumber = 4, episodeCount = 10, airDate = "2023-01-01"),
                TmdbTvSeason(id = 85, seasonNumber = 5, episodeCount = 8, airDate = "2026") // Year-only release window
            )
        )

        val history = mutableMapOf<String, PlaybackProgress>()
        for (s in 1..4) {
            for (e in 1..10) {
                val mediaId = MediaIdHelper.forEpisode(801, s, e)
                history[mediaId] = PlaybackProgress(
                    mediaId = mediaId,
                    title = "Epic Show 2 S0${s}E%02d".format(e),
                    positionMs = 2000L,
                    durationMs = 2000L,
                    isEpisode = true
                )
            }
        }

        val progress = TvSeriesProgressCalculator.calculateProgressPercent(series, history)
        assertEquals(100, progress)
        assertEquals(true, TvSeriesProgressCalculator.isSeriesWatched(series, history))
        assertEquals(false, TvSeriesProgressCalculator.isSeriesInProgress(series, history))
    }

    @Test
    fun `calculateProgressPercent does not drop past regular seasons with year-only air dates`() {
        val series = TmdbTvSummary(
            id = 850,
            name = "Classic Anime",
            numberOfEpisodes = 24,
            numberOfSeasons = 2,
            seasons = listOf(
                TmdbTvSeason(id = 1, seasonNumber = 1, episodeCount = 12, airDate = "2020"),
                TmdbTvSeason(id = 2, seasonNumber = 2, episodeCount = 12, airDate = "2021")
            )
        )
        // User watched all 12 episodes of Season 1
        val history = (1..12).associate { ep ->
            val id = MediaIdHelper.forEpisode(850, 1, ep)
            id to PlaybackProgress(
                mediaId = id,
                title = "Ep $ep",
                positionMs = 1000L,
                durationMs = 1000L,
                isEpisode = true
            )
        }

        // Available episodes must be 24 (12 + 12), not 12
        val progress = TvSeriesProgressCalculator.calculateProgressPercent(series, history)
        assertEquals(50, progress)
        assertEquals(false, TvSeriesProgressCalculator.isSeriesWatched(series, history))
        assertEquals(true, TvSeriesProgressCalculator.isSeriesInProgress(series, history))
    }

    @Test
    fun `calculateProgressPercent retains latest regular season when last episode to air is Season 0 special`() {
        val series = TmdbTvSummary(
            id = 851,
            name = "Sherlock Style",
            numberOfEpisodes = 6,
            numberOfSeasons = 2,
            status = "Returning Series",
            inProduction = true,
            lastEpisodeToAir = TmdbTvEpisode(
                id = 9992,
                seasonNumber = 0, // Special aired after S2
                episodeNumber = 1,
                airDate = "2024-12-25"
            ),
            seasons = listOf(
                TmdbTvSeason(id = 10, seasonNumber = 0, episodeCount = 1, airDate = "2024-12-25"),
                TmdbTvSeason(id = 11, seasonNumber = 1, episodeCount = 3, airDate = "2020-01-01"),
                TmdbTvSeason(id = 12, seasonNumber = 2, episodeCount = 3, airDate = "2022-01-01")
            )
        )

        // User watched only Season 1 (3 episodes out of 6 regular episodes)
        val history = (1..3).associate { ep ->
            val id = MediaIdHelper.forEpisode(851, 1, ep)
            id to PlaybackProgress(
                mediaId = id,
                title = "Ep $ep",
                positionMs = 1000L,
                durationMs = 1000L,
                isEpisode = true
            )
        }

        // Season 2 must NOT be dropped, so progress must be 50%, not 100%
        val progress = TvSeriesProgressCalculator.calculateProgressPercent(series, history)
        assertEquals(50, progress)
        assertEquals(false, TvSeriesProgressCalculator.isSeriesWatched(series, history))
        assertEquals(true, TvSeriesProgressCalculator.isSeriesInProgress(series, history))
    }

    @Test
    fun `calculateProgressPercent includes current-year season when lastEpisodeToAir confirms it has aired`() {
        val series = TmdbTvSummary(
            id = 852,
            name = "Airing Hit Show",
            numberOfEpisodes = 20,
            numberOfSeasons = 2,
            status = "Returning Series",
            inProduction = true,
            lastEpisodeToAir = TmdbTvEpisode(
                id = 9993,
                seasonNumber = 2,
                episodeNumber = 10,
                airDate = "2026-06-01"
            ),
            seasons = listOf(
                TmdbTvSeason(id = 21, seasonNumber = 1, episodeCount = 10, airDate = "2024-01-01"),
                TmdbTvSeason(id = 22, seasonNumber = 2, episodeCount = 10, airDate = "2026") // Year-only current year
            )
        )

        // User watched only Season 1
        val history = (1..10).associate { ep ->
            val id = MediaIdHelper.forEpisode(852, 1, ep)
            id to PlaybackProgress(
                mediaId = id,
                title = "Ep $ep",
                positionMs = 1000L,
                durationMs = 1000L,
                isEpisode = true
            )
        }

        // Season 2 has aired, so available episodes is 20, progress is 50%
        val progress = TvSeriesProgressCalculator.calculateProgressPercent(series, history)
        assertEquals(50, progress)
        assertEquals(false, TvSeriesProgressCalculator.isSeriesWatched(series, history))
    }

    @Test
    fun `calculateProgressPercent treats month-precision air date as aired once premiere month begins`() {
        val currentMonthIso = LocalDate.now().toString().take(7) // e.g. "2026-11"
        val series = TmdbTvSummary(
            id = 853,
            name = "Monthly Premiere Show",
            numberOfEpisodes = 10,
            numberOfSeasons = 1,
            seasons = listOf(
                TmdbTvSeason(id = 31, seasonNumber = 1, episodeCount = 10, airDate = currentMonthIso)
            )
        )

        // User watched 5 episodes of Season 1
        val history = (1..5).associate { ep ->
            val id = MediaIdHelper.forEpisode(853, 1, ep)
            id to PlaybackProgress(
                mediaId = id,
                title = "Ep $ep",
                positionMs = 1000L,
                durationMs = 1000L,
                isEpisode = true
            )
        }

        // Season premiered in current month, so available episodes is 10, progress is 50%
        val progress = TvSeriesProgressCalculator.calculateProgressPercent(series, history)
        assertEquals(50, progress)
        assertEquals(true, TvSeriesProgressCalculator.isSeriesInProgress(series, history))
    }
}
