package es.alejandro.grandarbiter.domain.customlists

import es.alejandro.grandarbiter.domain.model.customlists.CustomList
import es.alejandro.grandarbiter.domain.model.customlists.CustomListEntry
import es.alejandro.grandarbiter.domain.model.customlists.CustomListText
import es.alejandro.grandarbiter.domain.model.customlists.ListStatus
import es.alejandro.grandarbiter.domain.model.customlists.ListStatus.Banned
import es.alejandro.grandarbiter.domain.model.customlists.ListStatus.Points
import es.alejandro.grandarbiter.domain.model.customlists.ListStatus.Restricted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomListTextTest {

    private fun read(line: String) = CustomListText.parseLine(line)?.let { it.name to it.status }

    /** line → (name, status). Names here are only text: matching them to cards is another step. */
    private val table: List<Pair<String, Pair<String, ListStatus>>> = listOf(
        "3 Sol Ring" to ("Sol Ring" to Points(3)),
        "3 points Sol Ring" to ("Sol Ring" to Points(3)),
        "Sol Ring 3" to ("Sol Ring" to Points(3)),
        "Sol Ring: 3" to ("Sol Ring" to Points(3)),
        "Sol Ring - 3" to ("Sol Ring" to Points(3)),
        "Sol Ring (3)" to ("Sol Ring" to Points(3)),
        "Sol Ring (3 points)" to ("Sol Ring" to Points(3)),
        "Sol Ring 1 point" to ("Sol Ring" to Points(1)),
        "Sol Ring 2 pts" to ("Sol Ring" to Points(2)),
        "  - 3 Sol Ring  " to ("Sol Ring" to Points(3)),
        "• Sol Ring 3" to ("Sol Ring" to Points(3)),
        "Sol Ring banned" to ("Sol Ring" to Banned),
        "Sol Ring BANNED" to ("Sol Ring" to Banned),
        "Sol Ring - restricted" to ("Sol Ring" to Restricted),
        "Sol Ring (Restricted)" to ("Sol Ring" to Restricted),
        "Sol Ring" to ("Sol Ring" to Banned),
        "Fire // Ice 2" to ("Fire // Ice" to Points(2)),
        "99 Sol Ring" to ("Sol Ring" to Points(99)),
        // Out of range: the line is kept whole as a name, so it won't be found and stays to fix.
        "0 Sol Ring" to ("0 Sol Ring" to Banned),
        "Sol Ring 100" to ("Sol Ring 100" to Banned),
        "Sol Ring 123" to ("Sol Ring 123" to Banned),
        "100 Sol Ring" to ("100 Sol Ring" to Banned),
        // Numbers inside a name are not points.
        "1996 World Champion" to ("1996 World Champion" to Banned),
        "Borrowing 100,000 Arrows" to ("Borrowing 100,000 Arrows" to Banned),
    )

    @Test fun lines() {
        val wrong = table.mapNotNull { (line, expected) -> read(line).takeIf { it != expected }?.let { "$line → $it" } }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test fun skipped() {
        assertNull(CustomListText.parseLine(""))
        assertNull(CustomListText.parseLine("   "))
        assertNull(CustomListText.parseLine("# 7 Point Highlander"))
    }

    @Test fun parse_countsOnlyCardLines_andKeepsTheLine() {
        val lines = CustomListText.parse("# List\n\n3 Sol Ring\n Black Lotus restricted \n")
        assertEquals(2, lines.size)
        assertEquals("Black Lotus restricted", lines[1].line)
    }

    @Test fun format_thenParse_givesTheSameCards() {
        val list = CustomList(1, "7 Point Highlander", "Max 7 points\nUpdated 2026-11",
            listOf(
                CustomListEntry("b", "Black Lotus", Restricted),
                CustomListEntry("s", "Sol Ring", Points(3)),
                CustomListEntry("m", "Mana Crypt", Banned),
                CustomListEntry("f", "Fire // Ice", Points(1)),
            ))
        val text = CustomListText.format(list)
        assertEquals(
            "# 7 Point Highlander\n# Max 7 points\n# Updated 2026-11\n\n3 Sol Ring\n1 Fire // Ice\nMana Crypt banned\nBlack Lotus restricted\n",
            text,
        )
        assertEquals(list.sorted.map { it.name to it.status }, CustomListText.parse(text).map { it.name to it.status })
    }
}
