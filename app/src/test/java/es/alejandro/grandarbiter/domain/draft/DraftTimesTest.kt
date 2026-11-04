package es.alejandro.grandarbiter.domain.draft

import es.alejandro.grandarbiter.domain.draft.DraftStep.Pick
import es.alejandro.grandarbiter.domain.draft.DraftStep.Review
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The judge's own times: one list per pack size (by pick number), reviews, untimed picks. */
class DraftTimesTest {

    private val booster = BoosterDraftTimings.FALLBACK
    private val rows14 = DraftTimeRows.booster(14, booster)
    private val rows15 = DraftTimeRows.booster(15, booster)

    private fun plan(config: DraftConfig) = DraftPlans.create(config, DraftTimings.FALLBACK)
    private fun picks(config: DraftConfig, pack: Int) = plan(config).steps.filterIsInstance<Pick>().filter { it.pack == pack }

    // ---- Rows

    @Test fun boosterRows_byPickNumber_withTheMtrTime() {
        assertEquals(13, rows14.size)
        assertEquals(PickRow(14, 1, 14, 40), rows14.first())
        assertEquals(PickRow(14, 2, 13, 35), rows14[1])
        assertEquals(PickRow(15, 2, 14, 40), rows15[1])               // pick 2 of a 15-card pack: 14 cards
        assertEquals(PickRow(14, 13, 2, 5), rows14.last())
    }

    @Test fun twoHeadedGiantRows_twoCardsPerPick() {
        val rows = DraftTimeRows.twoHeadedGiant(14, TwoHeadedGiantTimings.FALLBACK)
        assertEquals(listOf(14, 12, 10, 8, 6, 4), rows.map { it.cardsLeft })
        assertEquals((1..6).toList(), rows.map { it.pick })
        assertEquals(50, rows.first().mtrSeconds)
    }

    // ---- Editing

    @Test fun mtr_byDefault() {
        assertTrue(DraftTimes.MTR.isMtr)
        assertEquals(DraftTimes.MTR, DraftConfig().times)
        assertEquals(40, DraftTimes.MTR.secondsFor(rows14.first()))
    }

    @Test fun withPick_onlyThatRowOfThatSize() {
        val t = DraftTimes.MTR.withPick(rows14.first(), 45)
        assertEquals(45, t.secondsFor(rows14.first()))
        assertEquals(40, t.secondsFor(rows15.first()))                // the 15-card list is its own
        assertTrue(t.isChanged(rows14.first()))
        assertFalse(t.isChanged(rows14[1]))
        assertFalse(t.isMtr)
    }

    @Test fun backToTheMtrValue_dropsTheChange() {
        val t = DraftTimes.MTR.withPick(rows14.first(), 45).withPick(rows14.first(), 40)
        assertEquals(DraftTimes.MTR, t)
        assertTrue(t.isMtr)
    }

