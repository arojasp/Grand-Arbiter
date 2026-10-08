package es.alejandro.grandarbiter.data.cards

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.alejandro.grandarbiter.data.cards.local.CardDataSource
import es.alejandro.grandarbiter.data.cards.local.CardDataVersion
import es.alejandro.grandarbiter.data.cards.local.CardImportResult
import es.alejandro.grandarbiter.data.cards.local.CardImporter
import es.alejandro.grandarbiter.data.cards.local.CardsDao
import es.alejandro.grandarbiter.data.cards.local.CardsDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

/** Real Room + FTS (unicode61) on a device/emulator. Placeholder cards only. */
@RunWith(AndroidJUnit4::class)
class CardImporterTest {

    private lateinit var db: CardsDatabase
    private lateinit var dao: CardsDao
    private lateinit var importer: CardImporter
    private lateinit var repo: CardRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CardsDatabase::class.java).build()
        dao = db.cardsDao()
        importer = CardImporter(db, dao, minCards = 2)
        repo = CardRepositoryImpl(dao)
    }

    @After
    fun tearDown() = db.close()

    private fun bytes(lines: List<String>): ByteArray = lines.joinToString("\n").toByteArray()

    private fun source(
        oracleAt: String = "2026-10-07T09:00:00.000+00:00",
        cards: List<String> = CARDS,
        rulings: List<String> = RULINGS,
    ) = object : CardDataSource {
        override val name = "test"
        override suspend fun version() = CardDataVersion(oracleAt, "2026-10-07T09:00:00.000+00:00")
        override fun openOracleCards() = ByteArrayInputStream(bytes(cards))
        override fun openRulings() = ByteArrayInputStream(bytes(rulings))
    }

    @Test
    fun import_filtersCards_dedupesRulings_andSearchWorks() = runBlocking {
        val result = importer.import(source()) as CardImportResult.Imported
        assertEquals(3, result.cards)                 // art series dropped
        assertEquals(2, result.rulingTexts)           // shared text stored once
        assertEquals(3, result.rulingRefs)            // duplicate (same card, later date) skipped

        assertEquals("Alpha Bolt", repo.search("alpha bo").first().name)
        assertEquals("Lim-Dûl Placeholder", repo.search("lim dul").single().name)   // diacritics folded
        assertTrue(repo.search("zap", inText = false).isEmpty())
        assertEquals(1, repo.search("zap", inText = true).size)
        // exact name first, tokens after cards
        assertEquals(listOf("Alpha Bolt", "Alpha Bolt Token"), repo.search("alpha bolt").map { it.name })

        val detail = repo.card("a1")
        assertNotNull(detail)
        assertEquals(listOf("2020-01-01", "2021-01-01"), detail!!.rulings.map { it.date })
    }

    @Test
    fun sameVersion_isUpToDate_newer_replacesEverything() = runBlocking {
        importer.import(source())
        assertTrue(importer.import(source()) is CardImportResult.UpToDate)
        val newer = importer.import(source(oracleAt = "2026-10-14T09:00:00.000+00:00", cards = CARDS.take(2)))
        assertTrue(newer is CardImportResult.Imported)
        assertEquals(2, dao.cardCount())
    }

    @Test
    fun brokenUpdate_isRejected_andOldDataStays() = runBlocking {
        importer.import(source())
        val result = importer.import(source(oracleAt = "2026-10-14T09:00:00.000+00:00", cards = CARDS.take(1)))
        assertTrue(result is CardImportResult.Rejected)
        assertEquals(3, dao.cardCount())
        assertEquals("2026-10-07T09:00:00.000+00:00", dao.meta()!!.oracleUpdatedAt)
    }

    private companion object {
        val CARDS = listOf(
            """{"oracle_id":"a1","name":"Alpha Bolt","layout":"normal","type_line":"Instant","oracle_text":"zap","games":["paper"],"legalities":{"modern":"legal"}}""",
            """{"oracle_id":"t1","name":"Alpha Bolt Token","layout":"token","type_line":"Token Creature","games":["paper"]}""",
            """{"oracle_id":"l1","name":"Lim-Dûl Placeholder","layout":"normal","games":["paper"],"some_new_field":1}""",
            """{"oracle_id":"x1","name":"Art","layout":"art_series","games":["paper"]}""",
        )
        val RULINGS = listOf(
            """{"oracle_id":"a1","source":"wotc","published_at":"2021-01-01","comment":"shared"}""",
            """{"oracle_id":"a1","source":"wotc","published_at":"2020-01-01","comment":"own"}""",
            """{"oracle_id":"l1","source":"wotc","published_at":"2021-01-01","comment":"shared"}""",
            """{"oracle_id":"a1","source":"wotc","published_at":"2023-01-01","comment":"own"}""",
            """{"oracle_id":"x1","source":"wotc","published_at":"2021-01-01","comment":"dropped card"}""",
        )
    }
}
