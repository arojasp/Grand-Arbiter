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
        val asIs = decodeObjects(clean)
        if (asIs.isFinal) return asIs
        val straight = clean.replace(LEFT_QUOTE, '"').replace(RIGHT_QUOTE, '"')
        val retried = if (straight != clean) decodeObjects(straight) else SharedPresetResult.NotAPreset
        if (retried.isFinal) return retried
        return when {
            asIs != SharedPresetResult.NotAPreset -> asIs
            retried != SharedPresetResult.NotAPreset -> retried
            // The preset's start is there but its end isn't: the message was cut when copied.
            KIND in clean -> SharedPresetResult.Incomplete
            else -> SharedPresetResult.NotAPreset
        }
    }

    /** A preset read, or one from a newer app: no retry can do better. */
    private val SharedPresetResult.isFinal get() = this is SharedPresetResult.Read || this == SharedPresetResult.NewerVersion

    /**
     * The first complete {…} in [text] that says it is a preset; others ("{jueces}") are skipped.
     * A complete {…} that mentions the preset's kind but can't be read (broken JSON, a kind that
     * isn't a plain string) is [SharedPresetResult.Invalid]: not cut off, just wrong.
     */
    private fun decodeObjects(text: String): SharedPresetResult {
        var mentionsKind = false
        for (candidate in objects(text)) {
            val obj = runCatching { json.parseToJsonElement(candidate) as? JsonObject }.getOrNull()
            if (obj != null && (obj["kind"] as? JsonPrimitive)?.contentOrNull == KIND) return decodeObject(obj)
            if (KIND in candidate) mentionsKind = true
        }
        return if (mentionsKind) SharedPresetResult.Invalid else SharedPresetResult.NotAPreset
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
        // Invisible characters written as JSON escapes survive parsing: dropped here too, or "M" + zero-width + "TR"
        // would look exactly like the built-in MTR and dodge its reserved name.
        val name = UserDraftPresets.cleanName(s.name.replace(INVISIBLE, ""))
        // Only what the app can show: pack sizes it offers, picks inside the pack, reviews between its packs.
        // Anything else would be stored and never used (a crafted preset could bloat the phone's storage).
        val keysOk = s.picks.all { (size, picks) -> size in DraftConfig.CARDS_PER_PACK && picks.keys.all { it in 1..size } } &&
            s.reviews.keys.all { it == DraftTimes.EVERY_REVIEW || it in DraftConfig.PACKS }
        if (!UserDraftPresets.isValidName(name) || !isSafeName(name) || !keysOk || s.minutes !in DraftConfig.CONSTRUCTION_MINUTES) {
            return SharedPresetResult.Invalid
        }
        val times = runCatching { DraftTimes(s.timed, s.picks.filterValues { it.isNotEmpty() }, s.reviews) }.getOrNull()
            ?: return SharedPresetResult.Invalid
        return SharedPresetResult.Read(SharedDraftPreset(name, format, times, s.minutes))
    }

    /**
     * No control characters (a bell, NUL, NEL), no text-direction overrides (they make a name read
     * as something else: "RCQ" + U+202E + "anoriG" shows as "RCQ Girona"), no broken surrogates.
     * A judge's keyboard never types these; a crafted preset might.
     */
    internal fun isSafeName(name: String): Boolean {
        name.forEachIndexed { i, c ->
            if (Character.isISOControl(c) || c.code in BIDI_CONTROLS) return false
            if (c.isHighSurrogate() && !(i + 1 < name.length && name[i + 1].isLowSurrogate())) return false
            if (c.isLowSurrogate() && !(i > 0 && name[i - 1].isHighSurrogate())) return false
        }
        return true
    }

    /** Stable words, not the enum's names: renaming a Kotlin constant must not break shared presets. */
    private val FORMATS = mapOf(DraftFormat.BOOSTER to "booster", DraftFormat.TWO_HEADED_GIANT to "two-headed-giant")
    private val BIDI_CONTROLS = setOf(0x061C, 0x200E, 0x200F) + (0x202A..0x202E) + (0x2066..0x2069)

    // By code, not as characters: invisible or look-alike characters in the source are easy to break.
    // Zero-width space, word joiner, BOM. Not the zero-width (non-)joiners: family emoji and some scripts need them.
    private val INVISIBLE = Regex("[" + listOf(0x200B, 0x2060, 0xFEFF).joinToString("") { Char(it).toString() } + "]")
    private val NO_BREAK_SPACE = Char(0x00A0)
    private val LEFT_QUOTE = Char(0x201C)
    private val RIGHT_QUOTE = Char(0x201D)
}
