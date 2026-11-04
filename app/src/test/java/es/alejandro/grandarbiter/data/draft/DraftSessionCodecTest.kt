package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.data.draft.DraftSessionCodec.Stored
import es.alejandro.grandarbiter.domain.draft.DraftConfig
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftSession
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.timer.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A draft must survive the app being killed mid-pick, and bad stored data must never crash. */
class DraftSessionCodecTest {

    private val session = DraftSession(DraftConfig(cardsPerPack = 15, constructionMinutes = 30), TimerState(12_345, 1_000))

    @Test fun roundTrip_running() = assertEquals(session, DraftSessionCodec.decode(DraftSessionCodec.encode(session)))

    @Test fun roundTrip_paused() {
        val paused = session.paused(50_000)
        assertEquals(paused, DraftSessionCodec.decode(DraftSessionCodec.encode(paused)))
    }

    @Test fun config_roundTrip() = assertEquals(session.config, DraftSessionCodec.decodeConfig(DraftSessionCodec.encodeConfig(session.config)))

    @Test fun unknownFormat_null() = assertNull(DraftSessionCodec.decode(Stored("WINSTON", 3, 14, 25)))

    @Test fun rochesterFirstTeam_roundTrip() {
        val rochester = DraftSession(DraftConfig.defaultsFor(DraftFormat.ROCHESTER).copy(firstTeam = 2), TimerState(5_000, 1_000), step = 7)
        assertEquals(rochester, DraftSessionCodec.decode(DraftSessionCodec.encode(rochester)))
    }

    @Test fun badFirstTeam_null() = assertNull(DraftSessionCodec.decode(Stored("ROCHESTER", 3, 15, 40, firstTeam = 3)))

    @Test fun outOfRange_null() {
        assertNull(DraftSessionCodec.decode(Stored("BOOSTER", 0, 14, 25)))
        assertNull(DraftSessionCodec.decode(Stored("BOOSTER", 3, 99, 25)))
        assertNull(DraftSessionCodec.decode(Stored("BOOSTER", 3, 14, 0)))
    }

    @Test fun negativeTime_null() = assertNull(DraftSessionCodec.decode(Stored("BOOSTER", 3, 14, 25, accumulatedMs = -1)))

    @Test fun manualStartAndStep_roundTrip() {
        val manual = DraftSession(DraftConfig(manualStart = true), TimerState(40_000, null), step = 3)
        assertEquals(manual, DraftSessionCodec.decode(DraftSessionCodec.encode(manual)))
        val auto = DraftConfig(manualStart = false)
        assertEquals(auto, DraftSessionCodec.decodeConfig(DraftSessionCodec.encodeConfig(auto)))
    }

    @Test fun draftFromAnOlderVersion_keepsRunningOnItsOwn() =
        assertEquals(false, DraftSessionCodec.decode(Stored("BOOSTER", 3, 14, 25))!!.config.manualStart)

    @Test fun lastConfigFromAnOlderVersion_getsTheFirstTimeDefault() =
        assertEquals(true, DraftSessionCodec.decodeConfig(Stored("BOOSTER", 3, 14, 25))!!.manualStart)

    @Test fun negativeStep_null() = assertNull(DraftSessionCodec.decode(Stored("BOOSTER", 3, 14, 25, step = -1)))

    @Test fun packSizes_roundTrip() {
        val mixed = DraftSession(DraftConfig(packSizes = listOf(15, 14, 14)), TimerState(5_000, null), step = 2)
        assertEquals("15,14,14", DraftSessionCodec.encode(mixed).packSizes)
        assertEquals(mixed, DraftSessionCodec.decode(DraftSessionCodec.encode(mixed)))
    }

    @Test fun noPackSizes_storedAsNull() = assertNull(DraftSessionCodec.encodeConfig(DraftConfig()).packSizes)

    @Test fun badPackSizes_null() {
        assertNull(DraftSessionCodec.decode(Stored("BOOSTER", 3, 14, 25, packSizes = "15,x,14")))
        assertNull(DraftSessionCodec.decode(Stored("BOOSTER", 3, 14, 25, packSizes = "15,14")))      // 3 packs, 2 sizes
        assertNull(DraftSessionCodec.decode(Stored("BOOSTER", 3, 14, 25, packSizes = "15,14,99")))
    }

    @Test fun missingFormat_null() = assertNull(DraftSessionCodec.decode(Stored(null, 3, 14, 25)))

    // ---- Since the per-step timer, the time counts from the start of the session's step.

    @Test fun storesItsTimeBase() = assertEquals(DraftSessionCodec.TIME_BASE_STEP, DraftSessionCodec.encode(session).timeBase)

    @Test fun draftFromAnOlderVersion_timeBecomesTimeIntoItsStep() {
        // 14-card Booster, MTR: pick 1 40 s, pick 2 35 s. Old time = from the draft start, 3 s into pick 3.
        val old = Stored("BOOSTER", 3, 14, 25, manualStart = true, accumulatedMs = 78_000, runningSince = 1_000, step = 2)
        val s = DraftSessionCodec.decode(old)!!
        assertEquals(TimerState(3_000, 1_000), s.timer)
        assertEquals(2, s.step)
    }

    @Test fun draftFromAnOlderVersion_neverNegative() =
        assertEquals(0L, DraftSessionCodec.decode(Stored("BOOSTER", 3, 14, 25, accumulatedMs = 10_000, step = 2))!!.timer.accumulatedMs)

    // ---- The judge's own times travel with the draft

    @Test fun customTimes_roundTrip() {
        val times = DraftTimes.MTR.withTimedPicks(false)
        val s = DraftSession(DraftConfig(constructionMinutes = 300, times = times), TimerState(5_000, null), step = 1)
        assertEquals(s, DraftSessionCodec.decode(DraftSessionCodec.encode(s)))
    }

    @Test fun mtrTimes_storedAsNull() = assertNull(DraftSessionCodec.encode(session).times)

    @Test fun badTimes_draftKeepsTheMtrs() =
        assertEquals(DraftTimes.MTR,
            DraftSessionCodec.decode(Stored("BOOSTER", 3, 14, 25, timeBase = DraftSessionCodec.TIME_BASE_STEP, times = "x"))!!.config.times)
}
