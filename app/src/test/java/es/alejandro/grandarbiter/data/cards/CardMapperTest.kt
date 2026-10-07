package es.alejandro.grandarbiter.data.cards

import es.alejandro.grandarbiter.data.cards.local.CardMapper
import es.alejandro.grandarbiter.data.cards.local.RulingRow
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallCard
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallFace
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallImages
import es.alejandro.grandarbiter.domain.model.cards.Legality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Placeholder text only, no real card text. */
class CardMapperTest {

    private val transform = ScryfallCard(
        oracleId = "t1",
        name = "Front // Back",
        layout = "transform",
        typeLine = "Creature — A // Creature — B",
        colorIdentity = listOf("U"),
        keywords = listOf("Flying", "Transform"),
        legalities = mapOf("modern" to "legal", "legacy" to "banned", "alchemy" to "legal"),
        cardFaces = listOf(
            ScryfallFace("Front", manaCost = "{U}", oracleText = "front text", power = "1", toughness = "1",
                colors = listOf("U"), imageUris = ScryfallImages("https://img/front.jpg")),
            ScryfallFace("Back", manaCost = "", oracleText = "back text", colors = listOf("U"),
                imageUris = ScryfallImages("https://img/back.jpg")),
        ),
    )

    @Test
    fun multiFaced_takesCostTextColorsAndImageFromFaces() {
        val e = CardMapper.toEntity(transform)
        assertEquals("{U}", e.manaCost)
        assertEquals("front text\n\nback text", e.oracleText)
        assertEquals("U", e.colors)
        assertEquals("https://img/front.jpg", e.imageUrl)
        assertEquals("Flying|Transform", e.keywords)
    }

    @Test
    fun legalities_arePaperFormatsOnlyInFixedOrder() {
        val e = CardMapper.toEntity(transform)
        assertTrue("alchemy" !in e.legalities)
        val parsed = CardMapper.parseLegalities(e.legalities)
        assertEquals("standard", parsed.first().format)
        assertEquals(Legality.LEGAL, parsed.first { it.format == "modern" }.legality)
        assertEquals(Legality.BANNED, parsed.first { it.format == "legacy" }.legality)
        assertEquals(Legality.NOT_LEGAL, parsed.first { it.format == "standard" }.legality)
    }

    @Test
    fun detail_roundTripsFacesAndHidesCardLevelText() {
        val d = CardMapper.toDetail(CardMapper.toEntity(transform), listOf(RulingRow("2024-01-01", "wotc", "r")))
        assertNull(d.oracleText)
        assertEquals(listOf("Front", "Back"), d.faces.map { it.name })
        assertNull(d.faces[1].manaCost)              // blank cost on the back face -> null
        assertEquals("https://img/back.jpg", d.faces[1].imageUrl)
        assertEquals(1, d.rulings.size)
        assertTrue(d.scryfallUrl.endsWith("oracleid%3At1"))
    }

    @Test
    fun colors_areInWubrgOrder_andTokensAreFlagged() {
        val e = CardMapper.toEntity(
            ScryfallCard(oracleId = "x", name = "Tok", layout = "token", typeLine = "Token Creature — Y",
                colors = listOf("G", "W", "R"), colorIdentity = listOf("G", "W"))
        )
        assertEquals("WRG", e.colors)
        assertEquals("WG", e.colorIdentity)
        assertTrue(e.isToken)
    }
}
