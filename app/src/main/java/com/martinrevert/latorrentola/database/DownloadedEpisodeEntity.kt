package com.martinrevert.latorrentola.database

import androidx.room.Entity
import com.martinrevert.latorrentola.model.user.DownloadedEpisode

@Entity(
    tableName = "downloaded_episodes",
    primaryKeys = ["userId", "hash"]
)
data class DownloadedEpisodeEntity(
    val userId: String,
    val hash: String,
    val seriesId: Int,
    val imdbId: String = "",
    val seriesName: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeName: String,
    val releaseTitle: String = "",
    val quality: String = "HD",
    val magnetUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val stillPath: String? = null
) {
    fun toDownloadedEpisode(): DownloadedEpisode = DownloadedEpisode(
        seriesId = seriesId,
        imdbId = imdbId.ifBlank { null },
        seriesName = seriesName,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeName = episodeName,
        releaseTitle = releaseTitle,
        quality = quality,
        hash = hash,
        magnetUrl = magnetUrl,
        timestamp = timestamp,
        stillPath = stillPath
    )

    companion object {
        fun from(userId: String, download: DownloadedEpisode): DownloadedEpisodeEntity {
            val normalizedImdb = com.martinrevert.latorrentola.utils.MediaIdHelper.normalizeImdbId(download.imdbId) ?: ""
            val docId = download.hash.ifBlank {
                if (normalizedImdb.isNotBlank()) {
                    "${normalizedImdb}_s${download.seasonNumber}_e${download.episodeNumber}"
                } else {
                    "${download.seriesId}_s${download.seasonNumber}_e${download.episodeNumber}"
                }
            }
            return DownloadedEpisodeEntity(
                userId = userId,
                hash = docId,
                seriesId = download.seriesId,
                imdbId = normalizedImdb,
                seriesName = download.seriesName,
                seasonNumber = download.seasonNumber,
                episodeNumber = download.episodeNumber,
                episodeName = download.episodeName,
                releaseTitle = download.releaseTitle,
                quality = download.quality,
                magnetUrl = download.magnetUrl,
                timestamp = download.timestamp,
                stillPath = download.stillPath
            )
        }
    }
}
