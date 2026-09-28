package com.martinrevert.latorrentola.model.user

import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.serialization.Serializable

/**
 * A user's downloaded TV episode torrent record saved in Firestore.
 *
 * @property seriesId TMDB series identifier.
 * @property seriesName Name of the TV series.
 * @property seasonNumber Season number of the episode.
 * @property episodeNumber Episode number within its season.
 * @property episodeName Title of the episode.
 * @property releaseTitle Full EZTV release title (e.g. "Series.S01E01.720p.HDTV").
 * @property quality Quality or format extracted from title/release.
 * @property hash Torrent info hash identifying the release.
 * @property magnetUrl Magnet URI used for downloading.
 * @property timestamp Time the download record was created, in milliseconds.
 * @property stillPath Episode backdrop/still image relative path from TMDB.
 */
@IgnoreExtraProperties
@Serializable
data class DownloadedEpisode(
    val seriesId: Int = 0,
    val seriesName: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val episodeName: String = "",
    val releaseTitle: String = "",
    val quality: String = "",
    val hash: String = "",
    val magnetUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val stillPath: String? = null
)
