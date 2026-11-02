package com.martinrevert.latorrentola.utils

import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import kotlin.math.roundToInt

/**
 * Calculates the overall consumption progress percentage of a TV series across all seasons and episodes.
 */
object TvSeriesProgressCalculator {

    /**
     * Evaluates all episode progress records associated with [series] in [watchHistoryMap]
     * and computes the true overall progress percentage relative to total series episodes.
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
        if (seriesId <= 0 || watchHistoryMap.isEmpty()) return null

        val seriesIdPrefix = "media_${seriesId}_s"
        val episodeEntries = mutableMapOf<Pair<Int, Int>, Float>()

        watchHistoryMap.forEach { (mediaId, progress) ->
            val parsed = MediaIdHelper.parseCanonicalEpisode(mediaId)
            val matchesSeries = if (parsed != null) {
                parsed.first == seriesId
            } else {
                mediaId.startsWith(seriesIdPrefix) ||
                    (progress.isEpisode && mediaId.contains("_${seriesId}_s"))
            }

            if (matchesSeries) {
                val fallbackPair = if (parsed == null) {
                    Regex("""_s(\d+)_e(\d+)""", RegexOption.IGNORE_CASE).find(mediaId)?.let { match ->
                        val s = match.groupValues[1].toIntOrNull() ?: 1
                        val e = match.groupValues[2].toIntOrNull() ?: 1
                        Pair(s, e)
                    }
                } else null

                val sNum = parsed?.second ?: fallbackPair?.first ?: 1
                val eNum = parsed?.third ?: fallbackPair?.second ?: 1
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

        val hasKnownTotal = (series.numberOfEpisodes != null && series.numberOfEpisodes > 0) ||
            series.seasons?.any { (it.episodeCount ?: 0) > 0 } == true

        val totalEpisodes = series.numberOfEpisodes?.takeIf { it > 0 }
            ?: series.seasons?.sumOf { it.episodeCount ?: 0 }?.takeIf { it > 0 }
            ?: (series.numberOfSeasons?.takeIf { it > 0 }?.let { it * 10 })
            ?: maxOf((episodeEntries.keys.maxOfOrNull { it.second } ?: episodeEntries.size) * 2, 10)

        val rawPercent = ((totalConsumed / totalEpisodes.toFloat()) * 100f).roundToInt()
        return if (hasKnownTotal) {
            rawPercent.coerceIn(1, 100)
        } else {
            rawPercent.coerceIn(1, 99)
        }
    }
}
