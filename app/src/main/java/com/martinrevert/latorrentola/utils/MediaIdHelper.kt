package com.martinrevert.latorrentola.utils

import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.user.PlaybackProgress

/**
 * Parsed components of an episode media identifier.
 *
 * Implements backward-compatible destructuring and properties:
 * - [first]: Int seriesId if numeric, or 0 if IMDb identifier.
 * - [second]: season number.
 * - [third]: episode number.
 * - [seriesIdentifier]: raw series identifier string (e.g. "tt0944947" or "111803").
 * - [imdbId]: normalized IMDb identifier if series identifier starts with "tt", or null.
 */
data class EpisodeComponents(
    val first: Int,
    val second: Int,
    val third: Int,
    val seriesIdentifier: String = if (first > 0) first.toString() else ""
) {
    val seasonNumber: Int get() = second
    val episodeNumber: Int get() = third
    val imdbId: String? get() = if (seriesIdentifier.startsWith("tt", ignoreCase = true)) seriesIdentifier else null

    /** Returns true if this episode matches the given TMDB series ID or IMDb ID. */
    fun matchesSeries(
        seriesId: Int,
        targetImdbId: String? = null,
        normTarget: String? = null
    ): Boolean {
        if (seriesId > 0 && first == seriesId) return true
        if (!targetImdbId.isNullOrBlank()) {
            val effectiveNormTarget = normTarget ?: MediaIdHelper.normalizeImdbId(targetImdbId)
            val normSelf = imdbId?.let { MediaIdHelper.normalizeImdbId(it) }
            if (!effectiveNormTarget.isNullOrBlank() && !normSelf.isNullOrBlank() && effectiveNormTarget.equals(normSelf, ignoreCase = true)) {
                return true
            }
            if (imdbId != null && seriesIdentifier.equals(targetImdbId, ignoreCase = true)) return true
        }
        return false
    }
}

/**
 * Utility object for generating and validating canonical, unique media identifiers (`mediaId`)
 * across Movies and TV Episodes for watch history tracking, favorites, downloads, and Firestore documents.
 *
 * Prioritizes global industry-standard IMDb identifiers (`tt...`) to ensure decoupling from
 * proprietary catalog backends (TMDB/YTS) while supporting legacy formats transparently.
 */
object MediaIdHelper {

    private val CANONICAL_IMDB_MOVIE_REGEX = Regex("""^tt\d{5,10}$""", RegexOption.IGNORE_CASE)
    private val CANONICAL_IMDB_EPISODE_REGEX = Regex("""^(tt\d{5,10})_s(\d+)_e(\d+)$""", RegexOption.IGNORE_CASE)
    private val DETERMINISTIC_FALLBACK_MOVIE_REGEX = Regex("""^tmdb_(\d+)$""", RegexOption.IGNORE_CASE)
    private val DETERMINISTIC_FALLBACK_EPISODE_REGEX = Regex("""^tmdb_(\d+)_s(\d+)_e(\d+)$""", RegexOption.IGNORE_CASE)
    private val LEGACY_EPISODE_REGEX = Regex("""^(?:media|tv)_(\d+)_s(\d+)_e(\d+)$""", RegexOption.IGNORE_CASE)
    private val INFO_HASH_REGEX = Regex("""^[0-9a-fA-F]{40}$""")
    private val MOVIE_EXTENSION_REGEX = Regex("""\.(mp4|mkv|avi|mov|wmv|webm|ts|m4v)$""", RegexOption.IGNORE_CASE)

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
     * Generates a canonical [mediaId] for a movie.
     * Prioritizes normalized IMDb ID (e.g. "tt0111161").
     * Falls back to a sanitized [title] if IMDb is not yet resolved.
     */
    fun forMovie(
        imdbId: String? = null,
        movieId: Int = 0,
        title: String? = null
    ): String {
        val normalizedImdb = normalizeImdbId(imdbId)
        return when {
            normalizedImdb != null -> normalizedImdb
            movieId > 0 -> "tmdb_$movieId"
            !title.isNullOrBlank() -> {
                val cleaned = title.replace(MOVIE_EXTENSION_REGEX, "").trim()
                cleaned.ifBlank { "unknown_movie" }
            }
            else -> "unknown_movie"
        }
    }

