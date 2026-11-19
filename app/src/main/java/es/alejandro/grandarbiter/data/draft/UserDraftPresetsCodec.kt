package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.UserDraftPreset
import es.alejandro.grandarbiter.domain.draft.UserDraftPresets
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/**
 * A format's presets as JSON (SharedPreferences) and back. A preset that can't be read is skipped,
 * not the whole list: one bad entry never loses the others. Same shape as [DraftTimesCodec] plus
 * name and minutes, so a preset can later be shared as text. Pure: unit-tested.
 */
object UserDraftPresetsCodec {

    @Serializable
    private data class Stored(
        val id: String,
        val name: String,
        val timed: Boolean = true,
        val picks: Map<Int, Map<Int, Int>> = emptyMap(),
        val reviews: Map<Int, Int> = emptyMap(),
        val minutes: Int,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val list = ListSerializer(Stored.serializer())

    fun encode(presets: List<UserDraftPreset>): String = json.encodeToString(list, presets.map {
        Stored(it.id, it.name, it.times.timedPicks, it.times.pickSeconds, it.times.reviewSeconds, it.constructionMinutes)
    })

    fun decode(format: DraftFormat, text: String?): List<UserDraftPreset> {
        if (text.isNullOrBlank()) return emptyList()
        // Each entry on its own: a missing field or a wrong type skips that preset only.
        val entries = runCatching { json.parseToJsonElement(text) as JsonArray }.getOrNull() ?: return emptyList()
        val presets = entries.mapNotNull { element ->
            runCatching {
                val s = json.decodeFromJsonElement(Stored.serializer(), element)
                UserDraftPreset(s.id, s.name, format, DraftTimes(s.timed, s.picks.filterValues { it.isNotEmpty() }, s.reviews), s.minutes)
            }.getOrNull()
        }
        // Two with one name (only from a hand-edited file): the first wins.
        return UserDraftPresets.sorted(presets.distinctBy { it.name.lowercase() })
    }
}
