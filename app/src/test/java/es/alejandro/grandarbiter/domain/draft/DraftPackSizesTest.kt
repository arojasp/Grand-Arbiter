package es.alejandro.grandarbiter.domain.draft

import es.alejandro.grandarbiter.domain.draft.DraftStep.Pick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Drafts mixing sets: every player opens the same kind of pack at the same time, but each pack
 * round can have its own size (e.g. a 15-card pack, then two 14-card Play Boosters).
 */
class DraftPackSizesTest {

    private fun plan(config: DraftConfig) = DraftPlans.create(config, DraftTimings.FALLBACK)
    private fun picks(config: DraftConfig, pack: Int) = plan(config).steps.filterIsInstance<Pick>().filter { it.pack == pack }

    private val mixed = DraftConfig(packs = 3, cardsPerPack = 14, packSizes = listOf(15, 14, 14))

    @Test fun noSizes_everyPackHasCardsPerPack() {
        val c = DraftConfig(cardsPerPack = 15)
        assertNull(c.packSizes)
        assertEquals(listOf(15, 15, 15), (1..3).map(c::cardsInPack))
    }

    @Test fun booster_eachPackFollowsItsOwnSize() {
        val first = picks(mixed, 1)
        assertEquals(14, first.size)                                  // 15 cards: 14 timed picks
        assertEquals((15 downTo 2).toList(), first.map { it.cardsLeft })
        assertEquals(40_000L, first[1].durationMs)                     // pick 2 of a 15-card pack: 14 cards, 40 s
        val second = picks(mixed, 2)
        assertEquals(13, second.size)                                  // 14 cards: 13 timed picks
        assertEquals((1..13).toList(), second.map { it.pick })
        assertEquals(35_000L, second[1].durationMs)                    // pick 2 of a 14-card pack: 13 cards, 35 s
    }

    @Test fun booster_total_isOnePackOf15PlusTwoOf14() {
        // 15-card pack 285 s, 14-card pack 245 s, reviews 60 + 90 s, construction 25 min.
        assertEquals((285 + 245 + 245 + 60 + 90) * 1_000L + 25 * 60_000L, plan(mixed).totalMs)
    }

    @Test fun sameSizes_sameAsWithout() =
        assertEquals(plan(DraftConfig(cardsPerPack = 14)).steps, plan(mixed.copy(packSizes = listOf(14, 14, 14))).steps)

    @Test fun estimate_picksByPack() {
        val e = DraftEstimate.of(plan(mixed))
        assertEquals(listOf(14, 13, 13), e.timedPicksByPack)
        assertEquals((285 + 245 + 245) * 1_000L, e.picksMs)
    }

    @Test fun twoHeadedGiant_eachPackUsesItsSizeColumn() {
        val c = DraftConfig.defaultsFor(DraftFormat.TWO_HEADED_GIANT).copy(packs = 2, packSizes = listOf(15, 14))
        val p15 = picks(c, 1)
        val p14 = picks(c, 2)
        assertEquals(15, p15.first().cardsLeft)
        assertEquals(14, p14.first().cardsLeft)
        assertEquals(listOf(15, 13, 11, 9, 7, 5, 3), p15.map { it.cardsLeft })
        assertEquals(listOf(14, 12, 10, 8, 6, 4), p14.map { it.cardsLeft })
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongNumberOfSizes_rejected() { mixed.copy(packSizes = listOf(15, 14)) }

    @Test(expected = IllegalArgumentException::class)
    fun sizeOutOfRange_rejected() { mixed.copy(packSizes = listOf(15, 14, 16)) }

    @Test(expected = IllegalArgumentException::class)
    fun twoHeadedGiant_onlyTheSizesWithTimes() {
        DraftConfig.defaultsFor(DraftFormat.TWO_HEADED_GIANT).copy(packs = 2, packSizes = listOf(15, 13))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rochester_notSupported() {
        DraftConfig.defaultsFor(DraftFormat.ROCHESTER).copy(packs = 2, packSizes = listOf(15, 14))
    }
}
