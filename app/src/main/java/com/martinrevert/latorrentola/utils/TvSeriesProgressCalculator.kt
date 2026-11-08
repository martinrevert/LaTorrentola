package com.martinrevert.latorrentola.utils

import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Calculates the overall consumption progress percentage of a TV series across regular seasons (S >= 1).
 * Season 0 specials are strictly excluded from series-level completion percentages.
 */
object TvSeriesProgressCalculator {

    /**
     * Checks if [title] matches [seriesName] exactly or with standard episode delimiters
     * (e.g. "Series - S01E01", "Series.S01E01", "Series S01E01").
     * Prevents false positives where a movie or spin-off starts with the same word.
     */
    fun isEpisodeTitleMatchingSeries(title: String, seriesName: String): Boolean {
        if (title.isBlank() || seriesName.isBlank()) return false
        val cleanTitle = title.trim()
        val cleanSeries = seriesName.trim()
        if (cleanTitle.equals(cleanSeries, ignoreCase = true)) return true
        if (cleanTitle.startsWith(cleanSeries, ignoreCase = true) && cleanTitle.length > cleanSeries.length) {
            val delimiter = cleanTitle[cleanSeries.length]
            return delimiter == ' ' || delimiter == '-' || delimiter == '.' || delimiter == '_'
        }
        return false
    }

    /**
     * Computes the total number of regular (season >= 1) episodes that have aired or are available,
     * ignoring upcoming unreleased seasons that do not have torrents or episodes yet.
     */
    fun getAvailableRegularEpisodes(series: TmdbTvSummary): Int {
        val seasons = series.seasons?.filter { it.seasonNumber >= 1 }
        if (seasons.isNullOrEmpty()) {
            return series.numberOfEpisodes?.takeIf { it > 0 }
                ?: series.numberOfSeasons?.takeIf { it > 0 }?.let { it * 10 }
                ?: 0
        }

        val today = LocalDate.now()
        val todayIso = today.toString()
        val currentYear = today.year
        val lastAiredSeason = series.lastEpisodeToAir?.seasonNumber
        val lastAiredEpisode = series.lastEpisodeToAir?.episodeNumber
        val nextAiredSeason = series.nextEpisodeToAir?.seasonNumber
        val nextAiredEpisode = series.nextEpisodeToAir?.episodeNumber
        val hasAnyAirDate = seasons.any { !it.airDate.isNullOrBlank() }

        var totalAvailable = 0
        for (season in seasons) {
            val sNum = season.seasonNumber
            val epCount = season.episodeCount ?: 0
            if (epCount <= 0) continue

            val rawAirDate = season.airDate?.trim()

            // 1. Check if season premiere airDate is in the future or an unannounced upcoming year
            val isAiredByLastEpisode = lastAiredSeason != null && sNum <= lastAiredSeason
            val isFutureAirDate = when {
                rawAirDate.isNullOrBlank() -> false
                rawAirDate.length == 4 -> {
                    val year = rawAirDate.toIntOrNull() ?: 0
                    !isAiredByLastEpisode && (year > currentYear || (year == currentYear && (nextAiredSeason != null && sNum >= nextAiredSeason || (lastAiredSeason != null && sNum > lastAiredSeason) || ((series.isSeriesContinuing || series.inProduction == true) && lastAiredSeason == null))))
                }
                rawAirDate.length == 7 -> "$rawAirDate-01" > todayIso
                else -> rawAirDate > todayIso
            }
            if (isFutureAirDate) {
                continue
            }

            // 2. For seasons beyond season 1 with missing air dates
            if (sNum > 1 && rawAirDate.isNullOrBlank()) {
                if (hasAnyAirDate || series.isSeriesContinuing || series.inProduction == true || lastAiredSeason != null || nextAiredSeason != null) {
                    continue
                }
            }

            // 3. If TMDB provides lastEpisodeToAir with a regular season (seasonNumber > 0)
            if (lastAiredSeason != null && lastAiredSeason > 0) {
                if (sNum > lastAiredSeason) {
                    continue
                }
                if (sNum == lastAiredSeason && lastAiredEpisode != null && lastAiredEpisode > 0) {
                    totalAvailable += minOf(epCount, lastAiredEpisode)
                    continue
                }
            }

            // 4. If TMDB provides nextEpisodeToAir:
            // - Any season strictly after nextAiredSeason has not aired.
            // - The season of nextAiredSeason itself has not aired if episode <= 1 (season premiere upcoming),
            //   or only episodes prior to nextAiredEpisode have aired.
            if (nextAiredSeason != null && nextAiredSeason > 0) {
                if (sNum > nextAiredSeason) {
                    continue
                }
                if (sNum == nextAiredSeason) {
                    if (nextAiredEpisode == null || nextAiredEpisode <= 1) {
                        continue
                    } else {
                        totalAvailable += minOf(epCount, nextAiredEpisode - 1)
                        continue
                    }
                }
            }

            totalAvailable += epCount
        }

        return if (totalAvailable > 0) {
            totalAvailable
        } else {
            0
        }
    }

