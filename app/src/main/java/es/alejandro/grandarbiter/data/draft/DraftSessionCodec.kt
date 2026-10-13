package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.domain.draft.DraftConfig
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftSession
import es.alejandro.grandarbiter.domain.timer.TimerState

/**
 * A draft session as plain values (what SharedPreferences holds) and back. Anything unreadable
 * (a format renamed or not available in this version, a value out of range) decodes to null:
 * the app shows "no draft" instead of crashing. Pure: unit-tested.
 */
object DraftSessionCodec {

    data class Stored(
        val format: String?,
        val packs: Int,
        val cardsPerPack: Int,
        val constructionMinutes: Int,
        val accumulatedMs: Long = 0,
        val runningSince: Long? = null,
    )

    fun encode(session: DraftSession): Stored = encodeConfig(session.config).copy(
        accumulatedMs = session.timer.accumulatedMs,
        runningSince = session.timer.runningSince,
    )

    fun encodeConfig(config: DraftConfig): Stored =
        Stored(config.format.name, config.packs, config.cardsPerPack, config.constructionMinutes)

    fun decode(stored: Stored): DraftSession? {
        val config = decodeConfig(stored) ?: return null
        if (stored.accumulatedMs < 0) return null
        return DraftSession(config, TimerState(stored.accumulatedMs, stored.runningSince))
    }

    fun decodeConfig(stored: Stored): DraftConfig? {
        val format = DraftFormat.entries.firstOrNull { it.name == stored.format } ?: return null
        if (!format.available) return null
        return runCatching { DraftConfig(format, stored.packs, stored.cardsPerPack, stored.constructionMinutes) }.getOrNull()
    }
}
