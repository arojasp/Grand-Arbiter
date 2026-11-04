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
) : ViewModel() {

    val format: DraftFormat = DraftFormat.entries.firstOrNull { it.name == state.get<String>(ARG_FORMAT) }
        ?.takeIf { it in DraftConfig.CUSTOM_TIMES_FORMATS } ?: DraftFormat.BOOSTER

    private val packSizes: List<Int> = state.get<String>(ARG_SIZES)?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
        ?.takeIf { it.isNotEmpty() } ?: List(DraftConfig.DEFAULT_PACKS) { DraftConfig.defaultsFor(format).cardsPerPack }

    private val lastMinutes: Int? = (sessions.setup(format) ?: sessions.lastConfig.takeIf { it.format == format })?.constructionMinutes

    private val stored = store.times(format)

    val model: StateFlow<DraftTimesModel> = combine(stored, timingsRepository.timings) { s, t ->
        DraftTimesModel.of(format, packSizes, s, t, lastMinutes)
    }.stateIn(viewModelScope, SharingStarted.Eagerly,
        DraftTimesModel.of(format, packSizes, stored.value, DraftTimings.FALLBACK, lastMinutes))

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
