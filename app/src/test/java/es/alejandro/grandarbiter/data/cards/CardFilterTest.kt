package es.alejandro.grandarbiter.data.cards

import es.alejandro.grandarbiter.data.cards.scryfall.CardFilter
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardFilterTest {

    private fun card(
        layout: String = "normal",
        games: List<String> = listOf("paper"),
        legal: Map<String, String> = mapOf("modern" to "legal"),
        setType: String = "expansion",
    ) = ScryfallCard(oracleId = "id", name = "X", layout = layout, games = games, legalities = legal, setType = setType)

    @Test fun paperCard_isKept() = assertNull(CardFilter.dropReason(card()))

    @Test fun artSeries_isDropped() = assertEquals("layout:art_series", CardFilter.dropReason(card(layout = "art_series")))

    @Test fun frontCard_isDropped() = assertEquals("layout:front_card", CardFilter.dropReason(card(layout = "front_card")))

    @Test fun alchemyCard_isDropped() = assertEquals(
        "digital-only:alchemy",
        CardFilter.dropReason(card(games = listOf("arena"), legal = mapOf("alchemy" to "legal", "modern" to "not_legal"), setType = "alchemy")),
    )

    /** Oracle-cards may pick an MTGO printing of a paper card (Palinchron): keep it. */
    @Test fun mtgoPrintingOfPaperLegalCard_isKept() =
        assertNull(CardFilter.dropReason(card(games = listOf("mtgo"), legal = mapOf("legacy" to "legal"))))

    /** Un-cards and tokens are paper but legal nowhere: keep them, casual events need them. */
    @Test fun paperCardLegalNowhere_isKept() =
        assertNull(CardFilter.dropReason(card(layout = "token", legal = emptyMap(), setType = "token")))

    @Test fun noOracleId_isDropped() =
        assertEquals("no oracle_id", CardFilter.dropReason(card().copy(oracleId = null)))
}
