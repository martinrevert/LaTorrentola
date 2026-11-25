package com.martinrevert.latorrentola.database

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.TMDB.TmdbTvExternalIds
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeason
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import org.junit.Test

class TvProgressCacheEntityTest {

    @Test
    fun `from and toTmdbTvSummary preserve seasons air schedule status and canonical imdbId`() {
        val original = TmdbTvSummary(
            id = 1399,
            name = "Game of Thrones",
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            firstAirDate = "2011-04-17",
            voteAverage = 8.4,
            status = "Ended",
            inProduction = false,
            numberOfSeasons = 8,
            numberOfEpisodes = 73,
            lastEpisodeToAir = TmdbTvEpisode(id = 999, seasonNumber = 8, episodeNumber = 6, name = "The Iron Throne"),
            nextEpisodeToAir = null,
            seasons = listOf(
                TmdbTvSeason(id = 1, seasonNumber = 1, episodeCount = 10, airDate = "2011-04-17"),
                TmdbTvSeason(id = 2, seasonNumber = 2, episodeCount = 10, airDate = "2012-04-01")
            ),
            externalIds = TmdbTvExternalIds(imdbId = "tt0944947")
        )

        val entity = TvProgressCacheEntity.from(original, cachedAt = 1_000_000L)
        val restored = entity.toTmdbTvSummary()

        assertThat(restored.id).isEqualTo(1399)
        assertThat(restored.effectiveImdbId).isEqualTo("tt0944947")
        assertThat(restored.name).isEqualTo("Game of Thrones")
        assertThat(restored.status).isEqualTo("Ended")
        assertThat(restored.inProduction).isFalse()
        assertThat(restored.seasons).hasSize(2)
        assertThat(restored.seasons?.first()?.episodeCount).isEqualTo(10)
        assertThat(restored.lastEpisodeToAir?.seasonNumber).isEqualTo(8)
        assertThat(restored.lastEpisodeToAir?.episodeNumber).isEqualTo(6)
    }

    @Test
    fun `toTmdbTvSummary degrades gracefully when persisted json is malformed`() {
        val corrupted = TvProgressCacheEntity(
            seriesId = 500,
            imdbId = "tt0500000",
            name = "Broken JSON Show",
            seasonsJson = "{not-valid-json",
            lastEpisodeToAirJson = "[invalid",
            nextEpisodeToAirJson = "<<<"
        )

        val restored = corrupted.toTmdbTvSummary()

        assertThat(restored.id).isEqualTo(500)
        assertThat(restored.seasons).isNull()
        assertThat(restored.lastEpisodeToAir).isNull()
        assertThat(restored.nextEpisodeToAir).isNull()
    }

    @Test
    fun `isStale uses 6 hour TTL for ongoing series and 7 day TTL for ended series`() {
        val baseTime = 1_700_000_000_000L
        val ongoing = TvProgressCacheEntity(
            seriesId = 1,
            status = "Returning Series",
            inProduction = true,
            cachedAt = baseTime
        )
        val ended = TvProgressCacheEntity(
            seriesId = 2,
            status = "Ended",
            inProduction = false,
            cachedAt = baseTime
        )

        val after5Hours = baseTime + (5L * 60L * 60L * 1000L)
        val after7Hours = baseTime + (7L * 60L * 60L * 1000L)
        val after8Days = baseTime + (8L * 24L * 60L * 60L * 1000L)

        assertThat(ongoing.isStale(after5Hours)).isFalse()
        assertThat(ongoing.isStale(after7Hours)).isTrue()

        assertThat(ended.isStale(after7Hours)).isFalse()
        assertThat(ended.isStale(after8Days)).isTrue()
    }
}
