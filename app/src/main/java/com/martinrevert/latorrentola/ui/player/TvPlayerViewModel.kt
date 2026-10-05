package com.martinrevert.latorrentola.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.EZTV.downloadInfoHash
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.model.user.DownloadedEpisode
import com.martinrevert.latorrentola.network.EztvRepository
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.TorrentLaunchHelper
import com.martinrevert.latorrentola.utils.TorrentLaunchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TvPlayerViewModel @Inject constructor(
    private val eztvRepository: EztvRepository,
    private val tmdbRepository: TmdbRepository,
    private val userLibraryRepository: UserLibraryRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _nextEpisodeReleases = MutableStateFlow<List<EztvTorrent>>(emptyList())
    val nextEpisodeReleases: StateFlow<List<EztvTorrent>> = _nextEpisodeReleases.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun fetchReleasesForNextEpisode(seriesId: Int, seasonNumber: Int, episodeNumber: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _nextEpisodeReleases.value = emptyList()

            val imdbId = tmdbRepository.getTvImdbId(seriesId)
            if (!imdbId.isNullOrEmpty()) {
                try {
                    val releases = eztvRepository.getTorrentsForEpisode(imdbId, seasonNumber, episodeNumber)
                    _nextEpisodeReleases.value = releases
                } catch (e: Exception) {
                    _error.value = e.localizedMessage ?: "Unknown error"
                }
            } else {
                _error.value = "Unable to find IMDb ID for series"
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

            try {
                val nextInfo = getNextEpisodeInfo(seriesId, "", currentSeasonNumber, currentEpisodeNumber)
                if (nextInfo != null && nextInfo.size >= 4) {
                    val nextSeason = nextInfo[2].toInt()
                    val nextEp = nextInfo[3].toInt()

                    val imdbId = tmdbRepository.getTvImdbId(seriesId)
                    if (!imdbId.isNullOrEmpty()) {
                        val releases = eztvRepository.getTorrentsForEpisode(imdbId, nextSeason, nextEp)
                        _nextEpisodeReleases.value = releases
                    } else {
                        _error.value = "Unable to find IMDb ID for series"
                    }
                } else {
                    _error.value = "No next episode available"
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Unknown error"
            }
            _isLoading.value = false
        }
    }

    fun downloadEpisode(
        context: Context,
        release: EztvTorrent,
        seriesId: Int,
        seriesName: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeName: String
    ) {
        viewModelScope.launch {
            try {
                val nextEpisodeInfo = getNextEpisodeInfo(seriesId, seriesName, seasonNumber, episodeNumber)

                val magnetUrl = release.magnetUrl.takeIf { it.isNotEmpty() }
                    ?: TorrentLaunchHelper.buildMagnetUri(
                        release.downloadInfoHash() ?: "",
                        "%s S%02dE%02d %s".format(seriesName, seasonNumber, episodeNumber, release.title)
                    )

                val result = TorrentLaunchHelper.launch(
                    context,
                    preferenceManager.getTorrentHandlingMode(),
                    magnetUrl,
                    "%s S%02dE%02d %s".format(seriesName, seasonNumber, episodeNumber, release.title),
                    nextEpisodeInfo
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
                            quality = extractQuality(release.title),
                            hash = release.downloadInfoHash()
                                ?: "tv_${seriesId}_s${seasonNumber}_e${episodeNumber}",
                            magnetUrl = magnetUrl,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } else {
                    _error.value = "Failed to start download: $result"
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Failed to download episode"
            }
        }
    }

    suspend fun getNextEpisodeInfo(
        seriesId: Int,
        seriesName: String,
        seasonNumber: Int,
        episodeNumber: Int
    ): ArrayList<String>? {
        if (seriesId <= 0 || seasonNumber <= 0 || episodeNumber <= 0) return null
        return try {
            val seasonDetails = tmdbRepository.getTvSeasonDetails(seriesId, seasonNumber)
            val episodesList = seasonDetails.episodes.orEmpty()
            val currentIndex = episodesList.indexOfFirst { it.episodeNumber == episodeNumber }

            if (currentIndex >= 0 && currentIndex < episodesList.size - 1) {
                val nextEp = episodesList[currentIndex + 1]
                if (isReleased(nextEp.airDate)) {
                    arrayListOf(
                        seriesId.toString(),
                        seriesName,
                        nextEp.seasonNumber.toString(),
                        nextEp.episodeNumber.toString(),
                        nextEp.name.orEmpty()
                    )
                } else null
            } else {
                try {
                    val nextSeasonDetails = tmdbRepository.getTvSeasonDetails(seriesId, seasonNumber + 1)
                    val firstEp = nextSeasonDetails.episodes.orEmpty().firstOrNull { it.episodeNumber == 1 }
                    if (firstEp != null && isReleased(firstEp.airDate)) {
                        arrayListOf(
                            seriesId.toString(),
                            seriesName,
                            firstEp.seasonNumber.toString(),
                            firstEp.episodeNumber.toString(),
                            firstEp.name.orEmpty()
                        )
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isReleased(airDate: String?): Boolean {
        if (airDate.isNullOrBlank()) return true
        return try {
            val parsedDate = LocalDate.parse(airDate)
            !parsedDate.isAfter(LocalDate.now())
        } catch (_: Exception) {
            true
        }
    }

    private fun extractQuality(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.contains("2160p") || lower.contains("4k") -> "2160p"
            lower.contains("1080p") -> "1080p"
            lower.contains("720p") -> "720p"
            lower.contains("hdtv") -> "HDTV"
            else -> "SD"
        }
    }
}