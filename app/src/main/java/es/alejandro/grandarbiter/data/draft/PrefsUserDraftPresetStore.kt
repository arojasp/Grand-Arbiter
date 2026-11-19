package es.alejandro.grandarbiter.data.draft

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import es.alejandro.grandarbiter.domain.draft.DraftConfig
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.UserDraftPreset
import es.alejandro.grandarbiter.domain.draft.UserDraftPresetStore
import es.alejandro.grandarbiter.domain.draft.UserDraftPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Each format's presets in SharedPreferences ("draft_presets", backed up with the other settings). */
class PrefsUserDraftPresetStore(
    context: Context,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : UserDraftPresetStore {

    private val prefs: SharedPreferences = context.getSharedPreferences("draft_presets", Context.MODE_PRIVATE)
    private val flows = DraftConfig.CUSTOM_TIMES_FORMATS.associateWith {
        MutableStateFlow(UserDraftPresetsCodec.decode(it, prefs.getString(it.name, null)))
    }

    override fun presets(format: DraftFormat): StateFlow<List<UserDraftPreset>> = flow(format).asStateFlow()

    @Synchronized
    override fun save(format: DraftFormat, name: String, times: DraftTimes, constructionMinutes: Int): UserDraftPreset {
        require(!UserDraftPresets.isReserved(name)) { "'$name' is a built-in preset's name" }
        val preset = UserDraftPreset(newId(), UserDraftPresets.cleanName(name), format, times, constructionMinutes)
        val list = UserDraftPresets.withSaved(flow(format).value, preset)
        write(format, list)
        return list.first { it.name == preset.name }
    }

    @Synchronized
    override fun rename(format: DraftFormat, id: String, name: String): Boolean {
        val list = UserDraftPresets.withRenamed(flow(format).value, id, name) ?: return false
        write(format, list)
        return true
    }

    @Synchronized
    override fun delete(format: DraftFormat, id: String) = write(format, UserDraftPresets.without(flow(format).value, id))

    private fun flow(format: DraftFormat) = requireNotNull(flows[format]) { "no presets for $format" }

    private fun write(format: DraftFormat, list: List<UserDraftPreset>) {
        flow(format).value = list
        prefs.edit {
            if (list.isEmpty()) remove(format.name) else putString(format.name, UserDraftPresetsCodec.encode(list))
        }
    }
}
