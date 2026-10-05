package com.martinrevert.latorrentola.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.EZTV.downloadInfoHash
import com.martinrevert.latorrentola.network.EztvRepository
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.model.user.DownloadedEpisode
import com.martinrevert.latorrentola.utils.TorrentLaunchHelper
import com.martinrevert.latorrentola.utils.TorrentLaunchResult
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.utils.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
            // First, get the IMDb ID from TMDB
            val imdbId = tmdbRepository.getTvImdbId(seriesId)
            if (!imdbId.isNullOrEmpty()) {
                // Then fetch torrents for the specific episode
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

    fun downloadEpisode(context: Context, release: EztvTorrent, seriesId: Int, seriesName: String, seasonNumber: Int, episodeNumber: Int, episodeName: String) {
        viewModelScope.launch {
            try {
                // Build magnet URL if not present in the release
                val magnetUrl = release.magnetUrl.takeIf { it.isNotEmpty() }
                    ?: TorrentLaunchHelper.buildMagnetUri(
                        release.downloadInfoHash() ?: "",
                        "%s S%02dE%02d %s".format(seriesName, seasonNumber, episodeNumber, release.title)
                    )

                // Launch the download using the user's preferred torrent handling mode
                val result = TorrentLaunchHelper.launch(
                    context,
                    preferenceManager.getTorrentHandlingMode(),
                    magnetUrl,
                    "%s S%02dE%02d %s".format(seriesName, seasonNumber, episodeNumber, release.title)
                )

                if (result == TorrentLaunchResult.Started) {
                    // Save download record to Firestore
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