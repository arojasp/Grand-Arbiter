package es.alejandro.grandarbiter.domain.draft

/** One timed stretch of the draft. Every step has a timer; untimed moments (the last card) aren't steps. */
sealed interface DraftStep {
    val durationMs: Long

    /** [pick] is 1-based within the pack; [cardsLeft] counts the card being picked. */
    data class Pick(
        val pack: Int,
        val pick: Int,
        val cardsLeft: Int,
        val direction: PassDirection,
        override val durationMs: Long,
    ) : DraftStep

    /** Between packs only (MTR 7.7): never after the last one. */
    data class Review(
        val afterPack: Int,
        val nextDirection: PassDirection,
        override val durationMs: Long,
    ) : DraftStep {
        val nextPack: Int get() = afterPack + 1
    }

    data class Construction(override val durationMs: Long) : DraftStep
}

/** Where a draft is at some elapsed time. */
sealed interface DraftPosition {
    data class Running(
        val index: Int,
        val step: DraftStep,
        val elapsedInStepMs: Long,
        val remainingMs: Long,
    ) : DraftPosition

    /** Manual start: [step] ran out and the timer waits at 0:00 until the next step is started. */
    data class Waiting(val index: Int, val step: DraftStep) : DraftPosition

    /** Every step has run out (deck construction time is up). */
    data object Finished : DraftPosition
}

/**
 * The whole draft as one timeline: the steps back to back. The current step is derived from the
 * elapsed time, so a single timer (pause, resume, seek) drives everything. Pure: unit-tested.
 */
class DraftPlan(val steps: List<DraftStep>) {

    /** Start of each step, plus the total at the end: starts[i] until starts[i + 1]. */
    private val starts: LongArray = LongArray(steps.size + 1).also { acc ->
        steps.forEachIndexed { i, s -> acc[i + 1] = acc[i] + s.durationMs }
    }

    init {
        require(steps.isNotEmpty()) { "a draft needs at least one step" }
        require(steps.all { it.durationMs > 0 }) { "every step needs a positive duration" }
    }

    val totalMs: Long get() = starts[steps.size]

    /** Moments (elapsed ms) when a step runs out: the alert moments. The last one is [totalMs]. */
    val stepEnds: List<Long> get() = (1..steps.size).map { starts[it] }

    fun startOf(index: Int): Long = starts[index.coerceIn(0, steps.size)]

    fun positionAt(elapsedMs: Long): DraftPosition {
        val t = elapsedMs.coerceAtLeast(0)
        if (t >= totalMs) return DraftPosition.Finished
        // Last start <= t. starts is sorted and strictly increasing (positive durations).
        var lo = 0
        var hi = steps.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= t) lo = mid else hi = mid - 1
        }
        val step = steps[lo]
        val inStep = t - starts[lo]
        return DraftPosition.Running(lo, step, elapsedInStepMs = inStep, remainingMs = step.durationMs - inStep)
    }
}