    @Test fun anyValue_upTo999_atLeast1() {
        assertEquals(999, DraftTimes.MTR.withPick(rows14.first(), 5_000).secondsFor(rows14.first()))
        assertEquals(1, DraftTimes.MTR.withPick(rows14.first(), -3).secondsFor(rows14.first()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun outOfRange_rejected() { DraftTimes(pickSeconds = mapOf(14 to mapOf(1 to 1_000))) }

    @Test fun allPicks_plusFive_onTheListsGiven() {
        val t = DraftTimes.MTR.withAllPicks(5, rows14)
        assertEquals(rows14.map { it.mtrSeconds + 5 }, rows14.map(t::secondsFor))
        assertEquals(rows15.map { it.mtrSeconds }, rows15.map(t::secondsFor))   // not on screen: untouched
    }

    @Test fun allPicks_minusFive_clampedAt1() {
        val down = DraftTimes.MTR.withAllPicks(-5, rows14)
        assertEquals(1, down.secondsFor(rows14.last()))              // 5 s - 5 -> 1 s, never 0
        assertEquals(35, down.secondsFor(rows14.first()))
        // Clamped rows don't come back: +5 gives 6 s, not the MTR's 5 s ("Reset to MTR" does).
        assertEquals(6, down.withAllPicks(5, rows14).secondsFor(rows14.last()))
    }

    @Test fun reviews_perPack_orEvery() {
        val t = DraftTimes.MTR.withReview(2, 120, mtrSeconds = 90)
        assertEquals(60, t.reviewSecondsAfter(1, 60))
        assertEquals(120, t.reviewSecondsAfter(2, 90))
        val every = DraftTimes.MTR.withReview(DraftTimes.EVERY_REVIEW, 45, mtrSeconds = 60)
        assertEquals(45, every.reviewSecondsAfter(3, 60))
        assertEquals(DraftTimes.MTR, every.withReview(DraftTimes.EVERY_REVIEW, 60, mtrSeconds = 60))
    }

    // ---- In the plan

    @Test fun mtrTimes_sameDraftAsBefore() =
        assertEquals(plan(DraftConfig()).steps, plan(DraftConfig(times = DraftTimes.MTR)).steps)

    @Test fun mixedSizes_eachPackUsesItsSizesList() {
        val times = DraftTimes.MTR.withPick(rows15.first(), 50).withPick(rows14.first(), 45)
        val c = DraftConfig(packSizes = listOf(15, 14, 14), times = times)
        assertEquals(50_000L, picks(c, 1).first().durationMs)
        assertEquals(40_000L, picks(c, 1)[1].durationMs)              // pick 2 of pack 1 (14 cards): 15-card list, MTR
        assertEquals(45_000L, picks(c, 2).first().durationMs)
        assertEquals(45_000L, picks(c, 3).first().durationMs)
    }

    @Test fun customReview_inTheBoosterPlan() {
        val c = DraftConfig(times = DraftTimes.MTR.withReview(1, 30, mtrSeconds = 60))
        assertEquals(listOf(30_000L, 90_000L), plan(c).steps.filterIsInstance<Review>().map { it.durationMs })
    }

    @Test fun untimed_picksAndReviewsHaveNoTime_constructionKeepsIt() {
        val c = DraftConfig(constructionMinutes = 30, times = DraftTimes.MTR.withTimedPicks(false))
        val steps = plan(c).steps
        assertTrue(steps.filterIsInstance<Pick>().all { it.durationMs == null })
        assertTrue(steps.filterIsInstance<Review>().all { it.durationMs == null })
        assertEquals(30 * 60_000L, (steps.last() as DraftStep.Construction).durationMs)
        assertEquals(39, steps.count { it is Pick })                   // the same picks, just untimed
    }

    @Test fun twoHeadedGiant_customAndUntimed() {
        val base = DraftConfig.defaultsFor(DraftFormat.TWO_HEADED_GIANT)
        val row = DraftTimeRows.twoHeadedGiant(14, TwoHeadedGiantTimings.FALLBACK).first()
        val custom = plan(base.copy(times = DraftTimes.MTR.withPick(row, 55).withReview(DraftTimes.EVERY_REVIEW, 90, 60)))
        assertEquals(55_000L, custom.steps.filterIsInstance<Pick>().first().durationMs)
        assertTrue(custom.steps.filterIsInstance<Review>().all { it.durationMs == 90_000L })
        val untimed = plan(base.copy(times = DraftTimes.MTR.withTimedPicks(false)))
        assertNull(untimed.steps.filterIsInstance<Pick>().first().durationMs)
        assertTrue(DraftEstimate.of(untimed).untimedPicks)
    }

    @Test fun customEstimate_usesTheRealTimes() {
        val c = DraftConfig(times = DraftTimes.MTR.withAllPicks(5, rows14))   // 3 packs × 13 picks × +5 s
        assertEquals(DraftEstimate.of(plan(DraftConfig())).picksMs + 39 * 5_000L, DraftEstimate.of(plan(c)).picksMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rochester_noCustomTimes() {
        DraftConfig.defaultsFor(DraftFormat.ROCHESTER).copy(times = DraftTimes.MTR.withTimedPicks(false))
    }

    @Test fun construction_upTo999Minutes() = assertEquals(999, DraftConfig(constructionMinutes = 999).constructionMinutes)
}
