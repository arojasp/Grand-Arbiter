package es.alejandro.grandarbiter.data.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesSearchQueryTest {

    @Test fun numbersAreRecognised() {
        listOf("603", "603.2", "603.2a", "2.5", "4.8.", " 100.1 ").forEach { assertTrue(it, RulesSearchQuery.looksLikeNumber(it)) }
        listOf("missed trigger", "603 trigger", "a.1", "").forEach { assertFalse(it, RulesSearchQuery.looksLikeNumber(it)) }
    }

    @Test fun numberMatching() {
        assertTrue(RulesSearchQuery.numberMatches("603", "603"))
        assertTrue(RulesSearchQuery.numberMatches("603", "603.2a"))
        assertFalse(RulesSearchQuery.numberMatches("60", "603"))
        assertTrue(RulesSearchQuery.numberMatches("603.2", "603.2a"))
        assertFalse(RulesSearchQuery.numberMatches("603.2", "603.20"))
        assertTrue(RulesSearchQuery.numberMatches("2.1", "2.1"))
        assertFalse(RulesSearchQuery.numberMatches("2.1", "2.10"))
        assertFalse(RulesSearchQuery.numberMatches("2.1", null))
    }

    @Test fun ftsQuery() {
        assertEquals("missed* trig*", RulesSearchQuery.toFts("Missed trig"))
        assertEquals("game* rule* violation*", RulesSearchQuery.toFts("game-rule \"violation\""))   // quotes/operators can't break the query
        assertNull(RulesSearchQuery.toFts("  ?! "))
    }

    @Test fun normalizedNumber() = assertEquals("4.8", RulesSearchQuery.normalizedNumber(" 4.8. "))
}
