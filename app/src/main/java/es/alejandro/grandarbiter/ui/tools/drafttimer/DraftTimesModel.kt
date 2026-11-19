package es.alejandro.grandarbiter.ui.tools.drafttimer

import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftTimeRows
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.DraftTimesPreset
import es.alejandro.grandarbiter.domain.draft.DraftTimings
import es.alejandro.grandarbiter.domain.draft.FormatTimes
import es.alejandro.grandarbiter.domain.draft.PickRow
import es.alejandro.grandarbiter.domain.draft.UserDraftPreset

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
    /** The judge's own presets of this format, by name. */
    val userPresets: List<UserDraftPreset> = emptyList(),
) {
    data class PickList(val packSize: Int, val rows: List<PickRow>)

    /** [pack]: the review after that pack, or [DraftTimes.EVERY_REVIEW] (Two-Headed Giant: one time for all). */
    data class ReviewRow(val pack: Int, val seconds: Int, val mtrSeconds: Int) {
        val isChanged: Boolean get() = seconds != mtrSeconds
    }

    /** The ready-made times offered here (none, or only MTR: no choice to show). */
    val presets: List<DraftTimesPreset> get() = DraftTimesPreset.availableFor(format)

    /**
     * The preset this draft's picks and reviews run as; null = custom times. Judged on the packs in
     * use, like the setup's summary: Pro Tour kept for 15-card packs, or changes only to a pack size
     * not in use, run as the MTR. Deck construction doesn't count (no preset sets it).
     */
    val preset: DraftTimesPreset?
        get() {
            val exact = DraftTimesPreset.matching(times)?.takeIf { it in presets && it !in presetsNotForThesePacks }
            val asMtr = timedPicks && changedPicksInUse == 0 && changedReviews == 0
            return exact ?: DraftTimesPreset.MTR.takeIf { asMtr && it in presets }
        }

    /** The judge's own preset that is exactly what's in use (times and construction); it wins over [preset]. */
    val userPreset: UserDraftPreset? get() = userPresets.firstOrNull { it.matches(times, constructionMinutes) }

    /** Offered but changing nothing with these packs: tapping one explains why instead. */
    val presetsNotForThesePacks: Set<DraftTimesPreset>
        get() = presets.filterNot { it.appliesTo(lists.map(PickList::packSize)) }.toSet()

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
            userPresets: List<UserDraftPreset> = emptyList(),
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
                mtrConstructionMinutes = mtrMinutes, userPresets = userPresets)
        }
    }
}
