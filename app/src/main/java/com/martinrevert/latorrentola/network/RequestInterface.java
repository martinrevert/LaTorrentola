package com.martinrevert.latorrentola.network;

import com.martinrevert.latorrentola.model.Results;

import io.reactivex.Observable;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

public interface RequestInterface {

    @FormUrlEncoded
    @POST("search")
    Observable<Results> getMovies(@Field("movieFilter") String moviefilter,
                                  @Field("yearFilter") String year,
                                  @Field("advanced") String advanced,
                                @Field("hdFilter") String hdFilter);
}
