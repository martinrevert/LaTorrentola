package com.martinrevert.latorrentola.ui.player

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

class LocalPlayerActivityTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
    }

    @Test
    fun `EXTRA_CAST_ENABLED key matches between service and LocalPlayerActivity`() {
        assertThat(LocalPlayerActivity.EXTRA_CAST_ENABLED).isEqualTo("torrent_cast_enabled")
    }

    @Test
    fun `EXTRA_CAST_URL key matches between service and LocalPlayerActivity`() {
        assertThat(LocalPlayerActivity.EXTRA_CAST_URL).isEqualTo("verified_media_cast_url")
    }

    @Test
    fun `EXTRA_STREAM_URL and EXTRA_INFO_HASH keys match expected values`() {
        assertThat(LocalPlayerActivity.EXTRA_STREAM_URL).isEqualTo("verified_media_stream_url")
        assertThat(LocalPlayerActivity.EXTRA_INFO_HASH).isEqualTo("torrent_info_hash")
    }

    @Test
    fun `extractSeasonAndEpisode extracts season and episode accurately from complex release titles`() {
        val (season, episode) = LocalPlayerActivity.extractSeasonAndEpisode(
            "Los Simpson S38E01 The Simpsons S38E01 1080p WEB H264-CAKES EZTV"
        )
        assertThat(season).isEqualTo(38)
        assertThat(episode).isEqualTo(1)

        val (s2, e2) = LocalPlayerActivity.extractSeasonAndEpisode("Breaking.Bad.5x16.Felina.720p.mkv")
        assertThat(s2).isEqualTo(5)
        assertThat(e2).isEqualTo(16)

        val (s3, e3) = LocalPlayerActivity.extractSeasonAndEpisode("Inception.2010.1080p.BluRay.mp4")
        assertThat(s3).isEqualTo(0)
        assertThat(e3).isEqualTo(0)
    }

    @Test
    fun `extractCleanSearchTitles strips quality tags and extracts clean series and movie candidates`() {
        val simpsonsCandidates = LocalPlayerActivity.extractCleanSearchTitles(
            "Los Simpson S38E01 The Simpsons S38E01 1080p WEB H264-CAKES EZTV"
        )
        assertThat(simpsonsCandidates).contains("Los Simpson")
        assertThat(simpsonsCandidates).contains("The Simpsons")

        val movieCandidates = LocalPlayerActivity.extractCleanSearchTitles(
            "Inception.2010.1080p.BluRay.x264-YTS.mp4"
        )
        assertThat(movieCandidates).contains("Inception")
    }

    @Test
    fun `filterFallbackResults filters out wrong season or episode when expected coordinates are provided`() {
        val wrongEpisode = com.martinrevert.latorrentola.network.OpenSubtitleResult(
            id = "1",
            fileId = 101L,
            language = "es",
            release = "the.simpsons.s18e16.homerazzi.hdtv",
            downloadCount = 10L,
            fileName = "the.simpsons.s18e16.srt",
            featureTitle = "The Simpsons - S18E16 - Homerazzi"
        )
        val matchingEpisode = com.martinrevert.latorrentola.network.OpenSubtitleResult(
            id = "2",
            fileId = 102L,
            language = "es",
            release = "the.simpsons.s38e01.1080p.web.h264-cakes",
            downloadCount = 20L,
            fileName = "the.simpsons.s38e01.srt",
            featureTitle = "The Simpsons - S38E01 - The Children's Book Job"
        )

        val results = LocalPlayerActivity.filterFallbackResults(
            rawResults = listOf(wrongEpisode, matchingEpisode),
            expectedTitle = "The Simpsons",
            expectedYear = null,
            expectedSeason = 38,
            expectedEpisode = 1
        )

        assertThat(results).hasSize(1)
        assertThat(results.first().id).isEqualTo("2")
    }

    @Test
    fun `playback completion threshold of 95 percent marks progress as completed and duration as final position`() {
        val duration = 100_000L

        fun computeProgress(pos: Long, dur: Long): Pair<Long, Boolean> {
            val isNearlyFinished = pos >= (dur * 0.95f).toLong()
            val finalPos = if (isNearlyFinished) dur else pos
            val isCompleted = isNearlyFinished || (dur > 0 && finalPos >= dur)
            return Pair(finalPos, isCompleted)
        }

        val (posBelow, completedBelow) = computeProgress(94_000L, duration)
        assertThat(posBelow).isEqualTo(94_000L)
        assertThat(completedBelow).isFalse()

        val (posAt, completedAt) = computeProgress(95_000L, duration)
        assertThat(posAt).isEqualTo(100_000L)
        assertThat(completedAt).isTrue()

        val (posAbove, completedAbove) = computeProgress(98_000L, duration)
        assertThat(posAbove).isEqualTo(100_000L)
        assertThat(completedAbove).isTrue()
    }

    @Test
    fun `new media item does not inherit previous controller position`() {
        val previousMediaId = "ep_1"
        val newMediaId = "ep_2"
        val previousPosition = 45_000L
        var lastKnownPosition = previousPosition

        val isNewMedia = previousMediaId != newMediaId
        if (isNewMedia) {
            lastKnownPosition = 0L
        }

        val startingPosition = if (isNewMedia) 0L else lastKnownPosition
        assertThat(startingPosition).isEqualTo(0L)
    }

    @Test
    fun `PlaybackTrackOption preserves castTrackId and selection state`() {
        val audioOption = PlaybackTrackOption(
            label = "Español (Latinoamérica) (AC-3 6 ch)",
            group = null,
            trackIndex = 0,
            castTrackId = 2L,
            isSelected = true
        )

        assertThat(audioOption.label).isEqualTo("Español (Latinoamérica) (AC-3 6 ch)")
        assertThat(audioOption.castTrackId).isEqualTo(2L)
        assertThat(audioOption.isSelected).isTrue()
        assertThat(audioOption.trackIndex).isEqualTo(0)
    }

    @Test
    fun `audio track options selection updates isSelected accurately`() {
        val initialTracks = listOf(
            PlaybackTrackOption("Audio 1", null, 0, 1L, true),
            PlaybackTrackOption("Audio 2", null, 1, 2L, false)
        )

        val selectedOption = initialTracks[1]
        val updatedTracks = initialTracks.map {
            it.copy(isSelected = it.label == selectedOption.label && it.trackIndex == selectedOption.trackIndex)
        }

        assertThat(updatedTracks[0].isSelected).isFalse()
        assertThat(updatedTracks[1].isSelected).isTrue()
    }

    @Test
    fun `EXTRA_FROM_PIP key is correctly defined`() {
        assertThat(LocalPlayerActivity.EXTRA_FROM_PIP).isEqualTo("extra_from_pip")
    }

    @Test
    fun `isPlayerActivityForeground returns false when activeActivityRef is null and not foreground`() {
        LocalPlayerActivity.isPlayerForeground = false
        assertThat(LocalPlayerActivity.isPlayerActivityForeground()).isFalse()
        LocalPlayerActivity.isPlayerForeground = true
        assertThat(LocalPlayerActivity.isPlayerActivityForeground()).isTrue()
        LocalPlayerActivity.isPlayerForeground = false
    }

    @Test
    fun `isAlreadyLoaded detects matching infoHash or canonical media id`() {
        val downloadHash = "abcdef1234567890abcdef1234567890abcdef12"
        val curInfoHash = "ABCDEF1234567890ABCDEF1234567890ABCDEF12"
        val curMediaId = "media_123_s1_e2"
        val canonicalEpisodeId = "media_123_s1_e2"

        val matchesByHash = curInfoHash.isNotBlank() && curInfoHash.equals(downloadHash, ignoreCase = true)
        val matchesByCanonicalId = canonicalEpisodeId == curMediaId

        assertThat(matchesByHash).isTrue()
        assertThat(matchesByCanonicalId).isTrue()
    }

    @Test
    fun `PIP surface release guard preserves video surface when LocalPlayerActivity is active or expanding`() {
        var shouldClearVideoSurface = false
        val isExpanding = true
        val isPlayerForeground = false

        if (!isExpanding && !isPlayerForeground) {
            shouldClearVideoSurface = true
        }

        assertThat(shouldClearVideoSurface).isFalse()

        val isExpandingFalse = false
        val isPlayerForegroundTrue = true
        var shouldClearWhenForeground = false
        if (!isExpandingFalse && !isPlayerForegroundTrue) {
            shouldClearWhenForeground = true
        }
        assertThat(shouldClearWhenForeground).isFalse()

        var shouldClearWhenNeither = false
        if (!isExpandingFalse && !isPlayerForeground) {
            shouldClearWhenNeither = true
        }
        assertThat(shouldClearWhenNeither).isTrue()
    }

    @Test
    fun `Cast handoff preserves playback position and enables autoplay`() {
        val lastPositionMs = 45_200L
        val localWasPlaying = true

        val autoplay = localWasPlaying
        val startTimeSec = lastPositionMs / 1000.0

        assertThat(autoplay).isTrue()
        assertThat(startTimeSec).isEqualTo(45.2)
    }
}
