package com.martinrevert.latorrentola.network;

import com.martinrevert.latorrentola.model.YTS.MovieDetails;

import io.reactivex.Observable;

import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface RequestYTSInterface {

    @GET("list_movies.json")
    Observable<MovieDetails> getMovieDetails(@Query("limit") String limit,
                                             @Query("minimum_rating") String minimum_rating);

}
