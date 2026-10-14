package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.data.draft.DraftSessionCodec.Stored
import es.alejandro.grandarbiter.domain.draft.DraftConfig
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftSession
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

    @Test fun missingFormat_null() = assertNull(DraftSessionCodec.decode(Stored(null, 3, 14, 25)))
}
