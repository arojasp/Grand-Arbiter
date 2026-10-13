package es.alejandro.grandarbiter.ui.tools.drafttimer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.data.draft.DraftTimingsRepository
import es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
import es.alejandro.grandarbiter.domain.draft.DraftAlertPolicy
import es.alejandro.grandarbiter.domain.draft.DraftAlerts
import es.alejandro.grandarbiter.domain.draft.DraftPlan
import es.alejandro.grandarbiter.domain.draft.DraftPlans
import es.alejandro.grandarbiter.domain.draft.DraftPosition
import es.alejandro.grandarbiter.domain.draft.DraftSession
import es.alejandro.grandarbiter.domain.draft.DraftSessionStore
import es.alejandro.grandarbiter.domain.draft.DraftSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

sealed interface DraftRunState {
    data object Loading : DraftRunState
    /** No draft in progress (ended, or never started): the screen goes back to the format picker. */
    data object NoDraft : DraftRunState
    data class Active(val snapshot: DraftSnapshot) : DraftRunState
}

/**
 * The running draft. Ticks once per second only while the screen is subscribed (foreground):
 * that is also when step-end alerts fire. Every new session value (pause, jump) or coming back to
 * the screen restarts the ticking with no previous tick, so a jump never alerts and returning
 * after a while gives no burst of vibrations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DraftRunViewModel(
    private val store: DraftSessionStore,
    timingsRepository: DraftTimingsRepository,
    settings: SettingsDataStore,
    private val alerts: DraftAlerts,
) : ViewModel() {

    private sealed interface Source {
        data object None : Source
        data class Ready(val session: DraftSession, val plan: DraftPlan) : Source
    }

    private val beep: StateFlow<Boolean> = settings.draftBeep
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Null until the MTR timings are read (a moment at start-up). */
    private val source: StateFlow<Source?> = combine(store.session, timingsRepository.timings) { session, timings ->
        if (session == null) Source.None else Source.Ready(session, DraftPlans.create(session.config, timings))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<DraftRunState> = source
        .flatMapLatest { s ->
            when (s) {
                null -> flowOf(DraftRunState.Loading)
                Source.None -> flowOf(DraftRunState.NoDraft)
                is Source.Ready -> ticks(s.session, s.plan)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DraftRunState.Loading)

    private fun ticks(session: DraftSession, plan: DraftPlan): Flow<DraftRunState> = flow {
        var previousElapsed: Long? = null   // null: just (re)started or jumped, never alert
        while (true) {
            val now = System.currentTimeMillis()
            val elapsed = session.elapsedAt(now)
            if (DraftAlertPolicy.shouldAlert(plan, previousElapsed, elapsed)) alerts.stepEnded(beep.value)
            previousElapsed = elapsed
            val snapshot = DraftSnapshot.of(session, plan, now)
            emit(DraftRunState.Active(snapshot))
            if (!session.isRunning || snapshot.position == DraftPosition.Finished) break
            delay(1_000 - elapsed % 1_000)   // next whole second: steps last whole seconds
        }
    }

    fun togglePause() = update { s, _, now -> if (s.isRunning) s.paused(now) else s.started(now) }
    fun next() = update { s, plan, now -> s.next(plan, now) }
    fun previous() = update { s, plan, now -> s.previous(plan, now) }

    /** "End draft" (after confirming) and "Finish" on the time-up screen. */
    fun endDraft() = store.save(null)

    private fun update(change: (DraftSession, DraftPlan, Long) -> DraftSession) {
        val ready = source.value as? Source.Ready ?: return
        store.save(change(ready.session, ready.plan, System.currentTimeMillis()))
    }
}
