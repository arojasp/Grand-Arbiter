package es.alejandro.grandarbiter.ui.reader

import es.alejandro.grandarbiter.data.rules.bundle.Block
import es.alejandro.grandarbiter.data.rules.bundle.HeadingRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagePartsTest {

    private fun h(role: HeadingRole, text: String) = Block.Heading(text, role)
    private fun p(text: String) = Block.Paragraph(text)

    @Test fun ipgInfractionIsSplitIntoParts() {
        val parts = groupIntoParts(listOf(
            h(HeadingRole.DEFINITION, "Definition"), p("d1"), p("d2"),
            h(HeadingRole.EXAMPLES, "Examples"), Block.Example("e1", "A"), Block.Example("e2", "B"),
            h(HeadingRole.PHILOSOPHY, "Philosophy"), p("ph"),
            h(HeadingRole.ADDITIONAL_REMEDY, "Additional Remedy"), p("ar"),
            h(HeadingRole.UPGRADE, "Upgrade"), p("u1"), p("u2"),
        ))
        assertEquals(
            listOf(HeadingRole.DEFINITION, HeadingRole.EXAMPLES, HeadingRole.PHILOSOPHY,
                HeadingRole.ADDITIONAL_REMEDY, HeadingRole.UPGRADE),
            parts.map { it.role },
        )
        assertEquals(2, parts[1].exampleCount)
        assertEquals(2, parts[4].blocks.size)
        assertEquals(listOf(true, false, false, true, false), parts.map { it.expandedByDefault })
    }

    @Test fun textBeforeTheFirstPartIsAnOpenIntro() {
        val parts = groupIntoParts(listOf(p("intro"), h(HeadingRole.DEFINITION, "Definition"), p("d")))
        assertEquals(listOf(null, HeadingRole.DEFINITION), parts.map { it.role })
        assertTrue(parts[0].expandedByDefault)
    }

    @Test fun documentsWithoutPartsAreOnePart() {
        val blocks = listOf(p("a"), Block.Heading("Regular"), p("b"))   // MTR heading without a role
        val parts = groupIntoParts(blocks)
        assertEquals(1, parts.size)
        assertEquals(blocks, parts[0].blocks)
        assertFalse(parts[0].role != null)
    }

    @Test fun emptyBlocksGiveNoParts() = assertEquals(emptyList<PagePart>(), groupIntoParts(emptyList()))
}
