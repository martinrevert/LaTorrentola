package com.martinrevert.latorrentola.network

import android.util.Log
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.torrent.TorrentRelease
import com.martinrevert.latorrentola.model.torrent.VideoQualityGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for TV episode releases and next episode navigation metadata.
 * Unifies scraping across EZTV and Torrentio providers and eliminates duplicate
 * resolution and fetching logic across ViewModels.
 */
@Singleton
class TvEpisodeReleasesRepository @Inject constructor(
    private val tmdbRepository: TmdbRepository,
    private val eztvRepository: EztvRepository,
    private val torrentioRepository: TorrentioRepository
) {
    companion object {
        private const val TAG = "TvEpisodeReleasesRepo"
    }

    /**
     * Resolves metadata for the next episode in the same season or the first episode of the next season.
     *
     * @return A list containing [seriesId, seriesName, seasonNumber, episodeNumber, episodeName]
     *         or null if there is no next episode or if information is unavailable.
     */
    suspend fun resolveNextEpisodeInfo(
        seriesId: Int,
        seriesName: String,
        seasonNumber: Int,
        currentEpisodeNumber: Int
    ): ArrayList<String>? = withContext(Dispatchers.IO) {
        if (seriesId <= 0 || seasonNumber <= 0 || currentEpisodeNumber <= 0) return@withContext null
        return@withContext try {
            var resolvedName = seriesName
            if (resolvedName.isBlank()) {
                try {
                    resolvedName = tmdbRepository.getTvDetails(seriesId).name.orEmpty()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }

            val seasonDetails = tmdbRepository.getTvSeasonDetails(seriesId, seasonNumber)
            val episodesList = seasonDetails.episodes.orEmpty()
            val currentIndex = episodesList.indexOfFirst { it.episodeNumber == currentEpisodeNumber }

            if (currentIndex >= 0 && currentIndex < episodesList.size - 1) {
                val nextEp = episodesList[currentIndex + 1]
                val nextSeasonNum = nextEp.seasonNumber.takeIf { it > 0 } ?: seasonNumber
                arrayListOf(
                    seriesId.toString(),
                    resolvedName,
                    nextSeasonNum.toString(),
                    nextEp.episodeNumber.toString(),
                    nextEp.name.orEmpty()
                )
            } else {
                try {
                    val nextSeasonDetails = tmdbRepository.getTvSeasonDetails(seriesId, seasonNumber + 1)
                    val firstEp = nextSeasonDetails.episodes.orEmpty().firstOrNull { it.episodeNumber == 1 }
                    if (firstEp != null) {
                        val nextSeasonNum = firstEp.seasonNumber.takeIf { it > 0 } ?: (seasonNumber + 1)
                        arrayListOf(
                            seriesId.toString(),
                            resolvedName,
                            nextSeasonNum.toString(),
                            firstEp.episodeNumber.toString(),
                            firstEp.name.orEmpty()
                        )
                    } else null
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    /**
     * Fetches unified torrent releases for an episode by querying EZTV and Torrentio in parallel.
     * Deduplicates by infohash and sorts by seed count and size descending.
     */
    suspend fun getEpisodeReleases(
        imdbId: String,
        seasonNumber: Int,
        episodeNumber: Int
    ): List<TorrentRelease> = withContext(Dispatchers.IO) {
        if (imdbId.isBlank()) return@withContext emptyList()
        return@withContext supervisorScope {
            val eztvDeferred = async {
                try {
                    eztvRepository.getTorrentsForEpisode(imdbId, seasonNumber, episodeNumber)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Error fetching EZTV releases for $imdbId: ${e.message}")
                    emptyList()
                }
            }
            val torrentioDeferred = async {
                try {
                    torrentioRepository.getSeriesTorrents(imdbId, seasonNumber, episodeNumber)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Error fetching Torrentio releases for $imdbId: ${e.message}")
                    emptyList()
                }
            }

            val eztvList = eztvDeferred.await()
            val torrentioList = torrentioDeferred.await()

            val eztvReleases = eztvList.map { torrentioRepository.fromEztvTorrent(it) }
            (eztvReleases + torrentioList)
                .distinctBy { it.hash.lowercase() }
                .sortedWith(
                    compareByDescending<TorrentRelease> { it.seeds }
                        .thenByDescending { it.sizeBytes ?: 0L }
                )
        }
    }

    /**
     * Resolves the IMDb ID from TMDB and fetches unified torrent releases.
     */
    suspend fun getEpisodeReleasesBySeriesId(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int
    ): List<TorrentRelease> = withContext(Dispatchers.IO) {
        if (seriesId <= 0) return@withContext emptyList()
        val imdbId = tmdbRepository.getTvImdbId(seriesId)
        if (imdbId.isNullOrBlank()) return@withContext emptyList()
        return@withContext getEpisodeReleases(imdbId, seasonNumber, episodeNumber)
    }

    /**
     * Fetches both the raw EZTV releases and unified releases grouped by quality group
     * for the episode detail screen.
     */
    suspend fun getEpisodeReleasesWithQualityGrouping(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int
    ): Pair<List<EztvTorrent>, Map<VideoQualityGroup, List<TorrentRelease>>> = withContext(Dispatchers.IO) {
        if (seriesId <= 0) return@withContext Pair(emptyList(), emptyMap())
        val imdbId = tmdbRepository.getTvImdbId(seriesId)
        if (imdbId.isNullOrBlank()) return@withContext Pair(emptyList(), emptyMap())

        return@withContext supervisorScope {
            val eztvDeferred = async {
                try {
                    eztvRepository.getTorrentsForEpisode(imdbId, seasonNumber, episodeNumber)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Error fetching EZTV releases: ${e.message}")
                    emptyList()
                }
            }
            val torrentioDeferred = async {
                try {
                    torrentioRepository.getSeriesTorrents(imdbId, seasonNumber, episodeNumber)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Error fetching Torrentio releases: ${e.message}")
                    emptyList()
                }
            }

            val eztvList = eztvDeferred.await()
            val torrentioList = torrentioDeferred.await()

            val eztvReleases = eztvList.map { torrentioRepository.fromEztvTorrent(it) }
            val allReleases = eztvReleases + torrentioList
            val grouped = allReleases
                .distinctBy { it.hash.lowercase() }
                .groupBy { it.qualityGroup }
                .mapValues { entry ->
                    entry.value.sortedWith(
                        compareByDescending<TorrentRelease> { it.seeds }
                            .thenByDescending { it.sizeBytes ?: 0L }
                    )
                }
                .toSortedMap(compareBy { it.ordinal })

            Pair(eztvList, grouped)
        }
    }

    /**
     * Converts a raw [EztvTorrent] to a unified [TorrentRelease].
     */
    fun fromEztvTorrent(torrent: EztvTorrent): TorrentRelease = torrentioRepository.fromEztvTorrent(torrent)
}
