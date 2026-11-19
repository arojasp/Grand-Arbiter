package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.domain.draft.BoosterDraftTimings
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftTimeRows
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.DraftTimesPreset
import es.alejandro.grandarbiter.domain.draft.UserDraftPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Sharing a preset as JSON and importing it. What a judge actually pastes: chat apps' prefixes,
 * several messages, other braces, invisible characters, curled quotes, cut-off messages, hand edits.
 */
class DraftPresetShareCodecTest {

    private val rows14 = DraftTimeRows.booster(14, BoosterDraftTimings.FALLBACK)
    private val rcq = UserDraftPreset("a", "RCQ Girona", DraftFormat.BOOSTER,
        DraftTimes(true, mapOf(14 to mapOf(1 to 50, 2 to 45)), mapOf(1 to 90)), 30)
    private val rcqJson = DraftPresetShareCodec.encode(rcq)

    private fun decode(text: String) = DraftPresetShareCodec.decode(text)
    private fun read(text: String): SharedDraftPreset {
        val result = decode(text)
        assertTrue("expected a preset, got $result for: ${text.take(80)}", result is SharedPresetResult.Read)
        return (result as SharedPresetResult.Read).preset
    }
    private fun shared(p: UserDraftPreset) = SharedDraftPreset(p.name, p.format, p.times, p.constructionMinutes)
    private fun assertRoundTrip(p: UserDraftPreset) = assertEquals(shared(p), read(DraftPresetShareCodec.encode(p)))
    private val head = """{"kind":"grand-arbiter-draft-preset","v":1"""

    // --- The format itself: what other phones will send and keep sending ---

    @Test fun encode_exactLine() = assertEquals(
        """{"kind":"grand-arbiter-draft-preset","v":1,"name":"RCQ Girona","format":"booster","picks":{"14":{"1":50,"2":45}},"reviews":{"1":90},"minutes":30}""",
        rcqJson,
    )

    @Test fun encode_untimed_twoHeadedGiant_exactLine() = assertEquals(
        """{"kind":"grand-arbiter-draft-preset","v":1,"name":"2HG","format":"two-headed-giant","timed":false,"minutes":40}""",
        DraftPresetShareCodec.encode(UserDraftPreset("b", "2HG", DraftFormat.TWO_HEADED_GIANT, DraftTimes.MTR.withTimedPicks(false), 40)),
    )

    /** Presets shared by version 1 must import in every later version: never change these lines. */
    @Test fun version1_alwaysReadable() {
        assertEquals(shared(rcq), read("""{"kind":"grand-arbiter-draft-preset","v":1,"name":"RCQ Girona","format":"booster","picks":{"14":{"1":50,"2":45}},"reviews":{"1":90},"minutes":30}"""))
        assertEquals(
            SharedDraftPreset("Casual", DraftFormat.TWO_HEADED_GIANT, DraftTimes(true, emptyMap(), mapOf(0 to 75)), 40),
            read("""{"kind":"grand-arbiter-draft-preset","v":1,"name":"Casual","format":"two-headed-giant","reviews":{"0":75},"minutes":40}"""),
        )
    }

    @Test fun encode_isOneLine_withoutTheLocalId() {
        val text = DraftPresetShareCodec.encode(rcq.copy(id = "secret-id"))
        assertFalse(text.contains('\n'))
        assertFalse(text.contains("secret-id"))
    }

    // --- Round trips ---

    @Test fun roundTrip_allPicksChanged() =
        assertRoundTrip(rcq.copy(times = DraftTimes.MTR.withAllPicks(10, rows14).withReview(1, 90, 60)))

    @Test fun roundTrip_proTourTimes() = assertRoundTrip(rcq.copy(times = DraftTimesPreset.PRO_TOUR.times))

    @Test fun roundTrip_mtr() = assertRoundTrip(rcq.copy(times = DraftTimes.MTR))

    @Test fun roundTrip_untimed_twoHeadedGiant_everyReview() = assertRoundTrip(
        UserDraftPreset("c", "2HG casual", DraftFormat.TWO_HEADED_GIANT,
            DraftTimes.MTR.withTimedPicks(false).withReview(DraftTimes.EVERY_REVIEW, 75, 60), 40),
    )

    @Test fun roundTrip_namesThatAreHardForJson() = listOf(
        "Mónica’s “PT”", "RCQ \"Girona\"", "Back\\slash", "Cube {night}", "} {", "Ñandú 🐉 cube", "x".repeat(40), "a",
    ).forEach { assertRoundTrip(rcq.copy(name = it)) }

