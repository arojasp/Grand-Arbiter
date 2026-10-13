package es.alejandro.grandarbiter.domain.draft

import es.alejandro.grandarbiter.domain.draft.DraftStep.Construction
import es.alejandro.grandarbiter.domain.draft.DraftStep.Pick
import es.alejandro.grandarbiter.domain.draft.DraftStep.Review
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The draft a judge will run at the table: every number here is from MTR 7.7 + Appendix B. */
class BoosterDraftPlanFactoryTest {

    private fun plan(config: DraftConfig = DraftConfig()) =
        BoosterDraftPlanFactory.create(config, BoosterDraftTimings.FALLBACK)

    @Test fun standardDraft_threePlayBoosters_totals39m45s() {
        val p = plan()
        assertEquals((39 * 60 + 45) * 1_000L, p.totalMs)
    }

    @Test fun fifteenCardPacks_total41m45s() {
        assertEquals((41 * 60 + 45) * 1_000L, plan(DraftConfig(cardsPerPack = 15)).totalMs)
    }

    @Test fun thirteenTimedPicksPerPlayBooster_theLastCardHasNoTimer() {
        val picks = plan().steps.filterIsInstance<Pick>()
        assertEquals(39, picks.size)
        for (pack in 1..3) {
            val inPack = picks.filter { it.pack == pack }
            assertEquals(13, inPack.size)
            assertEquals((1..13).toList(), inPack.map { it.pick })
            assertEquals((14 downTo 2).toList(), inPack.map { it.cardsLeft })
        }
    }

    @Test fun pickTimes_followTheTable() {
        val firstPack = plan().steps.filterIsInstance<Pick>().filter { it.pack == 1 }
        assertEquals(
            listOf(40, 35, 30, 25, 25, 20, 20, 15, 10, 10, 5, 5, 5).map { it * 1_000L },
            firstPack.map { it.durationMs },
        )
    }

    @Test fun fifteenCardPack_startsAtTheFifteenRow() {
        val first = plan(DraftConfig(cardsPerPack = 15)).steps.first() as Pick
        assertEquals(15, first.cardsLeft)
        assertEquals(40_000L, first.durationMs)
    }

    @Test fun directions_leftRightLeft() {
        val picks = plan().steps.filterIsInstance<Pick>()
        assertEquals(PassDirection.LEFT, picks.first { it.pack == 1 }.direction)
        assertEquals(PassDirection.RIGHT, picks.first { it.pack == 2 }.direction)
        assertEquals(PassDirection.LEFT, picks.first { it.pack == 3 }.direction)
    }

    @Test fun reviews_onlyBetweenPacks_60Then90() {
        val steps = plan().steps
        val reviews = steps.filterIsInstance<Review>()
        assertEquals(listOf(1, 2), reviews.map { it.afterPack })
        assertEquals(listOf(60_000L, 90_000L), reviews.map { it.durationMs })
        assertEquals(listOf(PassDirection.RIGHT, PassDirection.LEFT), reviews.map { it.nextDirection })
        // Right after the last pick of pack 1, and nothing between the last pick of pack 3 and construction.
        val lastPickPack1 = steps.indexOfLast { it is Pick && it.pack == 1 }
        assertTrue(steps[lastPickPack1 + 1] is Review)
        assertTrue(steps[steps.lastIndex - 1] is Pick)
    }

    @Test fun constructionIsLast_withTheConfiguredMinutes() {
        val last = plan(DraftConfig(constructionMinutes = 30)).steps.last()
        assertEquals(Construction(30 * 60_000L), last)
    }

    @Test fun onePack_noReview() {
        val steps = plan(DraftConfig(packs = 1)).steps
        assertTrue(steps.none { it is Review })
        assertEquals(14, steps.size)   // 13 picks + construction
    }

    @Test(expected = IllegalArgumentException::class)
    fun otherFormats_areRejected() {
        plan(DraftConfig(format = DraftFormat.ROCHESTER))
    }

    @Test(expected = IllegalArgumentException::class)
    fun config_rejectsSixteenCards() {
        DraftConfig(cardsPerPack = 16)
    }
}
