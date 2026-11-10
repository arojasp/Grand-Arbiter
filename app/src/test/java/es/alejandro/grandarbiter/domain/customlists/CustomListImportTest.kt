package es.alejandro.grandarbiter.domain.customlists

import es.alejandro.grandarbiter.domain.model.cards.CardSummary
import es.alejandro.grandarbiter.domain.model.customlists.CustomListEntry
import es.alejandro.grandarbiter.domain.model.customlists.CustomListImport
import es.alejandro.grandarbiter.domain.model.customlists.ListStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomListImportTest {

    /** A tiny card pool; lookup like the DAO: any case, or the first face of a split name. */
    private val pool = listOf("Sol Ring", "Fire // Ice", "Sol'Kanar the Tainted", "Number 7")
        .map { CardSummary(it.lowercase(), it, null, null, isToken = false) }

    private suspend fun find(name: String): CardSummary? =
        pool.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: pool.firstOrNull { it.name.startsWith("$name // ", ignoreCase = true) }

    @Test fun found_notFound_andTheLinesKept() = runTest {
        val r = CustomListImport.resolve("# 7PH\n3 Sol Ring\nfire 2\nMisspelled Card 1\n  - Unknown banned\n", ::find)
        assertEquals(
            listOf(CustomListEntry("sol ring", "Sol Ring", ListStatus.Points(3)), CustomListEntry("fire // ice", "Fire // Ice", ListStatus.Points(2))),
            r.found,
        )
        assertEquals(listOf("Misspelled Card 1", "- Unknown banned"), r.notFound)
    }

    @Test fun typographicApostrophe() = runTest {
        val r = CustomListImport.resolve("Sol’Kanar the Tainted restricted", ::find)
        assertEquals(listOf(ListStatus.Restricted), r.found.map { it.status })
    }

    @Test fun nameEndingInANumber_triesTheWholeLine() = runTest {
        val r = CustomListImport.resolve("Number 7\n2 Number 7", ::find)
        // "Number 7" alone: not "Number" with 7 points, but the card, banned. The second line wins.
        assertEquals(listOf(CustomListEntry("number 7", "Number 7", ListStatus.Points(2))), r.found)
        assertEquals(ListStatus.Banned, CustomListImport.resolve("Number 7", ::find).found.single().status)
    }

    @Test fun sameCardTwice_lastLineWins() = runTest {
        val r = CustomListImport.resolve("Sol Ring banned\n2 sol ring", ::find)
        assertEquals(listOf(ListStatus.Points(2)), r.found.map { it.status })
    }
}
