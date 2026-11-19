package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.domain.draft.BoosterDraftTimings
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftTimeRows
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.DraftTimesPreset
import es.alejandro.grandarbiter.domain.draft.UserDraftPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Sharing a preset as JSON and importing it: round trips, chat-app noise, and what is refused. */
class DraftPresetShareCodecTest {

    private val rows14 = DraftTimeRows.booster(14, BoosterDraftTimings.FALLBACK)

    private fun roundTrip(preset: UserDraftPreset) {
        val read = DraftPresetShareCodec.decode(DraftPresetShareCodec.encode(preset))
        assertEquals(
            SharedPresetResult.Read(SharedDraftPreset(preset.name, preset.format, preset.times, preset.constructionMinutes)),
            read,
        )
    }

    @Test fun roundTrip_booster() =
        roundTrip(UserDraftPreset("a", "RCQ Girona", DraftFormat.BOOSTER, DraftTimes.MTR.withAllPicks(10, rows14).withReview(1, 90, 60), 30))

    @Test fun roundTrip_proTourTimes() =
        roundTrip(UserDraftPreset("b", "PT copy", DraftFormat.BOOSTER, DraftTimesPreset.PRO_TOUR.times, 25))

    @Test fun roundTrip_untimed_twoHeadedGiant_everyReview() = roundTrip(
        UserDraftPreset("c", "2HG casual", DraftFormat.TWO_HEADED_GIANT,
            DraftTimes.MTR.withTimedPicks(false).withReview(DraftTimes.EVERY_REVIEW, 75, 60), 40),
    )

    @Test fun roundTrip_unicodeName_withCurlyQuotes() =
        roundTrip(UserDraftPreset("d", "Mónica’s “PT”", DraftFormat.BOOSTER, DraftTimes.MTR, 25))

    @Test fun encode_isOneLine_withoutTheLocalId() {
        val text = DraftPresetShareCodec.encode(UserDraftPreset("secret-id", "X", DraftFormat.BOOSTER, DraftTimes.MTR, 25))
        assertFalse(text.contains('\n'))
        assertFalse(text.contains("secret-id"))
    }

    @Test fun pastedWithWhatAChatAppAdds() {
        val shared = DraftPresetShareCodec.encode(UserDraftPreset("a", "RCQ", DraftFormat.BOOSTER, DraftTimes.MTR, 30))
        val pasted = "[19/11/26, 14:45] Alejandro: $shared\n"
        assertEquals("RCQ", (DraftPresetShareCodec.decode(pasted) as SharedPresetResult.Read).preset.name)
    }

    @Test fun curlyQuotesFromAKeyboard() {
        val typed = """{“kind”:“grand-arbiter-draft-preset”,“v”:1,“name”:“Typed”,“format”:“booster”,“minutes”:25}"""
        assertEquals("Typed", (DraftPresetShareCodec.decode(typed) as SharedPresetResult.Read).preset.name)
    }

    @Test fun nameIsCleaned() {
        val text = """{"kind":"grand-arbiter-draft-preset","v":1,"name":"  RCQ   Girona ","format":"booster","minutes":25}"""
        assertEquals("RCQ Girona", (DraftPresetShareCodec.decode(text) as SharedPresetResult.Read).preset.name)
    }

    @Test fun notAPreset() {
        listOf(
            "",
            "hello",
            "{not json}",
            """{"name":"No kind","format":"booster","minutes":25}""",
            """{"kind":"something-else","v":1,"name":"X","format":"booster","minutes":25}""",
            """{"kind":"grand-arbiter-draft-preset","v":0,"name":"X","format":"booster","minutes":25}""",
            "{" + " ".repeat(DraftPresetShareCodec.MAX_TEXT_LENGTH) + "}",
        ).forEach { assertEquals(it.take(40), SharedPresetResult.NotAPreset, DraftPresetShareCodec.decode(it)) }
    }

    @Test fun newerVersion() {
        val text = """{"kind":"grand-arbiter-draft-preset","v":2,"name":"Future","format":"booster","minutes":25}"""
        assertEquals(SharedPresetResult.NewerVersion, DraftPresetShareCodec.decode(text))
    }

    @Test fun invalid() {
        val head = """{"kind":"grand-arbiter-draft-preset","v":1"""
        listOf(
            """$head,"name":"","format":"booster","minutes":25}""",                                  // no name
            """$head,"name":"${"x".repeat(41)}","format":"booster","minutes":25}""",                // too long
            """$head,"name":"X","format":"rochester","minutes":25}""",                              // no presets for it
            """$head,"name":"X","format":"booster","minutes":0}""",                                 // no construction
            """$head,"name":"X","format":"booster","minutes":25,"picks":{"14":{"1":5000}}}""",     // time out of range
            """$head,"name":"X","format":"booster","minutes":25,"picks":{"100000":{"1":50}}}""",   // no such pack
            """$head,"name":"X","format":"booster","minutes":25,"reviews":{"42":60}}""",           // no such review
        ).forEach { assertEquals(it, SharedPresetResult.Invalid, DraftPresetShareCodec.decode(it)) }
    }
}
