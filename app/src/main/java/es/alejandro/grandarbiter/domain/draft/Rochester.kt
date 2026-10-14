package es.alejandro.grandarbiter.domain.draft

/**
 * Team Rochester Draft times, as the installed MTR gives them (Appendix B "Rochester Draft
 * Timing" + "Team Draft—40 minutes"). Parsed from the rules bundle; [FALLBACK] otherwise.
 */
data class RochesterTimings(
    /** "The review period for a booster after it has been laid out … is 20 seconds." */
    val reviewSeconds: Int,
    /** "Players have 5 seconds for each pick." A maximum: the judge advances with "Picked". */
    val pickSeconds: Int,
    /** Appendix B "Team Draft—40 minutes for deck construction and registration". */
    val constructionMinutes: Int,
) {
    companion object {
        /** MTR 2026-02-27, Appendix B. Verified against the bundled MTR by a unit test. */
        val FALLBACK = RochesterTimings(reviewSeconds = 20, pickSeconds = 5, constructionMinutes = 40)
    }
}

/**
 * Who opens each pack and who picks each card in Team Rochester (MTR 8.5). Pure: unit-tested
 * against the MTR's own example.
 *
 * Seats clockwise: 1A 1B 1C 2A 2B 2C. Player B of the team that picks first lays out the first
 * pack. The opener ("active player") moves around the table, clockwise for the 1st and 3rd
 * groups of packs and counterclockwise for the 2nd; the last opener of a group opens first in
 * the next. In each pack the picks snake: from the opener around the table in the group's
 * direction to the last player, who takes two in a row, then back, and so on.
 */
object RochesterOrder {
    val SEATS = listOf("1A", "1B", "1C", "2A", "2B", "2C")
    const val PLAYERS = 6

    /** Groups of packs alternate: 1st clockwise, 2nd counterclockwise, 3rd clockwise… */
    fun clockwise(group: Int): Boolean = group % 2 == 0

    /** The opener of every pack, in order: [groups] × 6 packs. [firstTeam]: 1 or 2. */
    fun openers(firstTeam: Int, groups: Int): List<String> {
        require(firstTeam in 1..2) { "firstTeam $firstTeam" }
        val result = mutableListOf<String>()
        var start = "${firstTeam}B"
        for (group in 0 until groups) {
            val ring = ring(start, clockwise(group))
            result += ring
            start = ring.last()   // the last opener of a group opens first in the next
        }
        return result
    }

    /** Who picks each card of a pack opened by [opener]: [cards] seats, snaking around the table. */
    fun picks(opener: String, clockwise: Boolean, cards: Int): List<String> {
        val ring = ring(opener, clockwise)
        val order = mutableListOf<String>()
        var at = 0
        var forward = true
        while (order.size < cards) {
            order += ring[at]
            val next = if (forward) at + 1 else at - 1
            if (next !in ring.indices) forward = !forward   // the end player takes two in a row
            else at = next
        }
        return order
    }

    /** The six seats starting at [from], going clockwise or counterclockwise. */
    private fun ring(from: String, clockwise: Boolean): List<String> {
        val i = SEATS.indexOf(from)
        require(i >= 0) { "unknown seat $from" }
        val step = if (clockwise) 1 else -1
        return List(PLAYERS) { k -> SEATS[Math.floorMod(i + k * step, PLAYERS)] }
    }
}

/**
 * Team Rochester Draft (MTR 8.5 + Appendix B): for every pack, the review once it's laid out,
 * then one 5-second step per card, each naming who picks and waiting for "Picked"; then deck
 * construction. [DraftConfig.packs] is packs PER PLAYER: 3 → 18 packs at the table.
 */
object RochesterPlanFactory {

    fun create(config: DraftConfig, timings: RochesterTimings): DraftPlan {
        require(config.format == DraftFormat.ROCHESTER) { "not a Rochester draft: ${config.format}" }
        val openers = RochesterOrder.openers(config.firstTeam, groups = config.packs)
        val steps = buildList {
            openers.forEachIndexed { i, opener ->
                val pack = i + 1
                val clockwise = RochesterOrder.clockwise(i / RochesterOrder.PLAYERS)
                // Clockwise around the table = to the left, as in a booster draft.
                val direction = if (clockwise) PassDirection.LEFT else PassDirection.RIGHT
                add(DraftStep.LayOut(pack, opener, direction, timings.reviewSeconds * 1_000L))
                RochesterOrder.picks(opener, clockwise, config.cardsPerPack).forEachIndexed { j, seat ->
                    add(DraftStep.Pick(
                        pack = pack,
                        pick = j + 1,
                        cardsLeft = config.cardsPerPack - j,
                        direction = direction,
                        durationMs = timings.pickSeconds * 1_000L,
                        actor = seat,
                        waitsForPicked = true,
                    ))
                }
            }
            add(DraftStep.Construction(config.constructionMinutes * 60_000L))
        }
        return DraftPlan(steps)
    }
}
