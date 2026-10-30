package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.model.omdb.OmdbMovieResponse
import retrofit2.http.GET
import retrofit2.http.Query

/** Retrofit contract for querying movie ratings and details from OMDb API. */
interface OmdbService {

    /**
     * Looks up movie details and ratings using the movie's IMDb identifier.
     *
     * @param apiKey Authorized OMDb API key.
     * @param imdbId IMDb identifier starting with 'tt'.
     */
    @GET("/")
    suspend fun getMovieByImdbId(
        @Query("apikey") apiKey: String,
        @Query("i") imdbId: String
    ): OmdbMovieResponse
}
