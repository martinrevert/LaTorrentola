package com.martinrevert.latorrentola.service

import androidx.media3.cast.CastTrackSelector
import androidx.media3.common.C
import com.google.common.collect.ImmutableSet

/**
 * Robust CastTrackSelector that guarantees only TrackGroups present in the Cast receiver's
 * available track groups are ever selected.
 *
 * Prevents IllegalStateException when transitioning from local playback to Google Cast
 * with embedded container tracks (e.g. SSA/ASS or MKV subtitles) in [TrackSelectionParameters.overrides].
 */
class AppCastTrackSelector : CastTrackSelector() {

    override fun evaluate(request: CastTrackSelectorRequest): CastTrackSelectorResult {
        val builder = request.buildResultUpon()
        val params = request.trackSelectionParameters
        val availableGroups = request.trackGroupList

        // Start from currently selected groups that actually exist on the Cast receiver
        val selectedGroups = request.currentlySelectedTrackGroups
            .filter { availableGroups.contains(it) }
            .toMutableSet()

        // 1. Audio Track Evaluation
        val audioTrackGroups = availableGroups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (audioTrackGroups.isNotEmpty()) {
            val audioOverride = params.overrides.values.firstOrNull { it.mediaTrackGroup.type == C.TRACK_TYPE_AUDIO }
            val targetAudioGroup = if (audioOverride != null) {
                // If the override matches an available group on Cast, use it; otherwise match by language or fallback
                audioTrackGroups.firstOrNull { it == audioOverride.mediaTrackGroup }
                    ?: audioOverride.mediaTrackGroup.getFormat(0).language?.let { overrideLang ->
                        audioTrackGroups.firstOrNull { it.getFormat(0).language?.equals(overrideLang, ignoreCase = true) == true }
                    }
                    ?: audioTrackGroups.first()
            } else {
                val prefAudioLang = params.preferredAudioLanguages.firstOrNull()
                if (prefAudioLang != null) {
                    audioTrackGroups.firstOrNull {
                        it.getFormat(0).language?.equals(prefAudioLang, ignoreCase = true) == true
                    }
                } else null
            }

            if (targetAudioGroup != null && availableGroups.contains(targetAudioGroup)) {
                selectedGroups.removeAll { it.type == C.TRACK_TYPE_AUDIO }
                selectedGroups.add(targetAudioGroup)
            }
        }

        // 2. Text Track Evaluation
        if (params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
            selectedGroups.removeAll { it.type == C.TRACK_TYPE_TEXT }
        } else {
            val textTrackGroups = availableGroups.filter { it.type == C.TRACK_TYPE_TEXT }
            if (textTrackGroups.isNotEmpty()) {
                val textOverride = params.overrides.values.firstOrNull { it.mediaTrackGroup.type == C.TRACK_TYPE_TEXT }
                val targetTextGroup = if (textOverride != null) {
                    // Match the local override against available Cast text tracks by reference or language
                    textTrackGroups.firstOrNull { it == textOverride.mediaTrackGroup }
                        ?: textOverride.mediaTrackGroup.getFormat(0).language?.let { overrideLang ->
                            textTrackGroups.firstOrNull { it.getFormat(0).language?.equals(overrideLang, ignoreCase = true) == true }
                        }
                        ?: textTrackGroups.first()
                } else {
                    val prefLang = params.preferredTextLanguages.firstOrNull()
                    if (prefLang != null) {
                        textTrackGroups.firstOrNull {
                            it.getFormat(0).language?.equals(prefLang, ignoreCase = true) == true
                        }
                    } else null
                } ?: textTrackGroups.first()

                if (availableGroups.contains(targetTextGroup)) {
                    selectedGroups.removeAll { it.type == C.TRACK_TYPE_TEXT }
                    selectedGroups.add(targetTextGroup)
                }
            }
        }

        // Strictly guarantee that NO track group outside of availableGroups can ever be selected
        val validSelectedGroups = selectedGroups.filter { availableGroups.contains(it) }
        builder.setSelections(ImmutableSet.copyOf(validSelectedGroups))
        return builder.build()
    }
}
