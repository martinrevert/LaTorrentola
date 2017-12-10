package com.martinrevert.latorrentola.network;

import com.martinrevert.latorrentola.model.argenteam.MovieDetails;
import com.martinrevert.latorrentola.model.argenteam.Results;

import io.reactivex.Observable;

import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface RequestArgenteamInterface {

    @POST("search/{imdb}")
    Observable<Results> getMovie(@Path("imdb") String imdb);

    @GET("movie/{id}")
    Observable<MovieDetails> getMovieId(@Path ("id") Integer id);


}
