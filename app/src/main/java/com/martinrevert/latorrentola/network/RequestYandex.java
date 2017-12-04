package com.martinrevert.latorrentola.network;

import com.martinrevert.latorrentola.model.Yandex.Summary;

import io.reactivex.Observable;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Created by martin on 04/12/17.
 */
public interface RequestYandex {

        @GET("translate")
        Observable<Summary> getTranslate(@Query("key") String api,
                                         @Query("text") String limit,
                                         @Query("lang") String minimum_rating);


    }
