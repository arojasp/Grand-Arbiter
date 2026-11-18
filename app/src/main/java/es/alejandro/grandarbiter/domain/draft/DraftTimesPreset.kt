package es.alejandro.grandarbiter.domain.draft

/**
 * Ready-made draft times the judge picks in one step instead of editing rows. A preset is plain
 * [DraftTimes]: choosing it stores those times, and the times in use match a preset only while
 * they are exactly its times (one edited row makes them custom).
 */
enum class DraftTimesPreset(val times: DraftTimes) {

    /** Appendix B of the installed MTR. */
    MTR(DraftTimes.MTR),

    /**
     * Pro Tour Booster Draft: more time for the first six picks of a 14-card pack (Play Boosters).
     * Every other pick, the reviews and other pack sizes stay as the MTR has them. Source: the
     * pick-time table Pro Tour judges use (November 2026), by cards left in the pack:
     * 14 → 50 s, 13 → 45, 12 → 40, 11 → 35, 10 → 30, 9 → 25.
     */
    PRO_TOUR(
        DraftTimes(
            pickSeconds = mapOf(
                PRO_TOUR_PACK_SIZE to mapOf(1 to 50, 2 to 45, 3 to 40, 4 to 35, 5 to 30, 6 to 25),
            ),
        ),
    );

    /** The [times] in use are this preset's, untouched. */
    fun matches(times: DraftTimes): Boolean = times == this.times

    companion object {
        /** The presets offered for [format], MTR first. */
        fun availableFor(format: DraftFormat): List<DraftTimesPreset> = when (format) {
            DraftFormat.BOOSTER -> listOf(MTR, PRO_TOUR)
            DraftFormat.TWO_HEADED_GIANT -> listOf(MTR)
            DraftFormat.ROCHESTER -> emptyList()
        }

        /** The preset whose times are exactly [times], if any. */
        fun matching(times: DraftTimes): DraftTimesPreset? = entries.firstOrNull { it.matches(times) }
    }
}

/** The pack size the Pro Tour times are for. Top-level: an enum entry can't read its companion. */
private const val PRO_TOUR_PACK_SIZE = 14
