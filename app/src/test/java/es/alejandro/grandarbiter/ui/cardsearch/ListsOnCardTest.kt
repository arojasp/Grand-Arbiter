package es.alejandro.grandarbiter.ui.cardsearch

import es.alejandro.grandarbiter.domain.model.cards.FormatLegality
import es.alejandro.grandarbiter.domain.model.cards.Legality
import es.alejandro.grandarbiter.domain.model.customlists.CustomList
import es.alejandro.grandarbiter.domain.model.customlists.CustomListEntry
import es.alejandro.grandarbiter.domain.model.customlists.ListStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which custom lists the card detail shows: only those that have the card, in the lists' order. */
class ListsOnCardTest {

    private fun list(name: String, vararg entries: CustomListEntry) = CustomList(1, name, "", entries.toList())
    private fun entry(id: String, status: ListStatus) = CustomListEntry(id, id, status)

    @Test fun onlyListsWithTheCard_inOrder() {
        val lists = listOf(
            list("7 Point Highlander", entry("sol", ListStatus.Points(3)), entry("vault", ListStatus.Points(1))),
            list("Pauper cube", entry("vault", ListStatus.Banned)),
            list("Old School", entry("sol", ListStatus.Restricted)),
        )
        assertEquals(
            listOf(ListOnCard("7 Point Highlander", ListStatus.Points(3)), ListOnCard("Old School", ListStatus.Restricted)),
            CardDetailViewModel.listsWith("sol", lists),
        )
    }

    @Test fun hiddenCommunityFormats_leave_officialsNeverDo() {
        val all = listOf("standard", "commander", "oldschool", "predh").map { FormatLegality(it, Legality.LEGAL) }
        val shown = CardDetailViewModel.visibleLegalities(all, hidden = setOf("oldschool", "standard"))
        assertEquals(listOf("standard", "commander", "predh"), shown.map { it.format })
    }

    @Test fun noListHasIt_nothingToShow() {
        assertTrue(CardDetailViewModel.listsWith("bolt", listOf(list("Cube", entry("sol", ListStatus.Banned)))).isEmpty())
    }
}
