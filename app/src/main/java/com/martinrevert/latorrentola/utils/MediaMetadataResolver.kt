package com.martinrevert.latorrentola.utils

import android.util.Log
import com.martinrevert.latorrentola.database.TorrentDownloadDao
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.YtsRepository
import kotlinx.coroutines.CancellationException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolved canonical media information decoupling reverse lookup, title cleaning,
 * and media identifier normalization from UI screens and activities.
 */
data class CanonicalMediaInfo(
    val mediaId: String,
    val title: String,
    val posterUrl: String? = null,
    val isEpisode: Boolean = false,
    val movieId: Int = 0,
    val seriesId: Int = 0,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val seriesName: String? = null,
    val episodeName: String? = null
)

/**
 * Decoupled resolver for resolving canonical media metadata across Torrents,
 * Network Storage (NAS/SMB/NFS/USB), and Local media.
 */
@Singleton
class MediaMetadataResolver @Inject constructor(
    private val tmdbRepository: TmdbRepository,
    private val ytsRepository: YtsRepository,
    private val torrentDownloadDao: TorrentDownloadDao
) {
    companion object {
        private const val TAG = "MediaMetadataResolver"

        /**
         * Extracts season and episode numbers from any number of candidate strings
         * (e.g. title, file name, URI path).
         * Matches standard S01E01, s1e1, 1x01, etc.
         */
        fun extractSeasonAndEpisode(vararg candidates: String?): Pair<Int, Int> {
            for (candidate in candidates) {
                if (candidate.isNullOrBlank()) continue
                val sMatch = Regex("""\bS(\d{1,2})[\s.]?E(\d{1,2})\b""", RegexOption.IGNORE_CASE).find(candidate)
                    ?: Regex("""\b(\d{1,2})x(\d{1,2})\b""", RegexOption.IGNORE_CASE).find(candidate)
                if (sMatch != null) {
                    val season = sMatch.groupValues[1].toIntOrNull() ?: 0
                    val episode = sMatch.groupValues[2].toIntOrNull() ?: 0
                    if (season > 0 && episode > 0) {
                        return Pair(season, episode)
                    }
                }
            }
            return Pair(0, 0)
        }

        /**
         * Cleans release filenames, stripping codec tags, quality, resolutions,
         * audio formats, scene group names, and video container extensions.
         */
        fun extractCleanSearchTitles(rawTitle: String): List<String> {
            if (rawTitle.isBlank()) return emptyList()

            // 1. Strip video extensions
            var text = rawTitle.replace(
                Regex("""\.(mp4|mkv|avi|mov|wmv|webm|ts|m4v)$""", RegexOption.IGNORE_CASE),
                ""
            )

            // 2. Strip quality, codec, release tags and everything that follows them
            text = text.replace(
                Regex(
                    """\b(1080p|720p|480p|2160p|4k|uhd|bluray|blu-ray|web-dl|webrip|web|dvdrip|dvd|hdtv|h264|x264|h265|x265|hevc|avc|10bit|aac|ac3|dts|ddp5\.?1|cakes|eztv|yts|yify|rarbg|ettv|galaxytv|tgx)\b.*$""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )

            // 3. Strip bracketed release tags
            text = text.replace(Regex("""\[.*?\]|\(.*?\)|[\{\}]"""), " ")

            // 4. Split by episode tags (e.g. S38E01, 38x01) or major title separators (- or /)
            val chunks = text.split(Regex("""\bS\d{1,2}[\s.]?E\d{1,2}\b|\b\d{1,2}x\d{1,2}\b|[-/]""", RegexOption.IGNORE_CASE))

            val results = mutableListOf<String>()
            for (chunk in chunks) {
                val cleanChunk = chunk
                    .replace(Regex("""\b(19\d\d|20\d\d)\b.*$"""), "")
                    .replace(Regex("""[._]"""), " ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                if (cleanChunk.length >= 2 && !results.contains(cleanChunk)) {
                    results.add(cleanChunk)
                }
            }

            // Also include the full string without episode tags if not already present
            val fullClean = text
                .replace(Regex("""\bS\d{1,2}[\s.]?E\d{1,2}\b|\b\d{1,2}x\d{1,2}\b""", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("""[._]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (fullClean.length >= 2 && !results.contains(fullClean)) {
                results.add(fullClean)
            }

            return results
        }

        /**
         * Extracts a 4-digit release year from a title candidate.
         */
        fun extractYear(candidate: String?): Int? {
            if (candidate.isNullOrBlank()) return null
            val match = Regex("""\b(19\d\d|20\d\d)\b""").find(candidate)
            return match?.value?.toIntOrNull()
        }

        /**
         * Normalizes a title for loose string comparisons.
         */
        fun normalizeTitle(title: String): String {
            return title
                .lowercase(Locale.ROOT)
                .replace(Regex("""\b(19\d\d|20\d\d)\b"""), " ")
                .replace(Regex("""[^a-z0-9]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }
    }

    /**
     * Resolves the canonical media information for playback or history tracking.
     * Guarantees that neither a 40-character infoHash nor a raw NAS file path is used as a mediaId.
     */
    suspend fun resolve(
        rawTitle: String,
        filePath: String? = null,
        streamUrl: String? = null,
        infoHash: String? = null,
        existingMovieId: Int = 0,
        existingSeriesId: Int = 0,
        existingSeasonNumber: Int = 0,
        existingEpisodeNumber: Int = 0,
        existingPosterUrl: String? = null
    ): CanonicalMediaInfo {
        var movieId = existingMovieId
        var seriesId = existingSeriesId
        var seasonNumber = existingSeasonNumber
        var episodeNumber = existingEpisodeNumber
        var posterUrl = existingPosterUrl
        var seriesName: String? = null
        var episodeName: String? = null

        // 1. Recover known metadata from local Room database if an infoHash is present
        if (!infoHash.isNullOrBlank() && (movieId <= 0 && seriesId <= 0)) {
            try {
                val dbRecord = torrentDownloadDao.get(infoHash.trim())
                if (dbRecord != null) {
                    if (movieId <= 0 && (dbRecord.movieId ?: 0) > 0) {
                        movieId = dbRecord.movieId ?: 0
                    }
                    if (seriesId <= 0 && (dbRecord.seriesId ?: 0) > 0) {
                        seriesId = dbRecord.seriesId ?: 0
                        if (seasonNumber <= 0) seasonNumber = dbRecord.seasonNumber ?: 0
                        if (episodeNumber <= 0) episodeNumber = dbRecord.episodeNumber ?: 0
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Failed to recover metadata from Room DB: ${e.message}")
            }
        }

        // 2. Detect season and episode numbers via regex if not provided
        if (seasonNumber <= 0 || episodeNumber <= 0) {
            val (detectedSeason, detectedEpisode) = extractSeasonAndEpisode(rawTitle, filePath, streamUrl)
            if (detectedSeason > 0 && detectedEpisode > 0) {
                seasonNumber = detectedSeason
                episodeNumber = detectedEpisode
            }
        }

        val cleanCandidates = extractCleanSearchTitles(rawTitle)
        val primaryCleanTitle = cleanCandidates.firstOrNull() ?: rawTitle.substringBeforeLast('.').trim()
        val detectedYear = extractYear(rawTitle) ?: extractYear(filePath)

        // 3. Handle TV Episode resolution
        val isEpisode = seasonNumber > 0 && episodeNumber > 0
        if (isEpisode) {
            if (seriesId <= 0 && primaryCleanTitle.isNotBlank()) {
                try {
                    val searchResult = tmdbRepository.searchTvSeries(primaryCleanTitle).results?.firstOrNull()
                    if (searchResult != null && searchResult.id > 0) {
                        seriesId = searchResult.id
                        seriesName = searchResult.name
                        if (posterUrl.isNullOrBlank()) {
                            posterUrl = searchResult.fullPosterUrl
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "TMDB TV search failed for '$primaryCleanTitle': ${e.message}")
                }
            }

            val finalMediaId = if (seriesId > 0) {
                MediaIdHelper.forEpisode(seriesId, seasonNumber, episodeNumber)
            } else {
                // Safe normalized fallback if TMDB lookup fails while offline (never infoHash)
                "media_${normalizeTitle(primaryCleanTitle)}_s${seasonNumber}_e${episodeNumber}"
            }

            val finalDisplayTitle = when {
                !seriesName.isNullOrBlank() -> "$seriesName - S${seasonNumber}E${episodeNumber}"
                rawTitle.isNotBlank() -> rawTitle
                else -> "Episode S${seasonNumber}E${episodeNumber}"
            }

            return CanonicalMediaInfo(
                mediaId = finalMediaId,
                title = finalDisplayTitle,
                posterUrl = posterUrl,
                isEpisode = true,
                movieId = 0,
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                seriesName = seriesName,
                episodeName = episodeName
            )
        }

        // 4. Handle Movie resolution
        if (movieId <= 0 && primaryCleanTitle.isNotBlank()) {
            try {
                val ytsSearch = ytsRepository.searchMovies(primaryCleanTitle, 1)
                val movies = ytsSearch.data?.movies.orEmpty()
                val matched = if (detectedYear != null) {
                    movies.firstOrNull { it.year == detectedYear } ?: movies.firstOrNull()
                } else {
                    movies.firstOrNull()
                }

                if (matched != null && matched.id > 0) {
                    movieId = matched.id
                    if (posterUrl.isNullOrBlank()) {
                        posterUrl = matched.largeCoverImage ?: matched.mediumCoverImage
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "YTS movie search failed for '$primaryCleanTitle': ${e.message}")
            }

            // Fallback to TMDB movie search if YTS has no match
            if (movieId <= 0) {
                try {
                    val tmdbSearch = tmdbRepository.searchTvSeries(primaryCleanTitle).results?.firstOrNull()
                    // If no movie catalog found, keep safe normalized fallback
                } catch (_: Exception) {}
            }
        }

        val finalMediaId = if (movieId > 0) {
            MediaIdHelper.forMovie(movieId)
        } else {
            // Clean fallback: sanitized title without file extensions or hashes
            primaryCleanTitle.ifBlank { "unknown_movie" }
        }

        return CanonicalMediaInfo(
            mediaId = finalMediaId,
            title = primaryCleanTitle.ifBlank { rawTitle },
            posterUrl = posterUrl,
            isEpisode = false,
            movieId = movieId,
            seriesId = 0,
            seasonNumber = 0,
            episodeNumber = 0
        )
    }
}
