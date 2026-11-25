package com.martinrevert.latorrentola.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeason
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import java.util.Locale

/**
 * Device-shared, offline-first Room cache of lightweight TMDB TV series catalog metadata
 * required to compute series watch progress (seasons, air dates, last/next episode, and status).
 *
 * Deliberately stored in a standalone table decoupled from `favorites_tv` and Firestore sync so
 * cloud library synchronization never overwrites cached TMDB season schedules.
 */
@Entity(tableName = "tv_progress_cache")
data class TvProgressCacheEntity(
    @PrimaryKey
    val seriesId: Int,
    val imdbId: String = "",
    val name: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val firstAirDate: String? = null,
    val voteAverage: Double? = null,
    val status: String? = null,
    val inProduction: Boolean? = null,
    val numberOfSeasons: Int? = null,
    val numberOfEpisodes: Int? = null,
    val seasonsJson: String? = null,
    val lastEpisodeToAirJson: String? = null,
    val nextEpisodeToAirJson: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
) {
    /** Converts this persisted cache record back into a lightweight [TmdbTvSummary]. */
    fun toTmdbTvSummary(): TmdbTvSummary {
        val seasons: List<TmdbTvSeason>? = seasonsJson?.takeIf(String::isNotBlank)?.let { json ->
            try {
                gson.fromJson<List<TmdbTvSeason>>(json, seasonListType)
            } catch (_: Exception) {
                null
            }
        }
        val lastEpisode: TmdbTvEpisode? = lastEpisodeToAirJson?.takeIf(String::isNotBlank)?.let { json ->
            try {
                gson.fromJson(json, TmdbTvEpisode::class.java)
            } catch (_: Exception) {
                null
            }
        }
        val nextEpisode: TmdbTvEpisode? = nextEpisodeToAirJson?.takeIf(String::isNotBlank)?.let { json ->
            try {
                gson.fromJson(json, TmdbTvEpisode::class.java)
            } catch (_: Exception) {
                null
            }
        }
        return TmdbTvSummary(
            id = seriesId,
            imdbId = imdbId.ifBlank { null },
            name = name.ifBlank { null },
            posterPath = posterPath,
            backdropPath = backdropPath,
            firstAirDate = firstAirDate,
            voteAverage = voteAverage,
            status = status,
            inProduction = inProduction,
            numberOfSeasons = numberOfSeasons,
            numberOfEpisodes = numberOfEpisodes,
            lastEpisodeToAir = lastEpisode,
            nextEpisodeToAir = nextEpisode,
            seasons = seasons
        )
    }

    /**
     * Returns `true` when this cache record should be silently refreshed in the background.
     * Ended/canceled series use a longer TTL ([ENDED_SERIES_TTL_MS]) than returning series ([ONGOING_SERIES_TTL_MS]).
     */
    fun isStale(now: Long = System.currentTimeMillis()): Boolean {
        val normalizedStatus = status?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val isEnded = inProduction != true &&
            nextEpisodeToAirJson.isNullOrBlank() &&
            (normalizedStatus == "ended" || normalizedStatus == "canceled" || normalizedStatus == "cancelled")
        val ttlMs = if (isEnded) ENDED_SERIES_TTL_MS else ONGOING_SERIES_TTL_MS
        return (now - cachedAt) >= ttlMs
    }

    companion object {
        /** Silent refresh interval for returning or in-production TV series (6 hours). */
        const val ONGOING_SERIES_TTL_MS = 6L * 60L * 60L * 1000L

        /** Silent refresh interval for ended or canceled TV series (7 days). */
        const val ENDED_SERIES_TTL_MS = 7L * 24L * 60L * 60L * 1000L

        private val gson = Gson()
        private val seasonListType = object : TypeToken<List<TmdbTvSeason>>() {}.type

        /** Creates a cache entity from a TMDB TV summary or full detail response. */
        fun from(
            summary: TmdbTvSummary,
            cachedAt: Long = System.currentTimeMillis()
        ): TvProgressCacheEntity {
            val seasonsJson = summary.seasons?.takeIf { it.isNotEmpty() }?.let { gson.toJson(it) }
            val lastEpJson = summary.lastEpisodeToAir?.let { gson.toJson(it) }
            val nextEpJson = summary.nextEpisodeToAir?.let { gson.toJson(it) }
            return TvProgressCacheEntity(
                seriesId = summary.id,
                imdbId = summary.effectiveImdbId ?: "",
                name = summary.name.orEmpty(),
                posterPath = summary.posterPath,
                backdropPath = summary.backdropPath,
                firstAirDate = summary.firstAirDate,
                voteAverage = summary.voteAverage,
                status = summary.status,
                inProduction = summary.inProduction,
                numberOfSeasons = summary.numberOfSeasons,
                numberOfEpisodes = summary.numberOfEpisodes,
                seasonsJson = seasonsJson,
                lastEpisodeToAirJson = lastEpJson,
                nextEpisodeToAirJson = nextEpJson,
                cachedAt = cachedAt
            )
        }
    }
}

/** Room DAO for offline-first TMDB TV progress metadata caching. */
@Dao
interface TvProgressCacheDao {
    @Query("SELECT * FROM tv_progress_cache WHERE seriesId = :seriesId LIMIT 1")
    suspend fun get(seriesId: Int): TvProgressCacheEntity?

    @Query("SELECT * FROM tv_progress_cache WHERE seriesId IN (:seriesIds)")
    suspend fun getByIds(seriesIds: List<Int>): List<TvProgressCacheEntity>

    @Query("SELECT * FROM tv_progress_cache WHERE imdbId IN (:imdbIds)")
    suspend fun getByImdbIds(imdbIds: List<String>): List<TvProgressCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TvProgressCacheEntity)
}
