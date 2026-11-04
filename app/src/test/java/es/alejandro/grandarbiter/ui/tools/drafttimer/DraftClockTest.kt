package es.alejandro.grandarbiter.ui.tools.drafttimer

import org.junit.Assert.assertEquals
import org.junit.Test

/** Countdowns round up (never 0:00 early); time spent rounds down (never ahead). */
class DraftClockTest {

    @Test fun remaining_roundsUp() {
        assertEquals("0:01", formatClock(1))
        assertEquals("0:40", formatClock(39_001))
    }

    @Test fun elapsed_roundsDown() {
        assertEquals("0:00", formatElapsed(999))
        assertEquals("0:47", formatElapsed(47_999))
        assertEquals("12:05", formatElapsed(725_000))
        assertEquals("0:00", formatElapsed(-5))
    }
}
