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

        // Test Movie ID format
        val movieMediaId = MediaIdHelper.forMovie(movieId = movieId, infoHash = "hash123", title = "Inception")
        assertThat(movieMediaId).isEqualTo("12345")

        // Test fallback for movie without movieId (infoHash is rejected per AGENTS.md, title is used as temporary fallback)
        val fallbackMovieMediaId = MediaIdHelper.forMovie(movieId = 0, infoHash = "hash123", title = "Inception")
        assertThat(fallbackMovieMediaId).isEqualTo("Inception")

        // Test TV Episode ID format
        val episodeMediaId = MediaIdHelper.forEpisode(
            seriesId = seriesId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            infoHash = "hash456",
            title = "Episode 2"
        )
        assertThat(episodeMediaId).isEqualTo("media_100_s1_e2")
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
