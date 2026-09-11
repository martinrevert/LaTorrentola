package com.martinrevert.latorrentola.utils

import com.martinrevert.latorrentola.model.YTS.Movie

object MovieFilter {
    fun filterMovies(
        movies: List<Movie>?,
        excludedLanguages: String,
        selectedQuality: String? = null
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
            
            langMatch && qualityMatch
        }
    }
}
