package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.model.fcm.RecentMovie
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Retrofit contract for device subscriptions and recently announced movies. */
interface FcmService {

    /** Registers the device token with the notification backend. */
    @POST("api/subscriptions/subscribe")
    suspend fun subscribe(
        @Query("token") token: String
    ): Response<Unit>

    /** Removes the device token from the notification backend. */
    @POST("api/subscriptions/unsubscribe")
    suspend fun unsubscribe(
        @Query("token") token: String
    ): Response<Unit>

    /** Retrieves the movie entries recently announced by the backend. */
    @GET("api/movies/recent")
    suspend fun getRecentMovieIds(): List<RecentMovie>
}
