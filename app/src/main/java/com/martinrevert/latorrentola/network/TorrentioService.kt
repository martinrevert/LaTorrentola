package com.martinrevert.latorrentola.network

import com.martinrevert.latorrentola.model.torrentio.TorrentioResponse
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit interface for the Torrentio Stremio addon public API.
 */
interface TorrentioService {

    /**
     * Retrieves available torrent streams for a movie given its IMDb identifier (e.g. "tt1375666").
     *
     * @param imdbId IMDb identifier formatted with the "tt" prefix.
     */
    @GET("stream/movie/{imdbId}.json")
    suspend fun getMovieStreams(
        @Path("imdbId") imdbId: String
    ): TorrentioResponse

    /**
     * Retrieves available torrent streams for a TV series episode (e.g. "tt0903747:1:1").
     *
     * @param seriesParam IMDb identifier, season, and episode formatted as "{imdbId}:{season}:{episode}".
     */
    @GET("stream/series/{seriesParam}.json")
    suspend fun getSeriesStreams(
        @Path("seriesParam") seriesParam: String
    ): TorrentioResponse
}
