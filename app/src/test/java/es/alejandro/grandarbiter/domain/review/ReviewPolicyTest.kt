package es.alejandro.grandarbiter.domain.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPolicyTest {

    /** [n] lookups spread over [days] days starting on day 100, the last one on [lastDay]. */
    private fun used(n: Int, days: Int, lastDay: Long = 100L + days - 1): ReviewStats {
        var s = ReviewStats()
        repeat(n) { i -> s = ReviewPolicy.afterLookup(s, minOf(100L + i % days, lastDay)) }
        return s
    }

    @Test fun counts_lookupsAndDistinctDays() {
        var s = ReviewStats()
        s = ReviewPolicy.afterLookup(s, 100)
        s = ReviewPolicy.afterLookup(s, 100)
        s = ReviewPolicy.afterLookup(s, 102)
        assertEquals(ReviewStats(firstDay = 100, lookups = 3, daysUsed = 2, lastLookupDay = 102), s)
    }

    @Test fun neverBeforeAnyUse() = assertFalse(ReviewPolicy.shouldAsk(ReviewStats(), 500))

    @Test fun enoughUse_andAWeek_asks() {
        val s = ReviewStats(firstDay = 100, lookups = 10, daysUsed = 3, lastLookupDay = 106)
        assertTrue(ReviewPolicy.shouldAsk(s, 107))
    }

    @Test fun tooFewLookups_orDays_orTooSoon_doNot() {
        val ok = ReviewStats(firstDay = 100, lookups = 10, daysUsed = 3, lastLookupDay = 106)
        assertFalse(ReviewPolicy.shouldAsk(ok.copy(lookups = 9), 107))
        assertFalse(ReviewPolicy.shouldAsk(ok.copy(daysUsed = 2), 107))
        assertFalse(ReviewPolicy.shouldAsk(ok, 106))                     // 6 days since first use
    }

    @Test fun afterAsking_waitsDaysBetweenAsks() {
        val asked = ReviewStats(firstDay = 100, lookups = 40, daysUsed = 9, lastLookupDay = 200, askedDay = 150)
        val next = 150L + ReviewPolicy.DAYS_BETWEEN_ASKS
        assertFalse(ReviewPolicy.shouldAsk(asked, next - 1))
        assertTrue(ReviewPolicy.shouldAsk(asked, next))
    }

    @Test fun manyLookupsInOneDay_areOneDay() {
        val s = used(n = 30, days = 1)
        assertEquals(1, s.daysUsed)
        assertFalse(ReviewPolicy.shouldAsk(s, 120))
    }
}