package es.alejandro.grandarbiter.domain.draft

import es.alejandro.grandarbiter.domain.draft.DraftPosition.Running
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Untimed picks and reviews: no countdown, the clock shows the time spent, and the draft only
 * moves on when the judge says so. Deck construction keeps its time.
 */
class DraftUntimedTest {

    // Untimed pick, untimed pick, untimed review, 60 s construction.
    private val plan = DraftPlan(listOf(
        DraftStep.Pick(1, 1, 3, PassDirection.LEFT, null),
        DraftStep.Pick(1, 2, 2, PassDirection.LEFT, null),
        DraftStep.Review(1, PassDirection.RIGHT, null),
        DraftStep.Construction(60_000),
    ))

    private fun session(manualStart: Boolean) = DraftSession(DraftConfig(manualStart = manualStart)).started(now = 0)

    @Test fun untimedStep_neverEnds_showsTheTimeSpent() {
        listOf(false, true).forEach { manual ->
            val p = session(manual).positionAt(plan, 3_600_000) as Running      // an hour later: same pick
            assertEquals(0, p.index)
            assertEquals(3_600_000L, p.elapsedInStepMs)
            assertNull(p.remainingMs)
        }
    }

    @Test fun next_movesOn_andTheTimeStartsAgain() {
        val s = session(false).next(plan, now = 40_000)
        val p = s.positionAt(plan, 45_000) as Running
        assertEquals(1, p.index)
        assertEquals(5_000L, p.elapsedInStepMs)
        assertNull(p.remainingMs)
    }

    @Test fun lastUntimedStep_thenConstructionRunsWithItsTime() {
        val atReview = session(false).next(plan, 10_000).next(plan, 20_000)
        assertEquals(2, (atReview.positionAt(plan, 500_000) as Running).index)   // the review waits for the judge
        val building = atReview.next(plan, 500_000)
        val p = building.positionAt(plan, 530_000) as Running
        assertEquals(3, p.index)
        assertEquals(30_000L, p.remainingMs)
    }

    @Test fun previous_restartsTheUntimedStep() {
        val s = session(false).next(plan, 10_000).previous(plan, 25_000)
        val p = s.positionAt(plan, 25_000) as Running
        assertEquals(0, p.index)
        assertEquals(0L, p.elapsedInStepMs)
    }

    @Test fun pause_freezesTheTimeSpent() {
        val s = session(false).paused(now = 7_000)
        assertEquals(7_000L, (s.positionAt(plan, 900_000) as Running).elapsedInStepMs)
    }

    @Test fun noAlertsWhileUntimed() {
        val s = session(false)
        assertFalse(DraftAlertPolicy.shouldAlert(plan, s.elapsedAt(plan, 59_500), s.elapsedAt(plan, 60_500), s.step))
    }

    @Test fun constructionAlertStillFires_fromItsStep() {
        val building = session(false).next(plan, 1_000).next(plan, 2_000).next(plan, 3_000)
        assertEquals(3, building.step)
        assertTrue(DraftAlertPolicy.shouldAlert(plan, 59_900, 60_100, building.step))
    }

    @Test fun estimate_untimedPicks() {
        val e = DraftEstimate.of(plan)
        assertTrue(e.untimedPicks)
        assertEquals(0L, e.picksMs)
        assertEquals(emptyList<Int>(), e.reviewSeconds)
        assertEquals(60_000L, e.totalMs)
        assertFalse(DraftEstimate.of(DraftPlans.create(DraftConfig(), DraftTimings.FALLBACK)).untimedPicks)
    }
}
