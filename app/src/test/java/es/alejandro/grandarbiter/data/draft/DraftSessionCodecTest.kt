package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.data.draft.DraftSessionCodec.Stored
import es.alejandro.grandarbiter.domain.draft.DraftConfig
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

    @Test fun formatNotAvailableYet_null() = assertNull(DraftSessionCodec.decode(Stored("ROCHESTER", 3, 14, 25)))

    @Test fun outOfRange_null() {
        assertNull(DraftSessionCodec.decode(Stored("BOOSTER", 0, 14, 25)))
        assertNull(DraftSessionCodec.decode(Stored("BOOSTER", 3, 99, 25)))
        assertNull(DraftSessionCodec.decode(Stored("BOOSTER", 3, 14, 0)))
    }

    @Test fun negativeTime_null() = assertNull(DraftSessionCodec.decode(Stored("BOOSTER", 3, 14, 25, accumulatedMs = -1)))

    @Test fun missingFormat_null() = assertNull(DraftSessionCodec.decode(Stored(null, 3, 14, 25)))
}
