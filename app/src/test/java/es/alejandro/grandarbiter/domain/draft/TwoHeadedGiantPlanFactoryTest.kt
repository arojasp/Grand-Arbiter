package es.alejandro.grandarbiter.domain.draft

import es.alejandro.grandarbiter.domain.draft.DraftStep.Construction
import es.alejandro.grandarbiter.domain.draft.DraftStep.Pick
import es.alejandro.grandarbiter.domain.draft.DraftStep.Review
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Two-Headed Giant Booster Draft at the table: MTR 9.6, 9.7 + Appendix B. */
class TwoHeadedGiantPlanFactoryTest {

    private val defaults = DraftConfig.defaultsFor(DraftFormat.TWO_HEADED_GIANT)

    private fun plan(config: DraftConfig = defaults) = DraftPlans.create(config, DraftTimings.FALLBACK)

    @Test fun defaults_sixPacksOf14_40Minutes() {
        assertEquals(6, defaults.packs)
        assertEquals(14, defaults.cardsPerPack)
        assertEquals(40, defaults.constructionMinutes)
    }

    @Test fun sixPacksOf14_totals64m30s() =
        assertEquals((64 * 60 + 30) * 1_000L, plan().totalMs)

    @Test fun sixPacksOf15_totals65m() =
        assertEquals(65 * 60_000L, plan(defaults.copy(cardsPerPack = 15)).totalMs)

    @Test fun fourteenCards_sixTimedPicksOfTwoCards_lastTwoUntimed() {
        val firstPack = plan().steps.filterIsInstance<Pick>().filter { it.pack == 1 }
        assertEquals((1..6).toList(), firstPack.map { it.pick })
        assertEquals(listOf(14, 12, 10, 8, 6, 4), firstPack.map { it.cardsLeft })
        assertEquals(listOf(50, 45, 40, 30, 20, 10).map { it * 1_000L }, firstPack.map { it.durationMs })
        assertTrue(firstPack.all { it.cardsTaken == 2 })
    }

    @Test fun fifteenCards_sevenTimedPicks_lastCardUntimed() {
        val firstPack = plan(defaults.copy(cardsPerPack = 15)).steps.filterIsInstance<Pick>().filter { it.pack == 1 }
        assertEquals(listOf(15, 13, 11, 9, 7, 5, 3), firstPack.map { it.cardsLeft })
        assertEquals(5_000L, firstPack.last().durationMs)
    }

    @Test fun directions_alternateLeftRight_sixTimes() {
        val firsts = plan().steps.filterIsInstance<Pick>().filter { it.pick == 1 }.map { it.direction }
        assertEquals(List(3) { listOf(PassDirection.LEFT, PassDirection.RIGHT) }.flatten(), firsts)
    }

    @Test fun reviews_fixed60s_betweenPacksOnly() {
        val steps = plan().steps
        assertEquals(List(5) { 60_000L }, steps.filterIsInstance<Review>().map { it.durationMs })
        assertTrue(steps[steps.lastIndex - 1] is Pick)
        assertEquals(Construction(40 * 60_000L), steps.last())
    }

    @Test fun estimate_twoCardsPerPick() {
        val e = DraftEstimate.of(plan())
        assertEquals(6, e.packs)
        assertEquals(6, e.timedPicksPerPack)
        assertEquals(2, e.cardsPerPick)
        assertEquals(19 * 60_000L + 30_000L, e.picksMs)
        assertEquals(List(5) { 60 }, e.reviewSeconds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun thirteenCardBoosters_rejected() { defaults.copy(cardsPerPack = 13) }

    @Test fun boosterDefaults_unchanged_andManualStartCarried() {
        assertEquals(DraftConfig(manualStart = false), DraftConfig.defaultsFor(DraftFormat.BOOSTER, manualStart = false))
        assertEquals(false, DraftConfig.defaultsFor(DraftFormat.TWO_HEADED_GIANT, manualStart = false).manualStart)
    }
}
