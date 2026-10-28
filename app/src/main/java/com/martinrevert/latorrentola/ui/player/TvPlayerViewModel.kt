package com.martinrevert.latorrentola.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.model.torrent.TorrentRelease
import com.martinrevert.latorrentola.model.user.DownloadedEpisode
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.TvEpisodeReleasesRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.TorrentLaunchHelper
import com.martinrevert.latorrentola.utils.TorrentLaunchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

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
