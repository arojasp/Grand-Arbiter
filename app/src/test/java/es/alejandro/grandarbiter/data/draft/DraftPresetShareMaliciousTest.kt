package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.UserDraftPreset
import es.alejandro.grandarbiter.domain.draft.UserDraftPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Presets crafted to do harm, as anyone can paste anything into Import. What could go wrong:
 * the app freezing or crashing (huge, deep or odd text), a preset that fills the phone's storage,
 * a name that pretends to be something else, numbers that overflow. Nothing in a preset is ever
 * run, opened or shown as markup; these tests keep it so.
 */
class DraftPresetShareMaliciousTest {

    private val head = """{"kind":"grand-arbiter-draft-preset","v":1"""
    private fun decode(text: String) = DraftPresetShareCodec.decode(text)
    private fun read(text: String) = (decode(text) as? SharedPresetResult.Read)?.preset
        ?: throw AssertionError("expected a preset, got ${decode(text)}")
    /** A JSON escape ("‮") built in code, so this file holds no invisible characters. */
    private fun esc(code: Int) = "\\u" + "%04x".format(code)
    private fun named(jsonName: String) = """$head,"name":"$jsonName","format":"booster","minutes":25}"""

    // --- Freezing or crashing the app: every one answers at once, without throwing ---

    private fun assertQuick(label: String, text: String) {
        lateinit var result: SharedPresetResult
        val ms = measureTimeMillis { result = decode(text) }
        assertTrue("$label took $ms ms", ms < 2_000)
        assertTrue(label, result !is SharedPresetResult.Read)
    }

    @Test fun hostileShapes_answerQuickly() {
        val max = DraftPresetShareCodec.MAX_TEXT_LENGTH
        assertQuick("open braces", "{".repeat(max))
        assertQuick("close braces", "}".repeat(max))
        assertQuick("many empty objects", "{}".repeat(max / 2))
        assertQuick("many objects mentioning the kind", """{"kind":"grand-arbiter-draft-preset"}""".repeat(max / 40))
        assertQuick("quotes", "\"".repeat(max))
        assertQuick("backslashes", "{\"a\":\"" + "\\".repeat(max - 10) + "\"}")
        assertQuick("deep objects", "{\"a\":".repeat(max / 6) + "1" + "}".repeat(max / 6))
        assertQuick("deep arrays", "{\"a\":" + "[".repeat(max / 2 - 10) + "]".repeat(max / 2 - 10) + "}")
        assertQuick("huge number", """$head,"name":"X","format":"booster","minutes":${"9".repeat(max - 100)}}""")
        assertQuick("huge name", named("x".repeat(max - 100)))
        assertQuick("mostly curly quotes", "“".repeat(max))
    }

    /** Unknown fields are skipped, however big: nothing of them reaches the preset or the phone's storage. */
    @Test fun hugeUnknownField_ignored() {
        val junk = "x".repeat(DraftPresetShareCodec.MAX_TEXT_LENGTH - 120)
        lateinit var preset: SharedDraftPreset
        val ms = measureTimeMillis { preset = read("""$head,"name":"X","format":"booster","minutes":25,"junk":"$junk"}""") }
        assertTrue("$ms ms", ms < 2_000)
        assertEquals(SharedDraftPreset("X", DraftFormat.BOOSTER, DraftTimes.MTR, 25), preset)
    }

    @Test fun overTheLimit_notEvenRead() {
        val preset = """$head,"name":"X","format":"booster","minutes":25}"""
        val padded = " ".repeat(DraftPresetShareCodec.MAX_TEXT_LENGTH) + preset
        assertEquals(SharedPresetResult.NotAPreset, decode(padded))
    }

    @Test fun brokenJsonThatMentionsThePreset_isInvalid_notCutOff() {
        listOf(
            """$head,"name":"X","format":"booster","minutes":25,}""",                         // trailing comma
            """$head,"name":"X","format":"booster","minutes":NaN}""",
            """$head,"name":"X","format":"booster","minutes":25 // comment}""",
            """{"kind":["grand-arbiter-draft-preset"],"v":1,"name":"X","format":"booster","minutes":25}""",
            """{"kind":{"grand-arbiter-draft-preset":1},"v":1}""",
            """{"kind":"grand-arbiter-draft-preset ","v":1,"name":"X","format":"booster","minutes":25}""",
        ).forEach { assertEquals(it, SharedPresetResult.Invalid, decode(it)) }
    }

    // --- Numbers that overflow or aren't whole ---

    @Test fun numbersOutOfInt_orNotWhole_areInvalid() {
        // "1e2" is 100 to the JSON reader: a whole number in range, so it's fine (asserted below).
        assertEquals(100, read("""$head,"name":"X","format":"booster","minutes":1e2}""").constructionMinutes)
        listOf("2147483648", "-2147483649", "99999999999999999999", "1E309", "1e-2", "-0", "0.1", "25.0", "true", "null", "[]", "{}")
            .forEach { minutes ->
                assertEquals(minutes, SharedPresetResult.Invalid, decode("""$head,"name":"X","format":"booster","minutes":$minutes}"""))
            }
        listOf("1.5", "99999999999", "\"one\"", "null").forEach { v ->
            assertEquals(v, SharedPresetResult.Invalid, decode("""{"kind":"grand-arbiter-draft-preset","v":$v,"name":"X","format":"booster","minutes":25}"""))
        }
        assertEquals(SharedPresetResult.Invalid, decode("""$head,"name":"X","format":"booster","minutes":25,"picks":{"14":{"1":2147483648}}}"""))
        assertEquals(SharedPresetResult.Invalid, decode("""$head,"name":"X","format":"booster","minutes":25,"picks":{"4294967310":{"1":50}}}"""))
    }

