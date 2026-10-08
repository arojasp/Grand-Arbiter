package es.alejandro.grandarbiter.ui.tools

import es.alejandro.grandarbiter.data.cards.local.CardMapper
import es.alejandro.grandarbiter.data.cards.local.LegalityRow
import es.alejandro.grandarbiter.domain.model.cards.BanCategory
import es.alejandro.grandarbiter.domain.model.cards.Legality
import es.alejandro.grandarbiter.ui.tools.banned.BanListFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Placeholder card names only. */
class BanListsTest {

    private fun row(id: String, name: String, vararg statuses: Pair<String, String>) =
        LegalityRow(id, name, statuses.joinToString(";") { "${it.first}=${it.second}" })

    private val rows = listOf(
        row("1", "Zeta Card", "modern" to "banned", "legacy" to "legal"),
        row("2", "Alpha Card", "vintage" to "restricted", "legacy" to "banned"),
        row("3", "Jötun Example", "modern" to "banned", "oathbreaker" to "banned"),
    )

    @Test
    fun banLists_groupByFormat_sortByName_officialFirst() {
        val lists = CardMapper.banLists(rows)
        val modern = lists.first { it.format == "modern" }
        assertEquals(listOf("Jötun Example", "Zeta Card"), modern.entries.map { it.name })
        assertEquals(Legality.RESTRICTED, lists.first { it.format == "vintage" }.entries.single().legality)
        assertTrue(lists.first { it.format == "standard" }.entries.isEmpty())      // empty formats still listed
        val firstCommunity = lists.indexOfFirst { !it.official }
        assertTrue(lists.drop(firstCommunity).none { it.official })               // official block, then community
        assertEquals("oathbreaker", lists[firstCommunity].format)
    }

    @Test
    fun filter_cardNameMatches_openTheirFormats_accentInsensitive() {
        val views = BanListFilter.apply(CardMapper.banLists(rows), "jotun")
        assertEquals(setOf("modern", "oathbreaker"), views.map { it.list.format }.toSet())
        assertTrue(views.all { it.autoExpand && it.matches == setOf("3") })
    }

    @Test
    fun filter_formatName_showsTheFormatClosed() {
        val views = BanListFilter.apply(CardMapper.banLists(rows), "duel")
        assertEquals("duel", views.single().list.format)
        assertTrue(!views.single().autoExpand)
    }

    @Test
    fun filter_blank_showsEverything() =
        assertEquals(CardMapper.banLists(rows).size, BanListFilter.apply(CardMapper.banLists(rows), "  ").size)

    @Test
    fun categories_areSeparatedFromNamedBans() {
        val rows = listOf(
            LegalityRow("n", "Real Ban", "vintage=banned"),
            LegalityRow("u", "Sticker Thing", "vintage=banned", setType = "funny"),
            LegalityRow("c", "Some Plot", "vintage=banned", typeLine = "Conspiracy"),
            LegalityRow("a", "Gamble", "vintage=banned", oracleText = "Remove this card from your deck before playing if you're not playing for ante."),
            LegalityRow("x", "Antenna Thing", "vintage=banned", oracleText = "Its antennae twitch."),   // "ante" only as a whole word
            LegalityRow("r", "Restricted One", "vintage=restricted"),
        )
        val vintage = CardMapper.banLists(rows).first { it.format == "vintage" }
        assertEquals(listOf("Antenna Thing", "Real Ban", "Restricted One"), vintage.named.map { it.name })
        assertEquals(
            mapOf("u" to BanCategory.UN_CARD, "c" to BanCategory.CONSPIRACY, "a" to BanCategory.ANTE),
            vintage.byCategory.associate { it.oracleId to it.category },
        )
    }

    @Test
    fun searchMatchInCategory_opensTheCategoryBlock() {
        val rows = listOf(LegalityRow("u", "Sticker Thing", "legacy=banned", setType = "funny"), LegalityRow("n", "Real Ban", "legacy=banned"))
        val view = BanListFilter.apply(CardMapper.banLists(rows), "sticker").single()
        assertTrue(view.autoExpand && view.autoExpandCategories)
    }
}
