package es.alejandro.grandarbiter.ui.tools.drafttimer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.data.draft.DraftTimingsRepository
import es.alejandro.grandarbiter.domain.draft.DraftConfig
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftSessionStore
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.DraftTimesStore
import es.alejandro.grandarbiter.domain.draft.DraftTimings
import es.alejandro.grandarbiter.domain.draft.FormatTimes
import es.alejandro.grandarbiter.domain.draft.PickRow
import es.alejandro.grandarbiter.domain.draft.UserDraftPreset
import es.alejandro.grandarbiter.domain.draft.UserDraftPresetStore
import es.alejandro.grandarbiter.domain.draft.UserDraftPresets
import es.alejandro.grandarbiter.data.draft.SharedDraftPreset
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The Times screen of one format (Booster, Two-Headed Giant). Every change is saved at once
 * ([DraftTimesStore]): the setup reads the same store. The setup's pack sizes come as an argument
 * ("15,14,14"): only those sizes' lists are shown; changes to other sizes are kept.
 */
class DraftTimesViewModel(
    state: SavedStateHandle,
    private val store: DraftTimesStore,
    sessions: DraftSessionStore,
    timingsRepository: DraftTimingsRepository,
    private val presets: UserDraftPresetStore,
) : ViewModel() {

    val format: DraftFormat = DraftFormat.entries.firstOrNull { it.name == state.get<String>(ARG_FORMAT) }
        ?.takeIf { it in DraftConfig.CUSTOM_TIMES_FORMATS } ?: DraftFormat.BOOSTER

    private val packSizes: List<Int> = state.get<String>(ARG_SIZES)?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
        ?.takeIf { it.isNotEmpty() } ?: List(DraftConfig.DEFAULT_PACKS) { DraftConfig.defaultsFor(format).cardsPerPack }

    private val lastMinutes: Int? = (sessions.setup(format) ?: sessions.lastConfig.takeIf { it.format == format })?.constructionMinutes

    private val stored = store.times(format)

    private val own = presets.presets(format)

    val model: StateFlow<DraftTimesModel> = combine(stored, timingsRepository.timings, own) { s, t, p ->
        DraftTimesModel.of(format, packSizes, s, t, lastMinutes, p)
    }.stateIn(viewModelScope, SharingStarted.Eagerly,
        DraftTimesModel.of(format, packSizes, stored.value, DraftTimings.FALLBACK, lastMinutes, own.value))

    /**
     * A built-in preset sets picks and reviews and leaves deck construction as it is; the judge's
     * own sets both, as saved.
     */
    fun choose(choice: PresetChoice) = when (choice) {
        is PresetChoice.BuiltIn -> saveTimes { choice.preset.times }
        is PresetChoice.Own -> store.save(format, FormatTimes(choice.preset.times, choice.preset.constructionMinutes))
    }

    /** The times and construction on screen under [name]; a preset with that name is replaced. */
    fun savePreset(name: String) {
        val m = model.value
        // Construction as shown (it may still come from the last draft): stored too, so the preset matches.
        store.save(format, stored.value.copy(constructionMinutes = m.constructionMinutes))
        presets.save(format, name, m.times, m.constructionMinutes)
    }

    fun renamePreset(preset: UserDraftPreset, name: String): Boolean = presets.rename(format, preset.id, name)
    fun deletePreset(preset: UserDraftPreset) = presets.delete(format, preset.id)

    /** The preset of [format] called [name] (any case), to ask before an import replaces it. */
    fun presetNamed(format: DraftFormat, name: String): UserDraftPreset? =
        UserDraftPresets.named(presets.presets(format).value, name)

    /** A shared preset saved under [name], in its own format (maybe not this screen's). Times in use don't change. */
    fun importPreset(preset: SharedDraftPreset, name: String) {
        presets.save(preset.format, name, preset.times, preset.constructionMinutes)
    }

    fun setTimedPicks(on: Boolean) = saveTimes { it.withTimedPicks(on) }
    fun setPick(row: PickRow, seconds: Int) = saveTimes { it.withPick(row, seconds) }
    fun allPicks(deltaSeconds: Int) = saveTimes { it.withAllPicks(deltaSeconds, model.value.rowsOnScreen) }
    fun setReview(review: DraftTimesModel.ReviewRow, seconds: Int) = saveTimes { it.withReview(review.pack, seconds, review.mtrSeconds) }

    fun setConstructionMinutes(minutes: Int) =
        store.save(format, stored.value.copy(constructionMinutes = minutes.coerceIn(DraftConfig.CONSTRUCTION_MINUTES)))

    /** Everything back to the MTR, deck construction included. */
    fun resetToMtr() = store.save(format, FormatTimes(DraftTimes.MTR, model.value.mtrConstructionMinutes))

    private fun saveTimes(change: (DraftTimes) -> DraftTimes) {
        val current = stored.value
        // Construction as shown (it may still come from the last draft): saved with the first change.
        store.save(format, current.copy(times = change(current.times), constructionMinutes = model.value.constructionMinutes))
    }

    companion object {
        const val ARG_FORMAT = "format"
        const val ARG_SIZES = "sizes"
    }
}
