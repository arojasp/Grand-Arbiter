package es.alejandro.grandarbiter.domain.draft

/**
 * Individual Booster Draft (MTR 7.7 + Appendix B): for each pack, one step per timed pick; a
 * review between packs (not after the last); then deck construction. The judge's own times
 * ([DraftConfig.times]) replace the MTR's where set; untimed picks and reviews have no duration.
 */
object BoosterDraftPlanFactory {

    fun create(config: DraftConfig, timings: BoosterDraftTimings): DraftPlan {
        require(config.format == DraftFormat.BOOSTER) { "not a booster draft: ${config.format}" }
        val times = config.times
        val steps = buildList {
            for (pack in 1..config.packs) {
                val direction = PassDirection.forPack(pack)
                // No time in the table ("N/A": the last card) -> no row, no step.
                for (row in DraftTimeRows.booster(config.cardsInPack(pack), timings)) {
                    add(DraftStep.Pick(
                        pack = pack,
                        pick = row.pick,
                        cardsLeft = row.cardsLeft,
                        direction = direction,
                        durationMs = times.takeIf { it.timedPicks }?.let { it.secondsFor(row) * 1_000L },
                    ))
                }
                if (pack < config.packs) {
                    add(DraftStep.Review(
                        afterPack = pack,
                        nextDirection = PassDirection.forPack(pack + 1),
                        durationMs = times.takeIf { it.timedPicks }?.let { it.reviewSecondsAfter(pack, timings.reviewSeconds(pack)) * 1_000L },
                    ))
                }
            }
            add(DraftStep.Construction(config.constructionMinutes * 60_000L))
        }
        return DraftPlan(steps)
    }
}
