package com.martinrevert.latorrentola.utils

import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.user.PlaybackProgress

/**
 * Parsed components of an episode media identifier.
 *
 * Implements backward-compatible destructuring and properties:
 * - [first]: Int seriesId (always 0 in pure IMDb format).
 * - [second]: season number.
 * - [third]: episode number.
 * - [seriesIdentifier]: raw series identifier string (e.g. "tt0944947").
 * - [imdbId]: normalized IMDb identifier (e.g. "tt0944947").
 */
data class EpisodeComponents(
    val imdbId: String,
    val seasonNumber: Int,
    val episodeNumber: Int
) {
    val first: Int get() = 0
    val second: Int get() = seasonNumber
    val third: Int get() = episodeNumber
    val seriesIdentifier: String get() = imdbId

    /** Returns true if this episode matches the given target IMDb ID. */
    fun matchesSeries(
        targetImdbId: String? = null,
        normTarget: String? = null
    ): Boolean {
        if (!targetImdbId.isNullOrBlank()) {
            val effectiveNormTarget = normTarget ?: MediaIdHelper.normalizeImdbId(targetImdbId)
            val normSelf = MediaIdHelper.normalizeImdbId(imdbId)
            if (!effectiveNormTarget.isNullOrBlank() && !normSelf.isNullOrBlank() && effectiveNormTarget.equals(normSelf, ignoreCase = true)) {
                return true
            }
            if (seriesIdentifier.equals(targetImdbId, ignoreCase = true)) return true
        }
        return false
    }

    @Deprecated("seriesId is unused in pure IMDb canonical model", ReplaceWith("matchesSeries(targetImdbId, normTarget)"))
    fun matchesSeries(
        seriesId: Int,
        targetImdbId: String?,
        normTarget: String? = null
    ): Boolean = matchesSeries(targetImdbId, normTarget)
}

/**
 * Utility object for generating and validating canonical, unique media identifiers (`mediaId`)
 * across Movies and TV Episodes for watch history tracking, favorites, downloads, and Firestore documents.
 *
 * Exclusively works with global industry-standard IMDb identifiers (`tt...`) to ensure total decoupling
 * from proprietary catalog backends (TMDB/YTS) with zero legacy overhead.
 */
object MediaIdHelper {

    private val CANONICAL_IMDB_MOVIE_REGEX = Regex("""^tt\d{5,10}$""", RegexOption.IGNORE_CASE)
    private val CANONICAL_IMDB_EPISODE_REGEX = Regex("""^(tt\d{5,10})_s(\d+)_e(\d+)$""", RegexOption.IGNORE_CASE)

    /**
     * Normalizes a raw IMDb identifier candidate into the standard "tt1234567" format.
     * Uses zero-allocation, zero-regex char checks for maximum performance in high-frequency loops.
     */
    fun normalizeImdbId(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val len = trimmed.length

        // Case 1: Standard "tt1234567" (7 to 12 chars total: "tt" + 5..10 digits)
        if (len in 7..12 && trimmed.startsWith("tt", ignoreCase = true)) {
            var allDigits = true
            for (i in 2 until len) {
                if (!trimmed[i].isDigit()) {
                    allDigits = false
                    break
                }
            }
            if (allDigits) {
                return trimmed.lowercase()
            }
        }

        // Case 2: Raw numeric string "1234567" (5 to 10 digits)
        if (len in 5..10) {
            var allDigits = true
            for (i in 0 until len) {
                if (!trimmed[i].isDigit()) {
                    allDigits = false
                    break
                }
            }
            if (allDigits) {
                return "tt$trimmed"
            }
        }

        return null
    }

    /**
     * Generates a canonical [mediaId] for a movie strictly in IMDb format (e.g. "tt0111161").
     */
    fun forMovie(imdbId: String?): String {
        return normalizeImdbId(imdbId) ?: "unknown_movie"
    }

    /**
     * Overload for movie identifier with optional parameters for backward compatibility.
     */
    fun forMovie(
        imdbId: String? = null,
        movieId: Int = 0,
        title: String? = null
    ): String {
        return normalizeImdbId(imdbId) ?: "unknown_movie"
    }

    /**
     * Backward-compatible overload ignoring [infoHash].
     */
    fun forMovie(movieId: Int, infoHash: String?, title: String?): String {
        return forMovie(imdbId = null, movieId = movieId, title = title)
    }

    /**
     * Backward-compatible overload for movie identifier without IMDb ID.
     */
    fun forMovie(movieId: Int, title: String? = null): String {
        return forMovie(imdbId = null, movieId = movieId, title = title)
    }

    /**
     * Generates a canonical [mediaId] for a TV episode strictly in IMDb format:
     * `{imdbId}_s{seasonNumber}_e{episodeNumber}` (e.g. "tt0944947_s1_e2").
     */
    fun forEpisode(
        imdbId: String?,
        seasonNumber: Int,
        episodeNumber: Int
    ): String {
        val normalizedImdb = normalizeImdbId(imdbId)
        return if (normalizedImdb != null && seasonNumber >= 0 && episodeNumber > 0) {
            "${normalizedImdb}_s${seasonNumber}_e${episodeNumber}"
        } else {
            "unknown_episode"
        }
    }

