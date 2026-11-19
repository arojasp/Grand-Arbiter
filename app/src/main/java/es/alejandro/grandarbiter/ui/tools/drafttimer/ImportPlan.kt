package es.alejandro.grandarbiter.ui.tools.drafttimer

import es.alejandro.grandarbiter.data.draft.SharedDraftPreset
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.UserDraftPreset
import es.alejandro.grandarbiter.domain.draft.UserDraftPresets

/** What tapping Import does next: save at once, or ask first. Pure: unit-tested. */
internal sealed interface ImportStep {
    /** Nothing in the way: saved under the shared name. */
    data object Save : ImportStep
    /** That format already has a preset with this name (any case): Replace or Change name. */
    data class AskReplace(val existing: UserDraftPreset) : ImportStep
    /** "MTR" or "Pro Tour": can't be an own preset's name, so ask for another. */
    data object AskName : ImportStep
}

internal object ImportPlan {

    /** [named]: the preset of a format with a name, as the store has it (the format may not be the screen's). */
    fun step(preset: SharedDraftPreset, named: (DraftFormat, String) -> UserDraftPreset?): ImportStep {
        if (UserDraftPresets.isReserved(preset.name)) return ImportStep.AskName
        val existing = named(preset.format, preset.name) ?: return ImportStep.Save
        return ImportStep.AskReplace(existing)
    }

    /** "Import as": a name the preset can be saved under without replacing anything. */
    fun isFree(preset: SharedDraftPreset, name: String, named: (DraftFormat, String) -> UserDraftPreset?): Boolean {
        val clean = UserDraftPresets.cleanName(name)
        return UserDraftPresets.isValidName(clean) && !UserDraftPresets.isReserved(clean) && named(preset.format, clean) == null
    }
}
