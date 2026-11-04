package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.domain.draft.BoosterDraftTimings
import es.alejandro.grandarbiter.domain.draft.DraftTimeRows
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The judge's times survive the app being closed; bad stored data falls back to the MTR's. */
class DraftTimesCodecTest {

    private val rows = DraftTimeRows.booster(14, BoosterDraftTimings.FALLBACK)
    private val custom = DraftTimes.MTR.withPick(rows[0], 45).withPick(rows[1], 40)
        .withReview(2, 120, mtrSeconds = 90).withReview(DraftTimes.EVERY_REVIEW, 70, mtrSeconds = 60)

    @Test fun mtr_storedAsNull() = assertNull(DraftTimesCodec.encode(DraftTimes.MTR))

    @Test fun roundTrip() = assertEquals(custom, DraftTimesCodec.decode(DraftTimesCodec.encode(custom)))

    @Test fun untimed_roundTrip() {
        val untimed = custom.withTimedPicks(false)
        assertEquals(untimed, DraftTimesCodec.decode(DraftTimesCodec.encode(untimed)))
        val onlyUntimed = DraftTimes.MTR.withTimedPicks(false)
        assertEquals(onlyUntimed, DraftTimesCodec.decode(DraftTimesCodec.encode(onlyUntimed)))
    }

    @Test fun nullOrBlank_isMtr() {
        assertEquals(DraftTimes.MTR, DraftTimesCodec.decode(null))
        assertEquals(DraftTimes.MTR, DraftTimesCodec.decode(""))
    }

    @Test fun garbage_isMtr() = assertEquals(DraftTimes.MTR, DraftTimesCodec.decode("{picks:"))

    @Test fun outOfRange_isMtr() = assertEquals(DraftTimes.MTR, DraftTimesCodec.decode("""{"picks":{"14":{"1":1000}}}"""))

    @Test fun unknownFields_ignored() =
        assertEquals(DraftTimes(pickSeconds = mapOf(14 to mapOf(1 to 45))), DraftTimesCodec.decode("""{"picks":{"14":{"1":45}},"future":1}"""))
}
