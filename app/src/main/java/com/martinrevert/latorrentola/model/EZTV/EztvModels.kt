package com.martinrevert.latorrentola.model.EZTV

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root response object returned by the EZTV API endpoint (`get-torrents`).
 *
 * @property imdbId Numeric IMDb identifier query string.
 * @property torrentsCount Total matching torrent count reported by EZTV.
 * @property limit Max result count requested per page.
 * @property page Current result page number.
 * @property torrents Torrent release entries returned for the request.
 */
@Serializable
data class EztvResponse(
    @SerializedName("imdb_id")
    @SerialName("imdb_id")
    val imdbId: String? = null,
    @SerializedName("torrents_count")
    @SerialName("torrents_count")
    val torrentsCount: Int = 0,
    @SerializedName("limit")
    @SerialName("limit")
    val limit: Int = 100,
    @SerializedName("page")
    @SerialName("page")
    val page: Int = 1,
    @SerializedName("torrents")
    @SerialName("torrents")
    val torrents: List<EztvTorrent> = emptyList()
)

/**
 * An individual torrent release item returned by the EZTV API.
 *
 * @property id Unique EZTV torrent identifier.
 * @property hash Torrent info hash.
 * @property filename Name of the .torrent file or release filename.
 * @property episodeUrl URL to the episode page on EZTV.
 * @property torrentUrl Direct download link for the .torrent file.
 * @property magnetUrl Direct magnet URI.
 * @property title Release title containing show name, season, episode, and quality info.
 * @property imdbId IMDb identifier for the series.
 * @property season Season number string or integer representation.
 * @property episode Episode number string or integer representation.
 * @property seeds Number of seeders available.
 * @property peers Number of leechers/peers available.
 * @property dateReleasedUnix Release timestamp in seconds.
 * @property sizeBytes Release size in bytes.
 */
@Serializable
data class EztvTorrent(
    @SerializedName("id")
    @SerialName("id")
    val id: Int = 0,
    @SerializedName("hash")
    @SerialName("hash")
    val hash: String = "",
    @SerializedName("filename")
    @SerialName("filename")
    val filename: String? = null,
    @SerializedName("episode_url")
    @SerialName("episode_url")
    val episodeUrl: String? = null,
    @SerializedName("torrent_url")
    @SerialName("torrent_url")
    val torrentUrl: String? = null,
    @SerializedName("magnet_url")
    @SerialName("magnet_url")
    val magnetUrl: String = "",
    @SerializedName("title")
    @SerialName("title")
    val title: String = "",
    @SerializedName("imdb_id")
    @SerialName("imdb_id")
    val imdbId: String? = null,
    @SerializedName("season")
    @SerialName("season")
    val season: String = "0",
    @SerializedName("episode")
    @SerialName("episode")
    val episode: String = "0",
    @SerializedName("seeds")
    @SerialName("seeds")
    val seeds: Int = 0,
    @SerializedName("peers")
    @SerialName("peers")
    val peers: Int = 0,
    @SerializedName("date_released_unix")
    @SerialName("date_released_unix")
    val dateReleasedUnix: Long? = null,
    @SerializedName("size_bytes")
    @SerialName("size_bytes")
    val sizeBytes: String? = null
) {
    /** Parses the season number as an integer. */
    val seasonNumberInt: Int
        get() = season.toIntOrNull() ?: 0

    /** Parses the episode number as an integer. */
    val episodeNumberInt: Int
        get() = episode.toIntOrNull() ?: 0

    /** Formats the byte size into readable megabytes or gigabytes. */
    val formattedSize: String
        get() {
            val bytes = sizeBytes?.toLongOrNull() ?: return ""
            val mb = bytes / (1024.0 * 1024.0)
            return if (mb >= 1024) {
                "%.2f GB".format(mb / 1024.0)
            } else {
                "%.0f MB".format(mb)
            }
        }
}
