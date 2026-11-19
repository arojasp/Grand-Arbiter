package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.domain.draft.DraftConfig
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.UserDraftPreset
import es.alejandro.grandarbiter.domain.draft.UserDraftPresets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A preset read from shared text, not saved yet: the import dialog shows it first. */
data class SharedDraftPreset(
    val name: String,
    val format: DraftFormat,
    val times: DraftTimes,
    val constructionMinutes: Int,
)

sealed interface SharedPresetResult {
    data class Read(val preset: SharedDraftPreset) : SharedPresetResult
    /** No JSON, or JSON that isn't a Grand Arbiter draft preset. */
    data object NotAPreset : SharedPresetResult
    /** Shared from a newer Grand Arbiter: update the app. */
    data object NewerVersion : SharedPresetResult
    /** A preset, but a name, format or time the app can't use (edited by hand, or cut off). */
    data object Invalid : SharedPresetResult
}

/**
 * A preset as the JSON that's shared (Share in the preset's menu) and imported (Import preset).
 * JSON only, one line: obviously something to import, and no reading of free text. Only changed times
 * travel; the rest follow the receiver's installed MTR, as on the phone that shared it.
 * Import takes the text from the first "{" to the last "}", so what chat apps add when copying
 * ("[19/11 14:45] Alejandro: ") doesn't matter. Pure: unit-tested.
 */
object DraftPresetShareCodec {

    const val VERSION = 1
    private const val KIND = "grand-arbiter-draft-preset"
    /** Far more than any real preset: a pasted book is not read. */
    const val MAX_TEXT_LENGTH = 20_000

    @Serializable
    private data class Shared(
        val kind: String = "",
        val v: Int = 0,
        val name: String = "",
        val format: String = "",
        val timed: Boolean = true,
        /** "14" -> "1" -> 50: pack size -> pick -> seconds; only the changed picks. */
        val picks: Map<Int, Map<Int, Int>> = emptyMap(),
        /** "1" -> 60; "0": every review (Two-Headed Giant). */
        val reviews: Map<Int, Int> = emptyMap(),
        val minutes: Int = 0,
    )

    /** Defaults left out ("timed":true, no picks or reviews): a shorter line to share. */
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(preset: UserDraftPreset): String = json.encodeToString(
        Shared.serializer(),
        Shared(
            kind = KIND,
            v = VERSION,
            name = preset.name,
            format = FORMATS.getValue(preset.format),
            timed = preset.times.timedPicks,
            picks = preset.times.pickSeconds,
            reviews = preset.times.reviewSeconds,
            minutes = preset.constructionMinutes,
        ),
    )

    fun decode(text: String): SharedPresetResult {
        if (text.length > MAX_TEXT_LENGTH) return SharedPresetResult.NotAPreset
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end < start) return SharedPresetResult.NotAPreset
        val body = text.substring(start, end + 1)
        // As shared first; then with the quotes a phone keyboard curls when the JSON is edited by hand
        // (only as a retry: a name may well contain curly quotes).
        val s = parse(body) ?: parse(body.replace('“', '"').replace('”', '"'))
            ?: return SharedPresetResult.NotAPreset
        if (s.kind != KIND) return SharedPresetResult.NotAPreset
        if (s.v > VERSION) return SharedPresetResult.NewerVersion
        if (s.v < 1) return SharedPresetResult.NotAPreset
        val format = FORMATS.entries.firstOrNull { it.value == s.format }?.key ?: return SharedPresetResult.Invalid
        val name = UserDraftPresets.cleanName(s.name)
        val keysOk = s.picks.keys.all { it in PACK_SIZES } &&
            s.picks.values.all { picks -> picks.keys.all { it in PICKS } } &&
            s.reviews.keys.all { it in REVIEWS }
        if (!UserDraftPresets.isValidName(name) || !keysOk || s.minutes !in DraftConfig.CONSTRUCTION_MINUTES) {
            return SharedPresetResult.Invalid
        }
        val times = runCatching { DraftTimes(s.timed, s.picks.filterValues { it.isNotEmpty() }, s.reviews) }.getOrNull()
            ?: return SharedPresetResult.Invalid
        return SharedPresetResult.Read(SharedDraftPreset(name, format, times, s.minutes))
    }

    private fun parse(body: String): Shared? = runCatching { json.decodeFromString(Shared.serializer(), body) }.getOrNull()

    /** Stable words, not the enum's names: renaming a Kotlin constant must not break shared presets. */
    private val FORMATS = mapOf(DraftFormat.BOOSTER to "booster", DraftFormat.TWO_HEADED_GIANT to "two-headed-giant")
    private val PACK_SIZES = 1..99
    private val PICKS = 1..99
    private val REVIEWS = 0..9
}
