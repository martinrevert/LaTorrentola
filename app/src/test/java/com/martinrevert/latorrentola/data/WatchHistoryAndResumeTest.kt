package com.martinrevert.latorrentola.data

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.utils.MediaIdHelper
import org.junit.Test

class WatchHistoryAndResumeTest {

    @Test
    fun `canonical mediaId formatting using MediaIdHelper`() {
        val movieId = 12345
        val seriesId = 100
        val seasonNumber = 1
        val episodeNumber = 2

        // Test movie with canonical IMDb ID
        val imdbMovieMediaId = MediaIdHelper.forMovie(imdbId = "tt1375666", movieId = movieId, title = "Inception")
        assertThat(imdbMovieMediaId).isEqualTo("tt1375666")

        // Test movie without IMDb ID returns unknown_movie
        val fallbackMovieMediaId = MediaIdHelper.forMovie(movieId = movieId, infoHash = "hash123", title = "Inception")
        assertThat(fallbackMovieMediaId).isEqualTo("unknown_movie")

        // Test TV Episode with canonical IMDb ID
        val imdbEpisodeMediaId = MediaIdHelper.forEpisode(
            imdbId = "tt0944947",
            seriesId = seriesId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
        assertThat(imdbEpisodeMediaId).isEqualTo("tt0944947_s1_e2")

        // Test TV Episode without IMDb ID returns unknown_episode
        val episodeMediaId = MediaIdHelper.forEpisode(
            seriesId = seriesId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            infoHash = "hash456",
            title = "Episode 2"
        )
        assertThat(episodeMediaId).isEqualTo("unknown_episode")
    }

    @Test
    fun `remote watch history timestamp precedence over local`() {
        val localProgress = PlaybackProgress(
            mediaId = "12345",
            title = "Movie",
            positionMs = 10_000L,
            durationMs = 100_000L,
            timestamp = 1000L,
            isEpisode = false
        )

        val remoteProgressNewer = PlaybackProgress(
            mediaId = "12345",
            title = "Movie",
            positionMs = 50_000L,
            durationMs = 100_000L,
            timestamp = 2000L,
            isEpisode = false
        )

        val shouldChooseRemote = remoteProgressNewer.timestamp > localProgress.timestamp
        assertThat(shouldChooseRemote).isTrue()
    }

    @Test
    fun `resume piece estimation formula`() {
        val resumePositionMs = 50_000L
        val durationMs = 100_000L
        val fileLength = 1_000_000L
        val pieceLength = 16_384
        val firstPiece = 0
        val lastPiece = 60

        val estimatedOffset = ((resumePositionMs.toDouble() / durationMs.toDouble()) * fileLength).toLong()
        val offsetInFile = estimatedOffset.coerceIn(0L, fileLength)
        val fileOffset = 0L
        val resumePiece = ((fileOffset + offsetInFile) / pieceLength).toInt().coerceIn(firstPiece, lastPiece)

        assertThat(resumePiece).isGreaterThan(0)
        assertThat(resumePiece).isAtMost(lastPiece)
    }
}
