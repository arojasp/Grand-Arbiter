package es.alejandro.grandarbiter.domain.draft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One vibration when a step runs out; never a burst, never on a manual jump. */
class DraftAlertPolicyTest {

    // Step ends at 10 s, 15 s, 75 s.
    private val plan = DraftPlan(listOf(
        DraftStep.Pick(1, 1, 2, PassDirection.LEFT, 10_000),
        DraftStep.Review(1, PassDirection.RIGHT, 5_000),
        DraftStep.Construction(60_000),
    ))

    private fun alert(previous: Long?, now: Long) = DraftAlertPolicy.shouldAlert(plan, previous, now)

    @Test fun tickAcrossAStepEnd_alerts() = assertTrue(alert(9_900, 10_100))

    @Test fun tickLandingExactlyOnTheEnd_alerts() = assertTrue(alert(9_900, 10_000))

    @Test fun tickInsideAStep_doesNot() = assertFalse(alert(10_100, 10_300))

    @Test fun theSameEnd_alertsOnlyOnce() {
        assertTrue(alert(9_900, 10_100))
        assertFalse(alert(10_100, 10_300))
    }

    @Test fun endOfConstruction_alerts() = assertTrue(alert(74_900, 75_100))

    @Test fun backFromBackground_afterMinutes_noBurst() = assertFalse(alert(1_000, 70_000))

    @Test fun backFromBackground_justAfterAnEnd_alertsOnce() = assertTrue(alert(1_000, 15_500))

    @Test fun manualJump_previousIsNull_noAlert() = assertFalse(alert(null, 15_000))

    @Test fun pausedOrClockBack_noAlert() {
        assertFalse(alert(10_100, 10_100))
        assertFalse(alert(10_100, 9_000))
    }

    @Test fun windowEdge() {
        assertTrue(alert(9_000, 10_000 + DraftAlertPolicy.WINDOW_MS))
        assertFalse(alert(9_000, 10_000 + DraftAlertPolicy.WINDOW_MS + 1))
    }

    // ---- The session's time counts from the start of its step.

    @Test fun endsFrom_countFromThatStep() {
        assertEquals(listOf(10_000L, 15_000L, 75_000L), plan.endsFrom(0))
        assertEquals(listOf(5_000L, 65_000L), plan.endsFrom(1))
        assertEquals(emptyList<Long>(), plan.endsFrom(3))
    }

    @Test fun fromAStep_alertsAtThatStepsEnds() {
        assertTrue(DraftAlertPolicy.shouldAlert(plan, 4_900, 5_100, fromStep = 1))      // review ran out
        assertFalse(DraftAlertPolicy.shouldAlert(plan, 9_900, 10_100, fromStep = 1))    // not a step end from there
    }
}
