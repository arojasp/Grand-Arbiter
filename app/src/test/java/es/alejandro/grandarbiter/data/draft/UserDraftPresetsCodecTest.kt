package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.domain.draft.BoosterDraftTimings
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftTimeRows
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.DraftTimesPreset
import es.alejandro.grandarbiter.domain.draft.UserDraftPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Presets in SharedPreferences: round trip, and a bad entry never loses the others. */
class UserDraftPresetsCodecTest {

    private val rows14 = DraftTimeRows.booster(14, BoosterDraftTimings.FALLBACK)

    @Test fun roundTrip() {
        val presets = listOf(
            UserDraftPreset("a", "Cube night", DraftFormat.BOOSTER, DraftTimes.MTR.withAllPicks(10, rows14).withReview(1, 90, 60), 35),
            UserDraftPreset("b", "PT", DraftFormat.BOOSTER, DraftTimesPreset.PRO_TOUR.times, 25),
            UserDraftPreset("c", "Casual", DraftFormat.BOOSTER, DraftTimes.MTR.withTimedPicks(false), 40),
        )
        val decoded = UserDraftPresetsCodec.decode(DraftFormat.BOOSTER, UserDraftPresetsCodec.encode(presets))
        assertEquals(presets.sortedBy { it.name }, decoded)                 // read back by name
    }

    @Test fun nothingOrGarbage_isAnEmptyList() {
        assertTrue(UserDraftPresetsCodec.decode(DraftFormat.BOOSTER, null).isEmpty())
        assertTrue(UserDraftPresetsCodec.decode(DraftFormat.BOOSTER, "not json").isEmpty())
    }

    @Test fun badEntries_skipped_othersKept() {
        val text = """[
            {"id":"1","name":"Good","minutes":25},
            {"id":"2","name":"","minutes":25},
            {"id":"3","name":"Too long picks","picks":{"14":{"1":5000}},"minutes":25},
            {"id":"4","name":"No minutes"},
            {"id":"5","name":"good","minutes":30}
        ]"""
        assertEquals(listOf("1"), UserDraftPresetsCodec.decode(DraftFormat.BOOSTER, text).map { it.id })
    }
}
