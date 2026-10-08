package es.alejandro.grandarbiter.ui.tools

import es.alejandro.grandarbiter.data.releasenotes.ReleaseNotesBlock.Card
import es.alejandro.grandarbiter.data.releasenotes.ReleaseNotesBlock.Paragraph
import es.alejandro.grandarbiter.data.releasenotes.ReleaseNotesDocument
import es.alejandro.grandarbiter.data.releasenotes.ReleaseNotesSection
import es.alejandro.grandarbiter.ui.tools.releasenotes.NotesItem
import es.alejandro.grandarbiter.ui.tools.releasenotes.ReleaseNotesItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesItemsTest {

    private val doc = ReleaseNotesDocument(
        schemaVersion = 1, code = "FRA", name = "Reality Fracture", url = "u", lastModified = "2026-06-22",
        sections = listOf(
            ReleaseNotesSection("General Notes", listOf(Paragraph("Cards with the FRA set code are legal in Standard."))),
            ReleaseNotesSection("Reality Fracture Main Set Card-Specific Notes", listOf(
                Card("Ajani Resolute", listOf("{1}{W}", "Legendary Planeswalker — Ajani"), listOf("You gain life that many times.")),
                Card("Bloodline Recollector", listOf("{1}{B}"), emptyList()),
                Card("Echo of Ajani", listOf("{W}"), listOf("Copies Ajani's ability.")),
            )),
        ),
    )

    @Test fun noQuery_everySectionThenItsBlocks() {
        val items = ReleaseNotesItems.build(doc, "")
        assertEquals(6, items.size)
        assertEquals(listOf("General Notes", "Main Set Card-Specific Notes"),
            items.filterIsInstance<NotesItem.Section>().map { it.title })
    }

    @Test fun onlyCardSpecificSections_areCardLists() {
        val sections = ReleaseNotesItems.build(doc, "").filterIsInstance<NotesItem.Section>()
        assertEquals(listOf(false, true), sections.map { it.cardList })
        assertEquals(3, sections[1].cards)
    }

    @Test fun cardSpecificCards_areRows_otherBlocksStayFull() {
        val items = ReleaseNotesItems.build(doc, "")
        assertTrue(items.filterIsInstance<NotesItem.Block>().all { it.section == 0 })      // General Notes: full
        assertEquals(listOf("Ajani Resolute", "Bloodline Recollector", "Echo of Ajani"),
            items.filterIsInstance<NotesItem.CardRow>().map { it.name })
    }

    @Test fun accordion_showsHeadersAlways_andOnlyExpandedContent() {
        val items = ReleaseNotesItems.build(doc, "")
        assertEquals(2, ReleaseNotesItems.visible(items, emptySet()).size)          // both headers, nothing else
        val open = ReleaseNotesItems.visible(items, setOf(1))
        assertEquals(5, open.size)                                                    // 2 headers + 3 card rows
        assertTrue(open.drop(2).all { it is NotesItem.CardRow })
    }

    @Test fun search_cardNamesFirst_thenText() {
        val found = ReleaseNotesItems.build(doc, "ajani").map { (it as NotesItem.CardRow).name }
        assertEquals(listOf("Ajani Resolute", "Echo of Ajani"), found)   // both names match: original order
        val text = ReleaseNotesItems.build(doc, "standard")
        assertTrue(text.single() is NotesItem.Block)                     // General Notes paragraph, in full
    }

    @Test fun cardLookup_byPosition() {
        assertEquals("Bloodline Recollector", ReleaseNotesItems.card(doc, 1, 1)?.name)
        assertEquals(null, ReleaseNotesItems.card(doc, 0, 0))            // a paragraph, not a card
        assertEquals(null, ReleaseNotesItems.card(doc, 9, 0))            // document changed under the link
    }

    @Test fun copyText_isPlain_withSource() {
        val card = doc.sections[1].blocks[0] as Card
        assertEquals(
            "Ajani Resolute\n{1}{W}\nLegendary Planeswalker — Ajani\n\n• You gain life that many times.\n\n— Reality Fracture Release Notes",
            ReleaseNotesItems.copyText(card, doc.name),
        )
    }
}
