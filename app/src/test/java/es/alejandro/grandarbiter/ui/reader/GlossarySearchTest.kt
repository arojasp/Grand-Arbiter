package es.alejandro.grandarbiter.ui.reader

import es.alejandro.grandarbiter.data.rules.bundle.GlossaryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class GlossarySearchTest {

    private val entries = listOf("Attacking Creature", "Double Strike", "First Strike", "Strive", "Trample")
        .map { GlossaryEntry(it, "def") }

    private fun search(q: String, limit: Int = Int.MAX_VALUE) = GlossarySearch.filter(entries, q, limit).map { it.term }

    @Test fun startsWithFirst_thenContains_caseInsensitive() =
        assertEquals(listOf("Strive", "Double Strike", "First Strike"), search("stri"))

    @Test fun blankShowsEverything() = assertEquals(entries.size, search("  ").size)

    @Test fun limit() = assertEquals(listOf("Strive"), search("stri", limit = 1))

    @Test fun noMatch() = assertEquals(emptyList<String>(), search("hexproof"))
}
