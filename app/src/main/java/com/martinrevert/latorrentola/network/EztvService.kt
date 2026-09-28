package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.model.EZTV.EztvResponse
import retrofit2.http.GET
import retrofit2.http.Query

/** Retrofit endpoints for the EZTV television torrent catalog. */
interface EztvService {

    /**
     * Retrieves torrent releases associated with a numeric IMDb identifier.
     *
     * @param imdbId Numeric IMDb identifier (e.g., "0944947" or "4154756").
     * @param page Page index requested.
     * @param limit Maximum results returned per page (default 100).
     */
    @GET("get-torrents")
    suspend fun getTorrents(
        @Query("imdb_id") imdbId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): EztvResponse
}
