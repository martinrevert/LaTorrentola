package com.martinrevert.latorrentola.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.database.TorrentDownloadDao
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.torrent.TorrentDownload
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.model.torrent.TorrentRelease
import com.martinrevert.latorrentola.model.torrent.VideoQualityGroup
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.TvEpisodeReleasesRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.utils.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Manages state and torrent fetching for an individual TV series episode.
 *
 * @property tmdbRepository Access to TMDB episode metadata and external IDs.
 * @property tvEpisodeReleasesRepository Single source of truth for unified episode torrent releases.
 * @property userLibraryRepository Firestore persistence for downloaded episode records.
 * @property preferenceManager Provides the selected torrent handling mode.
 * @property torrentDownloadDao Observes local torrent download states.
 */
@HiltViewModel
class TvEpisodeDetailViewModel @Inject constructor(
    private val tmdbRepository: TmdbRepository,
    private val tvEpisodeReleasesRepository: TvEpisodeReleasesRepository,
    private val userLibraryRepository: UserLibraryRepository,
    private val preferenceManager: PreferenceManager,
    private val torrentDownloadDao: TorrentDownloadDao
) : ViewModel() {

    val allDownloads: StateFlow<List<TorrentDownload>> =
        torrentDownloadDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Torrent handling mode selected in Settings. */
    val torrentHandlingMode: StateFlow<TorrentHandlingMode> =
        preferenceManager.torrentHandlingModeFlow

    /** User's in-progress watch history items mapped by media ID. */
    val watchHistoryMap: StateFlow<Map<String, PlaybackProgress>> = userLibraryRepository.getWatchHistory()
        .map { list -> list.associateBy { it.mediaId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    private val _rawUiState = MutableStateFlow<TvEpisodeDetailRawUiState>(TvEpisodeDetailRawUiState.Loading)

    private var activeSeriesId: Int = 0
    private var activeSeriesImdbId: String? = null
    private var activeSeasonNumber: Int = 0
    private var activeEpisodeNumber: Int = 0
    private var cachedNextEpisodeInfo: List<Any>? = null

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
                val downloadedHashes = matching.mapNotNull {
                    it.hash.takeIf(String::isNotBlank)?.lowercase()
                }.toSet()
                TvEpisodeDetailUiState.Success(
                    seriesName = rawState.seriesName,
                    episode = rawState.episode,
                    torrents = rawState.torrents,
                    releasesByQuality = rawState.releasesByQuality,
                    isDownloaded = matching.isNotEmpty(),
                    downloadedHashes = downloadedHashes,
                    posterUrl = rawState.posterUrl,
                    imdbId = activeSeriesImdbId
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

                // 2. Get season details for episode resolution when episodeJson is absent or invalid
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

                // 3. Resolve series title and vertical poster from TMDB
                var resolvedSeriesName = seriesName
                var resolvedPosterUrl: String? = null
                try {
                    val series = tmdbRepository.getTvDetails(seriesId)
                    if (resolvedSeriesName.isBlank()) {
                        resolvedSeriesName = series.name.orEmpty()
                    }
                    resolvedPosterUrl = series.fullPosterUrl
                    activeSeriesImdbId = series.effectiveImdbId ?: tmdbRepository.getTvImdbId(seriesId)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }

                // 4. Resolve next episode info via unified repository
                cachedNextEpisodeInfo = tvEpisodeReleasesRepository.resolveNextEpisodeInfo(
                    seriesId = seriesId,
                    seriesName = resolvedSeriesName,
                    seasonNumber = seasonNumber,
                    currentEpisodeNumber = episode.episodeNumber
                )

                // 5. Fetch torrents via unified repository (EZTV + Torrentio in parallel)
                val (eztvTorrents, releasesByQuality) = tvEpisodeReleasesRepository.getEpisodeReleasesWithQualityGrouping(
                    seriesId = seriesId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber
                )

                _rawUiState.value = TvEpisodeDetailRawUiState.Success(
                    seriesName = resolvedSeriesName,
                    episode = episode,
                    torrents = eztvTorrents,
                    releasesByQuality = releasesByQuality,
                    posterUrl = resolvedPosterUrl
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _rawUiState.value = TvEpisodeDetailRawUiState.Error(
                    e.localizedMessage?.takeIf { it.isNotBlank() && it != "null" }
                        ?: e.message?.takeIf { it.isNotBlank() && it != "null" }
                        ?: "Unable to load episode details"
                )
            }
        }
    }

    /** Removes a downloaded episode record by its torrent hash. */
    fun removeEpisodeDownload(hash: String) {
        viewModelScope.launch {
            userLibraryRepository.removeDownloadedEpisodeByHash(hash)
        }
    }

    /**
     * Gets information about the next episode, if available.
     *
     * @return A list containing [seriesId, seriesName, seasonNumber, episodeNumber, episodeName] for the next episode,
     *         or null if there is no next episode or if information is unavailable.
     */
    fun getNextEpisodeInfo(): List<Any>? = cachedNextEpisodeInfo

    /** Marks the active episode as completed in Room and Firestore. */
    fun markEpisodeAsCompleted(seriesName: String, episode: TmdbTvEpisode) {
        viewModelScope.launch {
            userLibraryRepository.markEpisodeAsCompleted(
                seriesId = activeSeriesId,
                seasonNumber = activeSeasonNumber,
                episodeNumber = episode.episodeNumber,
                episodeTitle = episode.name.orEmpty(),
                seriesName = seriesName,
                stillPath = episode.stillPath
            )
        }
    }

    /** Marks the active episode as uncompleted in Room and Firestore. */
    fun markEpisodeAsUncompleted(episode: TmdbTvEpisode? = null) {
        viewModelScope.launch {
            userLibraryRepository.markEpisodeAsUncompleted(
                seriesId = activeSeriesId,
                seasonNumber = activeSeasonNumber,
                episodeNumber = episode?.episodeNumber ?: activeEpisodeNumber
            )
        }
    }

    /**
     * Clears in-memory episode details state to prevent stale data display when navigating between episodes.
     */
    fun resetState() {
        activeSeriesId = 0
        activeSeasonNumber = 0
        activeEpisodeNumber = 0
        cachedNextEpisodeInfo = null
        _rawUiState.value = TvEpisodeDetailRawUiState.Loading
    }
}

/**
 * Raw internal UI state representation for episode details before downloads are merged.
 */
sealed interface TvEpisodeDetailRawUiState {
    data object Loading : TvEpisodeDetailRawUiState
    data class Error(val message: String) : TvEpisodeDetailRawUiState
    data class Success(
        val seriesName: String,
        val episode: TmdbTvEpisode,
        val torrents: List<EztvTorrent>,
        val releasesByQuality: Map<VideoQualityGroup, List<TorrentRelease>>,
        val posterUrl: String? = null
    ) : TvEpisodeDetailRawUiState
}

/**
 * UI state exposed to the episode detail view.
 */
sealed interface TvEpisodeDetailUiState {
    data object Loading : TvEpisodeDetailUiState
    data class Error(val message: String) : TvEpisodeDetailUiState
    data class Success(
        val seriesName: String,
        val episode: TmdbTvEpisode,
        val torrents: List<EztvTorrent>,
        val releasesByQuality: Map<VideoQualityGroup, List<TorrentRelease>>,
        val isDownloaded: Boolean,
        val downloadedHashes: Set<String> = emptySet(),
        val posterUrl: String? = null,
        val imdbId: String? = null
    ) : TvEpisodeDetailUiState
}
