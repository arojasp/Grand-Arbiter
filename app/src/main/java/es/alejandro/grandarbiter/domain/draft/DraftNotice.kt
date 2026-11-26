package es.alejandro.grandarbiter.domain.draft

/**
 * What the draft notification shows and offers at one moment. Pure: unit-tested; the texts are
 * the app's (DraftNotification).
 *
 * The notification offers only what the draft screen does with a plain tap: pause and resume,
 * "Start…" in manual start, "Picked" in Rochester, moving on from an untimed step. The jumps the
 * screen guards with a hold (previous, next on a timed step) and "End draft" are never offered.
 */
data class DraftNotice(
    /** Running or Waiting; never Finished (there is no notice then). */
    val position: DraftPosition,
    /** What comes after [step]; null on the last one. */
    val next: DraftStep?,
    val isRunning: Boolean,
    /** Wall-clock time a running timed step runs out: the system counts down to it. */
    val endsAtMs: Long?,
    /** Wall-clock time a running untimed step started: the system counts up from it. */
    val startedAtMs: Long?,
    /** Time left in a paused timed step. */
    val remainingMs: Long?,
    val actions: List<Action>,
) {
    enum class Action { PAUSE, RESUME, START_NEXT, PICKED, NEXT }

    /** The step on screen: the one running, or the one that ran out and waits. */
    val step: DraftStep
        get() = when (val p = position) {
            is DraftPosition.Running -> p.step
            is DraftPosition.Waiting -> p.step
            DraftPosition.Finished -> error("no notice once the draft is finished")
        }

    val isWaiting: Boolean get() = position is DraftPosition.Waiting

    /** Changes only when what the notification says changes (not with the clock): the moment to update it. */
    val key: Key
        get() = Key(
            index = when (val p = position) {
                is DraftPosition.Running -> p.index
                is DraftPosition.Waiting -> p.index
                DraftPosition.Finished -> -1
            },
            waiting = isWaiting,
            running = isRunning,
        )

    data class Key(val index: Int, val waiting: Boolean, val running: Boolean)

    companion object {
        /** Null once the draft is over: the notification goes away. */
        fun of(s: DraftSnapshot, now: Long): DraftNotice? = when (val p = s.position) {
            DraftPosition.Finished -> null
            is DraftPosition.Waiting -> DraftNotice(
                position = p, next = s.nextStep, isRunning = s.isRunning,
                endsAtMs = null, startedAtMs = null, remainingMs = null,
                actions = listOf(if ((p.step as? DraftStep.Pick)?.waitsForPicked == true) Action.PICKED else Action.START_NEXT),
            )
            is DraftPosition.Running -> {
                val step = p.step
                val pauseOrResume = if (s.isRunning) Action.PAUSE else Action.RESUME
                DraftNotice(
                    position = p, next = s.nextStep, isRunning = s.isRunning,
                    endsAtMs = if (s.isRunning) p.remainingMs?.let { now + it } else null,
                    startedAtMs = if (s.isRunning && p.remainingMs == null) now - p.elapsedInStepMs else null,
                    remainingMs = if (s.isRunning) null else p.remainingMs,
                    actions = when {
                        step is DraftStep.Pick && step.waitsForPicked -> listOf(Action.PICKED, pauseOrResume)
                        // Untimed: moving on is THE action; pause only shows to resume a draft left paused.
                        !step.isTimed -> if (s.isRunning) listOf(Action.NEXT) else listOf(Action.NEXT, Action.RESUME)
                        else -> listOf(pauseOrResume)
                    },
                )
            }
        }
    }
}
