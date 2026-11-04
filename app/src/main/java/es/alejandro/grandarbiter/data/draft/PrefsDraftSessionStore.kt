package es.alejandro.grandarbiter.data.draft

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import es.alejandro.grandarbiter.data.draft.DraftSessionCodec.Stored
import es.alejandro.grandarbiter.domain.draft.DraftConfig
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftSession
import es.alejandro.grandarbiter.domain.draft.DraftSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The draft in progress in SharedPreferences, like the judge call timer: a few numbers written
 * only on start, pause, jumps and end. No service, no notification: the time comes from the clock.
 */
class PrefsDraftSessionStore(context: Context) : DraftSessionStore {

    private val prefs: SharedPreferences = context.getSharedPreferences("draft_timer", Context.MODE_PRIVATE)
    private val _session = MutableStateFlow(load(SESSION))
    override val session: StateFlow<DraftSession?> = _session.asStateFlow()

    override val lastConfig: DraftConfig
        get() = readStored(LAST)?.let { DraftSessionCodec.decodeConfig(it) } ?: DraftConfig()

    override fun setup(format: DraftFormat): DraftConfig? =
        readStored(SETUP + format.name + "_")?.let { DraftSessionCodec.decodeConfig(it) }?.takeIf { it.format == format }

    @Synchronized
    override fun saveSetup(config: DraftConfig) {
        prefs.edit { write(SETUP + config.format.name + "_", DraftSessionCodec.encodeConfig(config)) }
    }

    @Synchronized
    override fun save(session: DraftSession?) {
        _session.value = session
        prefs.edit {
            if (session == null) {
                KEYS.forEach { remove(SESSION + it) }
            } else {
                write(SESSION, DraftSessionCodec.encode(session))
                write(LAST, DraftSessionCodec.encodeConfig(session.config))
            }
        }
    }

    private fun load(prefix: String): DraftSession? = readStored(prefix)?.let(DraftSessionCodec::decode)

    private fun readStored(prefix: String): Stored? {
        if (!prefs.contains(prefix + FORMAT)) return null
        return Stored(
            format = prefs.getString(prefix + FORMAT, null),
            packs = prefs.getInt(prefix + PACKS, 0),
            cardsPerPack = prefs.getInt(prefix + CARDS, 0),
            constructionMinutes = prefs.getInt(prefix + MINUTES, 0),
            manualStart = if (prefs.contains(prefix + MANUAL)) prefs.getBoolean(prefix + MANUAL, true) else null,
            accumulatedMs = prefs.getLong(prefix + ACCUMULATED, 0),
            runningSince = if (prefs.contains(prefix + SINCE)) prefs.getLong(prefix + SINCE, 0) else null,
            step = prefs.getInt(prefix + STEP, 0),
            firstTeam = prefs.getInt(prefix + FIRST_TEAM, 1),
            packSizes = prefs.getString(prefix + PACK_SIZES, null),
            timeBase = prefs.getString(prefix + TIME_BASE, null),
            times = prefs.getString(prefix + TIMES, null),
        )
    }

    private fun SharedPreferences.Editor.write(prefix: String, s: Stored) {
        putString(prefix + FORMAT, s.format)
        putInt(prefix + PACKS, s.packs)
        putInt(prefix + CARDS, s.cardsPerPack)
        putInt(prefix + MINUTES, s.constructionMinutes)
        if (s.manualStart != null) putBoolean(prefix + MANUAL, s.manualStart) else remove(prefix + MANUAL)
        putInt(prefix + STEP, s.step)
        putInt(prefix + FIRST_TEAM, s.firstTeam)
        if (s.packSizes != null) putString(prefix + PACK_SIZES, s.packSizes) else remove(prefix + PACK_SIZES)
        if (s.timeBase != null) putString(prefix + TIME_BASE, s.timeBase) else remove(prefix + TIME_BASE)
        if (s.times != null) putString(prefix + TIMES, s.times) else remove(prefix + TIMES)
        putLong(prefix + ACCUMULATED, s.accumulatedMs)
        if (s.runningSince != null) putLong(prefix + SINCE, s.runningSince) else remove(prefix + SINCE)
    }

    private companion object {
        const val SESSION = "session_"
        const val LAST = "last_"
        const val SETUP = "setup_"
        const val FORMAT = "format"
        const val PACKS = "packs"
        const val CARDS = "cards_per_pack"
        const val MINUTES = "construction_minutes"
        const val ACCUMULATED = "accumulated_ms"
        const val SINCE = "running_since"
        const val MANUAL = "manual_start"
        const val STEP = "step"
        const val FIRST_TEAM = "first_team"
        const val PACK_SIZES = "pack_sizes"
        const val TIME_BASE = "time_base"
        const val TIMES = "times"
        val KEYS = listOf(FORMAT, PACKS, CARDS, MINUTES, MANUAL, ACCUMULATED, SINCE, STEP, FIRST_TEAM, PACK_SIZES, TIME_BASE, TIMES)
    }
}
