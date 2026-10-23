package com.martinrevert.latorrentola.utils

/**
 * Utility object for generating and validating canonical, unique media identifiers (`mediaId`)
 * across Movies and TV Episodes for watch history tracking and Firestore documents.
 *
 * Mandated by AGENTS.md:
 * - Movies MUST be identified by their canonical catalog numeric string (e.g. `"12230"`).
 * - Episodes MUST be identified by the mnemonic `"media_{seriesId}_s{seasonNumber}_e{episodeNumber}"`.
 * - 40-character torrent SHA1 infoHashes or raw media paths MUST NEVER be used as media identifiers.
 */
object MediaIdHelper {

    private val CANONICAL_EPISODE_REGEX = Regex("""^media_(\d+)_s(\d+)_e(\d+)$""", RegexOption.IGNORE_CASE)
    private val LEGACY_EPISODE_REGEX = Regex("""^media_.*_s(\d+)_e(\d+)$""", RegexOption.IGNORE_CASE)
    private val INFO_HASH_REGEX = Regex("""^[0-9a-fA-F]{40}$""")

    /**
     * Generates a canonical [mediaId] for a movie.
     * Uses [movieId] as a string if valid (> 0).
     * Falls back to a sanitized [title] if [movieId] is not yet resolved.
     * infoHash is explicitly ignored and rejected as a mediaId.
     */
    fun forMovie(movieId: Int, title: String? = null): String {
        return when {
            movieId > 0 -> movieId.toString()
            !title.isNullOrBlank() -> {
                val cleaned = title.replace(Regex("""\.(mp4|mkv|avi|mov|wmv|webm|ts|m4v)$""", RegexOption.IGNORE_CASE), "").trim()
                cleaned.ifBlank { "unknown_movie" }
            }
            else -> "unknown_movie"
        }
    }

    /**
     * Backward-compatible overload ignoring [infoHash].
     */
    fun forMovie(movieId: Int, infoHash: String?, title: String?): String {
        return forMovie(movieId, title)
    }

    /**
     * Generates a canonical [mediaId] for a TV episode.
     * Formats strictly as `media_{seriesId}_s{seasonNumber}_e{episodeNumber}` if valid (> 0).
     */
    fun forEpisode(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        title: String? = null
    ): String {
        return when {
            seriesId > 0 && seasonNumber > 0 && episodeNumber > 0 ->
                "media_${seriesId}_s${seasonNumber}_e${episodeNumber}"
            seasonNumber > 0 && episodeNumber > 0 && !title.isNullOrBlank() -> {
                val clean = MediaMetadataResolver.extractCleanSearchTitles(title).firstOrNull() ?: title.trim()
                "media_${clean}_s${seasonNumber}_e${episodeNumber}"
            }
            !title.isNullOrBlank() -> title.trim()
            else -> "unknown_episode"
        }
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
        return forEpisode(seriesId, seasonNumber, episodeNumber, title)
    }

    /**
     * Checks if [mediaId] is a canonical movie ID (strictly numeric string, e.g. "12230").
     */
    fun isCanonicalMovieId(mediaId: String): Boolean {
        if (mediaId.isBlank()) return false
        return mediaId.all { it.isDigit() } && (mediaId.toLongOrNull() ?: 0L) > 0L
    }

    /**
     * Checks if [mediaId] adheres strictly to the canonical episode pattern `media_{seriesId}_s{season}_e{episode}`.
     */
    fun isCanonicalEpisodeId(mediaId: String): Boolean {
        if (mediaId.isBlank()) return false
        val match = CANONICAL_EPISODE_REGEX.matchEntire(mediaId) ?: return false
        val seriesId = match.groupValues[1].toIntOrNull() ?: return false
        val season = match.groupValues[2].toIntOrNull() ?: return false
        val episode = match.groupValues[3].toIntOrNull() ?: return false
        return seriesId > 0 && season > 0 && episode > 0
    }

    /**
     * Returns true if [mediaId] is strictly canonical (either numeric movie ID or canonical episode ID).
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
     * Parses seriesId, seasonNumber, and episodeNumber from a canonical episode [mediaId].
     */
    fun parseCanonicalEpisode(mediaId: String): Triple<Int, Int, Int>? {
        val match = CANONICAL_EPISODE_REGEX.matchEntire(mediaId) ?: return null
        val seriesId = match.groupValues[1].toIntOrNull() ?: return null
        val season = match.groupValues[2].toIntOrNull() ?: return null
        val episode = match.groupValues[3].toIntOrNull() ?: return null
        if (seriesId <= 0 || season <= 0 || episode <= 0) return null
        return Triple(seriesId, season, episode)
    }
}
