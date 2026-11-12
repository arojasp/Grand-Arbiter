package es.alejandro.grandarbiter.data.rules.bundle

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesBundleTest {

    /** Strict on purpose: a typo'd key in the bundle should fail here, not be silently ignored. */
    private val strict = Json { ignoreUnknownKeys = false }

    private fun sampleText(): String =
        requireNotNull(javaClass.getResource("/rules/bundle.sample.json")) {
            "rules/bundle.sample.json not found in test resources"
        }.readText()

    private fun sample(): RulesBundle = strict.decodeFromString(sampleText())

    private fun errorsFor(json: String): List<String> =
        RulesBundleValidator.validate(strict.decodeFromString<RulesBundle>(json))

    @Test
    fun sampleBundle_decodesAndIsValid() {
        val bundle = sample()
        assertEquals(listOf(DocumentId.CR, DocumentId.IPG, DocumentId.JAR), bundle.documents.map { it.id })
        assertEquals(emptyList<String>(), RulesBundleValidator.validate(bundle))
    }

    @Test
    fun ipgInfraction_isDecoded() {
        val missedTrigger = sample().documents.single { it.id == DocumentId.IPG }
            .sections.single { it.id == "ipg/2.1" }
        assertEquals(InfractionCategory.GAME_PLAY_ERROR, missedTrigger.infraction?.category)
        assertEquals(Penalty.NONE, missedTrigger.infraction?.penalty)
        assertTrue(missedTrigger.blocks.any { it is Block.Heading && it.role == HeadingRole.PHILOSOPHY })
    }

    @Test
    fun plainText_stripsMarkup() {
        assertEquals("see 603.1 and this", "see [603.1](ref:cr/603.1) and **this**".stripMarkup())
        assertEquals(listOf("ref:cr/603.1"), "see [603.1](ref:cr/603.1)".linkTargets())
    }

    @Test
    fun escapedCharacters_areLiteral() {
        assertEquals("a */* creature, cost [+1]", """a \*/\* creature, cost \[+1\]""".stripMarkup())
        assertFalse("""a \*/\* creature""".hasStrayAsterisk())
        assertTrue("a * b".hasStrayAsterisk())
        assertFalse("*em* and **strong**".hasStrayAsterisk())
        assertEquals(emptyList<String>(), """not a link \[x\](ref:cr/1)""".linkTargets())
    }

    @Test
    fun blankTableCell_isAllowed_butBlankParagraphIsNot() {
        fun withBlock(block: Block) = sample().let { b ->
            b.copy(documents = b.documents.map { d ->
                if (d.id != DocumentId.JAR) d
                else d.copy(sections = d.sections + RulesSection(id = "jar/t", title = "T", blocks = listOf(block)))
            })
        }
        val table = Block.Table(header = listOf("Infraction", "Penalty"), rows = listOf(listOf("Game Play Errors", "")))
        assertEquals(emptyList<String>(), RulesBundleValidator.validate(withBlock(table)))
        assertTrue(RulesBundleValidator.validate(withBlock(Block.Paragraph(" "))).any { "blank text" in it })
    }

    @Test
    fun brokenRef_isReported() {
        val errors = errorsFor(sampleText().replace("ref:ipg/2.1", "ref:ipg/9.9"))
        assertTrue(errors.toString(), errors.any { "broken ref 'ref:ipg/9.9'" in it })
    }

    @Test
    fun duplicateId_isReported() {
        val errors = errorsFor(sampleText().replace("\"id\": \"cr/603.2\"", "\"id\": \"cr/603.1\""))
        assertTrue(errors.toString(), errors.any { "duplicate id" in it })
    }

    @Test
    fun wrongDocumentPrefix_isReported() {
        val errors = errorsFor(sampleText().replace("\"id\": \"jar/missed-triggers\"", "\"id\": \"ipg/missed-triggers\""))
        assertTrue(errors.toString(), errors.any { "id prefix" in it })
    }

    @Test
    fun badEffectiveDate_isReported() {
        val errors = errorsFor(sampleText().replace("2024-08-05", "05/08/2024"))
        assertTrue(errors.toString(), errors.any { "bad effectiveDate" in it })
    }

    @Test
    fun sourceUrl_mustBeHttps() {
        for (url in listOf("http://media.wizards.com/x.pdf", "intent://x#Intent;end", "file:///sdcard/x", "")) {
            val errors = errorsFor(sampleText().replace("https://media.wizards.com/ContentResources/WPN/MTG_IPG_2024Sep23_EN.pdf", url))
            assertTrue(url, errors.any { "sourceUrl must be https://" in it })
        }
    }

    @Test(expected = SerializationException::class)
    fun unknownBlockType_failsEvenInLenientMode() {
        // Documents the compatibility rule: a new Block type is a breaking change.
        RulesJson.decodeFromString<RulesBundle>(sampleText().replace("\"type\": \"example\"", "\"type\": \"table\""))
    }

    @Test
    fun unknownKey_isIgnoredByAppJson() {
        val withExtra = sampleText().replaceFirst("\"schemaVersion\": 1,", "\"schemaVersion\": 1, \"futureField\": true,")
        assertEquals(1, RulesJson.decodeFromString<RulesBundle>(withExtra).schemaVersion)
    }
}
