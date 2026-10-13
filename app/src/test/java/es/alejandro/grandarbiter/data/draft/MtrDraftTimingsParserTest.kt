package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.data.rules.bundle.Block
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.data.rules.bundle.RulesBundle
import es.alejandro.grandarbiter.domain.draft.BoosterDraftTimings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Draft times come from the installed MTR: the parser must read today's Appendix B exactly. */
class MtrDraftTimingsParserTest {

    private val table = Block.Table(
        header = listOf("Cards remaining in pack", "Time allotted"),
        rows = listOf(
            listOf("15 cards", "40 seconds"), listOf("14 cards", "40 seconds"), listOf("2 cards", "5 seconds"),
            listOf("1 card", "N/A"),
        ),
    )

    private fun appendixB(
        construction: String = "Draft—25 minutes for deck registration and construction.",
        tableBlock: Block = table,
        review: String = "The time for review after the first booster pack is 60 seconds. Each subsequent review period increases by 30 seconds.",
    ) = listOf(
        Block.Paragraph("The **required** minimum time limit for any match is 40 minutes."),
        Block.BulletList(listOf(
            "Sealed Deck—20 minutes for deck registration and 30 minutes for deck construction.",
            construction,
            "Team Draft—40 minutes for deck construction and registration.",
        )),
        Block.Heading("Booster Draft Timing"),
        Block.Paragraph("Individual Booster Drafts have the following default time limits for each pick:"),
        tableBlock,
        Block.Paragraph(review),
        Block.Heading("Rochester Draft Timing"),
        Block.Paragraph("Players have 5 seconds for each pick."),
    )

    @Test fun readsTheTable_reviewAndConstruction() {
        val t = MtrDraftTimingsParser.parse(appendixB())!!
        assertEquals(mapOf(15 to 40, 14 to 40, 2 to 5), t.pickSeconds)   // 1 card: N/A, no timer
        assertEquals(60, t.firstReviewSeconds)
        assertEquals(30, t.reviewIncrementSeconds)
        assertEquals(25, t.constructionMinutes)
    }

    @Test fun teamDraftLine_isNotTakenForDraft() {
        val t = MtrDraftTimingsParser.parse(appendixB(construction = "Draft – 30 minutes for deck registration and construction."))!!
        assertEquals(30, t.constructionMinutes)
    }

    @Test fun noHeading_null() = assertNull(MtrDraftTimingsParser.parse(appendixB().filterNot { it is Block.Heading }))

    @Test fun noTable_null() = assertNull(MtrDraftTimingsParser.parse(appendixB(tableBlock = Block.Paragraph("moved"))))

    @Test fun unreadableTime_null() {
        val bad = table.copy(rows = listOf(listOf("14 cards", "forty")))
        assertNull(MtrDraftTimingsParser.parse(appendixB(tableBlock = bad)))
    }

    @Test fun reworded_review_null() = assertNull(MtrDraftTimingsParser.parse(appendixB(review = "Review: one minute.")))

    @Test fun noConstructionLine_null() = assertNull(MtrDraftTimingsParser.parse(appendixB(construction = "Something else")))

    /** The REAL MTR shipped in the APK. If Wizards changes Appendix B, this fails at build time. */
    @Test fun bundledMtr_parsesToTheKnownValues() {
        val file = File("src/main/assets/rules/bundle.json")   // Gradle runs unit tests from app/
        assumeTrue("no bundled rules (${file.absolutePath})", file.exists())
        val bundle = Json { ignoreUnknownKeys = false }.decodeFromString<RulesBundle>(file.readText())
        val mtr = bundle.documents.first { it.id == DocumentId.MTR }
        val blocks = mtr.sections.first { it.id == MtrDraftTimingsParser.PAGE_ID }.blocks
        assertEquals(BoosterDraftTimings.FALLBACK, MtrDraftTimingsParser.parse(blocks))
    }
}
