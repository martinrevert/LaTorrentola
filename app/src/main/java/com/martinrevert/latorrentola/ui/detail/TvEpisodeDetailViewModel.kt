package com.martinrevert.latorrentola.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.user.DownloadedEpisode
import com.martinrevert.latorrentola.network.EztvRepository
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Manages state and torrent fetching for an individual TV series episode.
 *
 * @property tmdbRepository Access to TMDB episode metadata and external IDs.
 * @property eztvRepository Access to EZTV episode torrent releases.
 * @property userLibraryRepository Firestore persistence for downloaded episode records.
 */
@HiltViewModel
class TvEpisodeDetailViewModel @Inject constructor(
    private val tmdbRepository: TmdbRepository,
    private val eztvRepository: EztvRepository,
    private val userLibraryRepository: UserLibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TvEpisodeDetailUiState>(TvEpisodeDetailUiState.Loading)
    val uiState: StateFlow<TvEpisodeDetailUiState> = _uiState.asStateFlow()

    private var activeSeriesId: Int = 0
    private var activeSeasonNumber: Int = 0
    private var activeEpisodeNumber: Int = 0
    private var activeSeriesName: String = ""
    private var currentEpisode: TmdbTvEpisode? = null

    /**
     * Loads episode details and searches EZTV for matching torrent releases.
     *
     * @param seriesId TMDB TV series identifier.
     * @param seasonNumber Season number.
     * @param episodeNumber Episode number within season.
     * @param seriesName Series display name.
     * @param episodeJson Optional serialized [TmdbTvEpisode] payload.
     */
    fun loadEpisode(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        seriesName: String = "",
        episodeJson: String? = null
    ) {
        if (activeSeriesId == seriesId &&
            activeSeasonNumber == seasonNumber &&
            activeEpisodeNumber == episodeNumber &&
            _uiState.value !is TvEpisodeDetailUiState.Loading
        ) {
            return
        }

        activeSeriesId = seriesId
        activeSeasonNumber = seasonNumber
        activeEpisodeNumber = episodeNumber
        activeSeriesName = seriesName

        viewModelScope.launch {
            _uiState.value = TvEpisodeDetailUiState.Loading
            try {
                // 1. Resolve episode metadata
                var episode = if (!episodeJson.isNullOrBlank()) {
                    try {
                        Json.decodeFromString(
                            TmdbTvEpisode.serializer(),
                            episodeJson
                        )
                    } catch (e: Exception) {
                        null
                    }
                } else null

                if (episode == null) {
                    val seasonDetails = tmdbRepository.getTvSeasonDetails(seriesId, seasonNumber)
                    episode = seasonDetails.episodes.orEmpty().firstOrNull { it.episodeNumber == episodeNumber }
                        ?: TmdbTvEpisode(
                            id = 0,
                            name = "Episode $episodeNumber",
                            episodeNumber = episodeNumber,
                            seasonNumber = seasonNumber
                        )
                }
                currentEpisode = episode

                // 2. Resolve series title if missing
                var resolvedSeriesName = seriesName
                if (resolvedSeriesName.isBlank()) {
                    try {
                        val series = tmdbRepository.getTvDetails(seriesId)
                        resolvedSeriesName = series.name.orEmpty()
                    } catch (_: Exception) { }
                }
                activeSeriesName = resolvedSeriesName

                // 3. Resolve IMDb ID and fetch torrents
                val imdbId = tmdbRepository.getTvImdbId(seriesId)
                val torrents = if (!imdbId.isNullOrBlank()) {
                    eztvRepository.getTorrentsForEpisode(imdbId, seasonNumber, episodeNumber)
                } else emptyList()

                _uiState.value = TvEpisodeDetailUiState.Success(
                    seriesName = resolvedSeriesName,
                    episode = episode,
                    torrents = torrents
                )
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _uiState.value = TvEpisodeDetailUiState.Error(
                        e.localizedMessage ?: "Unable to load episode details"
                    )
                }
            }
        }
    }

    /**
     * Records a download entry in Firestore when the user chooses a torrent release.
     *
     * @param torrent EZTV torrent release selected by user.
     */
    fun markEpisodeAsDownloaded(torrent: EztvTorrent) {
        val episode = currentEpisode ?: return
        viewModelScope.launch {
            userLibraryRepository.markEpisodeAsDownloaded(
                DownloadedEpisode(
                    seriesId = activeSeriesId,
                    seriesName = activeSeriesName,
                    seasonNumber = activeSeasonNumber,
                    episodeNumber = activeEpisodeNumber,
                    episodeName = episode.name.orEmpty(),
                    releaseTitle = torrent.title,
                    quality = extractQuality(torrent.title),
                    hash = torrent.hash,
                    magnetUrl = torrent.magnetUrl,
                    timestamp = System.currentTimeMillis(),
                    stillPath = episode.stillPath
                )
            )
        }
    }

    /** Helper to extract a short quality label (e.g. "1080p", "720p", "2160p") from release title. */
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

/** UI states for the episode detail screen. */
sealed interface TvEpisodeDetailUiState {
    data object Loading : TvEpisodeDetailUiState

    data class Success(
        val seriesName: String,
        val episode: TmdbTvEpisode,
        val torrents: List<EztvTorrent>
    ) : TvEpisodeDetailUiState

    data class Error(val message: String) : TvEpisodeDetailUiState
}
