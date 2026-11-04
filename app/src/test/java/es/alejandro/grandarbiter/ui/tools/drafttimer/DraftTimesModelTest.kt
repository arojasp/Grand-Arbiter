package es.alejandro.grandarbiter.ui.tools.drafttimer

import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftTimeRows
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.DraftTimings
import es.alejandro.grandarbiter.domain.draft.FormatTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Times screen: the lists of the sizes in use, the reviews, construction and "Reset to MTR". */
class DraftTimesModelTest {

    private fun booster(sizes: List<Int>, stored: FormatTimes = FormatTimes(), last: Int? = null) =
        DraftTimesModel.of(DraftFormat.BOOSTER, sizes, stored, DraftTimings.FALLBACK, last)

    @Test fun oneSize_oneList() {
        val m = booster(listOf(14, 14, 14))
        assertEquals(listOf(14), m.lists.map { it.packSize })
        assertEquals(13, m.rowsOnScreen.size)
        assertTrue(m.isMtr)
    }

    @Test fun mixedSizes_largestFirst() =
        assertEquals(listOf(15, 14), booster(listOf(14, 15, 14)).lists.map { it.packSize })

    @Test fun boosterReviews_onePerPackBreak_withTheirMtr() {
        val m = booster(listOf(14, 14, 14, 14))
        assertEquals(listOf(1, 2, 3), m.reviews.map { it.pack })
        assertEquals(listOf(60, 90, 120), m.reviews.map { it.mtrSeconds })
    }

    @Test fun onePack_noReviews() = assertTrue(booster(listOf(14)).reviews.isEmpty())

    @Test fun twoHeadedGiant_everyReview_andTakeTwo() {
        val m = DraftTimesModel.of(DraftFormat.TWO_HEADED_GIANT, List(6) { 14 }, FormatTimes(), DraftTimings.FALLBACK, null)
        assertEquals(listOf(DraftTimes.EVERY_REVIEW), m.reviews.map { it.pack })
        assertEquals(2, m.cardsPerPick)
        assertEquals(40, m.constructionMinutes)
    }

    @Test fun construction_storedThenLastDraftThenMtr() {
        assertEquals(25, booster(listOf(14)).constructionMinutes)
        assertEquals(30, booster(listOf(14), last = 30).constructionMinutes)
        assertEquals(45, booster(listOf(14), FormatTimes(constructionMinutes = 45), last = 30).constructionMinutes)
    }

    @Test fun notMtr_whenAnythingChanged() {
        assertFalse(booster(listOf(14), last = 30).isMtr)                                        // construction
        assertFalse(booster(listOf(14), FormatTimes(DraftTimes.MTR.withTimedPicks(false))).isMtr)
        val review = booster(listOf(14, 14), FormatTimes(DraftTimes.MTR.withReview(1, 70, 60)))
        assertFalse(review.isMtr)
        assertTrue(review.reviews.single().isChanged)
    }

    @Test fun differsInUse_onlyCountsWhatThisDraftUses() {
        val rows15 = DraftTimeRows.booster(15, DraftTimings.FALLBACK.booster)
        val only15 = FormatTimes(DraftTimes.MTR.withPick(rows15.first(), 50))
        assertFalse(booster(listOf(14, 14, 14), only15).differsInUse)          // no 15-card pack: MTR in use
        assertFalse(booster(listOf(14, 14, 14), only15).isMtr)                  // but there is something to reset
        val mixed = booster(listOf(15, 14, 14), only15)
        assertTrue(mixed.differsInUse)
        assertEquals(1, mixed.changedPicksInUse)
        assertTrue(booster(listOf(14), FormatTimes(DraftTimes.MTR.withTimedPicks(false))).differsInUse)
    }
}
