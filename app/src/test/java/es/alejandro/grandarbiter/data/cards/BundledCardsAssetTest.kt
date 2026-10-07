package es.alejandro.grandarbiter.data.cards

import es.alejandro.grandarbiter.data.cards.local.CardDataVersion
import es.alejandro.grandarbiter.data.cards.local.CardImporter
import es.alejandro.grandarbiter.data.cards.local.CardMapper
import es.alejandro.grandarbiter.data.cards.scryfall.CardFilter
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallCard
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallJson
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallRuling
import es.alejandro.grandarbiter.data.cards.scryfall.forEachJsonLine
import es.alejandro.grandarbiter.ui.cardsearch.CardText
import es.alejandro.grandarbiter.ui.cardsearch.manaDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The gate for the card seed shipped in the APK (app/src/main/assets/cards/). Runs the SAME
 * decode + filter + mapper the importer uses over every line, so a seed the app can't load
 * fails `./gradlew test` instead of failing silently on a judge's phone.
 */
class BundledCardsAssetTest {

    private val dir = File("src/main/assets/cards")

    @Test
    fun seed_decodesFiltersAndMapsEveryCard() {
        val file = File(dir, "oracle-cards.jsonl.gz")
        assumeTrue("no card seed yet (${file.absolutePath})", file.exists())
        val ids = HashSet<String>()
        val names = HashSet<String>()
        var dropped = 0
        file.inputStream().forEachJsonLine { line ->
            val card = ScryfallJson.decodeFromString<ScryfallCard>(line)
            if (!CardFilter.keep(card)) { dropped++; return@forEachJsonLine }
            val e = CardMapper.toEntity(card)
            assertTrue("duplicate oracle id ${e.oracleId}", ids.add(e.oracleId))
            names += e.name
        }
        assertTrue("only ${ids.size} cards", ids.size >= CardImporter.MIN_CARDS)
        assertTrue("art series etc. should be dropped", dropped > 0)
        for (name in listOf("Lightning Bolt", "Delver of Secrets // Insectile Aberration", "Fire // Ice", "Palinchron")) {
            assertTrue("missing $name", name in names)
        }
    }

    @Test
    fun seed_rulingsDecode() {
        val file = File(dir, "rulings.jsonl.gz")
        assumeTrue(file.exists())
        var n = 0
        file.inputStream().forEachJsonLine { ScryfallJson.decodeFromString<ScryfallRuling>(it); n++ }
        assertTrue("only $n rulings", n > 10_000)
    }

    /**
     * Every symbol the cards and rulings use has an official drawable. Fails when Scryfall adds a
     * symbol: run tools/cards/fetch_symbols.py + build_symbols.py. (The app still shows a drawn
     * fallback, but a release should ship the real one.)
     */
    @Test
    fun seed_everySymbolHasADrawable() {
        val cards = File(dir, "oracle-cards.jsonl.gz")
        val rulings = File(dir, "rulings.jsonl.gz")
        assumeTrue(cards.exists() && rulings.exists())
        val used = HashSet<String>()
        cards.inputStream().forEachJsonLine { line ->
            val c = ScryfallJson.decodeFromString<ScryfallCard>(line)
            val texts = listOfNotNull(c.manaCost, c.oracleText) + c.cardFaces.flatMap { listOfNotNull(it.manaCost, it.oracleText) }
            texts.forEach { used += CardText.symbols(it) }
        }
        rulings.inputStream().forEachJsonLine { used += CardText.symbols(ScryfallJson.decodeFromString<ScryfallRuling>(it).comment) }
        val missing = used.filter { manaDrawable(it) == null }.toSet()
        // Known oddities: an Un-card printing lowercase symbols, and a ruling quoting "{(b/r)}".
        assertEquals(emptySet<String>(), missing - setOf("w", "u", "b", "r", "g", "(b/r)", "(r/g)"))
    }

    @Test
    fun seed_versionParses() {
        val file = File(dir, "seed.json")
        assumeTrue(file.exists())
        val v = ScryfallJson.decodeFromString<CardDataVersion>(file.readText())
        assertEquals(false, v.isNewerThan(v))
    }
}
