package es.alejandro.grandarbiter.domain.draft

import es.alejandro.grandarbiter.domain.draft.DraftPosition.Finished
import es.alejandro.grandarbiter.domain.draft.DraftPosition.Running
import es.alejandro.grandarbiter.domain.timer.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Running a draft: pause/resume, the hold-to-confirm jumps, leaving the app and coming back. */
class DraftSessionTest {

    // 10 s pick, 5 s review, 60 s construction.
    private val plan = DraftPlan(listOf(
        DraftStep.Pick(1, 1, 2, PassDirection.LEFT, 10_000),
        DraftStep.Review(1, PassDirection.RIGHT, 5_000),
        DraftStep.Construction(60_000),
    ))
    private val started = DraftSession(DraftConfig()).started(now = 1_000)

    private fun DraftSession.index(now: Long) = (positionAt(plan, now) as Running).index

    @Test fun runsFromTheStart() {
        assertEquals(0, started.index(1_000))
        assertEquals(1, started.index(11_000))     // pick ran out: review
        assertEquals(2, started.index(16_000))
    }

    @Test fun pause_freezesTheStep() {
        val paused = started.paused(now = 6_000)    // 5 s into the pick
        val p = paused.positionAt(plan, now = 999_000) as Running
        assertEquals(0, p.index)
        assertEquals(5_000, p.remainingMs)
    }

    @Test fun resume_continuesWhereItWas() {
        val s = started.paused(6_000).started(100_000)
        assertEquals(5_000, (s.positionAt(plan, 100_000) as Running).remainingMs)
    }

    @Test fun next_startsTheNextStepWithItsFullTime_andKeepsRunning() {
        val s = started.next(plan, now = 4_000)     // 3 s into the pick
        val p = s.positionAt(plan, 4_000) as Running
        assertEquals(1, p.index)
        assertEquals(5_000, p.remainingMs)
        assertTrue(s.isRunning)
        assertEquals(2_000, (s.positionAt(plan, 7_000) as Running).remainingMs)
    }

    @Test fun next_whilePaused_staysPaused() {
        val s = started.paused(4_000).next(plan, now = 50_000)
        assertFalse(s.isRunning)
        assertEquals(1, s.index(999_000))
    }

    @Test fun previous_goesToTheStartOfThePreviousStep() {
        val s = started.previous(plan, now = 13_000)   // in the review
        val p = s.positionAt(plan, 13_000) as Running
        assertEquals(0, p.index)
        assertEquals(10_000, p.remainingMs)
    }

    @Test fun previous_onTheFirstStep_restartsIt() {
        val s = started.previous(plan, now = 8_000)
        assertEquals(10_000, (s.positionAt(plan, 8_000) as Running).remainingMs)
    }

    @Test fun next_fromConstruction_finishes() {
        val s = started.next(plan, now = 30_000)
        assertEquals(Finished, s.positionAt(plan, 30_000))
    }

    @Test fun finished_staysFinished_untilPrevious() {
        val done = started.next(plan, 30_000)
        assertEquals(done, done.next(plan, 31_000))
        assertEquals(2, done.previous(plan, 31_000).index(31_000))
    }

    @Test fun leavingTheApp_theRightStepOnReturn() {
        // Persisted as config + TimerState only; rebuilt from those after process death.
        val restored = DraftSession(started.config, TimerState(started.timer.accumulatedMs, started.timer.runningSince))
        assertEquals(2, restored.index(1_000 + 40_000))
        assertEquals(Finished, restored.positionAt(plan, 1_000 + 75_000))
    }

    @Test fun clockMovedBack_neverBeforeTheStart() = assertEquals(0, started.index(0))
}
