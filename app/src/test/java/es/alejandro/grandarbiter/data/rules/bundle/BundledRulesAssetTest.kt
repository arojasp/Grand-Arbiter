package es.alejandro.grandarbiter.data.rules.bundle

import es.alejandro.grandarbiter.data.rules.local.toRows
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The gate for the REAL bundle shipped in the APK (app/src/main/assets/rules/bundle.json).
 * Skipped until the file exists. Strict decoding: a typo'd key from the build script fails here.
 * Structural only: it cannot tell whether a rule or a penalty is CORRECT. That's the manual review.
 */
class BundledRulesAssetTest {

    private val file = File("src/main/assets/rules/bundle.json")   // Gradle runs unit tests from app/

    private fun bundle(): RulesBundle {
        assumeTrue("no bundled rules yet (${file.absolutePath})", file.exists())
        return Json { ignoreUnknownKeys = false }.decodeFromString(file.readText())
    }

    @Test
    fun bundledAsset_isValid() {
        assertEquals(emptyList<String>(), RulesBundleValidator.validate(bundle()))
    }

    @Test
    fun bundledAsset_mapsToRows() {
        val rows = bundle().toRows()
        assertEquals(rows.sections.size, rows.sections.map { it.id }.toSet().size)
    }

    @Test
    fun bundledCr_looksLikeAFullCr() {
        val cr = bundle().documents.firstOrNull { it.id == DocumentId.CR } ?: return
        val ids = cr.sections.map { it.id }.toSet()
        // Smoke checks against gross parse failures (a truncated or misparsed file).
        assertTrue("CR has ${cr.sections.size} sections", cr.sections.size > 2000)
        for (id in listOf("cr/1", "cr/9", "cr/100", "cr/100.1", "cr/603", "cr/702")) {
            assertTrue("missing $id", id in ids)
        }
        assertTrue("CR glossary has ${cr.glossary.size} terms", cr.glossary.size > 300)
    }

    @Test
    fun bundledIpg_hasEveryInfractionWithAPenalty() {
        val ipg = bundle().documents.firstOrNull { it.id == DocumentId.IPG } ?: return
        val infractions = ipg.sections.filter { it.infraction != null }
        // Chapters 2-4 of the IPG: every numbered subsection is an infraction, and nothing else is.
        val expected = ipg.sections.filter { it.number?.matches(Regex("[234]\\.\\d+")) == true }.map { it.id }
        assertEquals(expected, infractions.map { it.id })
        assertTrue("only ${infractions.size} infractions", infractions.size >= 20)
    }

    @Test
    fun bundledIpg_infractionsAreStructured() {
        val ipg = bundle().documents.firstOrNull { it.id == DocumentId.IPG } ?: return
        for (s in ipg.sections.filter { it.infraction != null }) {
            val roles = s.blocks.filterIsInstance<Block.Heading>().mapNotNull { it.role }
            assertEquals("${s.id} starts with its Definition", HeadingRole.DEFINITION, roles.firstOrNull())
            assertTrue("${s.id} has Examples", HeadingRole.EXAMPLES in roles)
            val labels = s.blocks.filterIsInstance<Block.Example>().map { it.label }
            assertEquals("${s.id} example labels", labels.indices.map { ('A' + it).toString() }, labels)
        }
        val all = ipg.sections.flatMap { s -> s.blocks.filterIsInstance<Block.Heading>().mapNotNull { it.role } }
        assertTrue("some Upgrade parts", HeadingRole.UPGRADE in all)
        assertTrue("some Downgrade parts", HeadingRole.DOWNGRADE in all)
    }

    @Test
    fun bundledDocuments_allFourPresent() {
        val ids = bundle().documents.map { it.id }.toSet()
        assertEquals(setOf(DocumentId.CR, DocumentId.MTR, DocumentId.IPG, DocumentId.JAR), ids)
    }
}
