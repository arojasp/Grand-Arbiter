package es.alejandro.grandarbiter.data.cards.search

import es.alejandro.grandarbiter.data.update.FakeHttp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** No network: FakeHttp answers like Scryfall's /cards/search. Placeholder cards only. */
class ScryfallSearchClientTest {

    private val http = FakeHttp()
    private val client = ScryfallSearchClient(http.client, baseUrl = "https://api.scryfall.com")
    private val query = "t:creature -t:faerie"
    private val url get() = client.url(query).toString()

    private val normal = """{"oracle_id":"o1","name":"Test Flyer","layout":"normal","mana_cost":"{2}{U}",
        "type_line":"Creature — Bird","games":["paper"],"legalities":{"modern":"legal"}}"""
    private val reversible = """{"name":"Test Elf // Test Elf","layout":"reversible_card","games":["paper"],
        "card_faces":[{"name":"Test Elf","oracle_id":"o2","mana_cost":"{G}","type_line":"Creature — Elf"},
                      {"name":"Test Elf","oracle_id":"o2","mana_cost":"{G}","type_line":"Creature — Elf"}]}"""
    private val token = """{"oracle_id":"o3","name":"Test Token","layout":"token","type_line":"Token Creature — Bird"}"""

    @Test fun url_encodesTheWholeQuery_oneRowPerCard() {
        val u = client.url("o:\"draw a card\" -t:land")
        assertEquals("/cards/search", u.encodedPath)
        assertEquals("o:\"draw a card\" -t:land", u.queryParameter("q"))
        assertEquals("cards", u.queryParameter("unique"))
    }

    @Test fun found_mapsCards_keepsTotalAndWarnings() = runBlocking {
        http.on(url, body = """{"object":"list","total_cards":300,"has_more":true,
            "warnings":["Invalid expression “is:slick” was ignored."],"data":[$normal,$reversible,$token]}""")
        val r = client.search(query) as OnlineSearch.Found
        assertEquals(300, r.total)
        assertEquals(listOf("Invalid expression “is:slick” was ignored."), r.warnings)
        assertEquals(listOf("o1", "o2", "o3"), r.cards.map { it.oracleId })
        assertEquals("{2}{U}", r.cards[0].manaCost)
        // Reversible: no oracle id of its own, so its face is the card (once, not twice).
        assertEquals("Test Elf", r.cards[1].name)
        assertTrue(r.cards[2].isToken)
        assertEquals(1, http.count(url))
    }

    @Test fun notFound_isAnEmptyResult_notAnError() = runBlocking {
        http.on(url, code = 404, body = """{"object":"error","code":"not_found","status":404,
            "details":"Your query didn't match any cards.","warnings":[]}""")
        val r = client.search(query) as OnlineSearch.Found
        assertTrue(r.cards.isEmpty())
        assertEquals(0, r.total)
    }

    @Test fun badRequest_isRejected_withScryfallsWords() = runBlocking {
        http.on(url, code = 400, body = """{"object":"error","code":"bad_request","status":400,
            "details":"All of your terms were ignored.","warnings":["Invalid expression “x:y” was ignored."]}""")
        val r = client.search(query) as OnlineSearch.Rejected
        assertEquals("All of your terms were ignored.", r.details)
        assertEquals(1, r.warnings.size)
    }

    @Test fun noNetwork_isNoConnection() = runBlocking {
        http.onThrow(url, IOException("offline"))
        assertEquals(OnlineSearch.NoConnection, client.search(query))
    }

    @Test fun busyOrDown_isUnavailable() = runBlocking {
        http.on(url, code = 503, body = "")
        assertEquals(OnlineSearch.Unavailable(503), client.search(query))
        http.on(url, code = 429, body = """{"object":"error","status":429}""")
        assertEquals(OnlineSearch.Unavailable(429), client.search(query))
    }

    @Test fun unreadableAnswer_isUnavailable_notACrash() = runBlocking {
        http.on(url, body = "<html>maintenance</html>")
        assertEquals(OnlineSearch.Unavailable(0), client.search(query))
    }
}
