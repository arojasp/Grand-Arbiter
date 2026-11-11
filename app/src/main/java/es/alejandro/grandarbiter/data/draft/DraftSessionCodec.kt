package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.domain.draft.DraftConfig
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftPlans
import es.alejandro.grandarbiter.domain.draft.DraftSession
import es.alejandro.grandarbiter.domain.draft.DraftTimings
import es.alejandro.grandarbiter.domain.timer.TimerState

/**
 * A draft session as plain values (what SharedPreferences holds) and back. Anything unreadable
 * (a format this version doesn't know, a value out of range) decodes to null:
 * the app shows "no draft" instead of crashing. Pure: unit-tested.
 */
object DraftSessionCodec {

    data class Stored(
        val format: String?,
        val packs: Int,
        val cardsPerPack: Int,
        val constructionMinutes: Int,
        /** Null: stored by a version without "Manual start". */
        val manualStart: Boolean? = null,
        val accumulatedMs: Long = 0,
        val runningSince: Long? = null,
        val step: Int = 0,
        /** Rochester: the team picking first; 1 when stored by a version without it. */
        val firstTeam: Int = 1,
        /** "15,14,14": each pack round's size; null = all [cardsPerPack] (or stored before this existed). */
        val packSizes: String? = null,
        /**
         * What [accumulatedMs] counts from: [TIME_BASE_STEP] = the start of [step].
         * Null: an older version, counted from the start of the draft.
         */
        val timeBase: String? = null,
        /** The judge's own times ([DraftTimesCodec]); null = the MTR's (or stored before this existed). */
        val times: String? = null,
    )

    const val TIME_BASE_STEP = "step"

    fun encode(session: DraftSession): Stored = encodeConfig(session.config).copy(
        accumulatedMs = session.timer.accumulatedMs,
        runningSince = session.timer.runningSince,
        step = session.step,
        timeBase = TIME_BASE_STEP,
    )

    fun encodeConfig(config: DraftConfig): Stored =
        Stored(config.format.name, config.packs, config.cardsPerPack, config.constructionMinutes, config.manualStart,
            firstTeam = config.firstTeam, packSizes = config.packSizes?.joinToString(","), times = DraftTimesCodec.encode(config.times))

    /** A draft started before "Manual start" existed ran on its own: it keeps doing so. */
    fun decode(stored: Stored): DraftSession? {
        val config = decodeConfig(stored, manualStartIfMissing = false) ?: return null
        if (stored.accumulatedMs < 0 || stored.step < 0) return null
        val accumulated = if (stored.timeBase == TIME_BASE_STEP) stored.accumulatedMs
            else fromDraftStart(config, stored.accumulatedMs, stored.step) ?: return null
        return DraftSession(config, TimerState(accumulated, stored.runningSince), stored.step)
    }

    /**
     * A draft saved by an older version counted its time from the start of the draft: the time into
     * [step] is that minus the step's start. Its plan is rebuilt with the built-in MTR times (the
     * same the rules bundle has; the timings repository isn't loaded yet when the store reads).
     */
    private fun fromDraftStart(config: DraftConfig, accumulatedMs: Long, step: Int): Long? = runCatching {
        val plan = DraftPlans.create(config, DraftTimings.FALLBACK)
        (accumulatedMs - plan.startOf(step)).coerceAtLeast(0)
    }.getOrNull()

    /** For the setup's last config: missing "Manual start" -> the first-time default (on). */
    fun decodeConfig(stored: Stored, manualStartIfMissing: Boolean = DraftConfig.DEFAULT_MANUAL_START): DraftConfig? {
        val format = DraftFormat.entries.firstOrNull { it.name == stored.format } ?: return null
        return runCatching {
            // A malformed list throws (NumberFormatException) like any other bad value: null.
            val packSizes = stored.packSizes?.split(",")?.map { it.trim().toInt() }
            DraftConfig(format, stored.packs, stored.cardsPerPack, stored.constructionMinutes, stored.manualStart ?: manualStartIfMissing,
                stored.firstTeam, packSizes, DraftTimesCodec.decode(stored.times))
        }.getOrNull()
    }
}
