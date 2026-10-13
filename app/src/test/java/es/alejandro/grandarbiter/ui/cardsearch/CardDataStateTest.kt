package es.alejandro.grandarbiter.ui.cardsearch

import es.alejandro.grandarbiter.domain.model.cards.CardDataInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the card search shows while the card data is (or isn't) there. */
class CardDataStateTest {

    private val info = CardDataInfo("2026-10-01", "2026-10-01", "seed", 0, 35_000)

    @Test fun nothingKnownYet_checking() =
        assertEquals(CardDataState.Checking, CardDataState.of(metaRead = false, info = null, importing = null))

    @Test fun importRunning_beforeTheDatabaseAnswers_preparing() =
        assertEquals(CardDataState.Preparing(null), CardDataState.of(metaRead = false, info = null, importing = 0))

    @Test fun importRunning_withProgress() =
        assertEquals(CardDataState.Preparing(12_500), CardDataState.of(metaRead = true, info = null, importing = 12_500))

    @Test fun emptyDatabase_importAboutToStart_preparing() =
        assertEquals(CardDataState.Preparing(null), CardDataState.of(metaRead = true, info = null, importing = null))

    @Test fun dataInstalled_ready() =
        assertEquals(CardDataState.Ready(info), CardDataState.of(metaRead = true, info = info, importing = null))

    @Test fun updateImport_oldDataStillSearchable_ready() =
        assertEquals(CardDataState.Ready(info), CardDataState.of(metaRead = true, info = info, importing = 20_000))
}
