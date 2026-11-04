package es.alejandro.grandarbiter.domain.draft

/**
 * The judge's own draft times (Booster and Two-Headed Giant; casual drafts). Only what was changed
 * is kept: a row left alone follows the installed MTR, so an MTR update still reaches it.
 * [MTR] = nothing changed. Deck construction minutes live in [DraftConfig.constructionMinutes].
 */
data class DraftTimes(
    /** Off: picks and reviews have no timer; the judge moves on by hand. Construction keeps its time. */
    val timedPicks: Boolean = true,
    /** Pack size -> pick number (1-based) -> seconds. */
    val pickSeconds: Map<Int, Map<Int, Int>> = emptyMap(),
    /** Review after pack N (1-based) -> seconds; [EVERY_REVIEW] for all of them (Two-Headed Giant). */
    val reviewSeconds: Map<Int, Int> = emptyMap(),
) {
    init {
        require(pickSeconds.values.all { picks -> picks.values.all { it in SECONDS } }) { "pick times not in $SECONDS: $pickSeconds" }
        require(reviewSeconds.values.all { it in SECONDS }) { "review times not in $SECONDS: $reviewSeconds" }
    }

    /** Nothing changed: the MTR's picks and reviews. */
    val isMtr: Boolean get() = timedPicks && pickSeconds.isEmpty() && reviewSeconds.isEmpty()

    /** The time of [row]: the judge's, or the MTR's. */
    fun secondsFor(row: PickRow): Int = pickSeconds[row.packSize]?.get(row.pick) ?: row.mtrSeconds

    fun reviewSecondsAfter(pack: Int, mtrSeconds: Int): Int = reviewSeconds[pack] ?: reviewSeconds[EVERY_REVIEW] ?: mtrSeconds

    fun isChanged(row: PickRow): Boolean = pickSeconds[row.packSize]?.containsKey(row.pick) == true

    /** [row] set to [seconds] (clamped); back to the MTR's value, the change is dropped. */
    fun withPick(row: PickRow, seconds: Int): DraftTimes {
        val value = seconds.coerceIn(SECONDS)
        val ofSize = pickSeconds[row.packSize].orEmpty().let { if (value == row.mtrSeconds) it - row.pick else it + (row.pick to value) }
        return copy(pickSeconds = if (ofSize.isEmpty()) pickSeconds - row.packSize else pickSeconds + (row.packSize to ofSize))
    }

    /** "All picks −5 s / +5 s": every row in [rows] (the lists on screen), each clamped. */
    fun withAllPicks(deltaSeconds: Int, rows: List<PickRow>): DraftTimes =
        rows.fold(this) { times, row -> times.withPick(row, times.secondsFor(row) + deltaSeconds) }

    /** The review after [pack] ([EVERY_REVIEW]: all of them) set to [seconds]; the MTR's value drops the change. */
    fun withReview(pack: Int, seconds: Int, mtrSeconds: Int): DraftTimes {
        val value = seconds.coerceIn(SECONDS)
        return copy(reviewSeconds = if (value == mtrSeconds) reviewSeconds - pack else reviewSeconds + (pack to value))
    }

    fun withTimedPicks(on: Boolean): DraftTimes = copy(timedPicks = on)

    companion object {
        val MTR = DraftTimes()
        /** Any time the judge wants, up to three digits. */
        val SECONDS = 1..999
        const val EVERY_REVIEW = 0
    }
}

/** One pick of a pack of [packSize]: its number, the cards in the pack then, and the MTR's time. */
data class PickRow(val packSize: Int, val pick: Int, val cardsLeft: Int, val mtrSeconds: Int)

/** The MTR's pick rows (the timed picks) of a pack, as the plan factories and the Times screen use them. */
object DraftTimeRows {

    /** Booster: one card per pick; the last card has no time ("N/A") and no row. */
    fun booster(packSize: Int, timings: BoosterDraftTimings): List<PickRow> =
        (packSize downTo 1).mapNotNull { cardsLeft ->
            timings.pickSeconds[cardsLeft]?.let { PickRow(packSize, packSize - cardsLeft + 1, cardsLeft, it) }
        }

    /** Two-Headed Giant: two cards per pick, from the column of that booster size. */
    fun twoHeadedGiant(packSize: Int, timings: TwoHeadedGiantTimings): List<PickRow> {
        val table = requireNotNull(timings.pickSeconds[packSize]) { "no Two-Headed Giant times for $packSize-card boosters" }
        return (packSize downTo 1 step TwoHeadedGiantPlanFactory.CARDS_PER_PICK).mapIndexedNotNull { i, cardsLeft ->
            table[cardsLeft]?.let { PickRow(packSize, i + 1, cardsLeft, it) }
        }
    }
}
