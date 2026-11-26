package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.domain.draft.DraftAlertPolicy
import es.alejandro.grandarbiter.domain.draft.DraftAlertSettings
import es.alejandro.grandarbiter.domain.draft.DraftAlerts
import es.alejandro.grandarbiter.domain.draft.DraftPlan
import es.alejandro.grandarbiter.domain.draft.DraftPlans
import es.alejandro.grandarbiter.domain.draft.DraftPosition
import es.alejandro.grandarbiter.domain.draft.DraftRunState
import es.alejandro.grandarbiter.domain.draft.DraftSession
import es.alejandro.grandarbiter.domain.draft.DraftSessionStore
import es.alejandro.grandarbiter.domain.draft.DraftSnapshot
import es.alejandro.grandarbiter.domain.draft.DraftTimings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/**
 * The running draft, ticked once per second while someone watches [state]. Step-end alerts fire
 * here, inside the one shared flow, so they fire once however many watch it. Every new session
 * value (pause, jump) and every restart of the watching starts the ticking again with no previous
 * tick, so a jump never alerts and coming back after a while gives no burst of vibrations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DraftTicker(
    store: DraftSessionStore,
    timings: Flow<DraftTimings>,
    alertSettings: Flow<DraftAlertSettings>,
    private val alerts: DraftAlerts,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The draft in progress and its plan, or none; null until the MTR timings are read (a moment at start-up). */
    sealed interface Source {
        data object None : Source
        data class Ready(val session: DraftSession, val plan: DraftPlan) : Source
    }

    private val settings: StateFlow<DraftAlertSettings> = alertSettings.stateIn(scope, SharingStarted.Eagerly, DraftAlertSettings())

    val source: StateFlow<Source?> = combine(store.session, timings) { session, t ->
        if (session == null) Source.None else Source.Ready(session, DraftPlans.create(session.config, t))
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Stops as soon as nobody watches, and forgets its last value then: whoever watches next gets a
     * fresh tick, never a snapshot from minutes ago.
     */
    val state: SharedFlow<DraftRunState> = source
        .flatMapLatest { s ->
            when (s) {
                null -> flowOf(DraftRunState.Loading)
                Source.None -> flowOf(DraftRunState.NoDraft)
                is Source.Ready -> ticks(s.session, s.plan)
            }
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), replay = 1)

    private fun ticks(session: DraftSession, plan: DraftPlan): Flow<DraftRunState> = flow {
        var previousElapsed: Long? = null   // null: just (re)started or jumped, never alert
        while (true) {
            val now = clock()
            val elapsed = session.elapsedAt(plan, now)
            if (DraftAlertPolicy.shouldAlert(plan, previousElapsed, elapsed, session.step)) {
                val s = settings.value
                if (s.vibrate) alerts.vibrate(s.strength)
                if (s.beep) alerts.beep(s.volume)
            }
            previousElapsed = elapsed
            val snapshot = DraftSnapshot.of(session, plan, now)
            emit(DraftRunState.Active(snapshot))
            // Nothing changes while paused, waiting for "Start…" (manual start) or after the end.
            if (!session.isRunning || snapshot.position !is DraftPosition.Running) break
            delay(1_000 - elapsed % 1_000)   // next whole second: steps last whole seconds (elapsed counts from a step start)
        }
    }
}

/** What the judge does to the running draft, from the draft screen (and later the notification). */
class DraftSessionActions(
    private val store: DraftSessionStore,
    private val ticker: DraftTicker,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun togglePause() = update { s, _, now -> if (s.isRunning) s.paused(now) else s.started(now) }

    /** From the notification, which may be a moment behind: does nothing if already paused (or running). */
    fun pause() = update { s, _, now -> if (s.isRunning) s.paused(now) else s }
    fun resume() = update { s, _, now -> if (s.isRunning) s else s.started(now) }
    fun next() = update { s, plan, now -> s.next(plan, now) }
    fun previous() = update { s, plan, now -> s.previous(plan, now) }

    /** Manual start: "Start pick 4", "Start review", "Start pack 2", "Start deck construction". */
    fun startNext() = update { s, plan, now -> s.startNext(plan, now) }

    /** "End draft" (after confirming) and "Finish" on the time-up screen. */
    fun endDraft() = store.save(null)

    private fun update(change: (DraftSession, DraftPlan, Long) -> DraftSession) {
        val ready = ticker.source.value as? DraftTicker.Source.Ready ?: return
        store.save(change(ready.session, ready.plan, clock()))
    }
}
