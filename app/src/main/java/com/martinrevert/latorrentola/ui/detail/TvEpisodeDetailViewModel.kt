package com.martinrevert.latorrentola.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.model.user.DownloadedEpisode
import com.martinrevert.latorrentola.network.EztvRepository
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.utils.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Manages state and torrent fetching for an individual TV series episode.
 *
 * @property tmdbRepository Access to TMDB episode metadata and external IDs.
 * @property eztvRepository Access to EZTV episode torrent releases.
 * @property userLibraryRepository Firestore persistence for downloaded episode records.
 * @property preferenceManager provides the selected torrent handling mode.
 */
@HiltViewModel
class TvEpisodeDetailViewModel @Inject constructor(
    private val tmdbRepository: TmdbRepository,
    private val eztvRepository: EztvRepository,
    private val userLibraryRepository: UserLibraryRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    /** Torrent handling mode selected in Settings. */
    val torrentHandlingMode: StateFlow<TorrentHandlingMode> =
        preferenceManager.torrentHandlingModeFlow

    private val _rawUiState = MutableStateFlow<TvEpisodeDetailRawUiState>(TvEpisodeDetailRawUiState.Loading)

    private var activeSeriesId: Int = 0
    private var activeSeasonNumber: Int = 0
    private var activeEpisodeNumber: Int = 0
    private var activeSeriesName: String = ""
    private var currentEpisode: TmdbTvEpisode? = null

    /** Observes downloaded episode history and dynamically reflects download status. */
    val uiState: StateFlow<TvEpisodeDetailUiState> = combine(
        _rawUiState,
        userLibraryRepository.getDownloadedEpisodes()
    ) { rawState, downloads ->
        when (rawState) {
            is TvEpisodeDetailRawUiState.Loading -> TvEpisodeDetailUiState.Loading
            is TvEpisodeDetailRawUiState.Error -> TvEpisodeDetailUiState.Error(rawState.message)
            is TvEpisodeDetailRawUiState.Success -> {
                val matching = downloads.filter {
                    it.seriesId == activeSeriesId &&
                            it.seasonNumber == activeSeasonNumber &&
                            it.episodeNumber == activeEpisodeNumber
                }
                TvEpisodeDetailUiState.Success(
                    seriesName = rawState.seriesName,
                    episode = rawState.episode,
                    torrents = rawState.torrents,
                    isDownloaded = matching.isNotEmpty(),
                    downloadedHashes = matching.map { it.hash }.toSet()
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TvEpisodeDetailUiState.Loading
    )

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
            _rawUiState.value !is TvEpisodeDetailRawUiState.Loading
        ) {
            return
        }

        activeSeriesId = seriesId
        activeSeasonNumber = seasonNumber
        activeEpisodeNumber = episodeNumber
        activeSeriesName = seriesName

        viewModelScope.launch {
            _rawUiState.value = TvEpisodeDetailRawUiState.Loading
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

                _rawUiState.value = TvEpisodeDetailRawUiState.Success(
                    seriesName = resolvedSeriesName,
                    episode = episode,
                    torrents = torrents
                )
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _rawUiState.value = TvEpisodeDetailRawUiState.Error(
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
            val validHash = when {
                torrent.hash.isNotBlank() -> torrent.hash
                torrent.magnetUrl.isNotBlank() -> extractHashFromMagnet(torrent.magnetUrl)
                else -> null
            } ?: "tv_${activeSeriesId}_s${activeSeasonNumber}_e${activeEpisodeNumber}"

            userLibraryRepository.markEpisodeAsDownloaded(
                DownloadedEpisode(
                    seriesId = activeSeriesId,
                    seriesName = activeSeriesName,
                    seasonNumber = activeSeasonNumber,
                    episodeNumber = activeEpisodeNumber,
                    episodeName = episode.name.orEmpty(),
                    releaseTitle = torrent.title,
                    quality = extractQuality(torrent.title),
                    hash = validHash,
                    magnetUrl = torrent.magnetUrl,
                    timestamp = System.currentTimeMillis(),
                    stillPath = episode.stillPath
                )
            )
        }
    }

    /** Extracts infohash from a magnet URL if torrent.hash is blank. */
    private fun extractHashFromMagnet(magnetUrl: String): String? {
        if (magnetUrl.isBlank()) return null
        val regex = Regex("""xt=urn:btih:([a-fA-F0-9]{40}|[a-zA-Z2-7]{32})""", RegexOption.IGNORE_CASE)
        return regex.find(magnetUrl)?.groupValues?.get(1)?.lowercase()
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

/** Internal raw UI states for episode details before combining with download history. */
private sealed interface TvEpisodeDetailRawUiState {
    data object Loading : TvEpisodeDetailRawUiState

    data class Success(
        val seriesName: String,
        val episode: TmdbTvEpisode,
        val torrents: List<EztvTorrent>
    ) : TvEpisodeDetailRawUiState

    data class Error(val message: String) : TvEpisodeDetailRawUiState
}

/** UI states for the episode detail screen. */
sealed interface TvEpisodeDetailUiState {
    data object Loading : TvEpisodeDetailUiState

    data class Success(
        val seriesName: String,
        val episode: TmdbTvEpisode,
        val torrents: List<EztvTorrent>,
        val isDownloaded: Boolean = false,
        val downloadedHashes: Set<String> = emptySet()
    ) : TvEpisodeDetailUiState

    data class Error(val message: String) : TvEpisodeDetailUiState
}
