package es.alejandro.grandarbiter.domain.draft

import es.alejandro.grandarbiter.domain.timer.TimerState

/**
 * A draft in progress: its config and ONE timer for the whole timeline (the same TimerState as
 * the judge call timer). Persisted as is; on return the right step comes from the clock.
 * Pure: unit-tested.
 */
data class DraftSession(
    val config: DraftConfig,
    val timer: TimerState = TimerState(),
) {
    val isRunning: Boolean get() = timer.isRunning

    fun elapsedAt(now: Long): Long = timer.elapsedAt(now)

    fun positionAt(plan: DraftPlan, now: Long): DraftPosition = plan.positionAt(elapsedAt(now))

    fun started(now: Long): DraftSession = copy(timer = timer.started(now))
    fun paused(now: Long): DraftSession = copy(timer = timer.paused(now))

    /** Jump to the start of the next step ("Next pick", "Start pack N"); from the last one, finish. */
    fun next(plan: DraftPlan, now: Long): DraftSession = when (val p = positionAt(plan, now)) {
        is DraftPosition.Running -> seek(plan.startOf(p.index + 1), now)
        DraftPosition.Finished -> this
    }

    /** Jump to the start of the previous step; on the first step, back to its start. */
    fun previous(plan: DraftPlan, now: Long): DraftSession = when (val p = positionAt(plan, now)) {
        is DraftPosition.Running -> seek(plan.startOf((p.index - 1).coerceAtLeast(0)), now)
        DraftPosition.Finished -> seek(plan.startOf(plan.steps.lastIndex), now)
    }

    /** Elapsed time set to [elapsedMs]; keeps running if it was running, paused if it was paused. */
    private fun seek(elapsedMs: Long, now: Long): DraftSession =
        copy(timer = TimerState(accumulatedMs = elapsedMs, runningSince = if (timer.isRunning) now else null))
}

/**
 * When to vibrate (and beep): once each time a step runs out. Only for a step end crossed in the
 * last [WINDOW_MS]: coming back to the app after minutes away gives no burst of alerts.
 * The caller passes [previousElapsedMs] = null after a manual jump (next/previous/start), so a
 * jump never alerts.
 */
object DraftAlertPolicy {
    const val WINDOW_MS = 2_000L

    fun shouldAlert(plan: DraftPlan, previousElapsedMs: Long?, elapsedMs: Long): Boolean {
        if (previousElapsedMs == null || elapsedMs <= previousElapsedMs) return false
        return plan.stepEnds.any { end -> end in (previousElapsedMs + 1)..elapsedMs && elapsedMs - end <= WINDOW_MS }
    }
}
