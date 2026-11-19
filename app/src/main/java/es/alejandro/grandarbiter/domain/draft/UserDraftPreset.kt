package es.alejandro.grandarbiter.domain.draft

import kotlinx.coroutines.flow.StateFlow

/**
 * The judge's own draft times, saved under a name for one format (Booster or Two-Headed Giant).
 * A snapshot: choosing it copies its times and construction minutes; editing them afterwards
 * doesn't change the preset.
 */
data class UserDraftPreset(
    val id: String,
    val name: String,
    val format: DraftFormat,
    val times: DraftTimes,
    val constructionMinutes: Int,
) {
    init {
        require(UserDraftPresets.isValidName(name)) { "bad preset name '$name'" }
        require(format in DraftConfig.CUSTOM_TIMES_FORMATS) { "no presets for $format" }
        require(constructionMinutes in DraftConfig.CONSTRUCTION_MINUTES) { "constructionMinutes $constructionMinutes" }
    }

    /** These are the times and construction minutes in use. */
    fun matches(times: DraftTimes, constructionMinutes: Int): Boolean =
        this.times == times && this.constructionMinutes == constructionMinutes
}

/** The rules of a format's list of presets: names, order, save / rename / delete. Pure: unit-tested. */
object UserDraftPresets {

    const val MAX_NAME_LENGTH = 40

    /** As stored and compared: no spaces at the ends, single spaces inside. */
    fun cleanName(raw: String): String = raw.trim().replace(SPACES, " ")

    fun isValidName(name: String): Boolean =
        name.isNotEmpty() && name.length <= MAX_NAME_LENGTH && name == cleanName(name)

    /** The built-in presets' names: two "Pro Tour" in one list would be confusing. */
    val RESERVED_NAMES = setOf("MTR", "Pro Tour")

    fun isReserved(name: String): Boolean = RESERVED_NAMES.any { it.equals(cleanName(name), ignoreCase = true) }

    /** The preset called [name] (ignoring case), if any. */
    fun named(presets: List<UserDraftPreset>, name: String): UserDraftPreset? {
        val clean = cleanName(name)
        return presets.firstOrNull { it.name.equals(clean, ignoreCase = true) }
    }

    /** By name, ignoring case: the order the picker shows. */
    fun sorted(presets: List<UserDraftPreset>): List<UserDraftPreset> =
        presets.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    /** [preset] added; one with the same name (any case) is replaced and its id kept. */
    fun withSaved(presets: List<UserDraftPreset>, preset: UserDraftPreset): List<UserDraftPreset> {
        val same = named(presets, preset.name)
        val saved = if (same != null) preset.copy(id = same.id) else preset
        return sorted(presets.filterNot { it.id == saved.id } + saved)
    }

    /** Null when [name] isn't valid, is a built-in's or another preset already has it. */
    fun withRenamed(presets: List<UserDraftPreset>, id: String, name: String): List<UserDraftPreset>? {
        val clean = cleanName(name)
        if (!isValidName(clean) || isReserved(clean)) return null
        val other = named(presets, clean)
        if (other != null && other.id != id) return null
        return sorted(presets.map { if (it.id == id) it.copy(name = clean) else it })
    }

    fun without(presets: List<UserDraftPreset>, id: String): List<UserDraftPreset> = presets.filterNot { it.id == id }

    private val SPACES = Regex("\\s+")
}

/** Each format's presets, kept on the phone. */
interface UserDraftPresetStore {
    fun presets(format: DraftFormat): StateFlow<List<UserDraftPreset>>

    /** Saved under [name]; a preset with that name (any case) is replaced. [name] must be valid and not reserved. */
    fun save(format: DraftFormat, name: String, times: DraftTimes, constructionMinutes: Int): UserDraftPreset

    /** False when [name] isn't valid, is a built-in's or is taken by another preset. */
    fun rename(format: DraftFormat, id: String, name: String): Boolean

    fun delete(format: DraftFormat, id: String)
}
