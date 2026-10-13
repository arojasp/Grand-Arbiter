package es.alejandro.grandarbiter.domain.draft

import es.alejandro.grandarbiter.domain.draft.DraftPosition.Finished
import es.alejandro.grandarbiter.domain.draft.DraftPosition.Running
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the running screen shows: current step, what comes next, totals. */
class DraftSnapshotTest {

    private val config = DraftConfig()
    private val plan = DraftPlans.create(config, BoosterDraftTimings.FALLBACK)
    private val session = DraftSession(config).started(now = 0)

    private fun at(ms: Long) = DraftSnapshot.of(session, plan, ms)

    @Test fun firstPick_nextIsSecondPick() {
        val s = at(0)
        val step = (s.position as Running).step as DraftStep.Pick
        assertEquals(1, step.pick)
        assertEquals(DraftStep.Pick(1, 2, 13, PassDirection.LEFT, 35_000), s.nextStep)
        assertTrue(s.isRunning)
    }

    @Test fun lastPickOfPack1_nextIsTheReview() {
        val lastPick = plan.steps.indexOfLast { it is DraftStep.Pick && it.pack == 1 }
        val s = at(plan.startOf(lastPick))
        assertEquals(DraftStep.Review(1, PassDirection.RIGHT, 60_000), s.nextStep)
    }

    @Test fun construction_hasNoNext() {
        val s = at(plan.startOf(plan.steps.lastIndex))
        assertTrue((s.position as Running).step is DraftStep.Construction)
        assertNull(s.nextStep)
    }

    @Test fun finished_elapsedCappedAtTheTotal() {
        val s = at(plan.totalMs + 60_000)
        assertEquals(Finished, s.position)
        assertNull(s.nextStep)
        assertEquals(plan.totalMs, s.elapsedMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun plans_onlyForAvailableFormats() {
        DraftPlans.create(DraftConfig(format = DraftFormat.TWO_HEADED_GIANT), BoosterDraftTimings.FALLBACK)
    }

    @Test fun onlyBoosterIsAvailable() =
        assertEquals(listOf(DraftFormat.BOOSTER), DraftFormat.entries.filter { it.available })
}
