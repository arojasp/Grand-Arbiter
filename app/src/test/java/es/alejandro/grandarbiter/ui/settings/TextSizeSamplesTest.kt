package es.alejandro.grandarbiter.ui.settings

import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallCard
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallJson
import es.alejandro.grandarbiter.data.cards.scryfall.forEachJsonLine
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class TextSizeSamplesTest {

    @Test
    fun twentyEightDifferentCards() {
        assertEquals(28, TextSizeSamples.names.size)
        assertEquals(28, TextSizeSamples.names.toSet().size)
    }

    @Test
    fun fits_shortTextOnly() {
        assertTrue(TextSizeSamples.fits("Counter target spell."))
        assertTrue(TextSizeSamples.fits("{T}: Add {G}."))
        assertFalse(TextSizeSamples.fits(" "))
        assertFalse(TextSizeSamples.fits("x".repeat(161)))
    }

    @Test
    fun neverTheSameTwiceInARow() {
        val random = Random(7)
        var previous = TextSizeSamples.nextIndex(random)
        repeat(500) {
            val next = TextSizeSamples.nextIndex(random)
            assertNotEquals(previous, next)
            assertTrue(next in TextSizeSamples.names.indices)
            previous = next
        }
    }

    @Test
    fun everyCardComesUp() {
        val random = Random(11)
        val seen = (1..2000).map { TextSizeSamples.nextIndex(random) }.toSet()
        assertEquals(TextSizeSamples.names.indices.toSet(), seen)
    }

    /** Every name is in the bundled card seed. A card whose new Oracle text doesn't fit is only skipped, so that isn't a failure. */
    @Test
    fun everyNameIsInTheSeed() {
        val file = File("src/main/assets/cards/oracle-cards.jsonl.gz")
        assumeTrue("no card seed yet (${file.absolutePath})", file.exists())
        val wanted = TextSizeSamples.names.toSet()
        val found = HashMap<String, String?>()
        file.inputStream().forEachJsonLine { line ->
            if (wanted.any { line.contains("\"$it\"") }) {
                val card = ScryfallJson.decodeFromString<ScryfallCard>(line)
                // Tokens can share a card's name (a Tarmogoyf token copy): the app never shows them.
                if (card.name in wanted && card.name !in found && "token" !in card.layout) found[card.name] = card.oracleText
            }
        }
        assertEquals(wanted - found.keys, emptySet<String>())
        val fitting = found.values.count { it != null && TextSizeSamples.fits(it) }
        assertTrue("only $fitting of ${wanted.size} cards fit the preview: replace some", fitting >= 24)
    }
}
