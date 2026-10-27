package com.martinrevert.latorrentola.service

import androidx.media3.cast.CastTrackSelector
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppCastTrackSelectorTest {

    private val selector = AppCastTrackSelector()

    private fun createRequest(
        availableGroups: List<TrackGroup>,
        currentGroups: Set<TrackGroup>,
        params: TrackSelectionParameters
    ): CastTrackSelector.CastTrackSelectorRequest {
        return CastTrackSelector.CastTrackSelectorRequest(
            0,
            params,
            ImmutableList.copyOf(availableGroups),
            ImmutableList.of(),
            ImmutableSet.copyOf(currentGroups),
            0
        )
    }

    @Test
    fun `evaluate filters out foreign override TrackGroup not present in available track groups`() {
        val castAudioFormat = Format.Builder()
            .setId("cast_audio_1")
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setLanguage("en")
            .build()
        val castAudioGroup = TrackGroup("audio_group", castAudioFormat)

        val castTextFormat = Format.Builder()
            .setId("cast_text_1")
            .setSampleMimeType(MimeTypes.TEXT_VTT)
            .setLanguage("es")
            .build()
        val castTextGroup = TrackGroup("text_group", castTextFormat)

        val availableGroups = listOf(castAudioGroup, castTextGroup)

        // Local ExoPlayer embedded subtitle track (e.g. text/x-ssa from an MKV)
        val foreignSsaFormat = Format.Builder()
            .setId("local_ssa_3")
            .setSampleMimeType(MimeTypes.TEXT_SSA)
            .setLanguage("en")
            .build()
        val foreignSsaTrackGroup = TrackGroup("foreign_mkv_group", foreignSsaFormat)

        // TrackSelectionParameters with override pointing to the foreign track group
        val params = TrackSelectionParameters.Builder()
            .addOverride(TrackSelectionOverride(foreignSsaTrackGroup, 0))
            .build()

        val request = createRequest(availableGroups, emptySet(), params)

        val result = selector.evaluate(request)

        // CRITICAL CHECK: Result must never contain foreignSsaTrackGroup
        assertThat(result.selections).doesNotContain(foreignSsaTrackGroup)
        // All selected track groups must be a subset of availableGroups
        assertThat(availableGroups).containsAtLeastElementsIn(result.selections)
    }

    @Test
    fun `evaluate matches text track by language if foreign override has same language`() {
        val castAudioGroup = TrackGroup("audio_1", Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("en").build())
        val castTextEsGroup = TrackGroup("text_es", Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage("es").build())
        val castTextEnGroup = TrackGroup("text_en", Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage("en").build())

        val availableGroups = listOf(castAudioGroup, castTextEsGroup, castTextEnGroup)

        // Local foreign override with language "en"
        val foreignSsaTrackGroup = TrackGroup(
            "foreign_ssa",
            Format.Builder().setSampleMimeType(MimeTypes.TEXT_SSA).setLanguage("en").build()
        )

        val params = TrackSelectionParameters.Builder()
            .addOverride(TrackSelectionOverride(foreignSsaTrackGroup, 0))
            .build()

        val request = createRequest(availableGroups, emptySet(), params)

        val result = selector.evaluate(request)

        // It should match castTextEnGroup by language "en"
        assertThat(result.selections).contains(castTextEnGroup)
        assertThat(result.selections).doesNotContain(foreignSsaTrackGroup)
    }

    @Test
    fun `evaluate deselects text tracks when text track type is disabled`() {
        val castAudioGroup = TrackGroup("audio_1", Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("en").build())
        val castTextGroup = TrackGroup("text_1", Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage("es").build())

        val availableGroups = listOf(castAudioGroup, castTextGroup)

        val params = TrackSelectionParameters.Builder()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()

        val request = createRequest(availableGroups, setOf(castTextGroup), params)

        val result = selector.evaluate(request)

        assertThat(result.selections.any { it.type == C.TRACK_TYPE_TEXT }).isFalse()
    }

    @Test
    fun `evaluate selects English audio track by default when multiple audio tracks are available`() {
        val italianAudioGroup = TrackGroup("audio_it", Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("it").build())
        val englishAudioGroup = TrackGroup("audio_en", Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("en").build())

        val availableGroups = listOf(italianAudioGroup, englishAudioGroup)
        val params = TrackSelectionParameters.Builder().build()

        val request = createRequest(availableGroups, setOf(italianAudioGroup), params)

        val result = selector.evaluate(request)

        // Must select English audio track by default even if Italian was initially set on receiver
        assertThat(result.selections).contains(englishAudioGroup)
        assertThat(result.selections).doesNotContain(italianAudioGroup)
    }

    @Test
    fun `evaluate respects preferredAudioLanguages when specified`() {
        val italianAudioGroup = TrackGroup("audio_it", Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("it").build())
        val englishAudioGroup = TrackGroup("audio_en", Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("en").build())

        val availableGroups = listOf(italianAudioGroup, englishAudioGroup)
        val params = TrackSelectionParameters.Builder()
            .setPreferredAudioLanguage("it")
            .build()

        val request = createRequest(availableGroups, emptySet(), params)

        val result = selector.evaluate(request)

        assertThat(result.selections).contains(italianAudioGroup)
        assertThat(result.selections).doesNotContain(englishAudioGroup)
    }

    @Test
    fun `evaluate matches audio override by language when foreign group was overridden`() {
        val localForeignAudioGroup = TrackGroup("foreign_it", Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).setLanguage("it").build())
        val castItalianAudioGroup = TrackGroup("cast_it", Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("it").build())
        val castEnglishAudioGroup = TrackGroup("cast_en", Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("en").build())

        val availableGroups = listOf(castItalianAudioGroup, castEnglishAudioGroup)
        val params = TrackSelectionParameters.Builder()
            .addOverride(TrackSelectionOverride(localForeignAudioGroup, 0))
            .build()

        val request = createRequest(availableGroups, emptySet(), params)

        val result = selector.evaluate(request)

        assertThat(result.selections).contains(castItalianAudioGroup)
        assertThat(result.selections).doesNotContain(localForeignAudioGroup)
    }
}