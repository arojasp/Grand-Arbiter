package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.data.rules.bundle.Block
import es.alejandro.grandarbiter.domain.draft.BoosterDraftTimings

/**
 * Reads the Booster Draft times from the installed MTR's Appendix B (page [PAGE_ID]):
 * - the table under the heading "Booster Draft Timing" (cards remaining -> seconds; "N/A" = no timer),
 * - the review sentence after it ("…first booster pack is 60 seconds… increases by 30 seconds"),
 * - the list item "Draft—25 minutes for deck registration and construction."
 * All or nothing: if any part is missing or changed shape, null, and the caller uses
 * [BoosterDraftTimings.FALLBACK]. A unit test runs this on the bundled MTR, so a format change is
 * caught at build time, not at a draft table.
 */
object MtrDraftTimingsParser {

    const val PAGE_ID = "mtr/appendix-b"

    private val CARDS = Regex("""(\d+)\s*cards?""", RegexOption.IGNORE_CASE)
    private val SECONDS = Regex("""(\d+)\s*seconds?""", RegexOption.IGNORE_CASE)
    private val FIRST_REVIEW = Regex("""review after the first booster pack is (\d+) seconds""", RegexOption.IGNORE_CASE)
    private val REVIEW_INCREMENT = Regex("""increases by (\d+) seconds""", RegexOption.IGNORE_CASE)
    private val CONSTRUCTION = Regex("""^Draft\s*[—–-]\s*(\d+)\s*minutes""", RegexOption.IGNORE_CASE)

    fun parse(blocks: List<Block>): BoosterDraftTimings? {
        val headingAt = blocks.indexOfFirst { it is Block.Heading && it.text.trim().equals("Booster Draft Timing", ignoreCase = true) }
        if (headingAt < 0) return null
        val section = blocks.drop(headingAt + 1).takeWhile { it !is Block.Heading }

        val table = section.filterIsInstance<Block.Table>().firstOrNull() ?: return null
        val pickSeconds = mutableMapOf<Int, Int>()
        for (row in table.rows) {
            val cards = row.getOrNull(0)?.let { CARDS.find(it) }?.groupValues?.get(1)?.toInt() ?: return null
            val time = row.getOrNull(1) ?: return null
            val seconds = SECONDS.find(time)?.groupValues?.get(1)?.toInt()
            when {
                seconds != null -> pickSeconds[cards] = seconds
                time.trim().equals("N/A", ignoreCase = true) -> Unit   // no timer for this card
                else -> return null
            }
        }
        if (pickSeconds.isEmpty()) return null

        val text = section.filterIsInstance<Block.Paragraph>().joinToString(" ") { it.text }
        val firstReview = FIRST_REVIEW.find(text)?.groupValues?.get(1)?.toInt() ?: return null
        val increment = REVIEW_INCREMENT.find(text)?.groupValues?.get(1)?.toInt() ?: return null

        val construction = blocks.asSequence()
            .filterIsInstance<Block.BulletList>()
            .flatMap { it.items }
            .firstNotNullOfOrNull { CONSTRUCTION.find(it.trim())?.groupValues?.get(1)?.toInt() }
            ?: return null

        return BoosterDraftTimings(pickSeconds, firstReview, increment, construction)
    }
}