    /**
     * Builds a pre-indexed episode progress structure from watch history for fast series lookups.
     */
    fun buildEpisodeProgressIndex(
        watchHistory: Collection<PlaybackProgress>
    ): TvSeriesProgressIndex {
        if (watchHistory.isEmpty()) return TvSeriesProgressIndex(emptyMap(), emptyList())
        val byImdb = HashMap<String, MutableList<Pair<EpisodeComponents, PlaybackProgress>>>()
        val allEpisodes = ArrayList<Pair<EpisodeComponents, PlaybackProgress>>()
        val seenMediaIds = HashSet<String>()

        for (progress in watchHistory) {
            if (!seenMediaIds.add(progress.mediaId)) continue
            val isEp = progress.isEpisode || MediaIdHelper.isCanonicalEpisodeId(progress.mediaId)
            if (!isEp) continue

            val parsed = MediaIdHelper.parseCanonicalEpisode(progress.mediaId) ?: continue
            val pair = Pair(parsed, progress)
            allEpisodes.add(pair)

            val normImdb = MediaIdHelper.normalizeImdbId(parsed.imdbId) ?: parsed.imdbId.lowercase()
            byImdb.getOrPut(normImdb) { ArrayList() }.add(pair)
        }
        return TvSeriesProgressIndex(byImdb, allEpisodes)
    }

    /**
     * Evaluates all regular episode progress records associated with [series] using [index]
     * and computes the true overall progress percentage relative to available regular episodes.
     * Season 0 specials are excluded.
     *
     * @param series TMDB TV summary metadata.
     * @param index Pre-indexed episode progress index.
     * @return Overall progress percentage in range `1..100`, or `null` if unwatched.
     */
    fun calculateProgressPercent(
        series: TmdbTvSummary,
        index: TvSeriesProgressIndex
    ): Int? {
        val seriesId = series.id
        var imdbId = series.effectiveImdbId
        val seriesName = series.name?.trim().orEmpty()

        if (imdbId.isNullOrBlank() && seriesName.isNotBlank() && !index.isEmpty) {
            val candidate = index.inferImdbIdForSeries(seriesName)
            if (!candidate.isNullOrBlank()) {
                imdbId = candidate
            }
        }

        if ((seriesId <= 0 && imdbId.isNullOrBlank() && seriesName.isBlank()) || index.isEmpty) return null

        val normImdbId = MediaIdHelper.normalizeImdbId(imdbId) ?: imdbId?.trim()?.lowercase()
        val episodes = index.findEpisodesForSeries(normImdbId, seriesName)
        if (episodes.isEmpty()) return null

        val episodeEntries = mutableMapOf<Pair<Int, Int>, Float>()
        for ((parsed, progress) in episodes) {
            val sNum = parsed.seasonNumber
            val eNum = parsed.episodeNumber

            // Season 0 specials are excluded from series overall progress calculation
            if (sNum <= 0) continue

            val epKey = Pair(sNum, eNum)

            val weight = if (progress.isCompleted || progress.progressPercent >= 95) {
                1.0f
            } else {
                (progress.progressPercent.coerceIn(0, 100) / 100.0f)
            }

            val currentWeight = episodeEntries[epKey] ?: 0f
            if (weight > currentWeight) {
                episodeEntries[epKey] = weight
            }
        }

        val totalConsumed = episodeEntries.values.sum()
        if (totalConsumed <= 0f) return null

        val availableEpisodes = getAvailableRegularEpisodes(series)
        val estimatedTotal = when {
            availableEpisodes > 0 -> availableEpisodes
            series.numberOfEpisodes != null && series.numberOfEpisodes > 0 -> series.numberOfEpisodes
            series.numberOfSeasons != null && series.numberOfSeasons > 0 -> series.numberOfSeasons * 10
            episodeEntries.isNotEmpty() -> maxOf(episodeEntries.keys.maxOfOrNull { it.second } ?: episodeEntries.size, 10)
            else -> 10
        }
        val totalEpisodes = maxOf(estimatedTotal, episodeEntries.size)
        if (totalEpisodes <= 0) return null

        val rawPercent = ((totalConsumed / totalEpisodes.toFloat()) * 100f).roundToInt()
        return rawPercent.coerceIn(1, 100)
    }

