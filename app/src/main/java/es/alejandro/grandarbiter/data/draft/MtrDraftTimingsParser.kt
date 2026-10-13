package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.data.rules.bundle.Block
import es.alejandro.grandarbiter.domain.draft.BoosterDraftTimings
import es.alejandro.grandarbiter.domain.draft.DraftTimings
import es.alejandro.grandarbiter.domain.draft.TwoHeadedGiantTimings

/**
 * Reads the draft times from the installed MTR's Appendix B (page [PAGE_ID]).
 *
 * Booster Draft:
 * - the table under the heading "Booster Draft Timing" (cards remaining -> seconds; "N/A" = no timer),
 * - the review sentence after it ("…first booster pack is 60 seconds… increases by 30 seconds"),
 * - the list item "Draft—25 minutes for deck registration and construction."
 *
 * Two-Headed Giant:
 * - the table under "Two-Headed Giant Draft Timing" (15-card column, 14-card column, time),
 * - "…receive 60 seconds to review their drafted cards in between booster packs",
 * - the list item "Two-Headed Giant Draft—40 minutes for deck construction and registration".
 *
 * Each format is all or nothing: if any part is missing or changed shape, null, and the caller
 * uses that format's FALLBACK. A unit test runs this on the bundled MTR, so a format change is
 * caught at build time, not at a draft table.
 */
object MtrDraftTimingsParser {

    const val PAGE_ID = "mtr/appendix-b"

    private val NUMBER = Regex("""(\d+)""")
    private val CARDS = Regex("""(\d+)\s*cards?""", RegexOption.IGNORE_CASE)
    private val SECONDS = Regex("""(\d+)\s*seconds?""", RegexOption.IGNORE_CASE)
    private val FIRST_REVIEW = Regex("""review after the first booster pack is (\d+) seconds""", RegexOption.IGNORE_CASE)
    private val REVIEW_INCREMENT = Regex("""increases by (\d+) seconds""", RegexOption.IGNORE_CASE)
    private val CONSTRUCTION = Regex("""^Draft\s*[—–-]\s*(\d+)\s*minutes""", RegexOption.IGNORE_CASE)
    private val THG_REVIEW = Regex("""(\d+) seconds to review""", RegexOption.IGNORE_CASE)
    private val THG_CONSTRUCTION = Regex("""^Two-Headed Giant Draft\s*[—–-]\s*(\d+)\s*minutes""", RegexOption.IGNORE_CASE)

    /** Every format, each falling back on its own if unreadable. */
    fun parseAll(blocks: List<Block>): DraftTimings = DraftTimings(
        booster = parse(blocks) ?: BoosterDraftTimings.FALLBACK,
        twoHeadedGiant = parseTwoHeadedGiant(blocks) ?: TwoHeadedGiantTimings.FALLBACK,
    )

    /** Booster Draft; null if Appendix B doesn't read as expected. */
    fun parse(blocks: List<Block>): BoosterDraftTimings? {
        val section = section(blocks, "Booster Draft Timing") ?: return null
        val table = section.filterIsInstance<Block.Table>().firstOrNull() ?: return null
        val pickSeconds = mutableMapOf<Int, Int>()
        for (row in table.rows) {
            val cards = row.getOrNull(0)?.let { CARDS.find(it) }?.groupValues?.get(1)?.toInt() ?: return null
            when (val seconds = seconds(row.getOrNull(1) ?: return null)) {
                null -> return null
                NO_TIMER -> Unit
                else -> pickSeconds[cards] = seconds
            }
        }
        if (pickSeconds.isEmpty()) return null

        val text = paragraphs(section)
        val firstReview = FIRST_REVIEW.find(text)?.groupValues?.get(1)?.toInt() ?: return null
        val increment = REVIEW_INCREMENT.find(text)?.groupValues?.get(1)?.toInt() ?: return null
        val construction = listItem(blocks, CONSTRUCTION) ?: return null
        return BoosterDraftTimings(pickSeconds, firstReview, increment, construction)
    }

    /** Two-Headed Giant; null if Appendix B doesn't read as expected. */
    fun parseTwoHeadedGiant(blocks: List<Block>): TwoHeadedGiantTimings? {
        val section = section(blocks, "Two-Headed Giant Draft Timing") ?: return null
        val table = section.filterIsInstance<Block.Table>().firstOrNull() ?: return null
        // Header: "Cards remaining in pack — 15-Card Booster", "14-Card Booster", "Time allotted".
        if (table.header.size != 3) return null
        val sizes = table.header.take(2).map { NUMBER.find(it)?.groupValues?.get(1)?.toInt() ?: return null }
        val pickSeconds = sizes.associateWith { mutableMapOf<Int, Int>() }
        for (row in table.rows) {
            if (row.size != 3) return null
            val seconds = seconds(row[2]) ?: return null
            for ((column, size) in sizes.withIndex()) {
                val cards = NUMBER.find(row[column])?.groupValues?.get(1)?.toInt() ?: continue   // "-": no row for this size
                if (seconds != NO_TIMER) pickSeconds.getValue(size)[cards] = seconds
            }
        }
        if (pickSeconds.values.any { it.isEmpty() }) return null

        val review = THG_REVIEW.find(paragraphs(section))?.groupValues?.get(1)?.toInt() ?: return null
        val construction = listItem(blocks, THG_CONSTRUCTION) ?: return null
        return TwoHeadedGiantTimings(pickSeconds, review, construction)
    }

    /** The blocks after [heading], up to the next heading; null if there's no such heading. */
    private fun section(blocks: List<Block>, heading: String): List<Block>? {
        val at = blocks.indexOfFirst { it is Block.Heading && it.text.trim().equals(heading, ignoreCase = true) }
        if (at < 0) return null
        return blocks.drop(at + 1).takeWhile { it !is Block.Heading }
    }

    private fun paragraphs(section: List<Block>): String =
        section.filterIsInstance<Block.Paragraph>().joinToString(" ") { it.text }

    private fun listItem(blocks: List<Block>, pattern: Regex): Int? = blocks.asSequence()
        .filterIsInstance<Block.BulletList>()
        .flatMap { it.items }
        .firstNotNullOfOrNull { pattern.find(it.trim())?.groupValues?.get(1)?.toInt() }

    /** "40 seconds" -> 40, "N/A" -> [NO_TIMER], anything else -> null (unreadable). */
    private fun seconds(cell: String): Int? = SECONDS.find(cell)?.groupValues?.get(1)?.toInt()
        ?: if (cell.trim().equals("N/A", ignoreCase = true)) NO_TIMER else null

    private const val NO_TIMER = -1
}
