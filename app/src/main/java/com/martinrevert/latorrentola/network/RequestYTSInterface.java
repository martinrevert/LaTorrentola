package com.martinrevert.latorrentola.network;

import com.martinrevert.latorrentola.model.YTS.MovieDetails;

import io.reactivex.Observable;

import retrofit2.http.GET;
import retrofit2.http.Query;

public interface RequestYTSInterface {

    @GET("list_movies.json")
    Observable<MovieDetails> getMovieDetails(@Query("limit") int limit,
                                             @Query("minimum_rating") String minimum_rating,
                                             @Query("page") int page);

    @GET("list_movies.json")
    Observable<MovieDetails> getMovieSearch(@Query("limit") int limit,
                                            @Query("query_term") String query);

    @GET("list_movies.json")
    Observable<MovieDetails> getGenreSearch(@Query("limit") int limit,
                                            @Query("genre") String query,
                                            @Query("page") int currentpage);

    @GET("list_movies.json")
    Observable<MovieDetails> getTridiSearch(@Query("limit") int limit,
                                            @Query("quality") String query,
                                            @Query("page") int currentpage);

}
