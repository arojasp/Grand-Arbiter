package es.alejandro.grandarbiter.ui.tools.drafttimer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.data.draft.DraftSessionActions
import es.alejandro.grandarbiter.data.draft.DraftTicker
import es.alejandro.grandarbiter.domain.draft.DraftRunState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The running draft screen. The ticking and the step-end alerts live in [DraftTicker]; this
 * watches it while the screen is shown (and 5 s more, so a rotation doesn't restart it).
 */
class DraftRunViewModel(
    ticker: DraftTicker,
    private val actions: DraftSessionActions,
) : ViewModel() {

    val state: StateFlow<DraftRunState> = ticker.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DraftRunState.Loading)

    fun togglePause() = actions.togglePause()
    fun next() = actions.next()
    fun previous() = actions.previous()

    /** Manual start: "Start pick 4", "Start review", "Start pack 2", "Start deck construction". */
    fun startNext() = actions.startNext()

    /** "End draft" (after confirming) and "Finish" on the time-up screen. */
    fun endDraft() = actions.endDraft()
}
