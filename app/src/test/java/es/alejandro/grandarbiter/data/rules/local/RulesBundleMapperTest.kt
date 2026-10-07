package es.alejandro.grandarbiter.data.rules.local

import es.alejandro.grandarbiter.data.rules.bundle.RulesBundle
import es.alejandro.grandarbiter.data.rules.bundle.RulesJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RulesBundleMapperTest {

    private val bundle: RulesBundle = RulesJson.decodeFromString(
        requireNotNull(javaClass.getResource("/rules/bundle.sample.json")).readText()
    )
    private val rows = bundle.toRows()
    private fun section(id: String) = rows.sections.single { it.id == id }

    @Test
    fun oneDocumentRowPerDocument_withBundleVersion() {
        assertEquals(listOf("cr", "ipg", "jar"), rows.documents.map { it.id })
        assertEquals(setOf(bundle.bundleVersion), rows.documents.map { it.bundleVersion }.toSet())
    }

    @Test
    fun levels_followTheParentChain() {
        assertEquals(0, section("cr/6").level)
        assertEquals(1, section("cr/603").level)
        assertEquals(2, section("cr/603.1").level)
        assertEquals(1, section("jar/missed-triggers").level)
    }

    @Test
    fun ordinals_areReadingOrderPerDocument() {
        rows.sections.groupBy { it.documentId }.forEach { (doc, list) ->
            assertEquals("ordinals of $doc", list.indices.toList(), list.map { it.ordinal })
        }
    }

    @Test
    fun infraction_isFlattenedIntoColumns() {
        assertEquals("NONE", section("ipg/2.1").penalty)
        assertEquals("GAME_PLAY_ERROR", section("ipg/2.1").infractionCategory)
        assertNull(section("cr/603.1").penalty)
    }

    @Test
    fun plainText_hasNoMarkup_andBlocksRoundTrip() {
        val s = section("ipg/2.1")
        assertFalse(s.plainText, s.plainText.contains("](ref:"))
        val original = bundle.documents.flatMap { it.sections }.single { it.id == "ipg/2.1" }.blocks
        assertEquals(original, s.blocks())
    }

    @Test
    fun glossary_isMapped() {
        assertEquals(listOf("cr" to "Triggered Ability"), rows.glossary.map { it.documentId to it.term })
    }
}
