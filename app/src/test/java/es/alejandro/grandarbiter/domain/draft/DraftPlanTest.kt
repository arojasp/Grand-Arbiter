package es.alejandro.grandarbiter.domain.draft

import es.alejandro.grandarbiter.domain.draft.DraftPosition.Finished
import es.alejandro.grandarbiter.domain.draft.DraftPosition.Running
import es.alejandro.grandarbiter.domain.timer.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which step is on screen at a given time from the start, especially at the boundaries. */
class DraftPlanTest {

    // 10 s pick, 5 s review, 60 s construction.
    private val plan = DraftPlan(listOf(
        DraftStep.Pick(1, 1, 2, PassDirection.LEFT, 10_000),
        DraftStep.Review(1, PassDirection.RIGHT, 5_000),
        DraftStep.Construction(60_000),
    ))

    /** A draft that runs on its own, [elapsed] ms after its start. */
    private fun DraftPlan.at(elapsed: Long) = DraftSession(DraftConfig(manualStart = false), TimerState(elapsed, null)).positionAt(this, 0)
    private fun running(elapsed: Long) = plan.at(elapsed) as Running

    @Test fun start_isTheFirstStep_withItsFullTime() {
        val p = running(0)
        assertEquals(0, p.index)
        assertEquals(10_000L, p.remainingMs)
    }

    @Test fun lastMillisecondOfAStep_isStillThatStep() {
        val p = running(9_999)
        assertEquals(0, p.index)
        assertEquals(1L, p.remainingMs)
    }

    @Test fun exactBoundary_isTheNextStep() {
        val p = running(10_000)
        assertEquals(1, p.index)
        assertEquals(0, p.elapsedInStepMs)
        assertEquals(5_000L, p.remainingMs)
    }

    @Test fun middleOfConstruction() {
        val p = running(45_000)
        assertEquals(2, p.index)
        assertEquals(30_000, p.elapsedInStepMs)
        assertEquals(30_000L, p.remainingMs)
    }

    @Test fun atTheTotal_andBeyond_isFinished() {
        assertEquals(75_000, plan.totalMs)
        assertEquals(Finished, plan.at(75_000))
        assertEquals(Finished, plan.at(10_000_000))
    }

    @Test fun negativeElapsed_isTheStart() = assertEquals(0, running(-500).index)

    @Test fun stepEnds_areTheAlertMoments() = assertEquals(listOf(10_000L, 15_000L, 75_000L), plan.endsFrom(0))

    @Test fun startOf_isClamped() {
        assertEquals(0, plan.startOf(-1))
        assertEquals(15_000, plan.startOf(2))
        assertEquals(75_000, plan.startOf(99))
    }

    @Test fun realDraft_everyStepIsFoundAtItsStart() {
        val real = BoosterDraftPlanFactory.create(DraftConfig(), BoosterDraftTimings.FALLBACK)
        real.steps.indices.forEach { i ->
            assertEquals(i, (real.at(real.startOf(i)) as Running).index)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyPlan_isRejected() { DraftPlan(emptyList()) }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDurationStep_isRejected() { DraftPlan(listOf(DraftStep.Construction(0))) }

    // ---- Untimed picks and reviews (durationMs null)

    private val untimed = DraftPlan(listOf(
        DraftStep.Pick(1, 1, 2, PassDirection.LEFT, null),
        DraftStep.Review(1, PassDirection.RIGHT, null),
        DraftStep.Construction(60_000),
    ))

    @Test fun untimed_countsZeroInTheTotal() {
        assertEquals(60_000, untimed.totalMs)
        assertEquals(0, untimed.startOf(2))
        assertTrue(untimed.hasUntimedSteps)
        assertFalse(plan.hasUntimedSteps)
    }

    @Test fun untimed_neverRunsOut_noAlertMoments() {
        assertEquals(emptyList<Long>(), untimed.endsFrom(0))
        assertEquals(listOf(60_000L), untimed.endsFrom(2))
    }
}
