package es.alejandro.grandarbiter.data.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardSearchQueryTest {
    @Test fun nameOnly_prefixesEveryWord() =
        assertEquals("name:light* name:bol*", CardSearchQuery.toFts("Light Bol", inText = false))

    @Test fun inText_searchesAllColumns() =
        assertEquals("draw* a* card*", CardSearchQuery.toFts("draw a card", inText = true))

    @Test fun punctuationIsIgnored() =
        assertEquals("name:ajani* name:s* name:pridemate*", CardSearchQuery.toFts("Ajani's Pridemate", false))

    /** Quotes, parentheses, '-' and OR can't reach FTS as operators: lowercase words only. */
    @Test fun ftsOperatorsCannotBeInjected() =
        assertEquals("name:a* name:or* name:b*", CardSearchQuery.toFts("\"a\" OR- (b*", false))

    @Test fun nothingToSearch_isNull() = assertNull(CardSearchQuery.toFts("  -- ", false))
}
