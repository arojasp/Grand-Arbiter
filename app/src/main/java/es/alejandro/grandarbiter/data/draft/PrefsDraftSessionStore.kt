package es.alejandro.grandarbiter.data.draft

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import es.alejandro.grandarbiter.data.draft.DraftSessionCodec.Stored
import es.alejandro.grandarbiter.domain.draft.DraftConfig
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
        get() = readStored(LAST)?.let(DraftSessionCodec::decodeConfig) ?: DraftConfig()

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
            accumulatedMs = prefs.getLong(prefix + ACCUMULATED, 0),
            runningSince = if (prefs.contains(prefix + SINCE)) prefs.getLong(prefix + SINCE, 0) else null,
        )
    }

    private fun SharedPreferences.Editor.write(prefix: String, s: Stored) {
        putString(prefix + FORMAT, s.format)
        putInt(prefix + PACKS, s.packs)
        putInt(prefix + CARDS, s.cardsPerPack)
        putInt(prefix + MINUTES, s.constructionMinutes)
        putLong(prefix + ACCUMULATED, s.accumulatedMs)
        if (s.runningSince != null) putLong(prefix + SINCE, s.runningSince) else remove(prefix + SINCE)
    }

    private companion object {
        const val SESSION = "session_"
        const val LAST = "last_"
        const val FORMAT = "format"
        const val PACKS = "packs"
        const val CARDS = "cards_per_pack"
        const val MINUTES = "construction_minutes"
        const val ACCUMULATED = "accumulated_ms"
        const val SINCE = "running_since"
        val KEYS = listOf(FORMAT, PACKS, CARDS, MINUTES, ACCUMULATED, SINCE)
    }
}
