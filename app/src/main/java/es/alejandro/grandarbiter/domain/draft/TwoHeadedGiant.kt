package es.alejandro.grandarbiter.domain.draft

/**
 * Two-Headed Giant Booster Draft times, as the installed MTR gives them (Appendix B "Two-Headed
 * Giant Draft Timing"): one column per booster size, because each team takes TWO cards per pick.
 * Parsed from the rules bundle; [FALLBACK] if parsing fails.
 */
data class TwoHeadedGiantTimings(
    /** Booster size (14 or 15) -> seconds by cards remaining. A missing key ("N/A") has no timer. */
    val pickSeconds: Map<Int, Map<Int, Int>>,
    /** "60 seconds to review … in between booster packs": the same after every pack. */
    val reviewSeconds: Int,
    /** "Two-Headed Giant Draft—40 minutes for deck construction and registration." */
    val constructionMinutes: Int,
) {
    /** The booster sizes the MTR has times for: the only ones the setup offers. */
    val boosterSizes: Set<Int> get() = pickSeconds.keys

    companion object {
        /** MTR 2026-02-27, Appendix B. Verified against the bundled MTR by a unit test. */
        val FALLBACK = TwoHeadedGiantTimings(
            pickSeconds = mapOf(
                15 to mapOf(15 to 50, 13 to 45, 11 to 40, 9 to 30, 7 to 20, 5 to 10, 3 to 5),
                14 to mapOf(14 to 50, 12 to 45, 10 to 40, 8 to 30, 6 to 20, 4 to 10),
            ),
            reviewSeconds = 60,
            constructionMinutes = 40,
        )
    }
}

/** Every format's times from the installed MTR, read together. */
data class DraftTimings(
    val booster: BoosterDraftTimings = BoosterDraftTimings.FALLBACK,
    val twoHeadedGiant: TwoHeadedGiantTimings = TwoHeadedGiantTimings.FALLBACK,
) {
    companion object {
        val FALLBACK = DraftTimings()
    }
}

/**
 * Two-Headed Giant Booster Draft (MTR 9.6, 9.7 + Appendix B): each team takes two cards per pick,
 * packs alternate left / right, a fixed review between packs, then deck construction.
 */
object TwoHeadedGiantPlanFactory {
    const val CARDS_PER_PICK = 2

    fun create(config: DraftConfig, timings: TwoHeadedGiantTimings): DraftPlan {
        require(config.format == DraftFormat.TWO_HEADED_GIANT) { "not a Two-Headed Giant draft: ${config.format}" }
        val table = requireNotNull(timings.pickSeconds[config.cardsPerPack]) {
            "no Two-Headed Giant times for ${config.cardsPerPack}-card boosters"
        }
        val steps = buildList {
            for (pack in 1..config.packs) {
                val direction = PassDirection.forPack(pack)
                var pick = 1
                for (cardsLeft in config.cardsPerPack downTo 1 step CARDS_PER_PICK) {
                    // No time in the table ("N/A": the last card or two) -> no timer, no step.
                    table[cardsLeft]?.let { seconds ->
                        add(DraftStep.Pick(pack, pick, cardsLeft, direction, seconds * 1_000L, cardsTaken = CARDS_PER_PICK))
                    }
                    pick++
                }
                if (pack < config.packs) {
                    add(DraftStep.Review(pack, PassDirection.forPack(pack + 1), timings.reviewSeconds * 1_000L))
                }
            }
            add(DraftStep.Construction(config.constructionMinutes * 60_000L))
        }
        return DraftPlan(steps)
    }
}
