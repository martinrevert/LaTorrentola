package com.martinrevert.latorrentola.utils

import com.martinrevert.latorrentola.model.YTS.Movie

object MovieFilter {
    fun filterMovies(movies: List<Movie>?, excludedLanguages: String): List<Movie> {
        if (movies == null) return emptyList()
        if (excludedLanguages.isBlank()) return movies
        
        val excludedList = excludedLanguages.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            
        if (excludedList.isEmpty()) return movies
        
        return movies.filter { movie ->
            val movieLang = movie.language?.lowercase() ?: ""
            !excludedList.contains(movieLang)
        }
    }
}
