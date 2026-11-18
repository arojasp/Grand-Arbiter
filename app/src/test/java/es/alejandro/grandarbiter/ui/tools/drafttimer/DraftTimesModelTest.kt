package es.alejandro.grandarbiter.ui.tools.drafttimer

import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftTimeRows
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.DraftTimesPreset
import es.alejandro.grandarbiter.domain.draft.DraftTimings
import es.alejandro.grandarbiter.domain.draft.FormatTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun presets_boosterOffersMtrAndProTour_selectedWhileUntouched() {
        assertEquals(listOf(DraftTimesPreset.MTR, DraftTimesPreset.PRO_TOUR), booster(listOf(14, 14, 14)).presets)
        assertEquals(DraftTimesPreset.MTR, booster(listOf(14, 14, 14)).preset)
        val pt = booster(listOf(14, 14, 14), FormatTimes(DraftTimesPreset.PRO_TOUR.times))
        assertEquals(DraftTimesPreset.PRO_TOUR, pt.preset)
        assertEquals(6, pt.changedPicksInUse)
        val edited = pt.times.withPick(pt.rowsOnScreen.last(), 10)
        assertNull(booster(listOf(14, 14, 14), FormatTimes(edited)).preset)          // custom: nothing selected
    }

    @Test fun presets_proTourOnlyFor14CardPacks() {
        assertEquals(setOf(DraftTimesPreset.PRO_TOUR), booster(listOf(15, 15, 15)).presetsNotForThesePacks)
        assertTrue(booster(listOf(15, 14, 14)).presetsNotForThesePacks.isEmpty())
        assertTrue(booster(listOf(14, 14, 14)).presetsNotForThesePacks.isEmpty())
    }

    @Test fun presets_judgedOnThePacksInUse() {
        // Pro Tour kept, but 15-card packs: the draft runs as the MTR, like the setup says.
        assertEquals(DraftTimesPreset.MTR, booster(listOf(15, 15, 15), FormatTimes(DraftTimesPreset.PRO_TOUR.times)).preset)
        // Changes only to the 14-card list, 15-card packs in use: MTR too.
        val row14 = DraftTimeRows.booster(14, DraftTimings.FALLBACK.booster).last()
        assertEquals(DraftTimesPreset.MTR, booster(listOf(15, 15, 15), FormatTimes(DraftTimes.MTR.withPick(row14, 10))).preset)
        // Untimed picks are never a preset.
        assertNull(booster(listOf(14, 14, 14), FormatTimes(DraftTimes.MTR.withTimedPicks(false))).preset)
    }

    @Test fun presets_constructionDoesNotChangeWhichOneIsSelected() =
        assertEquals(DraftTimesPreset.MTR, booster(listOf(14, 14, 14), FormatTimes(constructionMinutes = 30)).preset)

    @Test fun presets_twoHeadedGiant_onlyMtr_noChoice() {
        val m = DraftTimesModel.of(DraftFormat.TWO_HEADED_GIANT, List(6) { 14 }, FormatTimes(), DraftTimings.FALLBACK, null)
        assertEquals(listOf(DraftTimesPreset.MTR), m.presets)
    }

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
