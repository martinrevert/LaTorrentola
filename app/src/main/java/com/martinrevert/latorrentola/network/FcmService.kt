package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.model.fcm.RecentMovie
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface FcmService {

    @POST("api/subscriptions/subscribe")
    suspend fun subscribe(
        @Query("token") token: String
    ): Response<Unit>

    @POST("api/subscriptions/unsubscribe")
    suspend fun unsubscribe(
        @Query("token") token: String
    ): Response<Unit>

    @GET("api/movies/recent")
    suspend fun getRecentMovieIds(): List<RecentMovie>
}
