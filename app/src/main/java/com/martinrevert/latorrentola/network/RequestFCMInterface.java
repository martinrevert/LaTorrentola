package com.martinrevert.latorrentola.network;

import io.reactivex.Completable;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface RequestFCMInterface {

    @POST("subscription")
    Completable subscribeToTopic(@Query("token") String token);
}
