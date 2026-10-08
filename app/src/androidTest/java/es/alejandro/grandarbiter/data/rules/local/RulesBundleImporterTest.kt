package es.alejandro.grandarbiter.data.rules.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.alejandro.grandarbiter.data.rules.bundle.Block
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.data.rules.bundle.Infraction
import es.alejandro.grandarbiter.data.rules.bundle.InfractionCategory
import es.alejandro.grandarbiter.data.rules.bundle.Penalty
import es.alejandro.grandarbiter.data.rules.bundle.RulesBundle
import es.alejandro.grandarbiter.data.rules.bundle.RulesDocument
import es.alejandro.grandarbiter.data.rules.bundle.RulesJson
import es.alejandro.grandarbiter.data.rules.bundle.RulesSection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room + FTS on a device/emulator. Placeholder text only, no official rules text. */
@RunWith(AndroidJUnit4::class)
class RulesBundleImporterTest {

    private lateinit var db: RulesDatabase
    private lateinit var dao: RulesDao
    private lateinit var importer: RulesBundleImporter

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RulesDatabase::class.java).build()
        dao = db.rulesDao()
        importer = RulesBundleImporter(db, dao)
    }

    @After
    fun tearDown() = db.close()

    private fun bundle(version: Long, extraWord: String = "alpha") = RulesBundle(
        schemaVersion = RulesBundle.SUPPORTED_SCHEMA_VERSION,
        bundleVersion = version,
        generatedAt = "2026-10-07T00:00:00Z",
        documents = listOf(
            RulesDocument(
                id = DocumentId.IPG,
                title = "IPG",
                effectiveDate = "2024-09-23",
                sourceUrl = "https://example.invalid/ipg.pdf",
                sections = listOf(
                    RulesSection(id = "ipg/2", number = "2", title = "Game Play Errors",
                        blocks = listOf(Block.Paragraph("intro"))),
                    RulesSection(id = "ipg/2.1", parentId = "ipg/2", number = "2.1", title = "Missed Trigger",
                        infraction = Infraction(InfractionCategory.GAME_PLAY_ERROR, Penalty.NONE),
                        blocks = listOf(Block.Paragraph("placeholder $extraWord trigger text"))),
                ),
            ),
        ),
    )

    private fun source(b: RulesBundle) = RulesBundleSource { RulesJson.encodeToString(b) }

    @Test
    fun import_thenSearchAndLookup() = runBlocking {
        val result = importer.import(source(bundle(1)))
        assertEquals(ImportResult.Imported(1, 1, 2), result)

        val hits = dao.search("trigger*", null)
        assertEquals(listOf("ipg/2.1"), hits.map { it.id })
        assertEquals("NONE", dao.section("ipg/2.1")?.penalty)
        assertEquals(1, dao.section("ipg/2.1")?.level)
        assertEquals(listOf("ipg/2.1"), dao.byNumber("ipg", "2.1").map { it.id })
    }

    @Test
    fun sameOrOlderVersion_isUpToDate() = runBlocking {
        importer.import(source(bundle(5)))
        assertEquals(ImportResult.UpToDate(5), importer.import(source(bundle(5))))
        assertEquals(ImportResult.UpToDate(5), importer.import(source(bundle(4))))
    }

    @Test
    fun reimport_replacesContent_andFtsHasNoStaleRows() = runBlocking {
        importer.import(source(bundle(1, extraWord = "alpha")))
        importer.import(source(bundle(2, extraWord = "beta")))

        assertEquals(emptyList<String>(), dao.search("alpha", null).map { it.id })   // old text gone from FTS
        assertEquals(listOf("ipg/2.1"), dao.search("beta", null).map { it.id })
        assertEquals(listOf("ipg/2.1"), dao.search("trigger*", null).map { it.id }) // not duplicated
        assertEquals(2L, dao.installedBundleVersion())
    }

    @Test
    fun invalidBundle_isRejected_andPreviousContentSurvives() = runBlocking {
        importer.import(source(bundle(1)))
        val broken = bundle(2).let { b ->
            b.copy(documents = b.documents.map { d ->
                d.copy(sections = d.sections + RulesSection(id = "ipg/9", number = "9", title = "Bad",
                    blocks = listOf(Block.Paragraph("see [x](ref:ipg/404)"))))
            })
        }
        val result = importer.import(source(broken))
        assertTrue(result.toString(), result is ImportResult.Rejected)
        assertEquals(1L, dao.installedBundleVersion())
        assertEquals(listOf("ipg/2.1"), dao.search("trigger*", null).map { it.id })
    }

    @Test
    fun missingBundle_isNoBundle() = runBlocking {
        assertEquals(ImportResult.NoBundle, importer.import({ null }))
    }

    @Test
    fun newerSchema_isRejected() = runBlocking {
        val future = bundle(9).copy(schemaVersion = RulesBundle.SUPPORTED_SCHEMA_VERSION + 1)
        assertTrue(importer.import(source(future)) is ImportResult.Rejected)
    }
}