    @Test fun roundTrip_limits() {
        assertRoundTrip(rcq.copy(times = DraftTimes(true, mapOf(14 to mapOf(1 to 1, 2 to 999)), mapOf(1 to 1, 2 to 999)), constructionMinutes = 1))
        assertRoundTrip(rcq.copy(constructionMinutes = 999))
    }

    /** Many random presets: every one reads back as it was shared. */
    @Test fun roundTrip_random() {
        val random = Random(2026)
        val letters = "abcdefghijklmnñopqrstuvwxyzÁÉÍÓÚ 0123456789-'\"{}\\/·"
        repeat(500) {
            val format = if (random.nextBoolean()) DraftFormat.BOOSTER else DraftFormat.TWO_HEADED_GIANT
            val picks = (1..random.nextInt(0, 4)).associate {
                val size = random.nextInt(8, 21)
                size to (1..random.nextInt(1, size + 1)).associateWith { random.nextInt(1, 1000) }
            }
            val reviews = (0..random.nextInt(0, 4)).associateWith { random.nextInt(1, 1000) }
            val raw = (1..random.nextInt(1, 41)).map { letters[random.nextInt(letters.length)] }.joinToString("")
            val name = raw.trim().replace(Regex("\\s+"), " ").ifEmpty { "x" }
            assertRoundTrip(UserDraftPreset("r$it", name, format, DraftTimes(random.nextBoolean(), picks, reviews), random.nextInt(1, 1000)))
        }
    }

    // --- What chat apps add when copying ---

    @Test fun whatsApp_oneMessage() = assertEquals("RCQ Girona", read("[19/11/26, 14:45] Alejandro: $rcqJson").name)

    @Test fun whatsApp_severalMessages() = assertEquals("RCQ Girona", read(
        "[19/11/26, 14:44] Alejandro: Te paso los tiempos del RCQ\n" +
            "[19/11/26, 14:45] Alejandro: $rcqJson\n" +
            "[19/11/26, 14:45] Alejandro: Avisa si algo no cuadra {o lo que sea}",
    ).name)

    @Test fun telegram() = assertEquals("RCQ Girona", read("Alejandro, [19.11.26 14:45]\n$rcqJson").name)

    @Test fun otherBracesAndQuotesFirst() {
        assertEquals("RCQ Girona", read("Hola {jueces}, \"aquí\" va: $rcqJson ¡suerte!").name)
        assertEquals("RCQ Girona", read("""{"note":"not a preset"} $rcqJson""").name)
    }

    @Test fun twoPresetsPasted_theFirstIsImported() {
        val other = DraftPresetShareCodec.encode(rcq.copy(name = "Second"))
        assertEquals("RCQ Girona", read("$rcqJson\n$other").name)
    }

    @Test fun reformattedOnSeveralLines() = assertEquals(shared(rcq), read(
        "{\r\n  \"kind\": \"grand-arbiter-draft-preset\",\r\n  \"v\": 1,\r\n  \"name\": \"RCQ Girona\",\r\n" +
            "  \"format\": \"booster\",\r\n  \"picks\": { \"14\": { \"1\": 50, \"2\": 45 } },\r\n  \"reviews\": { \"1\": 90 },\r\n  \"minutes\": 30\r\n}",
    ))

    @Test fun invisibleCharacters() {
        val zeroWidth = Char(0x200B).toString()
        val bom = Char(0xFEFF).toString()
        val noBreak = Char(0x00A0).toString()
        assertEquals(shared(rcq), read(bom + rcqJson))
        assertEquals(shared(rcq), read(rcqJson.replace(",", ",$zeroWidth")))
        assertEquals(shared(rcq), read(rcqJson.replace(":", ":$noBreak")))
    }

    @Test fun curlyQuotesFromAKeyboard() {
        val typed = """{“kind”:“grand-arbiter-draft-preset”,“v”:1,“name”:“Typed”,“format”:“booster”,“minutes”:25}"""
        assertEquals("Typed", read(typed).name)
    }

    @Test fun keysInAnyOrder_unknownKeysIgnored() = assertEquals(shared(rcq), read(
        """{"minutes":30,"reviews":{"1":90},"futureField":{"x":[1,2]},"picks":{"14":{"2":45,"1":50}},"format":"booster","name":"RCQ Girona","v":1,"kind":"grand-arbiter-draft-preset"}""",
    ))

    @Test fun formatInCapitals() = assertEquals(DraftFormat.BOOSTER, read("""$head,"name":"X","format":"Booster","minutes":25}""").format)

    /** Numbers in quotes (a hand edit) are still numbers. */
    @Test fun quotedNumbers() {
        val p = read("""{"kind":"grand-arbiter-draft-preset","v":"1","name":"X","format":"booster","minutes":"30","picks":{"14":{"1":"50"}}}""")
        assertEquals(30, p.constructionMinutes)
        assertEquals(50, p.times.pickSeconds[14]?.get(1))
    }

