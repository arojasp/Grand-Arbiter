package es.alejandro.grandarbiter.ui.tools.drafttimer

import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftTimeRows
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.DraftTimings
import es.alejandro.grandarbiter.domain.draft.FormatTimes
import es.alejandro.grandarbiter.domain.draft.PickRow

/**
 * What the Times screen shows for one format and the setup's packs: one list per pack size in use
 * (largest first), the reviews and deck construction, each with its MTR value. Pure: unit-tested.
 */
data class DraftTimesModel(
    val format: DraftFormat,
    val times: DraftTimes,
    val lists: List<PickList>,
    val reviews: List<ReviewRow>,
    val constructionMinutes: Int,
    val mtrConstructionMinutes: Int,
) {
    data class PickList(val packSize: Int, val rows: List<PickRow>)

    /** [pack]: the review after that pack, or [DraftTimes.EVERY_REVIEW] (Two-Headed Giant: one time for all). */
    data class ReviewRow(val pack: Int, val seconds: Int, val mtrSeconds: Int) {
        val isChanged: Boolean get() = seconds != mtrSeconds
    }

    val timedPicks: Boolean get() = times.timedPicks
    val cardsPerPick: Int get() = if (format == DraftFormat.TWO_HEADED_GIANT) 2 else 1
    val constructionChanged: Boolean get() = constructionMinutes != mtrConstructionMinutes

    /** Everything as the MTR has it: "Reset to MTR" has nothing to do. */
    val isMtr: Boolean get() = times.isMtr && !constructionChanged

    val changedPicksInUse: Int get() = rowsOnScreen.count(times::isChanged)
    val changedReviews: Int get() = reviews.count { it.isChanged }

    /** This draft runs with other times than the MTR's (changes to pack sizes not in use don't count). */
    val differsInUse: Boolean get() = !timedPicks || changedPicksInUse > 0 || changedReviews > 0 || constructionChanged

    /** The rows on screen: what "All picks −5 s / +5 s" changes. */
    val rowsOnScreen: List<PickRow> get() = lists.flatMap { it.rows }

    companion object {
        /**
         * [packSizes]: the size of each pack of the setup, in order. [lastConstructionMinutes]: the
         * last draft's minutes of this format, used until the judge sets them here.
         */
        fun of(
            format: DraftFormat,
            packSizes: List<Int>,
            stored: FormatTimes,
            timings: DraftTimings,
            lastConstructionMinutes: Int?,
        ): DraftTimesModel {
            val times = stored.times
            val sizes = packSizes.distinct().sortedDescending()
            val lists: List<PickList>
            val reviews: List<ReviewRow>
            val mtrMinutes: Int
            when (format) {
                DraftFormat.TWO_HEADED_GIANT -> {
                    val t = timings.twoHeadedGiant
                    lists = sizes.filter { it in t.boosterSizes }.map { PickList(it, DraftTimeRows.twoHeadedGiant(it, t)) }
                    reviews = if (packSizes.size > 1) {
                        listOf(ReviewRow(DraftTimes.EVERY_REVIEW, times.reviewSecondsAfter(DraftTimes.EVERY_REVIEW, t.reviewSeconds), t.reviewSeconds))
                    } else emptyList()
                    mtrMinutes = t.constructionMinutes
                }
                else -> {
                    val t = timings.booster
                    lists = sizes.map { PickList(it, DraftTimeRows.booster(it, t)) }
                    reviews = (1 until packSizes.size).map { pack ->
                        ReviewRow(pack, times.reviewSecondsAfter(pack, t.reviewSeconds(pack)), t.reviewSeconds(pack))
                    }
                    mtrMinutes = t.constructionMinutes
                }
            }
            return DraftTimesModel(format, times, lists, reviews,
                constructionMinutes = stored.constructionMinutes ?: lastConstructionMinutes ?: mtrMinutes,
                mtrConstructionMinutes = mtrMinutes)
        }
    }
}
