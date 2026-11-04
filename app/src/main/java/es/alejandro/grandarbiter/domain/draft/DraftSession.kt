package es.alejandro.grandarbiter.domain.draft

import es.alejandro.grandarbiter.domain.timer.TimerState

/**
 * A draft in progress: its config, the step started last ([step]) and ONE timer (the same
 * TimerState as the judge call timer) counting from the START OF THAT STEP. Steps that move on by
 * themselves are walked from there with the clock. Persisted as is; on return the right step comes
 * from the clock.
 *
 * Some step ends wait: time never runs past them, the draft waits at 0:00
 * ([DraftPosition.Waiting]) until [startNext]. With manual start ([DraftConfig.manualStart]) every
 * step does; in Rochester every pick does ("Picked"). Other steps follow each other on their own.
 * Pure: unit-tested.
 */
data class DraftSession(
    val config: DraftConfig,
    val timer: TimerState = TimerState(),
    /** The step being timed (manual start: the one time stops at). Kept in sync by the jumps. */
    val step: Int = 0,
) {
    init { require(step >= 0) { "step $step < 0" } }

    val isRunning: Boolean get() = timer.isRunning

    /** Time since the start of [step], stopped at the first waiting step end it reached. */
    fun elapsedAt(plan: DraftPlan, now: Long): Long = walk(plan, now).second

    fun positionAt(plan: DraftPlan, now: Long): DraftPosition = walk(plan, now).first

    /** Whether the end of step [index] waits for the judge instead of moving on. */
    fun waitsAfter(plan: DraftPlan, index: Int): Boolean =
        config.manualStart || (plan.steps[index] as? DraftStep.Pick)?.waitsForPicked == true

    /**
     * From [step] (the last step started by hand), follow the clock through the steps that move on
     * by themselves; stop at the first one that ran out and waits, or where the time is now.
     */
    private fun walk(plan: DraftPlan, now: Long): Pair<DraftPosition, Long> {
        val elapsed = timer.elapsedAt(now).coerceAtLeast(0)
        var i = step.coerceAtMost(plan.steps.size)
        var start = 0L   // start of step i, counted from the start of [step]
        while (i < plan.steps.size) {
            val s = plan.steps[i]
            // Untimed: it never runs out; the judge moves on ("Next pick", "Start pack 2").
            val duration = s.durationMs ?: return DraftPosition.Running(i, s, elapsedInStepMs = elapsed - start, remainingMs = null) to elapsed
            val end = start + duration
            if (elapsed < end) return DraftPosition.Running(i, s, elapsedInStepMs = elapsed - start, remainingMs = end - elapsed) to elapsed
            if (waitsAfter(plan, i)) {
                val position = if (i == plan.steps.lastIndex) DraftPosition.Finished else DraftPosition.Waiting(i, s)
                return position to end
            }
            start = end
            i++
        }
        return DraftPosition.Finished to elapsed
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

    /** Step [index] (or the end, past the last) from its start; keeps running or paused. */
    private fun seek(plan: DraftPlan, index: Int, now: Long): DraftSession = copy(
        timer = TimerState(accumulatedMs = 0, runningSince = if (timer.isRunning) now else null),
        step = index.coerceAtMost(plan.steps.size),
    )
}

/**
 * When to vibrate (and beep): once each time a step runs out. Only for a step end crossed in the
 * last [WINDOW_MS]: coming back to the app after minutes away gives no burst of alerts.
 * The caller passes [previousElapsedMs] = null after a manual jump (next/previous/start), so a
 * jump never alerts. Elapsed times are the session's ([DraftSession.elapsedAt]): counted from the
 * start of step [fromStep] (the session's [DraftSession.step]).
 */
object DraftAlertPolicy {
    const val WINDOW_MS = 2_000L

    fun shouldAlert(plan: DraftPlan, previousElapsedMs: Long?, elapsedMs: Long, fromStep: Int = 0): Boolean {
        if (previousElapsedMs == null || elapsedMs <= previousElapsedMs) return false
        return plan.endsFrom(fromStep).any { end -> end in (previousElapsedMs + 1)..elapsedMs && elapsedMs - end <= WINDOW_MS }
    }
}