    @Test fun nameIsCleaned() = assertEquals("RCQ Girona", read("""$head,"name":"  RCQ   Girona ","format":"booster","minutes":25}""").name)

    @Test fun emptyPackListDropped() =
        assertEquals(DraftTimes.MTR, read("""$head,"name":"X","format":"booster","picks":{"14":{}},"minutes":25}""").times)

    // --- Cut off ---

    @Test fun cutOff_isIncomplete() {
        listOf(rcqJson.dropLast(1), rcqJson.take(rcqJson.length / 2), "[19/11/26, 14:45] Alejandro: ${rcqJson.take(60)}")
            .forEach { assertEquals(it, SharedPresetResult.Incomplete, decode(it)) }
    }

    // --- Refused ---

    @Test fun notAPreset() {
        listOf(
            "", "   ", "hello", "{}", "{not json}", "[1,2,3]", "null",
            """{"name":"No kind","format":"booster","minutes":25}""",
            """{"kind":"something-else","v":1,"name":"X","format":"booster","minutes":25}""",
            "{" + " ".repeat(DraftPresetShareCodec.MAX_TEXT_LENGTH) + "}",
        ).forEach { assertEquals(it.take(40), SharedPresetResult.NotAPreset, decode(it)) }
    }

    @Test fun deepNesting_noCrash() {
        val deep = "{\"a\":".repeat(3000) + "1" + "}".repeat(3000)
        assertEquals(SharedPresetResult.NotAPreset, decode(deep))
    }

    @Test fun newerVersion() {
        assertEquals(SharedPresetResult.NewerVersion,
            decode("""{"kind":"grand-arbiter-draft-preset","v":2,"name":"Future","format":"booster","minutes":25,"newThing":true}"""))
    }

    @Test fun invalid() {
        listOf(
            """{"kind":"grand-arbiter-draft-preset","name":"X","format":"booster","minutes":25}""",      // no version
            """{"kind":"grand-arbiter-draft-preset","v":0,"name":"X","format":"booster","minutes":25}""",
            """{"kind":"grand-arbiter-draft-preset","v":-1,"name":"X","format":"booster","minutes":25}""",
            """$head,"name":"","format":"booster","minutes":25}""",                                       // no name
            """$head,"name":"   ","format":"booster","minutes":25}""",
            """$head,"name":null,"format":"booster","minutes":25}""",
            """$head,"name":"${"x".repeat(41)}","format":"booster","minutes":25}""",                     // too long
            """$head,"name":"X","minutes":25}""",                                                         // no format
            """$head,"name":"X","format":"rochester","minutes":25}""",                                   // no presets for it
            """$head,"name":"X","format":"cube","minutes":25}""",
            """$head,"name":"X","format":"booster"}""",                                                   // no construction
            """$head,"name":"X","format":"booster","minutes":0}""",
            """$head,"name":"X","format":"booster","minutes":1000}""",
            """$head,"name":"X","format":"booster","minutes":30.5}""",
            """$head,"name":"X","format":"booster","minutes":25,"picks":{"14":{"1":5000}}}""",          // time out of range
            """$head,"name":"X","format":"booster","minutes":25,"picks":{"14":{"1":0}}}""",
            """$head,"name":"X","format":"booster","minutes":25,"picks":{"14":{"1":-5}}}""",
            """$head,"name":"X","format":"booster","minutes":25,"picks":{"14":{"0":50}}}""",            // no pick 0
            """$head,"name":"X","format":"booster","minutes":25,"picks":{"100000":{"1":50}}}""",        // no such pack
            """$head,"name":"X","format":"booster","minutes":25,"picks":{"a":{"1":50}}}""",
            """$head,"name":"X","format":"booster","minutes":25,"picks":[50,45]}""",
            """$head,"name":"X","format":"booster","minutes":25,"reviews":{"42":60}}""",               // no such review
            """$head,"name":"X","format":"booster","minutes":25,"timed":"yes"}""",
        ).forEach { assertEquals(it, SharedPresetResult.Invalid, decode(it)) }
    }

    // --- Finding the objects ---

    @Test fun objects_bracesInStringsAndProse() {
        assertEquals(listOf("""{"a":"}{"}""", "{b}"), DraftPresetShareCodec.objects("""x {"a":"}{"} y "q" {b} {open"""))
        assertEquals(listOf("""{"a":"\"}"}"""), DraftPresetShareCodec.objects("""{"a":"\"}"}"""))
        assertEquals(emptyList<String>(), DraftPresetShareCodec.objects("no braces } here {"))
    }
}
