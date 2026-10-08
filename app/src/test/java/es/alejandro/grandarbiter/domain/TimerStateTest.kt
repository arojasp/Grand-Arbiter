package es.alejandro.grandarbiter.domain

import es.alejandro.grandarbiter.domain.timer.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The judge call timer's arithmetic: what extra time the judge will grant depends on it. */
class TimerStateTest {

    @Test fun runs_fromTheStartMoment() {
        val s = TimerState().started(now = 1_000)
        assertTrue(s.isRunning)
        assertEquals(90_000, s.elapsedAt(91_000))
    }

    @Test fun pause_keepsTheTime_andStopsCounting() {
        val paused = TimerState().started(1_000).paused(61_000)
        assertFalse(paused.isRunning)
        assertEquals(60_000, paused.elapsedAt(999_999))
    }

    @Test fun resume_addsToWhatWasAccumulated() {
        val s = TimerState().started(0).paused(30_000).started(100_000)
        assertEquals(45_000, s.elapsedAt(115_000))
    }

    @Test fun startTwice_doesNotRestart() {
        val s = TimerState().started(0)
        assertEquals(s, s.started(50_000))
    }

    @Test fun clockMovedBack_neverNegative() =
        assertEquals(5_000, TimerState(accumulatedMs = 5_000, runningSince = 10_000).elapsedAt(2_000))
}
