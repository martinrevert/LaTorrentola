package com.martinrevert.latorrentola.utils

/**
 * Utility object for generating canonical, unique media identifiers (`mediaId`)
 * across Movies and TV Episodes for watch history tracking and Firestore documents.
 */
object MediaIdHelper {

    /**
     * Generates a canonical [mediaId] for a movie.
     * Uses [movieId] as a string if valid (> 0), falling back to [infoHash] or [title].
     */
    fun forMovie(movieId: Int, infoHash: String? = null, title: String? = null): String {
        return when {
            movieId > 0 -> movieId.toString()
            !infoHash.isNullOrBlank() -> infoHash.trim()
            !title.isNullOrBlank() -> title.trim()
            else -> "unknown_movie"
        }
    }

    /**
     * Generates a canonical [mediaId] for a TV episode.
     * Formats as `media_{seriesId}_s{seasonNumber}_e{episodeNumber}` if valid,
     * or falls back to [infoHash] / [title].
     */
    fun forEpisode(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        infoHash: String? = null,
        title: String? = null
    ): String {
        return when {
            seriesId > 0 && seasonNumber > 0 && episodeNumber > 0 ->
                "media_${seriesId}_s${seasonNumber}_e${episodeNumber}"
            !infoHash.isNullOrBlank() -> infoHash.trim()
            !title.isNullOrBlank() -> title.trim()
            else -> "unknown_episode"
        }
    }
}
