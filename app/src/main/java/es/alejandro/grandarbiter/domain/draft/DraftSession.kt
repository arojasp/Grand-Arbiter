package es.alejandro.grandarbiter.domain.draft

import es.alejandro.grandarbiter.domain.timer.TimerState

/**
 * A draft in progress: its config and ONE timer for the whole timeline (the same TimerState as
 * the judge call timer). Persisted as is; on return the right step comes from the clock.
 *
 * Manual start ([DraftConfig.manualStart]): time never runs past the end of [step]; there the
 * draft waits at 0:00 ([DraftPosition.Waiting]) until [startNext]. Without it the steps follow
 * each other on their own. Pure: unit-tested.
 */
data class DraftSession(
    val config: DraftConfig,
    val timer: TimerState = TimerState(),
    /** The step being timed (manual start: the one time stops at). Kept in sync by the jumps. */
    val step: Int = 0,
) {
    init { require(step >= 0) { "step $step < 0" } }

    val isRunning: Boolean get() = timer.isRunning

    /** Elapsed draft time; with manual start, capped at the end of [step]. */
    fun elapsedAt(plan: DraftPlan, now: Long): Long {
        val elapsed = timer.elapsedAt(now)
        return if (config.manualStart) elapsed.coerceAtMost(plan.startOf(step + 1)) else elapsed
    }

    fun positionAt(plan: DraftPlan, now: Long): DraftPosition {
        val elapsed = elapsedAt(plan, now)
        if (config.manualStart && step < plan.steps.size && elapsed >= plan.startOf(step + 1)) {
            return if (step >= plan.steps.lastIndex) DraftPosition.Finished
            else DraftPosition.Waiting(step, plan.steps[step])
        }
        return plan.positionAt(elapsed)
    }

    fun started(now: Long): DraftSession = copy(timer = timer.started(now))
    fun paused(now: Long): DraftSession = copy(timer = timer.paused(now))

    /** Jump to the start of the next step ("Next pick", "Start pack N"); from the last one, finish. */
    fun next(plan: DraftPlan, now: Long): DraftSession = when (val p = positionAt(plan, now)) {
        is DraftPosition.Running -> seek(plan, p.index + 1, now)
        is DraftPosition.Waiting -> seek(plan, p.index + 1, now)
        DraftPosition.Finished -> this
    }

    /** Jump to the start of the previous step; on the first step, back to its start. */
    fun previous(plan: DraftPlan, now: Long): DraftSession = when (val p = positionAt(plan, now)) {
        is DraftPosition.Running -> seek(plan, (p.index - 1).coerceAtLeast(0), now)
        is DraftPosition.Waiting -> seek(plan, p.index, now)   // the step that just ran out, again
        DraftPosition.Finished -> seek(plan, plan.steps.lastIndex, now)
    }

    /** Manual start: the judge starts the next step (always running, even if it was paused). */
    fun startNext(plan: DraftPlan, now: Long): DraftSession = next(plan, now).started(now)

    /** Time set to the start of step [index] (or the end, past the last); keeps running or paused. */
    private fun seek(plan: DraftPlan, index: Int, now: Long): DraftSession = copy(
        timer = TimerState(accumulatedMs = plan.startOf(index), runningSince = if (timer.isRunning) now else null),
        step = index.coerceAtMost(plan.steps.size),
    )
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
