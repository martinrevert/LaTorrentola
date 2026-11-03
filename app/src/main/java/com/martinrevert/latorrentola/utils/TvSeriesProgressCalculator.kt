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

        val todayIso = LocalDate.now().toString()
        val lastAiredSeason = series.lastEpisodeToAir?.seasonNumber
        val lastAiredEpisode = series.lastEpisodeToAir?.episodeNumber
        val nextAiredSeason = series.nextEpisodeToAir?.seasonNumber
        val hasAnyAirDate = seasons.any { !it.airDate.isNullOrBlank() }

        var totalAvailable = 0
        for (season in seasons) {
            val sNum = season.seasonNumber
            val epCount = season.episodeCount ?: 0
            if (epCount <= 0) continue

            // If air date is in the future, season hasn't aired and has no torrents
            val airDate = season.airDate
            if (!airDate.isNullOrBlank() && airDate > todayIso) {
                continue
            }

            // For seasons beyond season 1 with missing air date when other seasons have air dates,
            // or when series is actively continuing/in production, it has not aired yet.
            if (sNum > 1 && airDate.isNullOrBlank()) {
                if (hasAnyAirDate || series.isSeriesContinuing || series.inProduction == true || lastAiredSeason != null) {
                    continue
                }
            }

            // If TMDB provides lastEpisodeToAir, seasons after that haven't aired
            if (lastAiredSeason != null && lastAiredSeason > 0) {
                if (sNum > lastAiredSeason) {
                    continue
                }
                if (sNum == lastAiredSeason && lastAiredEpisode != null && lastAiredEpisode > 0) {
                    totalAvailable += minOf(epCount, lastAiredEpisode)
                    continue
                }
            }

            // If TMDB provides nextEpisodeToAir, seasons strictly after next episode haven't aired
            if (nextAiredSeason != null && nextAiredSeason > 0 && sNum > nextAiredSeason) {
                continue
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
        val seriesId = series.id
        var imdbId = series.effectiveImdbId
        val seriesName = series.name?.trim().orEmpty()

        // Infer missing IMDb ID from watchHistoryMap if episode titles match series name
        if (imdbId.isNullOrBlank() && seriesName.isNotBlank() && watchHistoryMap.isNotEmpty()) {
            for ((_, progress) in watchHistoryMap) {
                if (progress.isEpisode && isEpisodeTitleMatchingSeries(progress.title, seriesName)) {
                    val candidate = MediaIdHelper.parseCanonicalEpisode(progress.mediaId)?.imdbId
                    if (!candidate.isNullOrBlank()) {
                        imdbId = candidate
                        break
                    }
                }
            }
        }

        if ((seriesId <= 0 && imdbId.isNullOrBlank() && seriesName.isBlank()) || watchHistoryMap.isEmpty()) return null

        val imdbPrefix = imdbId?.let { "${it}_s" }
        val tmdbPrefix = if (seriesId > 0) "tmdb_${seriesId}_s" else null
        val episodeEntries = mutableMapOf<Pair<Int, Int>, Float>()

        watchHistoryMap.forEach { (mediaId, progress) ->
            val parsed = MediaIdHelper.parseCanonicalEpisode(mediaId)
            val fallbackPair = if (parsed == null) {
                Regex("""_s(\d+)_e(\d+)""", RegexOption.IGNORE_CASE).find(mediaId)?.let { match ->
                    val s = match.groupValues[1].toIntOrNull() ?: 1
                    val e = match.groupValues[2].toIntOrNull() ?: 1
                    Pair(s, e)
                }
            } else null

            // An episode must either be flagged as such, or have a parsed/regex episode structure
            val isEpisodeCandidate = progress.isEpisode || parsed != null || fallbackPair != null
            if (!isEpisodeCandidate) return@forEach

            val matchesSeries = if (parsed != null) {
                parsed.matchesSeries(seriesId, imdbId) ||
                    ((seriesId <= 0 && imdbId.isNullOrBlank() || (parsed.first <= 0 && parsed.imdbId.isNullOrBlank())) &&
                        seriesName.isNotBlank() && isEpisodeTitleMatchingSeries(progress.title, seriesName))
            } else {
                (imdbPrefix != null && mediaId.startsWith(imdbPrefix)) ||
                    (tmdbPrefix != null && mediaId.startsWith(tmdbPrefix)) ||
                    (seriesId > 0 && mediaId.contains("_${seriesId}_s")) ||
                    (seriesName.isNotBlank() && isEpisodeTitleMatchingSeries(progress.title, seriesName))
            }

            if (matchesSeries) {
                val sNum = parsed?.second ?: fallbackPair?.first ?: return@forEach
                val eNum = parsed?.third ?: fallbackPair?.second ?: return@forEach

                // Season 0 specials are excluded from series overall progress calculation
                if (sNum <= 0) return@forEach

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
