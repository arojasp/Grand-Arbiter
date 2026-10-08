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

    @Test fun search_cardNamesFirst_thenText() {
        val found = ReleaseNotesItems.build(doc, "ajani").map { ((it as NotesItem.Block).block as Card).name }
        assertEquals(listOf("Ajani Resolute", "Echo of Ajani"), found)   // both names match: original order
        val text = ReleaseNotesItems.build(doc, "standard")
        assertTrue(text.single() is NotesItem.Block)
    }

    @Test fun copyText_isPlain_withSource() {
        val card = doc.sections[1].blocks[0] as Card
        assertEquals(
            "Ajani Resolute\n{1}{W}\nLegendary Planeswalker — Ajani\n\n• You gain life that many times.\n\n— Reality Fracture Release Notes",
            ReleaseNotesItems.copyText(card, doc.name),
        )
    }
}
