package es.alejandro.grandarbiter.data.draft

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.DraftTimesStore
import es.alejandro.grandarbiter.domain.draft.FormatTimes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Each format's own times in SharedPreferences, written on every change (a settings screen). */
class PrefsDraftTimesStore(context: Context) : DraftTimesStore {

    private val prefs: SharedPreferences = context.getSharedPreferences("draft_times", Context.MODE_PRIVATE)
    private val flows = DraftFormat.entries.associateWith { MutableStateFlow(read(it)) }

    override fun times(format: DraftFormat): StateFlow<FormatTimes> = flows.getValue(format).asStateFlow()

    @Synchronized
    override fun save(format: DraftFormat, times: FormatTimes) {
        flows.getValue(format).value = times
        prefs.edit {
            val encoded = DraftTimesCodec.encode(times.times)
            if (encoded != null) putString(format.name + TIMES, encoded) else remove(format.name + TIMES)
            if (times.constructionMinutes != null) putInt(format.name + MINUTES, times.constructionMinutes) else remove(format.name + MINUTES)
        }
    }

    private fun read(format: DraftFormat) = FormatTimes(
        times = DraftTimesCodec.decode(prefs.getString(format.name + TIMES, null)),
        constructionMinutes = if (prefs.contains(format.name + MINUTES)) prefs.getInt(format.name + MINUTES, 0) else null,
    )

    private companion object {
        const val TIMES = "_times"
        const val MINUTES = "_construction_minutes"
    }
}
