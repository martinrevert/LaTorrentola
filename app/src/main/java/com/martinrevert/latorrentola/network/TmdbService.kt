package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.model.TMDB.TmdbActorDetail
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieExternalIds
import com.martinrevert.latorrentola.model.TMDB.TmdbSearchPersonResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit endpoints used to search TMDB people and retrieve movie identifiers. */
interface TmdbService {
    /** Searches TMDB for people matching [query]. */
    @GET("search/person")
    suspend fun searchPerson(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "es-ES"
    ): TmdbSearchPersonResponse

    /** Retrieves a person's details and requested appended response data. */
    @GET("person/{person_id}")
    suspend fun getPersonDetail(
        @Path("person_id") personId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "combined_credits",
        @Query("language") language: String = "es-ES"
    ): TmdbActorDetail

    /** Retrieves external identifiers associated with a TMDB movie. */
    @GET("movie/{movie_id}/external_ids")
    suspend fun getMovieExternalIds(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String
    ): TmdbMovieExternalIds
}
