package es.alejandro.grandarbiter.data.rules

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.alejandro.grandarbiter.data.rules.bundle.Block
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.data.rules.bundle.HeadingRole
import es.alejandro.grandarbiter.data.rules.bundle.Infraction
import es.alejandro.grandarbiter.data.rules.bundle.InfractionCategory
import es.alejandro.grandarbiter.data.rules.bundle.Penalty
import es.alejandro.grandarbiter.data.rules.bundle.RulesBundle
import es.alejandro.grandarbiter.data.rules.bundle.RulesDocument
import es.alejandro.grandarbiter.data.rules.bundle.RulesJson
import es.alejandro.grandarbiter.data.rules.bundle.RulesSection
import es.alejandro.grandarbiter.data.rules.local.ImportResult
import es.alejandro.grandarbiter.data.rules.local.RulesBundleImporter
import es.alejandro.grandarbiter.data.rules.local.RulesBundleSource
import es.alejandro.grandarbiter.data.rules.local.RulesDatabase
import es.alejandro.grandarbiter.domain.model.rules.PageRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Page boundaries, link resolution and search on a real Room DB. Placeholder text only. */
@RunWith(AndroidJUnit4::class)
class RulesRepositoryImplTest {

    private lateinit var db: RulesDatabase
    private lateinit var repo: RulesRepositoryImpl

    private fun p(t: String) = listOf(Block.Paragraph(t))

    private val bundle = RulesBundle(
        schemaVersion = RulesBundle.SUPPORTED_SCHEMA_VERSION,
        bundleVersion = 1,
        generatedAt = "2026-10-07T00:00:00Z",
        documents = listOf(
            RulesDocument(
                id = DocumentId.CR, title = "Comprehensive Rules", effectiveDate = "2026-09-25",
                sourceUrl = "https://example.invalid/cr.txt",
                sections = listOf(
                    RulesSection("cr/1", number = "1", title = "Game Concepts"),
                    RulesSection("cr/100", "cr/1", "100", "General"),
                    RulesSection("cr/100.1", "cr/100", "100.1", blocks = p("placeholder rule alpha")),
                    RulesSection("cr/100.1a", "cr/100.1", "100.1a", blocks = p("placeholder subrule beta, see [101.1](ref:cr/101.1)")),
                    RulesSection("cr/101", "cr/1", "101", "Golden Rules"),
                    RulesSection("cr/101.1", "cr/101", "101.1", blocks = p("placeholder gamma")),
                    RulesSection("cr/7", number = "7", title = "Additional Rules"),
                    RulesSection("cr/700", "cr/7", "700", "General", blocks = p("placeholder delta")),
                ),
            ),
            RulesDocument(
                id = DocumentId.IPG, title = "IPG", effectiveDate = "2024-09-23",
                sourceUrl = "https://example.invalid/ipg.pdf",
                sections = listOf(
                    RulesSection("ipg/introduction", title = "Introduction", blocks = p("placeholder intro")),
                    RulesSection("ipg/2", number = "2", title = "Game Play Errors", blocks = p("placeholder chapter intro")),
                    RulesSection("ipg/2.1", "ipg/2", "2.1", "Game Play Error — Missed Trigger",
                        blocks = listOf(Block.Heading("Definition", HeadingRole.DEFINITION), Block.Paragraph("placeholder trigger text")),
                        infraction = Infraction(InfractionCategory.GAME_PLAY_ERROR, Penalty.NONE)),
                ),
            ),
        ),
    )

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RulesDatabase::class.java).build()
        val result = RulesBundleImporter(db, db.rulesDao()).import(RulesBundleSource { RulesJson.encodeToString(bundle) })
        assertTrue(result.toString(), result is ImportResult.Imported)
        repo = RulesRepositoryImpl(db.rulesDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun documentsHaveTheirEffectiveDates() = runBlocking {
        val docs = repo.observeDocuments().first()
        assertEquals(listOf(DocumentId.CR, DocumentId.IPG), docs.map { it.id })
        assertEquals(LocalDate.of(2026, 9, 25), docs[0].effectiveDate)
    }

    @Test
    fun crContentsAreChaptersWithSectionPages() = runBlocking {
        val toc = repo.contents(DocumentId.CR)
        assertEquals(listOf("cr/1", "cr/7"), toc.map { it.sectionId })
        assertEquals(listOf("cr/100", "cr/101"), toc[0].children.map { it.sectionId })
        assertEquals(false, toc[0].opensPage)            // a chapter is only a group
        assertTrue(toc[0].children.all { it.opensPage })
    }

    @Test
    fun aPageIsTheSectionAndEverythingUnderIt() = runBlocking {
        val page = repo.page("cr/100")!!
        assertEquals(listOf("cr/100", "cr/100.1", "cr/100.1a"), page.sections.map { it.id })   // stops before cr/101
        assertEquals(listOf(0, 1, 2), page.sections.map { it.depth })
        val last = repo.page("cr/700")!!                                                     // last section of the doc
        assertEquals(listOf("cr/700"), last.sections.map { it.id })
    }

    @Test
    fun linksResolveToTheirPage() = runBlocking {
        assertEquals(PageRef("cr/100", "cr/100.1a"), repo.resolve("cr/100.1a"))
        assertEquals(PageRef("cr/101", "cr/101.1"), repo.resolve("cr/101.1"))
        assertEquals(PageRef("cr/100"), repo.resolve("cr/100"))
        assertNull(repo.resolve("cr/999.9"))
        assertEquals(listOf("cr/100", "cr/100.1", "cr/100.1a"), repo.page("cr/100.1a")!!.sections.map { it.id })
    }

    @Test
    fun ipgChapterIntroIsItsOwnPage() = runBlocking {
        val toc = repo.contents(DocumentId.IPG)
        val chapter = toc.first { it.sectionId == "ipg/2" }
        assertTrue(chapter.opensPage)                                   // has its own text
        assertEquals(Penalty.NONE, chapter.children.single().penalty)
        assertTrue(toc.first { it.sectionId == "ipg/introduction" }.opensPage)   // no children
        assertEquals(listOf("ipg/2"), repo.page("ipg/2")!!.sections.map { it.id })   // intro only, not 2.1
        val infraction = repo.page("ipg/2.1")!!.root
        assertEquals(Penalty.NONE, infraction.infraction?.penalty)
        assertEquals(HeadingRole.DEFINITION, (infraction.blocks.first() as Block.Heading).role)
    }

    @Test
    fun searchByWordsAndByNumber() = runBlocking {
        val words = repo.search("trigg", null)
        assertEquals(listOf("ipg/2.1"), words.map { it.target.pageId })
        val number = repo.search("100.1", DocumentId.CR)
        assertEquals(listOf("cr/100.1", "cr/100.1a"), number.map { it.target.anchorId })
        assertTrue(number.all { it.target.pageId == "cr/100" })
        assertEquals(emptyList<Any>(), repo.search("   ", null))
    }
}
