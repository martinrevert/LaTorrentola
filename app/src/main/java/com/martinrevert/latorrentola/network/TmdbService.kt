package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.model.TMDB.TmdbActorDetail
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieExternalIds
import com.martinrevert.latorrentola.model.TMDB.TmdbSearchPersonResponse
import com.martinrevert.latorrentola.model.TMDB.TmdbTvExternalIds
import com.martinrevert.latorrentola.model.TMDB.TmdbTvGenrePage
import com.martinrevert.latorrentola.model.TMDB.TmdbTvPage
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeasonDetails
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit endpoints for TMDB people, movie identifiers, and television catalogs. */
interface TmdbService {
    /** Searches TMDB for people matching [query]. */
    @GET("search/person")
    suspend fun searchPerson(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = TmdbLanguage.current()
    ): TmdbSearchPersonResponse

    /** Searches TMDB for TV series matching [query]. */
    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = TmdbLanguage.current()
    ): TmdbTvPage

    /** Retrieves a person's details and requested appended response data. */
    @GET("person/{person_id}")
    suspend fun getPersonDetail(
        @Path("person_id") personId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "combined_credits",
        @Query("language") language: String = TmdbLanguage.current()
    ): TmdbActorDetail

    /** Retrieves external identifiers associated with a TMDB movie. */
    @GET("movie/{movie_id}/external_ids")
    suspend fun getMovieExternalIds(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String
    ): TmdbMovieExternalIds

    /** Retrieves a page of TV series airing during the next seven days. */
    @GET("tv/on_the_air")
    suspend fun getOnTheAirTv(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int,
        @Query("language") language: String = TmdbLanguage.current()
    ): TmdbTvPage

    /** Retrieves a page of series airing today. */
    @GET("tv/airing_today")
    suspend fun getAiringTodayTv(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int,
        @Query("language") language: String = TmdbLanguage.current()
    ): TmdbTvPage

    /** Retrieves a page of series ordered by popularity. */
    @GET("tv/popular")
    suspend fun getPopularTv(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int,
        @Query("language") language: String = TmdbLanguage.current()
    ): TmdbTvPage

    /** Retrieves a page of series ordered by TMDB rating. */
    @GET("tv/top_rated")
    suspend fun getTopRatedTv(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int,
        @Query("language") language: String = TmdbLanguage.current()
    ): TmdbTvPage

    /** Retrieves the available television genres. */
    @GET("genre/tv/list")
    suspend fun getTvGenres(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = TmdbLanguage.current()
    ): TmdbTvGenrePage

    /** Retrieves a sorted page of TV series matching [genreId]. */
    @GET("discover/tv")
    suspend fun discoverTvByGenre(
        @Query("api_key") apiKey: String,
        @Query("with_genres") genreId: Int,
        @Query("sort_by") sortBy: String,
        @Query("page") page: Int,
        @Query("language") language: String = TmdbLanguage.current()
    ): TmdbTvPage

    /** Retrieves TV series details, seasons, and aggregate cast. */
    @GET("tv/{series_id}")
    suspend fun getTvDetails(
        @Path("series_id") seriesId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "aggregate_credits",
        @Query("language") language: String = TmdbLanguage.current()
    ): TmdbTvSummary

    /** Retrieves episode listings and metadata for one season. */
    @GET("tv/{series_id}/season/{season_number}")
    suspend fun getTvSeasonDetails(
        @Path("series_id") seriesId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = TmdbLanguage.current()
    ): TmdbTvSeasonDetails

    /** Retrieves external identifiers associated with a TMDB TV series. */
    @GET("tv/{series_id}/external_ids")
    suspend fun getTvExternalIds(
        @Path("series_id") seriesId: Int,
        @Query("api_key") apiKey: String
    ): TmdbTvExternalIds
}
