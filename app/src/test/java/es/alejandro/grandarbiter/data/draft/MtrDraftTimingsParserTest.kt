package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.data.rules.bundle.Block
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.data.rules.bundle.RulesBundle
import es.alejandro.grandarbiter.domain.draft.BoosterDraftTimings
import es.alejandro.grandarbiter.domain.draft.RochesterTimings
import es.alejandro.grandarbiter.domain.draft.TwoHeadedGiantTimings
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

    // ---- Rochester ----

    private fun appendixBWithRochester(text: String = "The review period for a booster after it has been laid out on the table and before the first card is drafted is 20 seconds. Players have 5 seconds for each pick.") =
        appendixB().filterNot { it is Block.Heading && it.text == "Rochester Draft Timing" || it is Block.Paragraph && it.text.startsWith("Players have") } +
            listOf(Block.Heading("Rochester Draft Timing"), Block.Paragraph(text))

    @Test fun rochester_reviewPickAndTeamDraftConstruction() =
        assertEquals(RochesterTimings(20, 5, 40), MtrDraftTimingsParser.parseRochester(appendixBWithRochester()))

    @Test fun rochester_reworded_null() =
        assertNull(MtrDraftTimingsParser.parseRochester(appendixBWithRochester("Picks are quick.")))

    @Test fun rochester_missing_fallsBackAlone() {
        val all = MtrDraftTimingsParser.parseAll(appendixB().filterNot { it is Block.Heading && it.text == "Rochester Draft Timing" })
        assertEquals(RochesterTimings.FALLBACK, all.rochester)
        assertEquals(25, all.booster.constructionMinutes)
    }

    // ---- Two-Headed Giant ----

    private val thgTable = Block.Table(
        header = listOf("Cards remaining in pack — 15-Card Booster", "14-Card Booster", "Time allotted"),
        rows = listOf(
            listOf("15", "14", "50 seconds"), listOf("13", "12", "45 seconds"),
            listOf("3", "-", "5 seconds"), listOf("1", "2", "N/A"),
        ),
    )

    private fun appendixBWith2hg(
        tableBlock: Block = thgTable,
        review: String = "In addition, players receive 60 seconds to review their drafted cards in between booster packs.",
        construction: String = "Two-Headed Giant Draft—40 minutes for deck construction and registration",
    ) = appendixB() + listOf(
        Block.BulletList(listOf(construction)),
        Block.Heading("Two-Headed Giant Draft Timing"),
        Block.Paragraph("Two-Headed Giant Booster Drafts have the following default time limits for each pick:"),
        tableBlock,
        Block.Paragraph(review),
    )

    @Test fun twoHeadedGiant_readsBothColumns_reviewAndConstruction() {
        val t = MtrDraftTimingsParser.parseTwoHeadedGiant(appendixBWith2hg())!!
        assertEquals(mapOf(15 to 50, 13 to 45, 3 to 5), t.pickSeconds[15])   // 1 card: N/A
        assertEquals(mapOf(14 to 50, 12 to 45), t.pickSeconds[14])           // "-" and 2 cards: N/A
        assertEquals(60, t.reviewSeconds)
        assertEquals(40, t.constructionMinutes)
    }

    @Test fun twoHeadedGiant_constructionLine_notTakenFromDraft() =
        assertEquals(25, MtrDraftTimingsParser.parse(appendixBWith2hg())!!.constructionMinutes)

    @Test fun twoHeadedGiant_missing_null_andParseAllFallsBack() {
        assertNull(MtrDraftTimingsParser.parseTwoHeadedGiant(appendixB()))
        val all = MtrDraftTimingsParser.parseAll(appendixB())
        assertEquals(TwoHeadedGiantTimings.FALLBACK, all.twoHeadedGiant)
        assertEquals(25, all.booster.constructionMinutes)                  // Booster still read
    }

    @Test fun twoHeadedGiant_rewordedReview_null() =
        assertNull(MtrDraftTimingsParser.parseTwoHeadedGiant(appendixBWith2hg(review = "One minute to review.")))

    @Test fun twoHeadedGiant_tableShapeChanged_null() =
        assertNull(MtrDraftTimingsParser.parseTwoHeadedGiant(appendixBWith2hg(tableBlock = table)))

    /** The REAL MTR shipped in the APK. If Wizards changes Appendix B, this fails at build time. */
    @Test fun bundledMtr_parsesToTheKnownValues() {
        val file = File("src/main/assets/rules/bundle.json")   // Gradle runs unit tests from app/
        assumeTrue("no bundled rules (${file.absolutePath})", file.exists())
        val bundle = Json { ignoreUnknownKeys = false }.decodeFromString<RulesBundle>(file.readText())
        val mtr = bundle.documents.first { it.id == DocumentId.MTR }
        val blocks = mtr.sections.first { it.id == MtrDraftTimingsParser.PAGE_ID }.blocks
        assertEquals(BoosterDraftTimings.FALLBACK, MtrDraftTimingsParser.parse(blocks))
        assertEquals(TwoHeadedGiantTimings.FALLBACK, MtrDraftTimingsParser.parseTwoHeadedGiant(blocks))
        assertEquals(RochesterTimings.FALLBACK, MtrDraftTimingsParser.parseRochester(blocks))
    }
}
