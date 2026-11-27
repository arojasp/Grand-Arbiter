package es.alejandro.grandarbiter.ui.tools.drafttimer

import org.junit.Assert.assertEquals
import org.junit.Test

/** Countdowns round up (never 0:00 early); time spent rounds down (never ahead). */
class DraftClockTest {

    @Test fun remaining_roundsUp() {
        assertEquals("0:01", formatClock(1))
        assertEquals("0:40", formatClock(39_001))
    }

    /** The notification's system countdown truncates (base − now) to whole seconds. */
    private fun systemCountdown(base: Long, now: Long): String {
        val s = (base - now) / 1000
        return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60)
    }

    @Test fun notificationCountdown_showsWhatTheScreenShows() {
        val end = 1_000_000L
        for (remaining in 0L..125_000L) {
            val now = end - remaining
            assertEquals("$remaining ms left", formatClock(remaining), systemCountdown(countdownBase(end), now))
        }
    }

    @Test fun notificationCountdown_reachesZeroOnlyAtTheEnd() {
        val end = 1_000_000L
        assertEquals("0:01", systemCountdown(countdownBase(end), end - 1))
        assertEquals("0:00", systemCountdown(countdownBase(end), end))
        // Without the offset it showed 0:00 almost a second early.
        assertEquals("0:00", systemCountdown(end, end - 999))
    }

    @Test fun elapsed_roundsDown() {
        assertEquals("0:00", formatElapsed(999))
        assertEquals("0:47", formatElapsed(47_999))
        assertEquals("12:05", formatElapsed(725_000))
        assertEquals("0:00", formatElapsed(-5))
    }
}
