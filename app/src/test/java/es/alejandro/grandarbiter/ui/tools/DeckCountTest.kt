package es.alejandro.grandarbiter.ui.tools

import es.alejandro.grandarbiter.ui.tools.deckcounter.CountStatus
import es.alejandro.grandarbiter.ui.tools.deckcounter.DeckCount
import es.alejandro.grandarbiter.ui.tools.deckcounter.DeckFormat
import es.alejandro.grandarbiter.ui.tools.deckcounter.Pile
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckCountTest {

    @Test fun constructed_main_minimum60_noMaximum() {
        assertEquals(CountStatus.Short(13), DeckCount.status(DeckFormat.CONSTRUCTED, Pile.MAIN, 47))
        assertEquals(CountStatus.Ok, DeckCount.status(DeckFormat.CONSTRUCTED, Pile.MAIN, 60))
        assertEquals(CountStatus.Ok, DeckCount.status(DeckFormat.CONSTRUCTED, Pile.MAIN, 75))   // MTR 6.1: no maximum
    }

    @Test fun constructed_sideboard_upTo15() {
        assertEquals(CountStatus.Ok, DeckCount.status(DeckFormat.CONSTRUCTED, Pile.SIDEBOARD, 0))
        assertEquals(CountStatus.Ok, DeckCount.status(DeckFormat.CONSTRUCTED, Pile.SIDEBOARD, 15))
        assertEquals(CountStatus.Over(2), DeckCount.status(DeckFormat.CONSTRUCTED, Pile.SIDEBOARD, 17))
    }

    @Test fun limited_40_andNoSideboardLimit() {
        assertEquals(CountStatus.Short(1), DeckCount.status(DeckFormat.LIMITED, Pile.MAIN, 39))
        assertEquals(CountStatus.Ok, DeckCount.status(DeckFormat.LIMITED, Pile.MAIN, 41))
        assertEquals(CountStatus.NoLimit, DeckCount.status(DeckFormat.LIMITED, Pile.SIDEBOARD, 45))
    }

    @Test fun commander_exactly100() {
        assertEquals(CountStatus.Short(1), DeckCount.status(DeckFormat.COMMANDER, Pile.MAIN, 99))
        assertEquals(CountStatus.Ok, DeckCount.status(DeckFormat.COMMANDER, Pile.MAIN, 100))
        assertEquals(CountStatus.Over(1), DeckCount.status(DeckFormat.COMMANDER, Pile.MAIN, 101))
    }

    @Test fun history_readsBackTheTaps() = assertEquals("4+4+3+1", DeckCount.history(listOf(4, 4, 3, 1)))
}
