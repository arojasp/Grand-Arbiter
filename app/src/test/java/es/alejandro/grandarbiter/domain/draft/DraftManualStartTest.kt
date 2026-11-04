package es.alejandro.grandarbiter.domain.draft

import es.alejandro.grandarbiter.domain.draft.DraftPosition.Finished
import es.alejandro.grandarbiter.domain.draft.DraftPosition.Running
import es.alejandro.grandarbiter.domain.draft.DraftPosition.Waiting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Manual start": at 0:00 the timer stops until the judge starts the next step. */
class DraftManualStartTest {

    // 10 s pick, 5 s review, 60 s construction.
    private val plan = DraftPlan(listOf(
        DraftStep.Pick(1, 1, 2, PassDirection.LEFT, 10_000),
        DraftStep.Review(1, PassDirection.RIGHT, 5_000),
        DraftStep.Construction(60_000),
    ))
    private val started = DraftSession(DraftConfig(manualStart = true)).started(now = 0)

    @Test fun isTheFirstTimeDefault() = assertTrue(DraftConfig().manualStart)

    @Test fun stepRunsNormally() {
        val p = started.positionAt(plan, 4_000) as Running
        assertEquals(0, p.index)
        assertEquals(6_000L, p.remainingMs)
    }

    @Test fun atZero_waits_andStaysThere() {
        assertEquals(Waiting(0, plan.steps[0]), started.positionAt(plan, 10_000))
        assertEquals(Waiting(0, plan.steps[0]), started.positionAt(plan, 600_000))   // minutes later: still waiting
        assertEquals(10_000, started.elapsedAt(plan, 600_000))                        // the clock stopped at the end
    }

    @Test fun startNext_startsTheNextStepWithItsFullTime() {
        val s = started.startNext(plan, now = 30_000)          // judge starts 20 s after 0:00
        val p = s.positionAt(plan, 30_000) as Running
        assertEquals(1, p.index)
        assertEquals(5_000L, p.remainingMs)
        assertEquals(2_000L, (s.positionAt(plan, 33_000) as Running).remainingMs)
        assertEquals(Waiting(1, plan.steps[1]), s.positionAt(plan, 35_000))
    }

    @Test fun startNext_whilePaused_starts() {
        val s = started.paused(10_000).startNext(plan, now = 50_000)
        assertTrue(s.isRunning)
        assertEquals(1, (s.positionAt(plan, 50_000) as Running).index)
    }

    @Test fun lastStepRunsOut_finished() {
        val construction = started.startNext(plan, 10_000).startNext(plan, 15_000)
        assertEquals(2, (construction.positionAt(plan, 15_000) as Running).index)
        assertEquals(Finished, construction.positionAt(plan, 75_000))
        assertEquals(Finished, construction.positionAt(plan, 999_000))
    }

    @Test fun holdButtons_stillWork() {
        val next = started.next(plan, 3_000)                     // skip the running pick
        assertEquals(1, (next.positionAt(plan, 3_000) as Running).index)
        assertEquals(Waiting(1, plan.steps[1]), next.positionAt(plan, 8_000))
        val back = next.previous(plan, 4_000)
        assertEquals(10_000L, (back.positionAt(plan, 4_000) as Running).remainingMs)
    }

    @Test fun previousWhileWaiting_restartsTheStepThatRanOut() {
        val s = started.previous(plan, 20_000)
        val p = s.positionAt(plan, 20_000) as Running
        assertEquals(0, p.index)
        assertEquals(10_000L, p.remainingMs)
    }

    @Test fun alert_onceWhenTheClockStops() {
        val before = started.elapsedAt(plan, 9_500)
        val atZero = started.elapsedAt(plan, 10_400)
        val later = started.elapsedAt(plan, 11_400)
        assertTrue(DraftAlertPolicy.shouldAlert(plan, before, atZero))
        assertFalse(DraftAlertPolicy.shouldAlert(plan, atZero, later))
    }

    @Test fun pauseOnAWaitingDraft_changesNothing() =
        assertEquals(Waiting(0, plan.steps[0]), started.paused(20_000).positionAt(plan, 90_000))
}
