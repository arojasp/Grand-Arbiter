package es.alejandro.grandarbiter.ui.tools

import es.alejandro.grandarbiter.ui.tools.quickrefs.IndexNode
import es.alejandro.grandarbiter.ui.tools.quickrefs.QuickReferenceIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The quick reference editor's index: what's listed as chapters and pages are opened. */
class QuickReferenceIndexTest {

    // CR-like: chapter 7 (not addable) > page 702 (rules load lazily); chapter 1 > page 100.
    private val tree = listOf(
        IndexNode("cr/1", "1. Game Concepts", addable = false, children = listOf(
            IndexNode("cr/100", "100. General", lazyChildren = true),
        )),
        IndexNode("cr/7", "7. Additional Rules", addable = false, children = listOf(
            IndexNode("cr/702", "702. Keyword Abilities", lazyChildren = true),
            IndexNode("cr/703", "703. Turn-Based Actions", lazyChildren = true),
        )),
    )
    private val rules702 = listOf(
        IndexNode("cr/702.1", "702.1", detail = "Most abilities describe exactly what they do…"),
        IndexNode("cr/702.19", "702.19. Trample"),
    )

    private fun rows(expanded: Set<String> = emptySet(), loaded: Map<String, List<IndexNode>> = emptyMap(), added: Set<String> = emptySet()) =
        QuickReferenceIndex.rows(tree, expanded, loaded, added)

    @Test fun collapsed_onlyChapters() {
        val r = rows()
        assertEquals(listOf("cr/1", "cr/7"), r.map { it.sectionId })
        assertTrue(r.all { it.expandable && !it.expanded && !it.addable && it.depth == 0 })
    }

    @Test fun openChapter_showsItsPages() {
        val r = rows(expanded = setOf("cr/7"))
        assertEquals(listOf("cr/1", "cr/7", "cr/702", "cr/703"), r.map { it.sectionId })
        assertEquals(1, r[2].depth)
        assertTrue(r[2].addable && r[2].expandable)
    }

    @Test fun openPage_notLoadedYet_isLoading() {
        val page = rows(expanded = setOf("cr/7", "cr/702")).first { it.sectionId == "cr/702" }
        assertTrue(page.expanded && page.loading)
    }

    @Test fun openPage_loaded_showsItsRules() {
        val r = rows(expanded = setOf("cr/7", "cr/702"), loaded = mapOf("cr/702" to rules702), added = setOf("cr/702.19"))
        assertEquals(listOf("cr/1", "cr/7", "cr/702", "cr/702.1", "cr/702.19", "cr/703"), r.map { it.sectionId })
        val trample = r.first { it.sectionId == "cr/702.19" }
        assertEquals(2, trample.depth)
        assertTrue(trample.added)
        assertFalse(trample.expandable)
        assertFalse(r.first { it.sectionId == "cr/702" }.loading)
    }

    @Test fun pageWithoutRules_stopsBeingExpandable() {
        val page = rows(expanded = setOf("cr/7", "cr/703"), loaded = mapOf("cr/703" to emptyList()))
            .first { it.sectionId == "cr/703" }
        assertFalse(page.expandable)
        assertFalse(page.expanded)
    }

    @Test fun pageOpen_butChapterClosed_hidden() =
        assertEquals(listOf("cr/1", "cr/7"), rows(expanded = setOf("cr/702"), loaded = mapOf("cr/702" to rules702)).map { it.sectionId })

    @Test fun find_andNeedsLoading() {
        val loaded = mapOf("cr/702" to rules702)
        assertEquals("702.19. Trample", QuickReferenceIndex.find(tree, loaded, "cr/702.19")?.label)
        assertNull(QuickReferenceIndex.find(tree, emptyMap(), "cr/702.19"))
        assertTrue(QuickReferenceIndex.needsLoading(tree, emptyMap(), "cr/702"))
        assertFalse(QuickReferenceIndex.needsLoading(tree, loaded, "cr/702"))
        assertFalse(QuickReferenceIndex.needsLoading(tree, emptyMap(), "cr/7"))   // chapter: pages already known
    }
}
