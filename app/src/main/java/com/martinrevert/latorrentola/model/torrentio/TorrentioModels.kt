package com.martinrevert.latorrentola.model.torrentio

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root response from Torrentio Stremio addon stream endpoint.
 *
 * @property streams List of available torrent media streams.
 */
@Serializable
data class TorrentioResponse(
    @SerializedName("streams")
    @SerialName("streams")
    val streams: List<TorrentioStream> = emptyList()
)

/**
 * Individual torrent stream metadata item returned by Torrentio.
 *
 * @property name Stream quality label and header (e.g. "Torrentio\n1080p" or "Torrentio\n4k HDR").
 * @property title Descriptive release information including release name, seeds, size, and provider.
 * @property infoHash BitTorrent hexadecimal v1 infohash.
 * @property fileIdx Target video file index inside multi-file torrents, when specified.
 * @property behaviorHints Extra playback hinting metadata such as filename and bingeGroup.
 * @property sources Tracker and DHT announce URLs for swarming.
 */
@Serializable
data class TorrentioStream(
    @SerializedName("name")
    @SerialName("name")
    val name: String? = null,
    @SerializedName("title")
    @SerialName("title")
    val title: String? = null,
    @SerializedName("infoHash")
    @SerialName("infoHash")
    val infoHash: String? = null,
    @SerializedName("fileIdx")
    @SerialName("fileIdx")
    val fileIdx: Int? = null,
    @SerializedName("behaviorHints")
    @SerialName("behaviorHints")
    val behaviorHints: TorrentioBehaviorHints? = null,
    @SerializedName("sources")
    @SerialName("sources")
    val sources: List<String>? = null
)

/**
 * Torrentio playback behavior hints.
 *
 * @property bingeGroup Grouping identifier across episodes of the same release group.
 * @property filename Specific media filename inside the torrent container.
 */
@Serializable
data class TorrentioBehaviorHints(
    @SerializedName("bingeGroup")
    @SerialName("bingeGroup")
    val bingeGroup: String? = null,
    @SerializedName("filename")
    @SerialName("filename")
    val filename: String? = null
)
