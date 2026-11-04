package es.alejandro.grandarbiter.domain.draft

import kotlinx.coroutines.flow.StateFlow

/** The draft in progress (at most one), kept across app restarts. */
interface DraftSessionStore {
    /** Null: no draft running. */
    val session: StateFlow<DraftSession?>

    /** The config of the last draft started, to prefill the setup (e.g. a 15-card cube). */
    val lastConfig: DraftConfig

    /** [format]'s setup as the judge left it, saved on every change; null if never changed. */
    fun setup(format: DraftFormat): DraftConfig?

    fun saveSetup(config: DraftConfig)

    /** Null ends the draft. A non-null session also becomes [lastConfig]. */
    fun save(session: DraftSession?)
}

/** What happens when a step runs out. Implemented with the Vibrator; faked in tests. */
interface DraftAlerts {
    /** One vibration if [vibrate] (on by default), a short beep if [beep] (off by default). */
    fun stepEnded(vibrate: Boolean, beep: Boolean)
}

/** Picks the plan factory for a format. Only available formats have one. */
object DraftPlans {
    fun create(config: DraftConfig, timings: DraftTimings): DraftPlan = when (config.format) {
        DraftFormat.BOOSTER -> BoosterDraftPlanFactory.create(config, timings.booster)
        DraftFormat.TWO_HEADED_GIANT -> TwoHeadedGiantPlanFactory.create(config, timings.twoHeadedGiant)
        DraftFormat.ROCHESTER -> RochesterPlanFactory.create(config, timings.rochester)
    }
}

/** The setup screen's "Estimated duration": picks, reviews, construction and total. Pure. */
data class DraftEstimate(
    val packs: Int,
    /** Timed picks in pack 1; see [timedPicksByPack] when pack sizes differ. */
    val timedPicksPerPack: Int,
    /** Timed picks of each pack: [13, 13, 13], or [14, 13, 13] for a 15-card pack then two of 14. */
    val timedPicksByPack: List<Int>,
    /** 2 in Two-Headed Giant. */
    val cardsPerPick: Int,
    val picksMs: Long,
    val reviewSeconds: List<Int>,
    /** Rochester: each pack's review once laid out (20 s). */
    val layOutSeconds: List<Int>,
    /** Rochester: pick times are maxima (picks end on "Picked"), so the total is "at most". */
    val atMost: Boolean,
    val pickSeconds: Int,
    val constructionMs: Long,
    /** The timed steps' total; with [untimedPicks], deck construction (and Rochester's lay-outs) only. */
    val totalMs: Long,
    /** Picks and reviews have no timer: [picksMs] is 0 and [reviewSeconds] empty. */
    val untimedPicks: Boolean = false,
) {
    companion object {
        fun of(plan: DraftPlan): DraftEstimate {
            val picks = plan.steps.filterIsInstance<DraftStep.Pick>()
            val packs = picks.maxOfOrNull { it.pack } ?: 0
            return DraftEstimate(
                packs = packs,
                timedPicksPerPack = picks.count { it.pack == 1 },
                timedPicksByPack = (1..packs).map { pack -> picks.count { it.pack == pack } },
                cardsPerPick = picks.firstOrNull()?.cardsTaken ?: 1,
                picksMs = picks.sumOf { it.durationMs ?: 0L },
                reviewSeconds = plan.steps.filterIsInstance<DraftStep.Review>().mapNotNull { r -> r.durationMs?.let { (it / 1000).toInt() } },
                layOutSeconds = plan.steps.filterIsInstance<DraftStep.LayOut>().map { (it.durationMs / 1000).toInt() },
                atMost = picks.any { it.waitsForPicked },
                pickSeconds = picks.firstOrNull()?.durationMs?.let { (it / 1000).toInt() } ?: 0,
                constructionMs = plan.steps.filterIsInstance<DraftStep.Construction>().sumOf { it.durationMs },
                totalMs = plan.totalMs,
                untimedPicks = picks.any { !it.isTimed },
            )
        }
    }
}

/** Everything the running screen shows at one moment. Pure: built from session + plan + clock. */
data class DraftSnapshot(
    val config: DraftConfig,
    val position: DraftPosition,
    val isRunning: Boolean,
    /** What comes after the current (or just ended) step: "Next pick: 11 cards · 25 s", "Start pack 2"; null on the last one. */
    val nextStep: DraftStep?,
    /** The next few steps (Rochester: "Next: 2A · then 2B, 2C"). */
    val upcoming: List<DraftStep>,
    val elapsedMs: Long,
    val totalMs: Long,
) {
    companion object {
        private const val UPCOMING = 6

        fun of(session: DraftSession, plan: DraftPlan, now: Long): DraftSnapshot {
            // The session counts from the start of its step; the snapshot shows draft time.
            val elapsed = plan.startOf(session.step) + session.elapsedAt(plan, now)
            val position = session.positionAt(plan, now)
            val index = when (position) {
                is DraftPosition.Running -> position.index
                is DraftPosition.Waiting -> position.index
                DraftPosition.Finished -> null
            }
            val upcoming = index?.let { plan.steps.drop(it + 1).take(UPCOMING) }.orEmpty()
            return DraftSnapshot(session.config, position, session.isRunning, upcoming.firstOrNull(), upcoming,
                elapsed.coerceAtMost(plan.totalMs), plan.totalMs)
        }
    }
}
