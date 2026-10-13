package es.alejandro.grandarbiter.domain.draft

import es.alejandro.grandarbiter.domain.draft.DraftPosition.Finished
import es.alejandro.grandarbiter.domain.draft.DraftPosition.Running
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which step is on screen at a given elapsed time, especially at the boundaries. */
class DraftPlanTest {

    // 10 s pick, 5 s review, 60 s construction.
    private val plan = DraftPlan(listOf(
        DraftStep.Pick(1, 1, 2, PassDirection.LEFT, 10_000),
        DraftStep.Review(1, PassDirection.RIGHT, 5_000),
        DraftStep.Construction(60_000),
    ))

    private fun running(elapsed: Long) = plan.positionAt(elapsed) as Running

    @Test fun start_isTheFirstStep_withItsFullTime() {
        val p = running(0)
        assertEquals(0, p.index)
        assertEquals(10_000, p.remainingMs)
    }

    @Test fun lastMillisecondOfAStep_isStillThatStep() {
        val p = running(9_999)
        assertEquals(0, p.index)
        assertEquals(1, p.remainingMs)
    }

    @Test fun exactBoundary_isTheNextStep() {
        val p = running(10_000)
        assertEquals(1, p.index)
        assertEquals(0, p.elapsedInStepMs)
        assertEquals(5_000, p.remainingMs)
    }

    @Test fun middleOfConstruction() {
        val p = running(45_000)
        assertEquals(2, p.index)
        assertEquals(30_000, p.elapsedInStepMs)
        assertEquals(30_000, p.remainingMs)
    }

    @Test fun atTheTotal_andBeyond_isFinished() {
        assertEquals(75_000, plan.totalMs)
        assertEquals(Finished, plan.positionAt(75_000))
        assertEquals(Finished, plan.positionAt(10_000_000))
    }

    @Test fun negativeElapsed_isTheStart() = assertEquals(0, running(-500).index)

    @Test fun stepEnds_areTheAlertMoments() = assertEquals(listOf(10_000L, 15_000L, 75_000L), plan.stepEnds)

    @Test fun startOf_isClamped() {
        assertEquals(0, plan.startOf(-1))
        assertEquals(15_000, plan.startOf(2))
        assertEquals(75_000, plan.startOf(99))
    }

    @Test fun realDraft_everyStepIsFoundAtItsStart() {
        val real = BoosterDraftPlanFactory.create(DraftConfig(), BoosterDraftTimings.FALLBACK)
        real.steps.indices.forEach { i ->
            assertEquals(i, (real.positionAt(real.startOf(i)) as Running).index)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyPlan_isRejected() { DraftPlan(emptyList()) }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDurationStep_isRejected() { DraftPlan(listOf(DraftStep.Construction(0))) }
}
