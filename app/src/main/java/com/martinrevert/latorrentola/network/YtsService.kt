package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.model.YTS.MovieDetails
import retrofit2.http.GET
import retrofit2.http.Query

interface YtsService {

    @GET("list_movies.json")
    suspend fun getMovieDetails(
        @Query("limit") limit: Int,
        @Query("minimum_rating") minimumRating: String,
        @Query("page") page: Int,
        @Query("with_rt_ratings") rtRatings: String
    ): MovieDetails

    @GET("list_movies.json")
    suspend fun getMovieSearch(
        @Query("limit") limit: Int,
        @Query("query_term") query: String
    ): MovieDetails

    @GET("list_movies.json")
    suspend fun getGenreSearch(
        @Query("limit") limit: Int,
        @Query("genre") query: String,
        @Query("page") currentPage: Int
    ): MovieDetails

    @GET("list_movies.json")
    suspend fun getTridiSearch(
        @Query("limit") limit: Int,
        @Query("quality") query: String,
        @Query("page") currentPage: Int
    ): MovieDetails
}
