package com.martinrevert.latorrentola.network

import android.util.Log
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.EZTV.matchesEpisode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accesses torrent releases from EZTV and filters results by episode.
 *
 * @property eztvService Retrofit client for the EZTV API.
 */
@Singleton
class EztvRepository @Inject constructor(
    private val eztvService: EztvService
) {

    /**
     * Fetches torrent releases from EZTV for a series IMDb identifier,
     * filtering by season and episode number.
     *
     * @param imdbId Numeric IMDb identifier (without "tt" prefix).
     * @param seasonNumber Season number to filter.
     * @param episodeNumber Episode number within season to filter.
     * @return List of matching torrent releases, or empty list on network failure.
     */
    suspend fun getTorrentsForEpisode(
        imdbId: String,
        seasonNumber: Int,
        episodeNumber: Int
    ): List<EztvTorrent> {
        if (imdbId.isBlank()) return emptyList()
        val cleanImdbId = imdbId.removePrefix("tt")

        return try {
            val response = eztvService.getTorrents(
                imdbId = cleanImdbId,
                page = 1,
                limit = 100
            )

            response.torrents.filter { torrent ->
                torrent.matchesEpisode(seasonNumber, episodeNumber)
            }.sortedByDescending { it.seeds }
        } catch (e: Exception) {
            Log.e("EztvRepository", "Error fetching EZTV torrents for IMDb ID $cleanImdbId: ${e.message}")
            emptyList()
        }
    }
}
