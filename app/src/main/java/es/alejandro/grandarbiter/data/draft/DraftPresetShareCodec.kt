package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.domain.draft.DraftConfig
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.UserDraftPreset
import es.alejandro.grandarbiter.domain.draft.UserDraftPresets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

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
    /** The start of a preset without its end: the message was cut when copied. */
    data object Incomplete : SharedPresetResult
    /** Shared from a newer Grand Arbiter: update the app. */
    data object NewerVersion : SharedPresetResult
    /** A preset, but a name, format or time the app can't use (edited by hand). */
    data object Invalid : SharedPresetResult
}

/**
 * A preset as the JSON that's shared (Share in the preset's menu) and imported (Import preset).
 * JSON only, one line: obviously something to import, and no reading of free text. Only changed times
 * travel; the rest follow the receiver's installed MTR, as on the phone that shared it.
 * Import looks for the first complete {…} that says it's a preset, so what chat apps add when copying
 * ("[19/11 14:45] Alejandro: ", a greeting, other braces) doesn't matter. Pure: unit-tested.
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
        // Invisible characters some apps add when copying; a no-break space is not JSON whitespace.
        val clean = text.replace(INVISIBLE, "").replace(NO_BREAK_SPACE, ' ')
        // As shared first; then with the quotes a phone keyboard curls when the JSON is edited by hand
        // (only as a retry: a name may well contain curly quotes).
        val result = decodeObjects(clean)
        if (result != SharedPresetResult.NotAPreset) return result
        val straight = clean.replace(LEFT_QUOTE, '"').replace(RIGHT_QUOTE, '"')
        if (straight != clean) decodeObjects(straight).let { if (it != SharedPresetResult.NotAPreset) return it }
        // The preset's start is there but its end isn't: the message was cut when copied.
        return if (KIND in clean) SharedPresetResult.Incomplete else SharedPresetResult.NotAPreset
    }

    /** The first complete {…} in [text] that says it is a preset; others ("{jueces}") are skipped. */
    private fun decodeObjects(text: String): SharedPresetResult {
        for (candidate in objects(text)) {
            val obj = runCatching { json.parseToJsonElement(candidate) as? JsonObject }.getOrNull() ?: continue
            if ((obj["kind"] as? JsonPrimitive)?.contentOrNull != KIND) continue
            return decodeObject(obj)
        }
        return SharedPresetResult.NotAPreset
    }

    /** Each top-level {…} of [text], braces inside JSON strings ignored. An unclosed one is left out. */
    internal fun objects(text: String): List<String> {
        val found = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        text.forEachIndexed { i, c ->
            when {
                inString && escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                inString && c == '"' -> inString = false
                inString -> Unit
                // A quote outside any object is prose ("He said "hi""), not the start of a JSON string.
                c == '"' && depth > 0 -> inString = true
                c == '{' -> { if (depth == 0) start = i; depth++ }
                c == '}' && depth > 0 -> { depth--; if (depth == 0) found += text.substring(start, i + 1) }
            }
        }
        return found
    }

    /** It says it's a preset: from here on, anything wrong is [SharedPresetResult.Invalid], not "not a preset". */
    private fun decodeObject(obj: JsonObject): SharedPresetResult {
        val v = (obj["v"] as? JsonPrimitive)?.intOrNull ?: return SharedPresetResult.Invalid
        if (v > VERSION) return SharedPresetResult.NewerVersion
        if (v < 1) return SharedPresetResult.Invalid
        val s = runCatching { json.decodeFromJsonElement(Shared.serializer(), obj) }.getOrNull()
            ?: return SharedPresetResult.Invalid
        val format = FORMATS.entries.firstOrNull { it.value == s.format.trim().lowercase() }?.key
            ?: return SharedPresetResult.Invalid
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

    /** Stable words, not the enum's names: renaming a Kotlin constant must not break shared presets. */
    private val FORMATS = mapOf(DraftFormat.BOOSTER to "booster", DraftFormat.TWO_HEADED_GIANT to "two-headed-giant")
    private val PACK_SIZES = 1..99
    private val PICKS = 1..99
    private val REVIEWS = 0..9

    // By code, not as characters: invisible or look-alike characters in the source are easy to break.
    private val INVISIBLE = Regex("[" + listOf(0x200B, 0x200C, 0x200D, 0x2060, 0xFEFF).joinToString("") { Char(it).toString() } + "]")
    private val NO_BREAK_SPACE = Char(0x00A0)
    private val LEFT_QUOTE = Char(0x201C)
    private val RIGHT_QUOTE = Char(0x201D)
}