    /**
     * Overload for episode identifier with optional parameters for backward compatibility.
     */
    fun forEpisode(
        imdbId: String? = null,
        seriesId: Int = 0,
        seasonNumber: Int,
        episodeNumber: Int,
        title: String? = null
    ): String {
        val normalizedImdb = normalizeImdbId(imdbId)
        return if (normalizedImdb != null && seasonNumber >= 0 && episodeNumber > 0) {
            "${normalizedImdb}_s${seasonNumber}_e${episodeNumber}"
        } else {
            "unknown_episode"
        }
    }

    /**
     * Backward-compatible overload for episode identifier without IMDb ID.
     */
    fun forEpisode(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        title: String? = null
    ): String {
        return forEpisode(imdbId = null, seriesId = seriesId, seasonNumber = seasonNumber, episodeNumber = episodeNumber, title = title)
    }

    /**
     * Backward-compatible overload ignoring [infoHash].
     */
    fun forEpisode(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        infoHash: String?,
        title: String?
    ): String {
        return forEpisode(imdbId = null, seriesId = seriesId, seasonNumber = seasonNumber, episodeNumber = episodeNumber, title = title)
    }

    /**
     * Checks if [mediaId] is a canonical movie ID (IMDb "tt1234567").
     */
    fun isCanonicalMovieId(mediaId: String): Boolean {
        if (mediaId.isBlank()) return false
        return CANONICAL_IMDB_MOVIE_REGEX.matches(mediaId)
    }

    /**
     * Checks if [mediaId] adheres to the canonical episode pattern (IMDb "{imdbId}_s{s}_e{e}").
     */
    fun isCanonicalEpisodeId(mediaId: String): Boolean {
        if (mediaId.isBlank()) return false
        val imdbMatch = CANONICAL_IMDB_EPISODE_REGEX.matchEntire(mediaId)
        if (imdbMatch != null) {
            val season = imdbMatch.groupValues[2].toIntOrNull() ?: return false
            val episode = imdbMatch.groupValues[3].toIntOrNull() ?: return false
            return season >= 0 && episode > 0
        }
        return false
    }

    /**
     * Returns true if [mediaId] is strictly canonical IMDb format.
     */
    fun isCanonical(mediaId: String): Boolean {
        return isCanonicalMovieId(mediaId) || isCanonicalEpisodeId(mediaId)
    }

    /**
     * Parses seriesIdentifier, seasonNumber, and episodeNumber from an episode [mediaId].
     * Exclusively parses canonical IMDb format ("tt11111111_s1_e2").
     */
    fun parseCanonicalEpisode(mediaId: String): EpisodeComponents? {
        val imdbMatch = CANONICAL_IMDB_EPISODE_REGEX.matchEntire(mediaId) ?: return null
        val imdbId = imdbMatch.groupValues[1]
        val season = imdbMatch.groupValues[2].toIntOrNull() ?: return null
        val episode = imdbMatch.groupValues[3].toIntOrNull() ?: return null
        if (season < 0 || episode <= 0) return null
        return EpisodeComponents(imdbId = imdbId, seasonNumber = season, episodeNumber = episode)
    }

    /**
     * Finds playback progress for [movie] in [watchHistoryMap] exclusively by its canonical IMDb ID.
     */
    fun findMovieProgress(
        movie: Movie,
        watchHistoryMap: Map<String, PlaybackProgress>
    ): PlaybackProgress? {
        if (watchHistoryMap.isEmpty()) return null
        val normImdb = normalizeImdbId(movie.imdbCode) ?: return null
        return watchHistoryMap[normImdb]?.takeUnless { it.isEpisode }
    }

    /**
     * Overload for finding movie progress by fields.
     */
    fun findMovieProgress(
        movieId: Int,
        imdbCode: String?,
        title: String?,
        watchHistoryMap: Map<String, PlaybackProgress>
    ): PlaybackProgress? {
        val dummy = Movie(
            id = movieId,
            imdbCode = imdbCode,
            title = title
        )
        return findMovieProgress(dummy, watchHistoryMap)
    }

    /**
     * Builds a lean lookup map from a list of [PlaybackProgress] items,
     * keying each entry directly by its canonical [PlaybackProgress.mediaId].
     */
    fun buildWatchHistoryMap(
        list: List<PlaybackProgress>
    ): Map<String, PlaybackProgress> {
        val initialCapacity = (list.size * 1.5).toInt().coerceAtLeast(16)
        val map = HashMap<String, PlaybackProgress>(initialCapacity)
        for (item in list) {
            map[item.mediaId] = item
            val normalizedId = normalizeImdbId(item.mediaId)
            if (normalizedId != null && normalizedId != item.mediaId) {
                map.putIfAbsent(normalizedId, item)
            }
            if (item.isEpisode || isCanonicalEpisodeId(item.mediaId)) {
                val lower = item.mediaId.lowercase()
                if (lower != item.mediaId) {
                    map.putIfAbsent(lower, item)
                }
            }
        }
        return map
    }
}