    /**
     * Backward-compatible overload for movie identifier without IMDb ID.
     */
    fun forMovie(movieId: Int, title: String? = null): String {
        return forMovie(imdbId = null, movieId = movieId, title = title)
    }

    /**
     * Backward-compatible overload ignoring [infoHash].
     */
    fun forMovie(movieId: Int, infoHash: String?, title: String?): String {
        return forMovie(imdbId = null, movieId = movieId, title = title)
    }

    /**
     * Generates a canonical [mediaId] for a TV episode.
     * Prioritizes IMDb format strictly as `{imdbId}_s{seasonNumber}_e{episodeNumber}` (e.g. "tt0944947_s1_e2").
     */
    fun forEpisode(
        imdbId: String? = null,
        seriesId: Int = 0,
        seasonNumber: Int,
        episodeNumber: Int,
        title: String? = null
    ): String {
        val normalizedImdb = normalizeImdbId(imdbId)
        return when {
            normalizedImdb != null && seasonNumber >= 0 && episodeNumber > 0 ->
                "${normalizedImdb}_s${seasonNumber}_e${episodeNumber}"
            seriesId > 0 && seasonNumber >= 0 && episodeNumber > 0 ->
                "tmdb_${seriesId}_s${seasonNumber}_e${episodeNumber}"
            seasonNumber >= 0 && episodeNumber > 0 && !title.isNullOrBlank() -> {
                val clean = MediaMetadataResolver.extractCleanSearchTitles(title).firstOrNull() ?: title.trim()
                "${clean}_s${seasonNumber}_e${episodeNumber}"
            }
            !title.isNullOrBlank() -> title.trim()
            else -> "unknown_episode"
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
     * Checks whether [candidate] is a 40-character hex torrent SHA-1 infoHash.
     */
    fun isInfoHash(candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return false
        return INFO_HASH_REGEX.matches(candidate.trim())
    }

    /**
     * Parses seriesIdentifier, seasonNumber, and episodeNumber from an episode [mediaId].
     * Supports canonical IMDb ("tt11111111_s1_e2"), deterministic fallback ("tmdb_111803_s1_e2"),
     * and legacy formats ("media_111803_s1_e2", "tv_111803_s1_e2") for migration.
     */
    fun parseCanonicalEpisode(mediaId: String): EpisodeComponents? {
        val imdbMatch = CANONICAL_IMDB_EPISODE_REGEX.matchEntire(mediaId)
        if (imdbMatch != null) {
            val imdbId = imdbMatch.groupValues[1]
            val season = imdbMatch.groupValues[2].toIntOrNull() ?: return null
            val episode = imdbMatch.groupValues[3].toIntOrNull() ?: return null
            if (season < 0 || episode <= 0) return null
            return EpisodeComponents(first = 0, second = season, third = episode, seriesIdentifier = imdbId)
        }
        val tmdbMatch = DETERMINISTIC_FALLBACK_EPISODE_REGEX.matchEntire(mediaId)
        if (tmdbMatch != null) {
            val seriesId = tmdbMatch.groupValues[1].toIntOrNull() ?: return null
            val season = tmdbMatch.groupValues[2].toIntOrNull() ?: return null
            val episode = tmdbMatch.groupValues[3].toIntOrNull() ?: return null
            if (seriesId <= 0 || season < 0 || episode <= 0) return null
            return EpisodeComponents(first = seriesId, second = season, third = episode, seriesIdentifier = seriesId.toString())
        }
        val legacyMatch = LEGACY_EPISODE_REGEX.matchEntire(mediaId)
        if (legacyMatch != null) {
            val seriesId = legacyMatch.groupValues[1].toIntOrNull() ?: return null
            val season = legacyMatch.groupValues[2].toIntOrNull() ?: return null
            val episode = legacyMatch.groupValues[3].toIntOrNull() ?: return null
            if (seriesId <= 0 || season < 0 || episode <= 0) return null
            return EpisodeComponents(first = seriesId, second = season, third = episode, seriesIdentifier = seriesId.toString())
        }
        return null
    }

    /**
     * Attempts to find playback progress for [movie] in [watchHistoryMap] by evaluating canonical IMDb ID,
     * TMDB ID ("tmdb_{id}"), legacy numeric ID, legacy "media_{id}", and movie title.
     */
    fun findMovieProgress(
        movie: Movie,
        watchHistoryMap: Map<String, PlaybackProgress>
    ): PlaybackProgress? {
        if (watchHistoryMap.isEmpty()) return null

        // 1. Check canonical IMDb ID
        val normImdb = normalizeImdbId(movie.imdbCode)
        if (normImdb != null) {
            watchHistoryMap[normImdb]?.takeUnless { it.isEpisode }?.let { return it }
        }

        // 2. Check TMDB deterministic ID, numeric ID, and legacy media_ prefix
        if (movie.id > 0) {
            watchHistoryMap["tmdb_${movie.id}"]?.takeUnless { it.isEpisode }?.let { return it }
            watchHistoryMap[movie.id.toString()]?.takeUnless { it.isEpisode }?.let { return it }
            watchHistoryMap["media_${movie.id}"]?.takeUnless { it.isEpisode }?.let { return it }
        }

        // 3. Check exact title and trimmed lowercase title
        val title = movie.title
        if (!title.isNullOrBlank()) {
            watchHistoryMap[title]?.takeUnless { it.isEpisode }?.let { return it }
            watchHistoryMap[title.trim().lowercase()]?.takeUnless { it.isEpisode }?.let { return it }
        }

        return null
    }

    /**
     * Backward-compatible overload for finding movie progress by fields.
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
     * Builds a comprehensive lookup map from a list of [PlaybackProgress] items,
     * keying each entry by its primary [PlaybackProgress.mediaId] as well as canonical and legacy aliases.
     */
    fun buildWatchHistoryMap(
        list: List<PlaybackProgress>
    ): Map<String, PlaybackProgress> {
        val map = HashMap<String, PlaybackProgress>(list.size * 3)
        for (item in list) {
            map[item.mediaId] = item
            val normalizedId = normalizeImdbId(item.mediaId)
            if (normalizedId != null) {
                map.putIfAbsent(normalizedId, item)
            }
            if (!item.isEpisode && item.title.isNotBlank()) {
                map.putIfAbsent(item.title, item)
                map.putIfAbsent(item.title.trim().lowercase(), item)
            }
            val ep = parseCanonicalEpisode(item.mediaId)
            if (ep != null) {
                if (ep.first > 0) {
                    map.putIfAbsent("tmdb_${ep.first}_s${ep.second}_e${ep.third}", item)
                    map.putIfAbsent("tv_${ep.first}_s${ep.second}_e${ep.third}", item)
                    map.putIfAbsent("media_${ep.first}_s${ep.second}_e${ep.third}", item)
                }
                if (!ep.imdbId.isNullOrBlank()) {
                    map.putIfAbsent("${ep.imdbId}_s${ep.second}_e${ep.third}", item)
                }
            } else if (isCanonicalMovieId(item.mediaId)) {
                val movieId = item.mediaId.removePrefix("tmdb_").removePrefix("media_").toIntOrNull()
                if (movieId != null && movieId > 0) {
                    map.putIfAbsent("tmdb_$movieId", item)
                    map.putIfAbsent(movieId.toString(), item)
                    map.putIfAbsent("media_$movieId", item)
                }
            } else {
                val numId = item.mediaId.removePrefix("media_").removePrefix("tmdb_").toIntOrNull()
                if (numId != null && numId > 0) {
                    map.putIfAbsent("tmdb_$numId", item)
                    map.putIfAbsent(numId.toString(), item)
                    map.putIfAbsent("media_$numId", item)
                }
            }
        }
        return map
    }
}
