package es.alejandro.grandarbiter.ui.tools.releasenotes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLinkTest {

    @Test fun webAndMail_open() {
        for (url in listOf("https://scryfall.com/card/x", "http://example.com", "HTTPS://example.com", "mailto:a@b.c")) {
            assertTrue(url, mayOpenExternally(url))
        }
    }

    @Test fun otherSchemes_areDropped() {
        for (url in listOf(
            "intent://scan#Intent;scheme=zxing;end",
            "file:///sdcard/Download/x.html",
            "content://com.example.provider/x",
            "javascript:alert(1)",
            "market://details?id=x",
            "tel:123",
            "no-scheme-at-all",
            "",
        )) {
            assertFalse(url, mayOpenExternally(url))
        }
    }
}
