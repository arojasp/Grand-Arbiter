package es.alejandro.grandarbiter.domain.draft

import kotlinx.coroutines.flow.StateFlow

/** The draft in progress (at most one), kept across app restarts. */
interface DraftSessionStore {
    /** Null: no draft running. */
    val session: StateFlow<DraftSession?>

    /** The config of the last draft started, to prefill the setup (e.g. a 15-card cube). */
    val lastConfig: DraftConfig

    /** Null ends the draft. A non-null session also becomes [lastConfig]. */
    fun save(session: DraftSession?)
}

/** What happens when a step runs out. Implemented with the Vibrator; faked in tests. */
interface DraftAlerts {
    /** One vibration, plus a short beep if [beep]. */
    fun stepEnded(beep: Boolean)
}

/** Picks the plan factory for a format. Only available formats have one. */
object DraftPlans {
    fun create(config: DraftConfig, timings: BoosterDraftTimings): DraftPlan = when (config.format) {
        DraftFormat.BOOSTER -> BoosterDraftPlanFactory.create(config, timings)
        DraftFormat.TWO_HEADED_GIANT, DraftFormat.ROCHESTER ->
            throw IllegalArgumentException("${config.format} is not available yet")
    }
}

/** The setup screen's "Estimated duration": picks, reviews, construction and total. Pure. */
data class DraftEstimate(
    val packs: Int,
    val timedPicksPerPack: Int,
    val picksMs: Long,
    val reviewSeconds: List<Int>,
    val constructionMs: Long,
    val totalMs: Long,
) {
    companion object {
        fun of(plan: DraftPlan): DraftEstimate {
            val picks = plan.steps.filterIsInstance<DraftStep.Pick>()
            val packs = picks.maxOfOrNull { it.pack } ?: 0
            return DraftEstimate(
                packs = packs,
                timedPicksPerPack = picks.count { it.pack == 1 },
                picksMs = picks.sumOf { it.durationMs },
                reviewSeconds = plan.steps.filterIsInstance<DraftStep.Review>().map { (it.durationMs / 1000).toInt() },
                constructionMs = plan.steps.filterIsInstance<DraftStep.Construction>().sumOf { it.durationMs },
                totalMs = plan.totalMs,
            )
        }
    }
}

/** Everything the running screen shows at one moment. Pure: built from session + plan + clock. */
data class DraftSnapshot(
    val config: DraftConfig,
    val position: DraftPosition,
    val isRunning: Boolean,
    /** What comes after the current step ("Next pick: 11 cards · 25 s", "Up next: pack 2"); null on the last one. */
    val nextStep: DraftStep?,
    val elapsedMs: Long,
    val totalMs: Long,
) {
    companion object {
        fun of(session: DraftSession, plan: DraftPlan, now: Long): DraftSnapshot {
            val elapsed = session.elapsedAt(now)
            val position = plan.positionAt(elapsed)
            val next = (position as? DraftPosition.Running)?.let { plan.steps.getOrNull(it.index + 1) }
            return DraftSnapshot(session.config, position, session.isRunning, next, elapsed.coerceAtMost(plan.totalMs), plan.totalMs)
        }
    }
}
