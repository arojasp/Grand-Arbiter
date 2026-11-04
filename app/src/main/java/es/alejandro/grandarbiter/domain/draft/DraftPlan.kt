package es.alejandro.grandarbiter.domain.draft

/**
 * One stretch of the draft. Untimed moments the MTR has no time for (the last card) aren't steps.
 * Picks and reviews can be UNTIMED ([durationMs] null: the judge drafts with no timer and moves on
 * by hand); lay-outs and deck construction always have a time.
 */
sealed interface DraftStep {
    val durationMs: Long?

    val isTimed: Boolean get() = durationMs != null

    /**
     * [pick] is 1-based within the pack; [cardsLeft] counts the card(s) being picked; [cardsTaken]: 2 in
     * Two-Headed Giant. Rochester: [actor] is the seat picking ("2B") and the step [waitsForPicked]: its
     * time is a maximum, it ends when the judge taps "Picked" (or, at 0:00, an official picks for them).
     */
    data class Pick(
        val pack: Int,
        val pick: Int,
        val cardsLeft: Int,
        val direction: PassDirection,
        override val durationMs: Long?,
        val cardsTaken: Int = 1,
        val actor: String? = null,
        val waitsForPicked: Boolean = false,
    ) : DraftStep

    /** Rochester: a pack laid out face up by [opener], reviewed before the first pick (MTR 8.5). */
    data class LayOut(
        val pack: Int,
        val opener: String,
        val direction: PassDirection,
        override val durationMs: Long,
    ) : DraftStep

    /** Between packs only (MTR 7.7): never after the last one. */
    data class Review(
        val afterPack: Int,
        val nextDirection: PassDirection,
        override val durationMs: Long?,
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
        /** Null in an untimed step: it never runs out. */
        val remainingMs: Long?,
    ) : DraftPosition

    /** Manual start: [step] ran out and the timer waits at 0:00 until the next step is started. */
    data class Waiting(val index: Int, val step: DraftStep) : DraftPosition

    /** Every step has run out (deck construction time is up). */
    data object Finished : DraftPosition
}

/**
 * The draft's steps in order. A session walks them with its timer ([DraftSession]); the plan only
 * knows the steps and their times. Pure: unit-tested.
 */
class DraftPlan(val steps: List<DraftStep>) {

    /** Start of each step counting only timed steps (an untimed one adds 0), plus that total at the end. */
    private val starts: LongArray = LongArray(steps.size + 1).also { acc ->
        steps.forEachIndexed { i, s -> acc[i + 1] = acc[i] + (s.durationMs ?: 0L) }
    }

    init {
        require(steps.isNotEmpty()) { "a draft needs at least one step" }
        require(steps.all { (it.durationMs ?: 1L) > 0 }) { "every timed step needs a positive duration" }
    }

    /** The timed steps' total (untimed ones count 0). */
    val totalMs: Long get() = starts[steps.size]

    val hasUntimedSteps: Boolean get() = steps.any { !it.isTimed }

    /** Where step [index] starts, counting only timed steps; past the end, [totalMs]. */
    fun startOf(index: Int): Long = starts[index.coerceIn(0, steps.size)]

    /**
     * When each step from [index] on runs out, counted from the start of step [index] (a session's
     * timer): the alert moments. They stop at the first untimed step, which never runs out.
     */
    fun endsFrom(index: Int): List<Long> {
        val from = index.coerceIn(0, steps.size)
        return (from until steps.size).asSequence()
            .takeWhile { steps[it].isTimed }
            .map { starts[it + 1] - starts[from] }
            .toList()
    }
}
