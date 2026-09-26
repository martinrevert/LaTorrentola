package com.martinrevert.latorrentola.utils

import com.martinrevert.latorrentola.model.YTS.Movie

/** Applies the user's language, torrent quality, and rating constraints to movie results. */
object MovieFilter {
    /**
     * Filters optional [movies] using comma-separated excluded languages and optional criteria.
     *
     * @param movies Source movies; `null` produces an empty list.
     * @param excludedLanguages Comma-separated language codes to exclude.
     * @param selectedQuality Optional torrent quality; `All` disables quality filtering.
     * @param minimumRating Optional lower rating bound; `null` or zero disables it.
     * @return Movies matching every active criterion.
     */
    fun filterMovies(
        movies: List<Movie>?,
        excludedLanguages: String,
        selectedQuality: String? = null,
        minimumRating: Int? = null
    ): List<Movie> {
        if (movies == null) return emptyList()
        
        val excludedList = excludedLanguages.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            
        return movies.filter { movie ->
            // Filter by language
            val movieLang = movie.language?.lowercase() ?: ""
            val langMatch = excludedList.isEmpty() || !excludedList.contains(movieLang)
            
            // Filter by quality
            val qualityMatch = if (selectedQuality == null || selectedQuality == "All") {
                true
            } else {
                movie.torrents?.any { torrent ->
                    when (selectedQuality) {
                        "1080p.x265" -> torrent.quality == "1080p" && torrent.type == "x265"
                        else -> torrent.quality == selectedQuality
                    }
                } ?: false
            }

            val ratingMatch = minimumRating == null || minimumRating == 0 ||
                (movie.rating?.toDoubleOrNull()?.let { it >= minimumRating.toDouble() } ?: false)
            
            langMatch && qualityMatch && ratingMatch
        }
    }
}
