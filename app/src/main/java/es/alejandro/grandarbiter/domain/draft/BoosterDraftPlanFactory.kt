package es.alejandro.grandarbiter.domain.draft

/**
 * Individual Booster Draft (MTR 7.7 + Appendix B): for each pack, one step per timed pick; a
 * review between packs (not after the last); then deck construction.
 */
object BoosterDraftPlanFactory {

    fun create(config: DraftConfig, timings: BoosterDraftTimings): DraftPlan {
        require(config.format == DraftFormat.BOOSTER) { "not a booster draft: ${config.format}" }
        val steps = buildList {
            for (pack in 1..config.packs) {
                val direction = PassDirection.forPack(pack)
                for (cardsLeft in config.cardsPerPack downTo 1) {
                    // No time in the table ("N/A": the last card) -> no timer, no step.
                    val seconds = timings.pickSeconds[cardsLeft] ?: continue
                    add(DraftStep.Pick(
                        pack = pack,
                        pick = config.cardsPerPack - cardsLeft + 1,
                        cardsLeft = cardsLeft,
                        direction = direction,
                        durationMs = seconds * 1_000L,
                    ))
                }
                if (pack < config.packs) {
                    add(DraftStep.Review(
                        afterPack = pack,
                        nextDirection = PassDirection.forPack(pack + 1),
                        durationMs = timings.reviewSeconds(pack) * 1_000L,
                    ))
                }
            }
            add(DraftStep.Construction(config.constructionMinutes * 60_000L))
        }
        return DraftPlan(steps)
    }
}
