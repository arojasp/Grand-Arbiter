package es.alejandro.grandarbiter.domain.draft

/**
 * Individual Booster Draft times, as the installed MTR gives them (Appendix B). Parsed from the
 * rules bundle so an MTR update reaches the timer; [FALLBACK] is used if parsing fails.
 */
data class BoosterDraftTimings(
    /** Seconds to pick, by cards remaining in the pack. A missing key (the last card: "N/A") has no timer. */
    val pickSeconds: Map<Int, Int>,
    /** Review after the first pack. */
    val firstReviewSeconds: Int,
    /** Each later review is this much longer. */
    val reviewIncrementSeconds: Int,
    /** "Draft—25 minutes for deck registration and construction." */
    val constructionMinutes: Int,
) {
    /** Review after [pack] (1-based): 60 s, 90 s, 120 s... with the current MTR. */
    fun reviewSeconds(afterPack: Int): Int = firstReviewSeconds + (afterPack - 1) * reviewIncrementSeconds

    companion object {
        /** MTR 2026-02-27, Appendix B. Verified against the bundled MTR by a unit test. */
        val FALLBACK = BoosterDraftTimings(
            pickSeconds = mapOf(
                15 to 40, 14 to 40, 13 to 35, 12 to 30, 11 to 25, 10 to 25, 9 to 20, 8 to 20,
                7 to 15, 6 to 10, 5 to 10, 4 to 5, 3 to 5, 2 to 5,
            ),
            firstReviewSeconds = 60,
            reviewIncrementSeconds = 30,
            constructionMinutes = 25,
        )
    }
}
