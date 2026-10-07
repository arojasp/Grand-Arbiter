package es.alejandro.grandarbiter.data.cards

import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallHeadersInterceptor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScryfallHeadersTest {
    @Test fun scryfallHosts_getTheHeaders() {
        assertTrue(ScryfallHeadersInterceptor.isScryfall("cards.scryfall.io"))
        assertTrue(ScryfallHeadersInterceptor.isScryfall("api.scryfall.com"))
        assertTrue(ScryfallHeadersInterceptor.isScryfall("data.scryfall.io"))
    }

    @Test fun otherHosts_areLeftAlone() {
        assertFalse(ScryfallHeadersInterceptor.isScryfall("media.wizards.com"))
        assertFalse(ScryfallHeadersInterceptor.isScryfall("notscryfall.io"))
    }
}
