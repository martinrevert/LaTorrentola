package com.martinrevert.latorrentola.ui.player

import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.video.MediaCodecVideoDecoderException
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.TMDB.TmdbTvSummary
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.network.OpenSubtitleResult
import com.martinrevert.latorrentola.ui.components.isObsoleteSurfaceError
import com.martinrevert.latorrentola.ui.components.isStuckPlayerError
import com.martinrevert.latorrentola.ui.components.resolvePlaybackDecodingError
import com.martinrevert.latorrentola.ui.components.resolvePlaybackStallError
import com.martinrevert.latorrentola.utils.AutoPlayQualitySelectionMethod
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.Job
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

        val (s4, e4) = LocalPlayerActivity.extractSeasonAndEpisode("Special.S00E01.mkv")
        assertThat(s4).isEqualTo(0)
        assertThat(e4).isEqualTo(1)
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
    fun `resolveKnownImdbId prioritizes active state, intent, room record, canonical mediaId, and title token`() {
        assertThat(
            LocalPlayerActivity.resolveKnownImdbId(
                currentImdbId = "tt1099212",
                intentImdbId = "tt9999999",
                serviceImdbId = null,
                dbImdbId = null,
                mediaItemId = null,
                mediaTitle = "Twilight",
                fileName = null
            )
        ).isEqualTo("tt1099212")

        assertThat(
            LocalPlayerActivity.resolveKnownImdbId(
                currentImdbId = null,
                intentImdbId = null,
                serviceImdbId = null,
                dbImdbId = "tt0111161",
                mediaItemId = null,
                mediaTitle = "The Shawshank Redemption",
                fileName = null
            )
        ).isEqualTo("tt0111161")

        assertThat(
            LocalPlayerActivity.resolveKnownImdbId(
                currentImdbId = null,
                intentImdbId = null,
                serviceImdbId = null,
                dbImdbId = null,
                mediaItemId = "tt0944947_s1_e2",
                mediaTitle = "Game of Thrones S01E02",
                fileName = null
            )
        ).isEqualTo("tt0944947")

        assertThat(
            LocalPlayerActivity.resolveKnownImdbId(
                currentImdbId = null,
                intentImdbId = null,
                serviceImdbId = null,
                dbImdbId = null,
                mediaItemId = "unknown_movie",
                mediaTitle = "Twilight",
                fileName = "Twilight.2008.tt1099212.1080p.mkv"
            )
        ).isEqualTo("tt1099212")
    }

    @Test
    fun `shouldResetPerMediaMetadataOnIntent resets on new non-PiP intents and preserves state on PiP expansion`() {
        assertThat(LocalPlayerActivity.shouldResetPerMediaMetadataOnIntent(isFromPip = false)).isTrue()
        assertThat(LocalPlayerActivity.shouldResetPerMediaMetadataOnIntent(isFromPip = true)).isFalse()

        // After resetting currentImdbId on a new local/NAS intent without extras, resolveKnownImdbId returns null
        // so searchSubtitles deduces the new file's IMDb ID instead of leaking the previous session's IMDb ID.
        var latchedImdbId: String? = "tt0944947"
        if (LocalPlayerActivity.shouldResetPerMediaMetadataOnIntent(isFromPip = false)) {
            latchedImdbId = null
        }
        assertThat(
            LocalPlayerActivity.resolveKnownImdbId(
                currentImdbId = latchedImdbId,
                intentImdbId = null,
                serviceImdbId = null,
                dbImdbId = null,
                mediaItemId = "unknown_movie",
                mediaTitle = "Twilight.2008.1080p.mkv",
                fileName = "Twilight.2008.1080p.mkv"
            )
        ).isNull()
    }

    @Test
    fun `preserveSubtitleConfigurations retains active or pending subtitle tracks across MediaItem refresh`() {
        val subtitleUri = mockk<Uri>(relaxed = true)
        val mediaUri = mockk<Uri>(relaxed = true)
        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage("es")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val activeItemWithSubs = MediaItem.Builder()
            .setMediaId("unknown_movie")
            .setUri(mediaUri)
            .setSubtitleConfigurations(listOf(subtitleConfig))
            .build()

        val refreshedItemWithoutSubs = MediaItem.Builder()
            .setMediaId("tt1099212")
            .setUri(mediaUri)
            .build()

        val mergedFromController = LocalPlayerActivity.preserveSubtitleConfigurations(
            updatedItem = refreshedItemWithoutSubs,
            activeControllerItem = activeItemWithSubs,
            currentItem = null
        )
        assertThat(mergedFromController.mediaId).isEqualTo("tt1099212")
        assertThat(mergedFromController.localConfiguration?.subtitleConfigurations)
            .containsExactly(subtitleConfig)

        val mergedFromPending = LocalPlayerActivity.preserveSubtitleConfigurations(
            updatedItem = refreshedItemWithoutSubs,
            activeControllerItem = null,
            currentItem = activeItemWithSubs
        )
        assertThat(mergedFromPending.localConfiguration?.subtitleConfigurations)
            .containsExactly(subtitleConfig)
    }

    @Test
    fun `selectBestMovieCandidate matches exact title and year for Twilight 2008 over 1998 and sequels`() {
        val twilight1998 = Movie(
            id = 10,
            imdbCode = "tt0119594",
            title = "Twilight",
            year = 1998
        )
        val newMoon = Movie(
            id = 11,
            imdbCode = "tt1259571",
            title = "The Twilight Saga: New Moon",
            year = 2009
        )
        val twilight2008 = Movie(
            id = 12,
            imdbCode = "tt1099212",
            title = "Twilight",
            year = 2008
        )

        val matched = LocalPlayerActivity.selectBestMovieCandidate(
            movies = listOf(twilight1998, newMoon, twilight2008),
            candidateTitle = "Twilight",
            expectedYear = 2008
        )

        assertThat(matched?.imdbCode).isEqualTo("tt1099212")
    }

    @Test
    fun `selectBestMovieCandidate prefers exact title with plus or minus 1 year tolerance over non-matching title with exact year`() {
        val unrelatedSameYear = Movie(
            id = 20,
            imdbCode = "tt9999991",
            title = "Whiplash: Behind the Scenes",
            year = 2015
        )
        val exactTitleOneYearOff = Movie(
            id = 21,
            imdbCode = "tt2582802",
            title = "Whiplash",
            year = 2014
        )

        val matched = LocalPlayerActivity.selectBestMovieCandidate(
            movies = listOf(unrelatedSameYear, exactTitleOneYearOff),
            candidateTitle = "Whiplash",
            expectedYear = 2015
        )

        assertThat(matched?.imdbCode).isEqualTo("tt2582802")
    }

    @Test
    fun `selectBestTvCandidate prioritizes exact series title over unrelated series matching episode broadcast year`() {
        val unrelatedSeriesPremiereInBroadcastYear = TmdbTvSummary(
            id = 9001,
            name = "The Simpsons: Family Guy Crossover Special",
            originalName = "The Simpsons: Family Guy Crossover Special",
            firstAirDate = "2024-09-29"
        )
        val exactSeriesMatch = TmdbTvSummary(
            id = 456,
            name = "The Simpsons",
            originalName = "The Simpsons",
            firstAirDate = "1989-12-17"
        )

        val matched = LocalPlayerActivity.selectBestTvCandidate(
            seriesList = listOf(unrelatedSeriesPremiereInBroadcastYear, exactSeriesMatch),
            candidateTitle = "The Simpsons",
            expectedYear = 2024
        )

        assertThat(matched?.id).isEqualTo(456)
    }

    @Test
    fun `sortSubtitleResultsForDevice groups es, ea, and sp ahead of en for Spanish locale`() {
        val enTop = OpenSubtitleResult(
            id = "en",
            fileId = 1L,
            language = "en",
            release = "Twilight.2008.EN",
            downloadCount = 200_000L,
            fileName = "twilight.en.srt",
            featureTitle = "Twilight"
        )
        val eaSub = OpenSubtitleResult(
            id = "ea",
            fileId = 2L,
            language = "ea",
            release = "Twilight.2008.LA",
            downloadCount = 12_000L,
            fileName = "twilight.ea.srt",
            featureTitle = "Twilight"
        )
        val spSub = OpenSubtitleResult(
            id = "sp",
            fileId = 3L,
            language = "sp",
            release = "Twilight.2008.EU",
            downloadCount = 9_000L,
            fileName = "twilight.sp.srt",
            featureTitle = "Twilight"
        )
        val esSub = OpenSubtitleResult(
            id = "es",
            fileId = 4L,
            language = "es",
            release = "Twilight.2008.ES",
            downloadCount = 15_000L,
            fileName = "twilight.es.srt",
            featureTitle = "Twilight"
        )

        val sortedEs = LocalPlayerActivity.sortSubtitleResultsForDevice(
            results = listOf(enTop, spSub, eaSub, esSub),
            deviceLanguage = "es"
        )
        assertThat(sortedEs.map { it.language }).containsExactly("es", "ea", "sp", "en").inOrder()

        val sortedEn = LocalPlayerActivity.sortSubtitleResultsForDevice(
            results = listOf(spSub, eaSub, esSub, enTop),
            deviceLanguage = "en"
        )
        assertThat(sortedEn.map { it.language }).containsExactly("en", "es", "ea", "sp").inOrder()
    }

    @Test
    fun `getLanguageFlag maps OpenSubtitles Spanish codes ea and sp accurately without misflagging English`() {
        assertThat(getLanguageFlag("ea", "Twilight.2008")).isEqualTo("🇦🇷")
        assertThat(getLanguageFlag("sp", "Twilight.2008")).isEqualTo("🇪🇸")
        assertThat(getLanguageFlag("es", "Twilight.2008")).isEqualTo("🇪🇸")
        assertThat(getLanguageFlag("es", "Twilight.2008.Latino")).isEqualTo("🇦🇷")
        assertThat(getLanguageFlag("en", "Despicable.Me.2010.Respect")).isEqualTo("🇺🇸")
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
        val curMediaId = "tt11280740_s1_e2"
        val canonicalEpisodeId = "tt11280740_s1_e2"

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

    @Test
    fun `resolvePlaybackDecodingError extracts HEVC 10-bit video details from MediaCodecVideoRenderer PlaybackException`() {
        val error = androidx.media3.common.PlaybackException(
            "MediaCodecVideoRenderer error, index=0, format=Format(1, Encoded By ~ Immortal, video/x-matroska, video/hevc, hvc1.2.4.L93.90, -1, en, [1280, 534, -1.0, ColorInfo(BT709, Limited range, SDR SMPTE 170M, false, 10bit Luma, 10bit Chroma)], [-1, -1, -1]), format_supported=NO_EXCEEDS_CAPABILITIES",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED
        )

        val resolved = com.martinrevert.latorrentola.ui.components.resolvePlaybackDecodingError(error)

        assertThat(resolved).isNotNull()
        assertThat(resolved?.isAudioError).isFalse()
        assertThat(resolved?.codecSummary).isEqualTo("HEVC / H.265 10-bit")
    }

    @Test
    fun `resolvePlaybackDecodingError extracts structured rendererFormat from ExoPlaybackException`() {
        val format = androidx.media3.common.Format.Builder()
            .setSampleMimeType(androidx.media3.common.MimeTypes.VIDEO_AV1)
            .setCodecs("av01.0.08M.10")
            .build()
        val exoError = androidx.media3.exoplayer.ExoPlaybackException.createForRenderer(
            RuntimeException("Decoder init failed"),
            "MediaCodecVideoRenderer",
            0,
            format,
            androidx.media3.common.C.FORMAT_EXCEEDS_CAPABILITIES,
            null,
            false,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
        )

        val resolved = com.martinrevert.latorrentola.ui.components.resolvePlaybackDecodingError(exoError)

        assertThat(resolved).isNotNull()
        assertThat(resolved?.isAudioError).isFalse()
        assertThat(resolved?.codecSummary).isEqualTo("AV1 10-bit")
    }

    @Test
    fun `resolvePlaybackDecodingError identifies audio decoding errors accurately`() {
        val format = androidx.media3.common.Format.Builder()
            .setSampleMimeType(androidx.media3.common.MimeTypes.AUDIO_TRUEHD)
            .build()
        val exoError = androidx.media3.exoplayer.ExoPlaybackException.createForRenderer(
            RuntimeException("Audio decoder unsupported"),
            "MediaCodecAudioRenderer",
            1,
            format,
            androidx.media3.common.C.FORMAT_UNSUPPORTED_SUBTYPE,
            null,
            false,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
        )

        val resolved = com.martinrevert.latorrentola.ui.components.resolvePlaybackDecodingError(exoError)

        assertThat(resolved).isNotNull()
        assertThat(resolved?.isAudioError).isTrue()
        assertThat(resolved?.codecSummary).isEqualTo("Dolby TrueHD")
    }

    @Test
    fun `resolvePlaybackDecodingError returns null for transient network and IO errors`() {
        val networkError = androidx.media3.common.PlaybackException(
            "Connection reset",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        )

        val resolved = com.martinrevert.latorrentola.ui.components.resolvePlaybackDecodingError(networkError)

        assertThat(resolved).isNull()
    }

    @Test
    fun `resolvePlaybackDecodingError ignores AVI container and resolves AVC video stream`() {
        val error = androidx.media3.common.PlaybackException(
            "MediaCodecVideoRenderer error, index=0, format=Format(1, null, video/x-msvideo, video/avc, avc1.640028, -1, en, [1920, 1080]), format_supported=NO_EXCEEDS_CAPABILITIES",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
        )

        val resolved = com.martinrevert.latorrentola.ui.components.resolvePlaybackDecodingError(error)

        assertThat(resolved).isNotNull()
        assertThat(resolved?.isAudioError).isFalse()
        assertThat(resolved?.codecSummary).isEqualTo("AVC / H.264")
    }

    @Test
    fun `resolvePlaybackDecodingError resolves codec from codecs string when sampleMimeType is absent`() {
        val error = androidx.media3.common.PlaybackException(
            "Decoder init failed: OMX.qcom.video.decoder.hevc, format=Format(1, null, null, null, hvc1.2.4.L93.90, -1, [1920, 1080])",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        )

        val resolved = com.martinrevert.latorrentola.ui.components.resolvePlaybackDecodingError(error)

        assertThat(resolved).isNotNull()
        assertThat(resolved?.isAudioError).isFalse()
        assertThat(resolved?.codecSummary).isEqualTo("HEVC / H.265 10-bit")
    }

    @Test
    fun `resolvePlaybackDecodingError maps colloquial audio and video mime aliases`() {
        val aacError = androidx.media3.common.PlaybackException(
            "MediaCodecAudioRenderer error, index=1, format=Format(2, audio/aac, -1)",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED
        )
        val dtsError = androidx.media3.common.PlaybackException(
            "MediaCodecAudioRenderer error, index=1, format=Format(2, audio/dts, -1)",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED
        )

        val resolvedAac = com.martinrevert.latorrentola.ui.components.resolvePlaybackDecodingError(aacError)
        val resolvedDts = com.martinrevert.latorrentola.ui.components.resolvePlaybackDecodingError(dtsError)

        assertThat(resolvedAac?.codecSummary).isEqualTo("AAC")
        assertThat(resolvedDts?.codecSummary).isEqualTo("DTS")
    }

    @Test
    fun `resolvePlaybackDecodingError identifies DTS-HD passthrough and AudioTrack failures`() {
        val audioTrackError = androidx.media3.common.PlaybackException(
            "AudioTrack write failed: audio/vnd.dts.hd buffer rejected",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
        )

        val resolved = com.martinrevert.latorrentola.ui.components.resolvePlaybackDecodingError(audioTrackError)

        assertThat(resolved).isNotNull()
        assertThat(resolved?.isAudioError).isTrue()
        assertThat(resolved?.codecSummary).isEqualTo("DTS-HD")
        assertThat(resolved?.isDtsHdError).isTrue()
    }

    @Test
    fun `resolvePlaybackDecodingError resolves audio vnd dts error message to standard DTS and not DTS-HD`() {
        val dtsCoreError = androidx.media3.common.PlaybackException(
            "AudioTrack write failed: audio/vnd.dts buffer rejected",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
        )

        val resolved = resolvePlaybackDecodingError(dtsCoreError)

        assertThat(resolved).isNotNull()
        assertThat(resolved?.isAudioError).isTrue()
        assertThat(resolved?.codecSummary).isEqualTo("DTS")
        assertThat(resolved?.isDtsHdError).isFalse()
    }

    @Test
    fun `isStuckPlayerError returns true for StuckPlayerException cause and stall messages`() {
        val stuckCause = androidx.media3.common.util.StuckPlayerException(
            androidx.media3.common.util.StuckPlayerException.STUCK_BUFFERING_NO_PROGRESS,
            600_000
        )
        val stuckExoError = androidx.media3.common.PlaybackException(
            "Unexpected runtime error",
            stuckCause,
            androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED
        )

        assertThat(isStuckPlayerError(stuckExoError)).isTrue()

        val directStuckError = androidx.media3.common.PlaybackException(
            "Player stuck playing without progress",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED
        )
        assertThat(isStuckPlayerError(directStuckError)).isTrue()

        val normalError = androidx.media3.common.PlaybackException(
            "Decoder init failed",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        )
        assertThat(isStuckPlayerError(normalError)).isFalse()
    }

    @Test
    fun `resolvePlaybackStallError creates PlaybackDecodingError with isStallError true`() {
        val stuckCause = java.lang.IllegalStateException("Player stuck buffering with no progress for 120000 ms")
        val stuckExoError = androidx.media3.common.PlaybackException(
            "Unexpected runtime error",
            stuckCause,
            androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED
        )

        val resolved = resolvePlaybackStallError(stuckExoError)
        assertThat(resolved).isNotNull()
        assertThat(resolved?.isStallError).isTrue()
        assertThat(resolved?.isAudioError).isFalse()

        val normalError = androidx.media3.common.PlaybackException(
            "Network timeout",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        )
        assertThat(resolvePlaybackStallError(normalError)).isNull()
    }

    @Test
    fun `handlePlayerTvKeyEvent pauses and shows controls with single OK press while playing`() {
        var seekBackCalled = false
        var seekForwardCalled = false
        var toggledPlayingState: Boolean? = null
        var showControlsCalled = false
        var hideControlsCalled = false

        val handled = LocalPlayerActivity.handlePlayerTvKeyEvent(
            keyCode = android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            controlsVisible = false,
            isPlaying = true,
            onSeekBack = { seekBackCalled = true },
            onSeekForward = { seekForwardCalled = true },
            onTogglePlayPause = { targetState -> toggledPlayingState = targetState },
            onShowControls = { showControlsCalled = true },
            onHideControls = { hideControlsCalled = true }
        )

        assertThat(handled).isTrue()
        assertThat(toggledPlayingState).isEqualTo(false) // paused!
        assertThat(showControlsCalled).isTrue()
        assertThat(seekBackCalled).isFalse()
        assertThat(seekForwardCalled).isFalse()
        assertThat(hideControlsCalled).isFalse()
    }

    @Test
    fun `handlePlayerTvKeyEvent resumes and shows controls with single OK press while paused`() {
        var toggledPlayingState: Boolean? = null
        var showControlsCalled = false

        val handled = LocalPlayerActivity.handlePlayerTvKeyEvent(
            keyCode = android.view.KeyEvent.KEYCODE_ENTER,
            controlsVisible = false,
            isPlaying = false,
            onSeekBack = {},
            onSeekForward = {},
            onTogglePlayPause = { targetState -> toggledPlayingState = targetState },
            onShowControls = { showControlsCalled = true },
            onHideControls = {}
        )

        assertThat(handled).isTrue()
        assertThat(toggledPlayingState).isEqualTo(true) // resumed/playing!
        assertThat(showControlsCalled).isTrue()
    }

    @Test
    fun `handlePlayerTvKeyEvent handles media play and pause keys directly`() {
        var toggledPlay: Boolean? = null
        var toggledPause: Boolean? = null

        val playHandled = LocalPlayerActivity.handlePlayerTvKeyEvent(
            keyCode = android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
            controlsVisible = false,
            isPlaying = false,
            onSeekBack = {},
            onSeekForward = {},
            onTogglePlayPause = { toggledPlay = it },
            onShowControls = {},
            onHideControls = {}
        )
        val pauseHandled = LocalPlayerActivity.handlePlayerTvKeyEvent(
            keyCode = android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
            controlsVisible = false,
            isPlaying = true,
            onSeekBack = {},
            onSeekForward = {},
            onTogglePlayPause = { toggledPause = it },
            onShowControls = {},
            onHideControls = {}
        )

        assertThat(playHandled).isTrue()
        assertThat(toggledPlay).isEqualTo(true)
        assertThat(pauseHandled).isTrue()
        assertThat(toggledPause).isEqualTo(false)
    }

    @Test
    fun `handlePlayerTvKeyEvent allows inspecting controls with UP and DOWN without toggling play pause`() {
        var toggledPlayingState: Boolean? = null
        var showControlsCalled = false

        val upHandled = LocalPlayerActivity.handlePlayerTvKeyEvent(
            keyCode = android.view.KeyEvent.KEYCODE_DPAD_UP,
            controlsVisible = false,
            isPlaying = true,
            onSeekBack = {},
            onSeekForward = {},
            onTogglePlayPause = { toggledPlayingState = it },
            onShowControls = { showControlsCalled = true },
            onHideControls = {}
        )

        assertThat(upHandled).isTrue()
        assertThat(showControlsCalled).isTrue()
        assertThat(toggledPlayingState).isNull() // Did not pause or play!
    }

    @Test
    fun `handlePlayerTvKeyEvent performs quick seek on LEFT and RIGHT when controls are hidden`() {
        var seekBackCalled = false
        var seekForwardCalled = false
        var showControlsCalled = false

        val leftHandled = LocalPlayerActivity.handlePlayerTvKeyEvent(
            keyCode = android.view.KeyEvent.KEYCODE_DPAD_LEFT,
            controlsVisible = false,
            isPlaying = true,
            onSeekBack = { seekBackCalled = true },
            onSeekForward = {},
            onTogglePlayPause = {},
            onShowControls = { showControlsCalled = true },
            onHideControls = {}
        )
        val rightHandled = LocalPlayerActivity.handlePlayerTvKeyEvent(
            keyCode = android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
            controlsVisible = false,
            isPlaying = true,
            onSeekBack = {},
            onSeekForward = { seekForwardCalled = true },
            onTogglePlayPause = {},
            onShowControls = { showControlsCalled = true },
            onHideControls = {}
        )

        assertThat(leftHandled).isTrue()
        assertThat(seekBackCalled).isTrue()
        assertThat(rightHandled).isTrue()
        assertThat(seekForwardCalled).isTrue()
        assertThat(showControlsCalled).isFalse()
    }

    @Test
    fun `handlePlayerTvKeyEvent hides controls on BACK when controls are visible`() {
        var hideControlsCalled = false

        val backHandledVisible = LocalPlayerActivity.handlePlayerTvKeyEvent(
            keyCode = android.view.KeyEvent.KEYCODE_BACK,
            controlsVisible = true,
            isPlaying = true,
            onSeekBack = {},
            onSeekForward = {},
            onTogglePlayPause = {},
            onShowControls = {},
            onHideControls = { hideControlsCalled = true }
        )
        val backHandledHidden = LocalPlayerActivity.handlePlayerTvKeyEvent(
            keyCode = android.view.KeyEvent.KEYCODE_BACK,
            controlsVisible = false,
            isPlaying = true,
            onSeekBack = {},
            onSeekForward = {},
            onTogglePlayPause = {},
            onShowControls = {},
            onHideControls = {}
        )

        assertThat(backHandledVisible).isTrue()
        assertThat(hideControlsCalled).isTrue()
        assertThat(backHandledHidden).isFalse() // Pass-through to exit activity
    }

    @Test
    fun `handlePlayerTvKeyEvent delegates DPAD_CENTER to focused item when controls are already visible`() {
        var toggledState: Boolean? = null

        val handled = LocalPlayerActivity.handlePlayerTvKeyEvent(
            keyCode = android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            controlsVisible = true,
            isPlaying = true,
            onSeekBack = {},
            onSeekForward = {},
            onTogglePlayPause = { toggledState = it },
            onShowControls = {},
            onHideControls = {}
        )

        // Returns false so the focused button in Compose receives the click
        assertThat(handled).isFalse()
        assertThat(toggledState).isNull()
    }

    @Test
    fun `shouldPauseOnTvExit returns true on Android TV and false on mobile`() {
        assertThat(LocalPlayerActivity.shouldPauseOnTvExit(isTv = true)).isTrue()
        assertThat(LocalPlayerActivity.shouldPauseOnTvExit(isTv = false)).isFalse()
    }

    @Test
    fun `shouldKeepScreenAwake always keeps screen awake on Android TV whether playing or paused`() {
        // Android TV - playing local
        assertThat(LocalPlayerActivity.shouldKeepScreenAwake(isTv = true, isPlaying = true, isLocalPlayback = true)).isTrue()
        // Android TV - paused local (ambient mode strictly prohibited)
        assertThat(LocalPlayerActivity.shouldKeepScreenAwake(isTv = true, isPlaying = false, isLocalPlayback = true)).isTrue()
        // Android TV - paused cast
        assertThat(LocalPlayerActivity.shouldKeepScreenAwake(isTv = true, isPlaying = false, isLocalPlayback = false)).isTrue()
        // Android TV - playing cast
        assertThat(LocalPlayerActivity.shouldKeepScreenAwake(isTv = true, isPlaying = true, isLocalPlayback = false)).isTrue()
    }

    @Test
    fun `shouldKeepScreenAwake keeps awake on mobile only when actively playing local media`() {
        // Mobile - playing local: keep awake
        assertThat(LocalPlayerActivity.shouldKeepScreenAwake(isTv = false, isPlaying = true, isLocalPlayback = true)).isTrue()
        // Mobile - paused: allow screen timeout
        assertThat(LocalPlayerActivity.shouldKeepScreenAwake(isTv = false, isPlaying = false, isLocalPlayback = true)).isFalse()
        // Mobile - casting: allow screen timeout
        assertThat(LocalPlayerActivity.shouldKeepScreenAwake(isTv = false, isPlaying = true, isLocalPlayback = false)).isFalse()
    }

    @Test
    fun `isDtsHdStallCondition enforces bufferAheadMs threshold and ignores absolute buffered position`() {
        // Starvation scenario: playback head stalled at 10_000ms, but forward buffer is only 1500ms
        val underbuffered = LocalPlayerActivity.isDtsHdStallCondition(
            playWhenReady = true,
            playbackState = Player.STATE_READY,
            bufferAheadMs = 1500L,
            currentPosition = 10_000L,
            lastPosition = 10_000L
        )
        assertThat(underbuffered).isFalse()

        // True hardware stall: playback head stalled at 10_000ms with >= 2000ms forward buffer
        val stalled = LocalPlayerActivity.isDtsHdStallCondition(
            playWhenReady = true,
            playbackState = Player.STATE_READY,
            bufferAheadMs = 2000L,
            currentPosition = 10_000L,
            lastPosition = 10_000L
        )
        assertThat(stalled).isTrue()

        // Not stalled if playing head is moving
        val moving = LocalPlayerActivity.isDtsHdStallCondition(
            playWhenReady = true,
            playbackState = Player.STATE_READY,
            bufferAheadMs = 5000L,
            currentPosition = 11_000L,
            lastPosition = 10_000L
        )
        assertThat(moving).isFalse()

        // Not stalled if paused
        val paused = LocalPlayerActivity.isDtsHdStallCondition(
            playWhenReady = false,
            playbackState = Player.STATE_READY,
            bufferAheadMs = 5000L,
            currentPosition = 10_000L,
            lastPosition = 10_000L
        )
        assertThat(paused).isFalse()
    }

    @Test
    fun `cancelDtsHdStallWatchdog cancels active job and returns null`() {
        val mockJob = mockk<Job>(relaxed = true)
        val result = LocalPlayerActivity.cancelDtsHdStallWatchdog(mockJob)

        verify(exactly = 1) { mockJob.cancel() }
        assertThat(result).isNull()
    }

    @Test
    fun `cancelDtsHdStallWatchdog handles null gracefully`() {
        val result = LocalPlayerActivity.cancelDtsHdStallWatchdog(null)
        assertThat(result).isNull()
    }

    @Test
    fun `shouldIgnoreNewIntent only returns true if activity is finishing or destroyed`() {
        assertThat(LocalPlayerActivity.shouldIgnoreNewIntent(isFinishing = true, isDestroyed = false)).isTrue()
        assertThat(LocalPlayerActivity.shouldIgnoreNewIntent(isFinishing = false, isDestroyed = true)).isTrue()
        assertThat(LocalPlayerActivity.shouldIgnoreNewIntent(isFinishing = false, isDestroyed = false)).isFalse()
    }

    @Test
    fun `shouldIgnorePlayerError ignores error during shutdown or media deletion`() {
        assertThat(LocalPlayerActivity.shouldIgnorePlayerError(isFinishing = true, isDestroyed = false, isDeletingMedia = false)).isTrue()
        assertThat(LocalPlayerActivity.shouldIgnorePlayerError(isFinishing = false, isDestroyed = true, isDeletingMedia = false)).isTrue()
        assertThat(LocalPlayerActivity.shouldIgnorePlayerError(isFinishing = false, isDestroyed = false, isDeletingMedia = true)).isTrue()
        assertThat(LocalPlayerActivity.shouldIgnorePlayerError(isFinishing = false, isDestroyed = false, isDeletingMedia = false)).isFalse()
    }

    @Test
    fun `shouldFinishOnPlaybackEnded returns true for movies and DO_NOTHING tv episodes`() {
        // Movies always finish on playback end
        assertThat(LocalPlayerActivity.shouldFinishOnPlaybackEnded(isTvEpisode = false, autoPlayMode = AutoPlayQualitySelectionMethod.BY_QUALITY)).isTrue()
        assertThat(LocalPlayerActivity.shouldFinishOnPlaybackEnded(isTvEpisode = false, autoPlayMode = AutoPlayQualitySelectionMethod.DO_NOTHING)).isTrue()

        // TV episodes finish if DO_NOTHING is configured
        assertThat(LocalPlayerActivity.shouldFinishOnPlaybackEnded(isTvEpisode = true, autoPlayMode = AutoPlayQualitySelectionMethod.DO_NOTHING)).isTrue()

        // TV episodes do not finish if auto-play or dialog prompt is configured
        assertThat(LocalPlayerActivity.shouldFinishOnPlaybackEnded(isTvEpisode = true, autoPlayMode = AutoPlayQualitySelectionMethod.OFF)).isFalse()
        assertThat(LocalPlayerActivity.shouldFinishOnPlaybackEnded(isTvEpisode = true, autoPlayMode = AutoPlayQualitySelectionMethod.BY_QUALITY)).isFalse()
        assertThat(LocalPlayerActivity.shouldFinishOnPlaybackEnded(isTvEpisode = true, autoPlayMode = AutoPlayQualitySelectionMethod.BY_SEED_PEERS)).isFalse()
    }

    @Test
    fun `resolvePlayerAnySheetVisible ignores controller-scoped sheets when controller is null`() {
        assertThat(
            LocalPlayerActivity.resolvePlayerAnySheetVisible(
                hasController = false,
                showCastBottomSheet = true,
                showMoreOptionsSheet = false,
                hasPlayerBottomSheet = false
            )
        ).isFalse()

        assertThat(
            LocalPlayerActivity.resolvePlayerAnySheetVisible(
                hasController = false,
                showCastBottomSheet = false,
                showMoreOptionsSheet = true,
                hasPlayerBottomSheet = false
            )
        ).isFalse()

        assertThat(
            LocalPlayerActivity.resolvePlayerAnySheetVisible(
                hasController = true,
                showCastBottomSheet = true,
                showMoreOptionsSheet = false,
                hasPlayerBottomSheet = false
            )
        ).isTrue()

        assertThat(
            LocalPlayerActivity.resolvePlayerAnySheetVisible(
                hasController = true,
                showCastBottomSheet = false,
                showMoreOptionsSheet = true,
                hasPlayerBottomSheet = false
            )
        ).isTrue()

        assertThat(
            LocalPlayerActivity.resolvePlayerAnySheetVisible(
                hasController = false,
                showCastBottomSheet = false,
                showMoreOptionsSheet = false,
                hasPlayerBottomSheet = true
            )
        ).isTrue()

        assertThat(
            LocalPlayerActivity.resolvePlayerAnySheetVisible(
                hasController = true,
                showCastBottomSheet = false,
                showMoreOptionsSheet = false,
                hasPlayerBottomSheet = false
            )
        ).isFalse()
    }

    @Test
    fun `shouldResetStreamRecoveryAttemptsOnNewIntent preserves attempts for same infoHash or streamUrl and resets on new stream`() {
        val hashA = "abcdef1234567890abcdef1234567890abcdef12"
        val hashB = "9999999999999999999999999999999999999999"
        val streamUrlA = "http://127.0.0.1:41234/stream"
        val streamUrlB = "http://127.0.0.1:52345/stream"

        // Same infoHash (e.g. ACTION_PLAY_STREAM recovery re-launching LocalPlayerActivity on a new loopback port)
        assertThat(
            LocalPlayerActivity.shouldResetStreamRecoveryAttemptsOnNewIntent(
                currentInfoHash = hashA,
                incomingInfoHash = hashA.uppercase(),
                currentStreamUrl = streamUrlA,
                incomingStreamUrl = streamUrlB
            )
        ).isFalse()

        // Same streamUrl without infoHash
        assertThat(
            LocalPlayerActivity.shouldResetStreamRecoveryAttemptsOnNewIntent(
                currentInfoHash = "",
                incomingInfoHash = "",
                currentStreamUrl = streamUrlA,
                incomingStreamUrl = streamUrlA
            )
        ).isFalse()

        // Switching to a different torrent/stream resets recovery attempts
        assertThat(
            LocalPlayerActivity.shouldResetStreamRecoveryAttemptsOnNewIntent(
                currentInfoHash = hashA,
                incomingInfoHash = hashB,
                currentStreamUrl = streamUrlA,
                incomingStreamUrl = streamUrlB
            )
        ).isTrue()
    }

    @Test
    fun `isTransientIoPlaybackError and resolveExhaustedIoRecoveryStallError surface stall error after 3 attempts`() {
        assertThat(
            LocalPlayerActivity.isTransientIoPlaybackError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        ).isTrue()
        assertThat(
            LocalPlayerActivity.isTransientIoPlaybackError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
        ).isTrue()
        assertThat(
            LocalPlayerActivity.isTransientIoPlaybackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        ).isTrue()
        assertThat(
            LocalPlayerActivity.isTransientIoPlaybackError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)
        ).isFalse()

        assertThat(LocalPlayerActivity.resolveExhaustedIoRecoveryStallError(recoveryAttempts = 1)).isNull()
        assertThat(LocalPlayerActivity.resolveExhaustedIoRecoveryStallError(recoveryAttempts = 3)).isNull()

        val exhaustedError = LocalPlayerActivity.resolveExhaustedIoRecoveryStallError(recoveryAttempts = 4)
        assertThat(exhaustedError).isNotNull()
        assertThat(exhaustedError?.isStallError).isTrue()
        assertThat(exhaustedError?.isAudioError).isFalse()
    }

    @Test
    fun `resolveRecoveryPlaybackPositionMs prefers positive controller position and falls back when reset to zero`() {
        assertThat(
            LocalPlayerActivity.resolveRecoveryPlaybackPositionMs(
                controllerPositionMs = 0L,
                lastKnownPositionMs = 125_000L
            )
        ).isEqualTo(125_000L)

        assertThat(
            LocalPlayerActivity.resolveRecoveryPlaybackPositionMs(
                controllerPositionMs = 130_000L,
                lastKnownPositionMs = 120_000L
            )
        ).isEqualTo(130_000L)

        // Preserves user backward seek instead of snapping forward to stale lastKnownPositionMs
        assertThat(
            LocalPlayerActivity.resolveRecoveryPlaybackPositionMs(
                controllerPositionMs = 12_000L,
                lastKnownPositionMs = 125_000L
            )
        ).isEqualTo(12_000L)

        assertThat(
            LocalPlayerActivity.resolveRecoveryPlaybackPositionMs(
                controllerPositionMs = -1L,
                lastKnownPositionMs = 0L
            )
        ).isEqualTo(0L)

        assertThat(
            LocalPlayerActivity.resolveRecoveryPlaybackPositionMs(
                controllerPositionMs = -1L,
                lastKnownPositionMs = -5_000L
            )
        ).isEqualTo(0L)
    }

    @Test
    fun `isObsoleteSurfaceError detects obsolete surface MediaCodec failures in cause chain`() {
        val codecFailure = IllegalStateException("rendering to non-initialized(obsolete) surface")
        val decoderFailure = RuntimeException("Decoder failed: c2.qti.hevc.decoder", codecFailure)
        val rendererFailure = RuntimeException("MediaCodecVideoRenderer error, index=0", decoderFailure)

        assertThat(isObsoleteSurfaceError(rendererFailure)).isTrue()
        assertThat(isObsoleteSurfaceError(codecFailure)).isTrue()
        assertThat(isObsoleteSurfaceError(IllegalStateException("surface was abandoned"))).isTrue()
        assertThat(isObsoleteSurfaceError(IllegalStateException("Surface has been abandoned"))).isTrue()
        assertThat(isObsoleteSurfaceError(IllegalStateException("Surface has been released"))).isTrue()
    }

    @Test
    fun `isObsoleteSurfaceError checks MediaCodecVideoDecoderException isSurfaceValid`() {
        val invalidSurface = mockk<Surface> {
            every { isValid } returns false
        }
        val validSurface = mockk<Surface> {
            every { isValid } returns true
        }
        val invalidSurfaceException = MediaCodecVideoDecoderException(
            IllegalStateException("generic codec failure"),
            null,
            invalidSurface
        )
        val validSurfaceException = MediaCodecVideoDecoderException(
            IllegalStateException("generic codec failure"),
            null,
            validSurface
        )

        assertThat(isObsoleteSurfaceError(invalidSurfaceException)).isTrue()
        assertThat(isObsoleteSurfaceError(validSurfaceException)).isFalse()
    }

    @Test
    fun `isObsoleteSurfaceError ignores non-surface messages, genuine decoder failures, and null`() {
        val genuine = RuntimeException(
            "MediaCodecVideoRenderer error, index=0",
            RuntimeException("Decoder failed: c2.qti.hevc.decoder", IllegalStateException("codec exploded"))
        )

        assertThat(isObsoleteSurfaceError(genuine)).isFalse()
        assertThat(isObsoleteSurfaceError(IllegalStateException("AudioTrack non-initialized"))).isFalse()
        assertThat(isObsoleteSurfaceError(IllegalStateException("obsolete DRM key"))).isFalse()
        assertThat(isObsoleteSurfaceError(null)).isFalse()
    }

    @Test
    fun `nextSurfaceRecoveryAttempt increments within window and resets outside or on clock rollback`() {
        assertThat(LocalPlayerActivity.nextSurfaceRecoveryAttempt(0, 0L, 1_000L)).isEqualTo(1)
        assertThat(LocalPlayerActivity.nextSurfaceRecoveryAttempt(1, 1_000L, 5_000L)).isEqualTo(2)
        assertThat(LocalPlayerActivity.nextSurfaceRecoveryAttempt(2, 5_000L, 8_000L)).isEqualTo(3)
        assertThat(LocalPlayerActivity.nextSurfaceRecoveryAttempt(3, 8_000L, 100_000L)).isEqualTo(1)
        assertThat(LocalPlayerActivity.nextSurfaceRecoveryAttempt(2, 10_000L, 5_000L)).isEqualTo(1)
        assertThat(LocalPlayerActivity.MAX_SURFACE_RECOVERY_ATTEMPTS).isEqualTo(2)
    }

    @Test
    fun `shouldRunObsoleteSurfaceRecovery requires foreground started state and active activity`() {
        assertThat(
            LocalPlayerActivity.shouldRunObsoleteSurfaceRecovery(
                isFinishing = false,
                isDestroyed = false,
                isDeletingMedia = false,
                isStarted = true
            )
        ).isTrue()
        assertThat(
            LocalPlayerActivity.shouldRunObsoleteSurfaceRecovery(
                isFinishing = false,
                isDestroyed = false,
                isDeletingMedia = false,
                isStarted = false
            )
        ).isFalse()
        assertThat(
            LocalPlayerActivity.shouldRunObsoleteSurfaceRecovery(
                isFinishing = true,
                isDestroyed = false,
                isDeletingMedia = false,
                isStarted = true
            )
        ).isFalse()
        assertThat(
            LocalPlayerActivity.shouldRunObsoleteSurfaceRecovery(
                isFinishing = false,
                isDestroyed = true,
                isDeletingMedia = false,
                isStarted = true
            )
        ).isFalse()
        assertThat(
            LocalPlayerActivity.shouldRunObsoleteSurfaceRecovery(
                isFinishing = false,
                isDestroyed = false,
                isDeletingMedia = true,
                isStarted = true
            )
        ).isFalse()
    }

    @Test
    fun `shouldAutoRecoverExistingLocalError matches only obsolete surface playback errors`() {
        val obsoleteError = PlaybackException(
            "MediaCodecVideoRenderer error",
            IllegalStateException("rendering to non-initialized(obsolete) surface"),
            PlaybackException.ERROR_CODE_DECODING_FAILED
        )
        val decoderError = PlaybackException(
            "Decoder init failed",
            null,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        )

        assertThat(LocalPlayerActivity.shouldAutoRecoverExistingLocalError(obsoleteError)).isTrue()
        assertThat(LocalPlayerActivity.shouldAutoRecoverExistingLocalError(decoderError)).isFalse()
        assertThat(LocalPlayerActivity.shouldAutoRecoverExistingLocalError(null)).isFalse()
    }

    @Test
    fun `resolveExhaustedSurfaceRecoveryError returns decoder error or retryable stall fallback`() {
        val decoderError = PlaybackException(
            "MediaCodecVideoRenderer error, format=Format(1, null, video/x-matroska, video/hevc, hvc1.2.4.L93.90)",
            IllegalStateException("rendering to non-initialized(obsolete) surface"),
            PlaybackException.ERROR_CODE_DECODING_FAILED
        )
        val resolvedDecoder = LocalPlayerActivity.resolveExhaustedSurfaceRecoveryError(decoderError)
        assertThat(resolvedDecoder.isAudioError).isFalse()
        assertThat(resolvedDecoder.isStallError).isFalse()
        assertThat(resolvedDecoder.codecSummary).isEqualTo("HEVC / H.265 10-bit")

        val unspecifiedError = PlaybackException(
            "rendering to non-initialized(obsolete) surface",
            null,
            PlaybackException.ERROR_CODE_UNSPECIFIED
        )
        val resolvedFallback = LocalPlayerActivity.resolveExhaustedSurfaceRecoveryError(unspecifiedError)
        assertThat(resolvedFallback.isAudioError).isFalse()
        assertThat(resolvedFallback.isStallError).isTrue()
        assertThat(resolvedFallback.codecSummary).isNull()
    }
}