    // --- Filling the phone with times that are never used ---

    @Test fun onlyPacksAndPicksTheAppHas() {
        listOf(
            """"picks":{"16":{"1":50}}""",               // no 16-card packs
            """"picks":{"4":{"1":50}}""",                // nor 4-card ones
            """"picks":{"14":{"15":50}}""",              // pick 15 of a 14-card pack
            """"picks":{"14":{"-1":50}}""",
            """"reviews":{"7":60}""",                    // at most 6 packs
            """"reviews":{"-1":60}""",
        ).forEach { field ->
            assertEquals(field, SharedPresetResult.Invalid, decode("""$head,"name":"X","format":"booster","minutes":25,$field}"""))
        }
    }

    /** The biggest preset that can be imported: every pick of every pack size. Small enough to store. */
    @Test fun biggestPossiblePreset_isSmall() {
        val all = (5..15).associateWith { size -> (1..size).associateWith { 999 } }
        val p = UserDraftPreset("x", "x".repeat(40), DraftFormat.BOOSTER, DraftTimes(true, all, (0..6).associateWith { 999 }), 999)
        val text = DraftPresetShareCodec.encode(p)
        assertTrue("${text.length} characters", text.length < 2_000)
        assertEquals(110, read(text).times.pickSeconds.values.sumOf { it.size })
    }

    // --- Names that pretend to be something else ---

    @Test fun controlCharacters_refused() {
        listOf(0x0000, 0x0007, 0x001B, 0x007F, 0x0085, 0x009B).forEach { code ->
            assertEquals("U+%04X".format(code), SharedPresetResult.Invalid, decode(named("RCQ${esc(code)}Girona")))
        }
    }

    /** "RCQ" + right-to-left override + "anoriG" reads "RCQ Girona" on screen. */
    @Test fun directionOverrides_refused() {
        listOf(0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069, 0x200E, 0x200F, 0x061C).forEach { code ->
            assertEquals("U+%04X".format(code), SharedPresetResult.Invalid, decode(named("RCQ ${esc(code)}anoriG")))
        }
    }

    @Test fun brokenSurrogates_refused() {
        assertEquals(SharedPresetResult.Invalid, decode(named("X${esc(0xD800)}")))         // high alone
        assertEquals(SharedPresetResult.Invalid, decode(named("X${esc(0xDC00)}Y")))        // low alone
        assertEquals(SharedPresetResult.Invalid, decode(named("${esc(0xDC00)}${esc(0xD800)}")))  // wrong order
        assertEquals("X" + String(Character.toChars(0x1F409)), read(named("X${esc(0xD83D)}${esc(0xDC09)}")).name)  // a real pair: 🐉
    }

    @Test fun newlinesAndTabs_becomeOneSpace() =
        assertEquals("RCQ Girona", read(named("RCQ\\n\\t\\r Girona")).name)

    /** A built-in's name hidden behind invisible or spacing characters is still a built-in's name. */
    @Test fun disguisedBuiltInNames_stillReserved() {
        val zeroWidth = Char(0x200B)
        val noBreak = Char(0x00A0)
        listOf("M${zeroWidth}TR", "MTR$noBreak", " ${noBreak}Pro${noBreak}Tour ", "PRO  TOUR", "M${esc(0x200B)}TR").forEach { disguised ->
            val name = read(named(disguised)).name
            assertTrue("'$disguised' read as '$name'", UserDraftPresets.isReserved(name))   // so Import asks for another name
        }
    }

    /** Text that means something elsewhere is just text here: shown as typed, never formatted or run. */
    @Test fun markupAndFormatStrings_keptAsText() {
        listOf("%1\$s %n %d", "<b>MTR</b>", "<script>x</script>", "javascript:alert(1)", "\${name}", "' OR 1=1 --", "../../etc")
            .forEach { name ->
                val json = name.replace("\\", "\\\\").replace("\"", "\\\"")
                assertEquals(name, read(named(json)).name)
            }
    }

    @Test fun familyEmoji_keepsItsJoiners() {
        val family = String(Character.toChars(0x1F468)) + Char(0x200D) + String(Character.toChars(0x1F469)) + Char(0x200D) +
            String(Character.toChars(0x1F467))
        val p = UserDraftPreset("x", "Cube $family", DraftFormat.BOOSTER, DraftTimes.MTR, 25)
        assertEquals("Cube $family", read(DraftPresetShareCodec.encode(p)).name)
    }

    @Test fun isSafeName() {
        assertTrue(DraftPresetShareCodec.isSafeName("Mónica’s “PT” 🐉"))
        assertFalse(DraftPresetShareCodec.isSafeName("a" + Char(0x202E) + "b"))
        assertFalse(DraftPresetShareCodec.isSafeName("a" + Char(0x0007)))
        assertFalse(DraftPresetShareCodec.isSafeName(Char(0xD800).toString()))
    }

    // --- Two values for one field ---

    /** The last one counts (as in most JSON readers), and is checked like any other. */
    @Test fun duplicateKeys_lastWins_andIsChecked() {
        assertEquals(25, read("""$head,"name":"X","format":"booster","minutes":0,"minutes":25}""").constructionMinutes)
        assertEquals(SharedPresetResult.Invalid, decode("""$head,"name":"X","format":"booster","minutes":25,"minutes":0}"""))
        assertEquals(SharedPresetResult.NewerVersion,
            decode("""{"kind":"grand-arbiter-draft-preset","v":1,"v":2,"name":"X","format":"booster","minutes":25}"""))
        assertEquals("MTR", read("""$head,"name":"Safe","name":"MTR","format":"booster","minutes":25}""").name)  // then Import asks for another name
    }
}
