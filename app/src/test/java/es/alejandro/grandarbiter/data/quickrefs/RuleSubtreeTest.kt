package es.alejandro.grandarbiter.data.quickrefs

import es.alejandro.grandarbiter.domain.model.rules.PageSection
import es.alejandro.grandarbiter.ui.tools.quickrefs.groupChildren
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleSubtreeTest {

    private fun s(id: String, depth: Int) = PageSection("cr/$id", id, null, depth, emptyList(), null)

    // Page cr/613 as the reader builds it.
    private val page = listOf(
        s("613", 0),
        s("613.1", 1), s("613.1a", 2), s("613.1b", 2),
        s("613.2", 1), s("613.2a", 2),
        s("613.4", 1), s("613.4a", 2), s("613.4b", 2),
    )

    @Test fun takesTheRuleAndItsDescendantsOnly() {
        assertEquals(listOf("613.1", "613.1a", "613.1b"), RuleSubtree.of(page, "cr/613.1").map { it.number })
        assertEquals(listOf("613.4", "613.4a", "613.4b"), RuleSubtree.of(page, "cr/613.4").map { it.number })
    }

    @Test fun leafRule_isJustItself() {
        assertEquals(listOf("613.2a"), RuleSubtree.of(page, "cr/613.2a").map { it.number })
    }

    @Test fun wholePage_fromTheRoot() {
        assertEquals(page.size, RuleSubtree.of(page, "cr/613").size)
    }

    @Test fun missingRule_isEmpty() {
        assertTrue(RuleSubtree.of(page, "cr/613.9").isEmpty())
    }

    @Test fun groupsChildrenWithTheirDescendants() {
        val groups = groupChildren(page)
        assertEquals(listOf("613.1", "613.2", "613.4"), groups.map { it.first().number })
        assertEquals(listOf("613.1", "613.1a", "613.1b"), groups.first().map { it.number })
        assertTrue(groupChildren(listOf(s("613.2a", 2))).isEmpty())
    }
}
