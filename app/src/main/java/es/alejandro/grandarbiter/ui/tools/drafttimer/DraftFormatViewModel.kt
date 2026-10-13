package es.alejandro.grandarbiter.ui.tools.drafttimer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.data.draft.DraftTimingsRepository
import es.alejandro.grandarbiter.domain.draft.DraftPlans
import es.alejandro.grandarbiter.domain.draft.DraftSessionStore
import es.alejandro.grandarbiter.domain.draft.DraftSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The format picker. If a draft is still in progress when the judge comes back, the screen asks
 * first: resume it or discard it (instead of jumping straight to it, which looked like a bug).
 */
class DraftFormatViewModel(
    private val store: DraftSessionStore,
    timingsRepository: DraftTimingsRepository,
) : ViewModel() {

    /** The draft in progress as it is right now (for the dialog's "Pack 2 · Pick 5…"); null if none. */
    val activeDraft: StateFlow<DraftSnapshot?> = combine(store.session, timingsRepository.timings) { session, timings ->
        session?.let { DraftSnapshot.of(it, DraftPlans.create(it.config, timings), System.currentTimeMillis()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun discard() = store.save(null)
}
