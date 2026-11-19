package es.alejandro.grandarbiter.domain.draft

import es.alejandro.grandarbiter.domain.draft.DraftStep.Pick
import es.alejandro.grandarbiter.domain.draft.DraftStep.Review
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ready-made times: MTR and Pro Tour. */
class DraftTimesPresetTest {

    private val booster = BoosterDraftTimings.FALLBACK
    private val rows14 = DraftTimeRows.booster(14, booster)
    private val rows15 = DraftTimeRows.booster(15, booster)
    private val proTour = DraftTimesPreset.PRO_TOUR.times

    private fun plan(config: DraftConfig) = DraftPlans.create(config, DraftTimings.FALLBACK)

    /** The Pro Tour table by cards left in a 14-card pack; 1 card has no time. */
    private val proTourByCardsLeft = mapOf(
        14 to 50, 13 to 45, 12 to 40, 11 to 35, 10 to 30, 9 to 25,
        8 to 20, 7 to 15, 6 to 10, 5 to 10, 4 to 5, 3 to 5, 2 to 5,
    )

    @Test fun proTour_14CardPack_matchesTheTable() {
        assertEquals(proTourByCardsLeft, rows14.associate { it.cardsLeft to proTour.secondsFor(it) })
    }

    @Test fun proTour_changesOnlyTheFirstSixPicks() {
        assertEquals((1..6).toList(), rows14.filter(proTour::isChanged).map { it.pick })
        assertEquals(50, rows14.sumOf { proTour.secondsFor(it) - it.mtrSeconds })   // +50 s per pack
    }

    @Test fun proTour_leavesOtherSizesAndReviewsAsMtr() {
        assertEquals(rows15.map { it.mtrSeconds }, rows15.map(proTour::secondsFor))
        assertEquals(60, proTour.reviewSecondsAfter(1, 60))
        assertTrue(proTour.timedPicks)
        assertFalse(proTour.isMtr)
    }

    @Test fun proTour_inTheBoosterPlan() {
        val mtr = plan(DraftConfig())
        val pt = plan(DraftConfig(times = proTour))
        val firstPack = pt.steps.filterIsInstance<Pick>().filter { it.pack == 1 }
        assertEquals(listOf(50_000L, 45_000L, 40_000L, 35_000L, 30_000L, 25_000L, 20_000L), firstPack.take(7).map { it.durationMs })
        assertEquals(mtr.steps.filterIsInstance<Review>(), pt.steps.filterIsInstance<Review>())
        assertEquals(DraftEstimate.of(mtr).picksMs + 3 * 50_000L, DraftEstimate.of(pt).picksMs)
    }

    @Test fun matching_onlyWhileUntouched() {
        assertEquals(DraftTimesPreset.MTR, DraftTimesPreset.matching(DraftTimes.MTR))
        assertEquals(DraftTimesPreset.PRO_TOUR, DraftTimesPreset.matching(proTour))
        assertNull(DraftTimesPreset.matching(proTour.withPick(rows14.first(), 55)))      // edited: custom
        assertNull(DraftTimesPreset.matching(proTour.withTimedPicks(false)))
        // Editing the row back to the preset's value makes it the preset again.
        assertEquals(DraftTimesPreset.PRO_TOUR, DraftTimesPreset.matching(proTour.withPick(rows14.first(), 55).withPick(rows14.first(), 50)))
    }

    @Test fun mtrToProTour_byHand_isTheSameAsThePreset() {
        val byHand = rows14.take(6).zip(listOf(50, 45, 40, 35, 30, 25)).fold(DraftTimes.MTR) { t, (row, s) -> t.withPick(row, s) }
        assertEquals(DraftTimesPreset.PRO_TOUR, DraftTimesPreset.matching(byHand))
    }

    @Test fun available_perFormat() {
        assertEquals(listOf(DraftTimesPreset.MTR, DraftTimesPreset.PRO_TOUR), DraftTimesPreset.availableFor(DraftFormat.BOOSTER))
        assertEquals(listOf(DraftTimesPreset.MTR), DraftTimesPreset.availableFor(DraftFormat.TWO_HEADED_GIANT))
        assertTrue(DraftTimesPreset.availableFor(DraftFormat.ROCHESTER).isEmpty())
    }

    @Test fun every_preset_isAllowedWhereItIsOffered() {
        for (format in DraftFormat.entries) for (preset in DraftTimesPreset.availableFor(format)) {
            DraftConfig.defaultsFor(format).copy(times = preset.times)   // throws if not allowed
        }
    }
}