    /**
     * Evaluates all regular episode progress records associated with [series] in [watchHistoryMap]
     * and computes the true overall progress percentage relative to available regular episodes.
     * Season 0 specials are excluded.
     *
     * @param series TMDB TV summary metadata.
     * @param watchHistoryMap Active watch progress map keyed by canonical media ID or title.
     * @return Overall progress percentage in range `1..100`, or `null` if unwatched.
     */
    fun calculateProgressPercent(
        series: TmdbTvSummary,
        watchHistoryMap: Map<String, PlaybackProgress>
    ): Int? {
        if (watchHistoryMap.isEmpty()) return null
        val index = buildEpisodeProgressIndex(watchHistoryMap.values)
        return calculateProgressPercent(series, index)
    }

    /**
     * Determines whether [series] is considered fully watched (100% progress) using [index].
     */
    fun isSeriesWatched(
        series: TmdbTvSummary,
        index: TvSeriesProgressIndex
    ): Boolean {
        val pct = calculateProgressPercent(series, index) ?: return false
        return pct >= 100
    }

    /**
     * Determines whether [series] is considered fully watched (100% progress).
     * Unified single source of truth aligning with [calculateProgressPercent].
     */
    fun isSeriesWatched(
        series: TmdbTvSummary,
        watchHistoryMap: Map<String, PlaybackProgress>
    ): Boolean {
        val pct = calculateProgressPercent(series, watchHistoryMap) ?: return false
        return pct >= 100
    }

    /**
     * Determines whether [series] is in progress (partially watched, 1..99%) using [index].
     */
    fun isSeriesInProgress(
        series: TmdbTvSummary,
        index: TvSeriesProgressIndex
    ): Boolean {
        val pct = calculateProgressPercent(series, index) ?: return false
        return pct in 1..99
    }

    /**
     * Determines whether [series] is in progress (partially watched, 1..99%).
     * Unified single source of truth aligning with [calculateProgressPercent].
     */
    fun isSeriesInProgress(
        series: TmdbTvSummary,
        watchHistoryMap: Map<String, PlaybackProgress>
    ): Boolean {
        val pct = calculateProgressPercent(series, watchHistoryMap) ?: return false
        return pct in 1..99
    }

    /**
     * Determines whether [series] is unwatched (0% progress or null) using [index].
     */
    fun isSeriesUnwatched(
        series: TmdbTvSummary,
        index: TvSeriesProgressIndex
    ): Boolean {
        val pct = calculateProgressPercent(series, index)
        return pct == null || pct == 0
    }

    /**
     * Determines whether [series] is unwatched (0% progress or null).
     * Unified single source of truth aligning with [calculateProgressPercent].
     */
    fun isSeriesUnwatched(
        series: TmdbTvSummary,
        watchHistoryMap: Map<String, PlaybackProgress>
    ): Boolean {
        val pct = calculateProgressPercent(series, watchHistoryMap)
        return pct == null || pct == 0
    }
}

/**
 * Lightweight, pre-indexed episode progress index for fast O(1) series progress lookups.
 */
class TvSeriesProgressIndex(
    private val byImdb: Map<String, List<Pair<EpisodeComponents, PlaybackProgress>>>,
    private val allEpisodes: List<Pair<EpisodeComponents, PlaybackProgress>>
) {
    fun findEpisodesForSeries(
        normImdbId: String?,
        seriesName: String
    ): List<Pair<EpisodeComponents, PlaybackProgress>> {
        if (!normImdbId.isNullOrBlank()) {
            val byId = byImdb[normImdbId]
            if (!byId.isNullOrEmpty()) {
                return byId
            }
        }
        if (seriesName.isNotBlank()) {
            return allEpisodes.filter { (_, progress) ->
                TvSeriesProgressCalculator.isEpisodeTitleMatchingSeries(progress.title, seriesName)
            }
        }
        return emptyList()
    }

    fun inferImdbIdForSeries(seriesName: String): String? {
        if (seriesName.isBlank()) return null
        for ((parsed, progress) in allEpisodes) {
            if (TvSeriesProgressCalculator.isEpisodeTitleMatchingSeries(progress.title, seriesName)) {
                if (parsed.imdbId.isNotBlank()) return parsed.imdbId
            }
        }
        return null
    }

    val isEmpty: Boolean get() = allEpisodes.isEmpty()
}
