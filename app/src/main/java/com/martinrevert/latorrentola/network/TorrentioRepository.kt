package com.martinrevert.latorrentola.network

import android.util.Log
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.EZTV.downloadInfoHash
import com.martinrevert.latorrentola.model.YTS.Torrent
import com.martinrevert.latorrentola.model.torrent.TorrentRelease
import com.martinrevert.latorrentola.model.torrentio.TorrentioStream
import com.martinrevert.latorrentola.utils.DevicePlaybackCapabilities
import com.martinrevert.latorrentola.utils.TorrentLaunchHelper
import com.martinrevert.latorrentola.utils.TorrentioMetadataParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accesses Torrentio Stremio addon public API and maps releases to unified [TorrentRelease] models.
 * Enforces anti-season-pack and anti-EZTV filtering rules and evaluates local hardware playback capabilities.
 */
@Singleton
class TorrentioRepository @Inject constructor(
    private val torrentioService: TorrentioService,
    private val capabilities: DevicePlaybackCapabilities
) {

    companion object {
        private const val TAG = "TorrentioRepository"
    }

    /**
     * Retrieves torrent releases for a movie from Torrentio using its IMDb identifier.
     *
     * @param imdbId IMDb identifier (with or without "tt" prefix).
     * @return List of mapped [TorrentRelease] items.
     */
    suspend fun getMovieTorrents(imdbId: String): List<TorrentRelease> = withContext(Dispatchers.IO) {
        if (imdbId.isBlank()) return@withContext emptyList()
        val cleanId = formatImdbId(imdbId)

        return@withContext try {
            val response = torrentioService.getMovieStreams(cleanId)
            response.streams
                .filter { !it.infoHash.isNullOrBlank() }
                .map { stream -> mapStreamToRelease(stream) }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Torrentio movie streams for $cleanId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Retrieves torrent releases for an individual TV series episode from Torrentio.
     * Enforces strict exclusion of whole-season packs and discards EZTV results.
     *
     * @param imdbId Series IMDb identifier.
     * @param seasonNumber Season number.
     * @param episodeNumber Episode number within season.
     * @return List of mapped [TorrentRelease] items for the specific episode.
     */
    suspend fun getSeriesTorrents(
        imdbId: String,
        seasonNumber: Int,
        episodeNumber: Int
    ): List<TorrentRelease> = withContext(Dispatchers.IO) {
        if (imdbId.isBlank()) return@withContext emptyList()
        val cleanId = formatImdbId(imdbId)
        val seriesParam = "$cleanId:$seasonNumber:$episodeNumber"

        return@withContext try {
            val response = torrentioService.getSeriesStreams(seriesParam)
            response.streams
                .filter { !it.infoHash.isNullOrBlank() }
                // 1. Discard EZTV results to avoid duplicating native EZTV catalog
                .filter { !TorrentioMetadataParser.isEztvStream(it) }
                // 2. Discard full season packs; the app strictly works with single episodes
                .filter { !TorrentioMetadataParser.isSeasonPack(it.title, it.behaviorHints?.filename) }
                .map { stream -> mapStreamToRelease(stream) }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Torrentio series streams for $seriesParam: ${e.message}")
            emptyList()
        }
    }

    /**
     * Converts a YTS [Torrent] to a unified [TorrentRelease].
     * Rule: YTS audio is explicitly marked as AAC (e.g. "AAC 5.1" or "AAC").
     */
    fun fromYtsTorrent(torrent: Torrent, movieTitle: String): TorrentRelease {
        val hash = torrent.hash.orEmpty().lowercase()
        val (qualityGroup, resolutionLabel) = TorrentioMetadataParser.extractResolution(
            name = torrent.quality,
            title = torrent.type
        )
        val audioCodec = TorrentioMetadataParser.extractAudioCodec(
            title = null,
            filename = null,
            isYts = true,
            audioChannels = torrent.audioChannels
        )
        val videoCodec = torrent.videoCodec?.uppercase() ?: "X264"
        val visualTags = mutableListOf<String>().apply {
            torrent.bitDepth?.let { if (it == "10") add("10-BIT") }
        }
        val sizeBytes = torrent.sizeBytes?.toLongOrNull()
        val formattedSize = torrent.size.orEmpty()
        val seeds = torrent.seeds ?: 0
        val peers = torrent.peers ?: 0
        val compatibility = capabilities.evaluateRelease(
            videoCodec = videoCodec,
            resolutionLabel = resolutionLabel,
            visualTags = visualTags,
            audioCodec = audioCodec
        )

        return TorrentRelease(
            hash = hash,
            title = movieTitle,
            qualityGroup = qualityGroup,
            resolutionLabel = resolutionLabel,
            audioCodec = audioCodec,
            videoCodec = videoCodec,
            visualTags = visualTags,
            sizeBytes = sizeBytes,
            formattedSize = formattedSize,
            seeds = seeds,
            peers = peers,
            provider = "YTS",
            compatibility = compatibility,
            isYts = true,
            magnetUrl = TorrentLaunchHelper.buildMagnetUri(hash, movieTitle),
            fileIdx = null
        )
    }

    /**
     * Converts an [EztvTorrent] to a unified [TorrentRelease].
     */
    fun fromEztvTorrent(torrent: EztvTorrent): TorrentRelease {
        val hash = torrent.downloadInfoHash() ?: torrent.hash.lowercase()
        val (qualityGroup, resolutionLabel) = TorrentioMetadataParser.extractResolution(
            name = null,
            title = torrent.title,
            filename = torrent.filename
        )
        val audioCodec = TorrentioMetadataParser.extractAudioCodec(
            title = torrent.title,
            filename = torrent.filename,
            isYts = false
        )
        val videoCodec = TorrentioMetadataParser.extractVideoCodec(torrent.title, torrent.filename)
        val visualTags = TorrentioMetadataParser.extractVisualTags(null, torrent.title, torrent.filename)
        val sizeBytes = torrent.sizeBytes?.toLongOrNull()
        val formattedSize = torrent.formattedSize
        val seeds = torrent.seeds
        val peers = torrent.peers
        val compatibility = capabilities.evaluateRelease(
            videoCodec = videoCodec,
            resolutionLabel = resolutionLabel,
            visualTags = visualTags,
            audioCodec = audioCodec
        )

        return TorrentRelease(
            hash = hash,
            title = torrent.title,
            qualityGroup = qualityGroup,
            resolutionLabel = resolutionLabel,
            audioCodec = audioCodec,
            videoCodec = videoCodec,
            visualTags = visualTags,
            sizeBytes = sizeBytes,
            formattedSize = formattedSize,
            seeds = seeds,
            peers = peers,
            provider = "EZTV",
            compatibility = compatibility,
            isYts = false,
            magnetUrl = torrent.magnetUrl.takeIf { it.isNotBlank() } ?: TorrentLaunchHelper.buildMagnetUri(hash, torrent.title),
            fileIdx = null
        )
    }

    private fun mapStreamToRelease(stream: TorrentioStream): TorrentRelease {
        val hash = stream.infoHash.orEmpty().lowercase()
        val titleLine = stream.title?.lines()?.firstOrNull()?.trim()
        val releaseName = stream.behaviorHints?.filename ?: titleLine ?: "Torrentio Release"
        val (qualityGroup, resolutionLabel) = TorrentioMetadataParser.extractResolution(
            name = stream.name,
            title = stream.title,
            filename = stream.behaviorHints?.filename
        )
        val provider = TorrentioMetadataParser.extractProvider(stream.title)
        val isYtsProvider = provider.equals("YTS", ignoreCase = true)
        val audioCodec = TorrentioMetadataParser.extractAudioCodec(
            title = stream.title,
            filename = stream.behaviorHints?.filename,
            isYts = isYtsProvider
        )
        val videoCodec = TorrentioMetadataParser.extractVideoCodec(stream.title, stream.behaviorHints?.filename)
        val visualTags = TorrentioMetadataParser.extractVisualTags(
            name = stream.name,
            title = stream.title,
            filename = stream.behaviorHints?.filename
        )
        val formattedSize = TorrentioMetadataParser.extractSize(stream.title)
        val sizeBytes = TorrentioMetadataParser.parseSizeToBytes(formattedSize)
        val seeds = TorrentioMetadataParser.extractSeeds(stream.title)

        val compatibility = capabilities.evaluateRelease(
            videoCodec = videoCodec,
            resolutionLabel = resolutionLabel,
            visualTags = visualTags,
            audioCodec = audioCodec
        )

        val magnetUrl = TorrentLaunchHelper.buildMagnetUri(
            hash = hash,
            title = releaseName
        )

        return TorrentRelease(
            hash = hash,
            title = releaseName,
            qualityGroup = qualityGroup,
            resolutionLabel = resolutionLabel,
            audioCodec = audioCodec,
            videoCodec = videoCodec,
            visualTags = visualTags,
            sizeBytes = sizeBytes,
            formattedSize = formattedSize,
            seeds = seeds,
            peers = 0,
            provider = provider,
            compatibility = compatibility,
            isYts = isYtsProvider,
            magnetUrl = magnetUrl,
            fileIdx = stream.fileIdx
        )
    }

    private fun formatImdbId(imdbId: String): String {
        val trimmed = imdbId.trim()
        return if (trimmed.startsWith("tt", ignoreCase = true)) {
            trimmed.lowercase()
        } else {
            "tt$trimmed"
        }
    }
}
