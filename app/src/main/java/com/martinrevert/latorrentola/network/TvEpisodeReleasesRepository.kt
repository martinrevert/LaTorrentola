package com.martinrevert.latorrentola.network

import android.util.Log
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSeasonDetails
import com.martinrevert.latorrentola.model.TMDB.UpcomingEpisodeSchedule
import com.martinrevert.latorrentola.model.torrent.TorrentRelease
import com.martinrevert.latorrentola.model.torrent.VideoQualityGroup
import com.martinrevert.latorrentola.model.torrent.groupByQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level continuation state after a user finishes watching a TV episode.
 */
enum class SeriesContinuationState {
    /** A next episode exists and has playable torrent releases available right now. */
    NEXT_EPISODE_READY,
    /** Remaining episodes exist in TMDB (current or subsequent season), but no torrents are available for the next episode yet. */
    REMAINING_EPISODES_NO_TORRENTS,
    /** All currently available episodes have been watched; the series is still in production / returning. */
    SEASON_COMPLETE_SERIES_CONTINUES,
    /** All episodes have been watched and TMDB reports the series has definitively ended or been canceled. */
    SERIES_ENDED
}

/**
 * Comprehensive evaluation of what follows a finished TV episode, combining TMDB series/season metadata,
 * torrent availability across providers, remaining episode counts, and upcoming broadcast schedules
 * prepared for future Google Calendar integration.
 */
