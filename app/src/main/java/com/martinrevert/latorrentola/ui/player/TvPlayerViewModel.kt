package com.martinrevert.latorrentola.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.torrent.TorrentRelease
import com.martinrevert.latorrentola.model.user.DownloadedEpisode
import com.martinrevert.latorrentola.network.NextEpisodeEvaluation
import com.martinrevert.latorrentola.network.SeriesContinuationState
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.TvEpisodeReleasesRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.TorrentLaunchHelper
import com.martinrevert.latorrentola.utils.TorrentLaunchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import com.martinrevert.latorrentola.model.torrent.VideoQualityGroup
import com.martinrevert.latorrentola.model.torrent.groupByQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TvPlayerViewModel @Inject constructor(
    private val tvEpisodeReleasesRepository: TvEpisodeReleasesRepository,
    private val tmdbRepository: TmdbRepository,
    private val userLibraryRepository: UserLibraryRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _nextEpisodeData = MutableStateFlow<NextEpisodeData?>(null)
    val nextEpisodeData: StateFlow<NextEpisodeData?> = _nextEpisodeData.asStateFlow()

    private val _nextEpisodeReleases = MutableStateFlow<List<TorrentRelease>>(emptyList())
    val nextEpisodeReleases: StateFlow<List<TorrentRelease>> = _nextEpisodeReleases.asStateFlow()

    /** Releases for the next episode grouped by video quality and sorted by seeds/size descending. */
    val nextEpisodeReleasesByQuality: StateFlow<Map<VideoQualityGroup, List<TorrentRelease>>> =
        _nextEpisodeReleases.map { it.groupByQuality() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyMap()
            )

    /** Set of infohashes for episodes already downloaded in the user's library. */
    val downloadedHashes: StateFlow<Set<String>> = userLibraryRepository.getDownloadedEpisodes()
        .map { list -> list.mapNotNull { it.hash.takeIf(String::isNotBlank)?.trim()?.lowercase() }.toSet() }
        .catch { emit(emptySet()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    private val _nextEpisodeEvaluation = MutableStateFlow<NextEpisodeEvaluation?>(null)
    val nextEpisodeEvaluation: StateFlow<NextEpisodeEvaluation?> = _nextEpisodeEvaluation.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Evaluates the next episode after ([currentSeasonNumber], [currentEpisodeNumber]), checking
     * torrent availability, remaining episodes in the season/series, TMDB continuation status,
     * and upcoming air dates for future Google Calendar integration.
     */
    suspend fun evaluateNextEpisode(
        seriesId: Int,
        seriesName: String,
        currentSeasonNumber: Int,
        currentEpisodeNumber: Int
    ): NextEpisodeEvaluation {
        _isLoading.value = true
        _error.value = null
        _nextEpisodeReleases.value = emptyList()
        _nextEpisodeEvaluation.value = null
        return try {
            val evaluation = tvEpisodeReleasesRepository.evaluateNextEpisode(
                seriesId = seriesId,
                seriesName = seriesName,
                currentSeasonNumber = currentSeasonNumber,
                currentEpisodeNumber = currentEpisodeNumber
            )
            _nextEpisodeEvaluation.value = evaluation
            if (evaluation.nextSeasonNumber > 0 && evaluation.nextEpisodeNumber > 0) {
                _nextEpisodeData.value = NextEpisodeData(
                    seriesId = evaluation.seriesId,
                    seriesName = evaluation.seriesName,
                    seasonNumber = evaluation.nextSeasonNumber,
                    episodeNumber = evaluation.nextEpisodeNumber,
                    episodeName = evaluation.nextEpisodeName
                )
            } else {
                _nextEpisodeData.value = null
            }
            _nextEpisodeReleases.value = evaluation.releases
            evaluation
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _error.value = e.localizedMessage ?: "Failed to evaluate next episode"
            val fallback = NextEpisodeEvaluation(
                state = SeriesContinuationState.SERIES_ENDED,
                seriesId = seriesId,
                seriesName = seriesName
            )
            _nextEpisodeEvaluation.value = fallback
            fallback
        } finally {
            _isLoading.value = false
        }
    }

    fun fetchReleasesForNextEpisode(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        seriesName: String = "",
        episodeName: String = ""
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _nextEpisodeReleases.value = emptyList()

            var resolvedSeriesName = seriesName
            if (resolvedSeriesName.isBlank() && seriesId > 0) {
                try {
                    resolvedSeriesName = tmdbRepository.getTvDetails(seriesId).name.orEmpty()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }

            _nextEpisodeData.value = NextEpisodeData(
                seriesId = seriesId,
                seriesName = resolvedSeriesName,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeName = episodeName
            )

            try {
                val releases =
                    tvEpisodeReleasesRepository.getEpisodeReleasesBySeriesId(seriesId, seasonNumber, episodeNumber)
                if (releases.isEmpty()) {
                    val imdbId = tmdbRepository.getTvImdbId(seriesId)
                    if (imdbId.isNullOrEmpty()) {
                        _error.value = "Unable to find IMDb ID for series"
                    }
                }
                _nextEpisodeReleases.value = releases
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = e.localizedMessage ?: "Unknown error"
            }
            _isLoading.value = false
        }
    }

    /**
     * Resolves the next episode dynamically from current series/season/episode and fetches its torrents.
     */
    fun fetchNextEpisodeAndReleases(seriesId: Int, currentSeasonNumber: Int, currentEpisodeNumber: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _nextEpisodeReleases.value = emptyList()
            _nextEpisodeData.value = null

            try {
                val nextInfo = tvEpisodeReleasesRepository.resolveNextEpisodeInfo(
                    seriesId,
                    "",
                    currentSeasonNumber,
                    currentEpisodeNumber
                )
                if (nextInfo != null && nextInfo.size >= 5) {
                    val nextSeriesId = nextInfo[0].toInt()
                    val nextSeriesName = nextInfo[1]
                    val nextSeason = nextInfo[2].toInt()
                    val nextEp = nextInfo[3].toInt()
                    val nextEpName = nextInfo[4]

                    _nextEpisodeData.value = NextEpisodeData(
                        seriesId = nextSeriesId,
                        seriesName = nextSeriesName,
                        seasonNumber = nextSeason,
                        episodeNumber = nextEp,
                        episodeName = nextEpName
                    )

                    val releases =
                        tvEpisodeReleasesRepository.getEpisodeReleasesBySeriesId(seriesId, nextSeason, nextEp)
                    if (releases.isEmpty()) {
                        val imdbId = tmdbRepository.getTvImdbId(seriesId)
                        if (imdbId.isNullOrEmpty()) {
                            _error.value = "Unable to find IMDb ID for series"
                        }
                    }
                    _nextEpisodeReleases.value = releases
                } else {
                    _error.value = "No next episode available"
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = e.localizedMessage ?: "Unknown error"
            }
            _isLoading.value = false
        }
    }

    suspend fun getReleasesForEpisode(imdbId: String, seasonNumber: Int, episodeNumber: Int): List<TorrentRelease> =
        tvEpisodeReleasesRepository.getEpisodeReleases(imdbId, seasonNumber, episodeNumber)

    suspend fun downloadEpisode(
        context: Context,
        release: TorrentRelease,
        seriesId: Int,
        seriesName: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeName: String
    ): TorrentLaunchResult {
        return try {
            val nextEpisodeInfo = getNextEpisodeInfo(seriesId, seriesName, seasonNumber, episodeNumber)

            val displayTitle = "%s S%02dE%02d %s".format(seriesName, seasonNumber, episodeNumber, release.title)
            val magnetUrl = release.magnetUrl.takeIf { !it.isNullOrBlank() }
                ?: TorrentLaunchHelper.buildMagnetUri(release.hash, displayTitle)

            val sizeBytes = release.sizeBytes
            val result = TorrentLaunchHelper.launch(
                context,
                preferenceManager.getTorrentHandlingMode(),
                magnetUrl,
                displayTitle,
                nextEpisodeInfo,
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                requiredSizeBytes = sizeBytes
            )

            if (result == TorrentLaunchResult.Started) {
                userLibraryRepository.markEpisodeAsDownloaded(
                    DownloadedEpisode(
                        seriesId = seriesId,
                        seriesName = seriesName,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        episodeName = episodeName,
                        releaseTitle = release.title,
                        quality = release.qualityGroup.name,
                        hash = release.hash.ifBlank {
                            "tv_${seriesId}_s${seasonNumber}_e${episodeNumber}"
                        },
                        magnetUrl = magnetUrl,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } else {
                _error.value = "Failed to start download: $result"
            }
            result
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _error.value = e.localizedMessage ?: "Failed to download episode"
            TorrentLaunchResult.NoExternalClient
        }
    }

    suspend fun downloadEpisode(
        context: Context,
        release: EztvTorrent,
        seriesId: Int,
        seriesName: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeName: String
    ): TorrentLaunchResult {
        val torrentRelease = tvEpisodeReleasesRepository.fromEztvTorrent(release)
        return downloadEpisode(context, torrentRelease, seriesId, seriesName, seasonNumber, episodeNumber, episodeName)
    }

    suspend fun getNextEpisodeInfo(
        seriesId: Int,
        seriesName: String,
        seasonNumber: Int,
        episodeNumber: Int
    ): ArrayList<String>? =
        tvEpisodeReleasesRepository.resolveNextEpisodeInfo(seriesId, seriesName, seasonNumber, episodeNumber)
}

/**
 * Data holder for resolved next episode information.
 *
 * @property seriesId TMDB series identifier.
 * @property seriesName Display name of the television series.
 * @property seasonNumber Season number of the next episode.
 * @property episodeNumber Episode number of the next episode within its season.
 * @property episodeName Title or name of the next episode.
 */
data class NextEpisodeData(
    val seriesId: Int,
    val seriesName: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeName: String
)
