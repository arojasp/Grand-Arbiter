package es.alejandro.grandarbiter.ui.reader

import es.alejandro.grandarbiter.data.rules.bundle.Block
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.domain.model.rules.PageSection
import org.junit.Assert.assertEquals
import org.junit.Test

/** What lands in the clipboard: clean plain text a judge can paste in a chat or an appeal report. */
class RuleTextTest {

    private fun section(number: String?, title: String?, vararg blocks: Block) =
        PageSection("x/$number", number, title, 1, blocks.toList(), null)

    @Test fun crSubrule_withExample_noMarkupLeft() {
        val s = section("603.2a", null,
            Block.Paragraph("Some **bold** text, see rule [603.1](ref:cr/603.1)."),
            Block.Example("A player casts *Shock*."))
        assertEquals(
            "CR 603.2a\n\nSome bold text, see rule 603.1.\n\nExample: A player casts Shock.",
            RuleText.of(s, DocumentId.CR),
        )
    }

    @Test fun titledSection_andChapterNumbers() {
        assertEquals("MTR 6.1 Tardiness", RuleText.reference(section("6.1", "Tardiness"), DocumentId.MTR))
        assertEquals("CR 603.", RuleText.reference(section("603", null), DocumentId.CR))   // as printed in the reader
    }

    @Test fun ipgInfraction_includesThePenalty_andLabeledExamples() {
        val s = section("2.5", "Game Rule Violation",
            Block.Heading("Definition"),
            Block.Paragraph("This infraction covers…"),
            Block.Example("A player forgets a trigger.", label = "A"))
        assertEquals(
            "IPG 2.5 Game Rule Violation\nPenalty: Warning\n\nDefinition\n\nThis infraction covers…\n\nExample A: A player forgets a trigger.",
            RuleText.of(s, DocumentId.IPG, penalty = "Warning"),
        )
    }

    @Test fun listsAndTables() {
        assertEquals("1. Shuffle\n2. Present", RuleText.blockText(Block.BulletList(listOf("Shuffle", "**Present**"), ordered = true)))
        assertEquals("• a\n• b", RuleText.blockText(Block.BulletList(listOf("a", "b"))))
        assertEquals(
            "Players | Rounds\n9–16 | 4\n33–64 | 6 (or 5 + cut)",   // line breaks inside a cell become spaces
            RuleText.blockText(Block.Table(listOf("Players", "Rounds"), listOf(listOf("9–16", "4"), listOf("33–64", "6 (or 5 +\ncut)")))),
        )
    }

    @Test fun sectionWithoutBlocks_isJustTheReference() =
        assertEquals("CR 613. Interaction of Continuous Effects", RuleText.of(section("613", "Interaction of Continuous Effects"), DocumentId.CR))
}