data class NextEpisodeEvaluation(
    val state: SeriesContinuationState,
    val seriesId: Int,
    val seriesName: String,
    val seriesStatus: String? = null,
    val isSeriesContinuing: Boolean = false,
    val nextSeasonNumber: Int = 0,
    val nextEpisodeNumber: Int = 0,
    val nextEpisodeName: String = "",
    val nextEpisodeAirDate: String? = null,
    val isNextEpisodeUnaired: Boolean = false,
    val remainingEpisodesCount: Int = 0,
    val releases: List<TorrentRelease> = emptyList(),
    val upcomingSchedules: List<UpcomingEpisodeSchedule> = emptyList()
)

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

        internal fun currentIsoDateUtc(): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return fmt.format(Date())
        }
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
     * Evaluates the next episode after ([currentSeasonNumber], [currentEpisodeNumber]), verifying:
     * 1. Whether a subsequent episode exists in the current or following season in TMDB.
     * 2. Whether torrent releases actually exist for that next episode.
     * 3. If no torrents exist, whether there are remaining episodes in the season/series (even when TMDB marks
     *    the overall series as ended) vs. whether the user finished all available episodes and the series will
     *    return or has ended.
     * 4. Collects structured [UpcomingEpisodeSchedule] entries for future Google Calendar integration.
     */
    suspend fun evaluateNextEpisode(
        seriesId: Int,
        seriesName: String,
        currentSeasonNumber: Int,
        currentEpisodeNumber: Int,
        todayIso: String = currentIsoDateUtc()
    ): NextEpisodeEvaluation = withContext(Dispatchers.IO) {
        if (seriesId <= 0 || currentSeasonNumber <= 0 || currentEpisodeNumber <= 0) {
            return@withContext NextEpisodeEvaluation(
                state = SeriesContinuationState.SERIES_ENDED,
                seriesId = seriesId,
                seriesName = seriesName
            )
        }

        val series = try {
            tmdbRepository.getTvDetails(seriesId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to fetch TV details for series $seriesId: ${e.message}")
            throw e
        }

        val resolvedName = seriesName.takeIf { it.isNotBlank() } ?: series.name.orEmpty()
        val isContinuingInTmdb = series.isSeriesContinuing
        val isEndedInTmdb = series.isSeriesEnded

        val currentSeasonDetails = try {
            tmdbRepository.getTvSeasonDetails(seriesId, currentSeasonNumber)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to fetch current season details for series $seriesId season $currentSeasonNumber: ${e.message}")
            throw e
        }

        val episodesInCurrentSeason = currentSeasonDetails.episodes.orEmpty().sortedBy { it.episodeNumber }
        val remainingInCurrentSeason = episodesInCurrentSeason.filter { it.episodeNumber > currentEpisodeNumber }

        val laterSeasonsSummary = series.seasons.orEmpty()
            .filter { it.seasonNumber > currentSeasonNumber && (it.episodeCount ?: 0) > 0 }
            .sortedBy { it.seasonNumber }

        val targetNextSeasonNumber = laterSeasonsSummary.firstOrNull()?.seasonNumber ?: (currentSeasonNumber + 1)
        val nextSeasonDetails: TmdbTvSeasonDetails? = if (remainingInCurrentSeason.isEmpty()) {
            try {
                tmdbRepository.getTvSeasonDetails(seriesId, targetNextSeasonNumber)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        } else {
            null
        }
        val nextSeasonEpisodes = nextSeasonDetails?.episodes.orEmpty().sortedBy { it.episodeNumber }

        val candidateEpisode: TmdbTvEpisode? = remainingInCurrentSeason.firstOrNull()
            ?: nextSeasonEpisodes.firstOrNull { it.episodeNumber >= 1 }

        val candidateSeasonNumber = when {
            remainingInCurrentSeason.isNotEmpty() ->
                candidateEpisode?.seasonNumber?.takeIf { it > 0 } ?: currentSeasonNumber
            candidateEpisode != null ->
                candidateEpisode.seasonNumber.takeIf { it > 0 } ?: targetNextSeasonNumber
            else -> 0
        }

        val remainingInLaterSeasonsCount = if (laterSeasonsSummary.isNotEmpty()) {
            laterSeasonsSummary.sumOf { season ->
                if (nextSeasonDetails != null && season.seasonNumber == nextSeasonDetails.seasonNumber) {
                    maxOf(season.episodeCount ?: 0, nextSeasonEpisodes.size)
                } else {
                    season.episodeCount ?: 0
                }
            }
        } else {
            nextSeasonEpisodes.size
        }

        val totalRemainingEpisodes = remainingInCurrentSeason.size + remainingInLaterSeasonsCount

        // Collect upcoming episode air dates for future Google Calendar integration
        val scheduleList = mutableListOf<UpcomingEpisodeSchedule>()
        fun addEpisodeScheduleIfPresent(ep: TmdbTvEpisode, fallbackSeason: Int) {
            val airDate = ep.airDate?.trim().orEmpty()
            if (airDate.isNotBlank() && airDate >= todayIso) {
                val sNum = ep.seasonNumber.takeIf { it > 0 } ?: fallbackSeason
                scheduleList.add(
                    UpcomingEpisodeSchedule(
                        seriesId = seriesId,
                        seriesName = resolvedName,
                        seasonNumber = sNum,
                        episodeNumber = ep.episodeNumber.takeIf { it > 0 },
                        episodeName = ep.name?.takeIf { it.isNotBlank() },
                        airDateIso = airDate,
                        overview = ep.overview?.takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        remainingInCurrentSeason.forEach { addEpisodeScheduleIfPresent(it, currentSeasonNumber) }
        nextSeasonEpisodes.forEach { addEpisodeScheduleIfPresent(it, targetNextSeasonNumber) }
        series.nextEpisodeToAir?.let { nextAir ->
            if (nextAir.seasonNumber > currentSeasonNumber ||
                (nextAir.seasonNumber == currentSeasonNumber && nextAir.episodeNumber > currentEpisodeNumber)
            ) {
                addEpisodeScheduleIfPresent(nextAir, nextAir.seasonNumber.takeIf { it > 0 } ?: currentSeasonNumber)
            }
        }
        laterSeasonsSummary.forEach { season ->
            val airDate = season.airDate?.trim().orEmpty()
            if (airDate.isNotBlank() && airDate >= todayIso && scheduleList.none { it.seasonNumber == season.seasonNumber }) {
                scheduleList.add(
                    UpcomingEpisodeSchedule(
                        seriesId = seriesId,
                        seriesName = resolvedName,
                        seasonNumber = season.seasonNumber,
                        episodeNumber = 1,
                        episodeName = season.name?.takeIf { it.isNotBlank() },
                        airDateIso = airDate,
                        overview = season.overview?.takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        val upcomingSchedules = scheduleList
            .distinctBy { Triple(it.seasonNumber, it.episodeNumber, it.airDateIso) }
            .sortedBy { it.airDateIso }

        if (candidateEpisode != null) {
            val candidateEpNum = candidateEpisode.episodeNumber
            val releases = getEpisodeReleasesBySeriesId(seriesId, candidateSeasonNumber, candidateEpNum)
            if (releases.isNotEmpty()) {
                return@withContext NextEpisodeEvaluation(
                    state = SeriesContinuationState.NEXT_EPISODE_READY,
                    seriesId = seriesId,
                    seriesName = resolvedName,
                    seriesStatus = series.status,
                    isSeriesContinuing = isContinuingInTmdb || !isEndedInTmdb,
                    nextSeasonNumber = candidateSeasonNumber,
                    nextEpisodeNumber = candidateEpNum,
                    nextEpisodeName = candidateEpisode.name.orEmpty(),
                    nextEpisodeAirDate = candidateEpisode.airDate,
                    isNextEpisodeUnaired = false,
                    remainingEpisodesCount = maxOf(1, totalRemainingEpisodes),
                    releases = releases,
                    upcomingSchedules = upcomingSchedules
                )
            }

            val epAirDate = candidateEpisode.airDate?.trim()?.takeIf { it.isNotBlank() }
                ?: series.nextEpisodeToAir?.takeIf {
                    it.seasonNumber == candidateSeasonNumber && it.episodeNumber == candidateEpNum
                }?.airDate?.trim()?.takeIf { it.isNotBlank() }

            val isFutureAirDate = epAirDate != null && epAirDate > todayIso
            val isUnscheduledFutureStub = epAirDate == null && !isEndedInTmdb

            // If we rolled over to a next season that hasn't aired yet, report season complete + series continues
            if (candidateSeasonNumber > currentSeasonNumber && (isFutureAirDate || isUnscheduledFutureStub)) {
                val nextPremiereDate = epAirDate
                    ?: series.nextEpisodeToAir?.airDate?.takeIf { it.isNotBlank() }
                    ?: laterSeasonsSummary.firstOrNull()?.airDate?.takeIf { it.isNotBlank() }
                return@withContext NextEpisodeEvaluation(
                    state = SeriesContinuationState.SEASON_COMPLETE_SERIES_CONTINUES,
                    seriesId = seriesId,
                    seriesName = resolvedName,
                    seriesStatus = series.status,
                    isSeriesContinuing = true,
                    nextSeasonNumber = candidateSeasonNumber,
                    nextEpisodeNumber = candidateEpNum,
                    nextEpisodeName = candidateEpisode.name.orEmpty(),
                    nextEpisodeAirDate = nextPremiereDate,
                    isNextEpisodeUnaired = true,
                    remainingEpisodesCount = maxOf(1, totalRemainingEpisodes),
                    releases = emptyList(),
                    upcomingSchedules = upcomingSchedules
                )
            }

            // Otherwise, there ARE remaining episodes in the season/series (even if TMDB says the series ended!),
            // but no torrents were found for the immediate next episode.
            return@withContext NextEpisodeEvaluation(
                state = SeriesContinuationState.REMAINING_EPISODES_NO_TORRENTS,
                seriesId = seriesId,
                seriesName = resolvedName,
                seriesStatus = series.status,
                isSeriesContinuing = isContinuingInTmdb || (!isEndedInTmdb && isFutureAirDate),
                nextSeasonNumber = candidateSeasonNumber,
                nextEpisodeNumber = candidateEpNum,
                nextEpisodeName = candidateEpisode.name.orEmpty(),
                nextEpisodeAirDate = epAirDate,
                isNextEpisodeUnaired = isFutureAirDate,
                remainingEpisodesCount = maxOf(1, totalRemainingEpisodes),
                releases = emptyList(),
                upcomingSchedules = upcomingSchedules
            )
        }

        // No candidate episode exists after (currentSeasonNumber, currentEpisodeNumber)
        val hasFutureSeasonOrNextAir = isContinuingInTmdb ||
            laterSeasonsSummary.isNotEmpty() ||
            series.nextEpisodeToAir != null

        return@withContext if (hasFutureSeasonOrNextAir && !isEndedInTmdb) {
            val nextDate = series.nextEpisodeToAir?.airDate?.takeIf { it.isNotBlank() }
                ?: laterSeasonsSummary.firstOrNull()?.airDate?.takeIf { it.isNotBlank() }
            NextEpisodeEvaluation(
                state = SeriesContinuationState.SEASON_COMPLETE_SERIES_CONTINUES,
                seriesId = seriesId,
                seriesName = resolvedName,
                seriesStatus = series.status,
                isSeriesContinuing = true,
                nextSeasonNumber = series.nextEpisodeToAir?.seasonNumber
                    ?: laterSeasonsSummary.firstOrNull()?.seasonNumber
                    ?: (currentSeasonNumber + 1),
                nextEpisodeNumber = series.nextEpisodeToAir?.episodeNumber ?: 1,
                nextEpisodeName = series.nextEpisodeToAir?.name.orEmpty(),
                nextEpisodeAirDate = nextDate,
                isNextEpisodeUnaired = true,
                remainingEpisodesCount = remainingInLaterSeasonsCount,
                releases = emptyList(),
                upcomingSchedules = upcomingSchedules
            )
        } else {
            NextEpisodeEvaluation(
                state = SeriesContinuationState.SERIES_ENDED,
                seriesId = seriesId,
                seriesName = resolvedName,
                seriesStatus = series.status,
                isSeriesContinuing = false,
                remainingEpisodesCount = 0,
                releases = emptyList(),
                upcomingSchedules = upcomingSchedules
            )
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
            val grouped = allReleases.groupByQuality()

            Pair(eztvList, grouped)
        }
    }

    /**
     * Converts a raw [EztvTorrent] to a unified [TorrentRelease].
     */
    fun fromEztvTorrent(torrent: EztvTorrent): TorrentRelease = torrentioRepository.fromEztvTorrent(torrent)
}
