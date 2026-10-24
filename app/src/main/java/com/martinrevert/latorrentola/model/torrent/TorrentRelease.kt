package com.martinrevert.latorrentola.model.torrent

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

/**
 * High-level video resolution grouping for UI presentation.
 */
enum class VideoQualityGroup(val displayTitle: String, val sortOrder: Int) {
    UHD_4K("4K Ultra HD", 1),
    FHD_1080P("1080p Full HD", 2),
    HD_720P("720p HD", 3),
    OTHER("Otras calidades", 4)
}

/**
 * Playback compatibility status evaluated against local device hardware capabilities.
 */
enum class PlaybackCompatibility {
    /** Native decoding supported directly on this device's screen and speakers/headphones. */
    NATIVE,

    /** Video supported, but audio format requires AVR / HDMI Passthrough / External Player or NAS. */
    AUDIO_REQUIRES_PASSTHROUGH_OR_EXTERNAL,

    /** Video resolution or codec is beyond this device's hardware decoding capability. */
    VIDEO_UNSUPPORTED
}

/**
 * Unified release model for UI presentation across YTS, EZTV, and Torrentio sources.
 * Keeps UI components decoupled and avoids mutating persistent Room/Firestore entities.
 *
 * @property hash BitTorrent v1 infohash.
 * @property title User-visible release title or filename.
 * @property qualityGroup Video resolution grouping (4K, 1080p, 720p, Other).
 * @property resolutionLabel Normalized resolution text (e.g. "4K", "1080p", "720p").
 * @property audioCodec Audio format label (e.g. "AAC", "AAC 5.1", "DTS-HD MA 5.1", "TrueHD Atmos", "DDP 5.1").
 * @property videoCodec Video codec string when detected (e.g. "x265", "x264", "AV1").
 * @property visualTags Visual enhancement tags such as "HDR10", "DV", "10-BIT".
 * @property sizeBytes Size in bytes for disk space calculations.
 * @property formattedSize Human-readable file size (e.g. "2.4 GB").
 * @property seeds Available seeders reported by tracker or scraping backend.
 * @property peers Available leechers/peers when reported.
 * @property provider Source provider or indexer name (e.g. "YTS", "EZTV", "1337x", "ThePirateBay").
 * @property compatibility Evaluated hardware playback capability for the current device.
 * @property isYts Whether this release originated directly from the YTS catalog.
 * @property magnetUrl Direct magnet URI if precomputed.
 * @property fileIdx Target file index inside multi-file torrents.
 */
@Serializable
data class TorrentRelease(
    @SerializedName("hash")
    val hash: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("quality_group")
    val qualityGroup: VideoQualityGroup,
    @SerializedName("resolution_label")
    val resolutionLabel: String,
    @SerializedName("audio_codec")
    val audioCodec: String,
    @SerializedName("video_codec")
    val videoCodec: String? = null,
    @SerializedName("visual_tags")
    val visualTags: List<String> = emptyList(),
    @SerializedName("size_bytes")
    val sizeBytes: Long? = null,
    @SerializedName("formatted_size")
    val formattedSize: String = "",
    @SerializedName("seeds")
    val seeds: Int = 0,
    @SerializedName("peers")
    val peers: Int = 0,
    @SerializedName("provider")
    val provider: String = "",
    @SerializedName("compatibility")
    val compatibility: PlaybackCompatibility = PlaybackCompatibility.NATIVE,
    @SerializedName("is_yts")
    val isYts: Boolean = false,
    @SerializedName("magnet_url")
    val magnetUrl: String? = null,
    @SerializedName("file_idx")
    val fileIdx: Int? = null
) {
    val infoHash: String
        get() = hash

    val sourceName: String
        get() = provider

    val isOriginalYts: Boolean
        get() = isYts

    val isOriginalEztv: Boolean
        get() = provider.equals("EZTV", ignoreCase = true)
}
