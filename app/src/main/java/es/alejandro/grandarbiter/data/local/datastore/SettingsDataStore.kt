package es.alejandro.grandarbiter.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import es.alejandro.grandarbiter.domain.model.DisplaySettings
import es.alejandro.grandarbiter.domain.model.TextScale
import es.alejandro.grandarbiter.domain.model.ThemeMode
import es.alejandro.grandarbiter.domain.updates.SectionUpdates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val TEXT_SCALE = stringPreferencesKey("text_scale")
    private val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
    private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    private val DRAFT_BEEP = booleanPreferencesKey("draft_beep")
    private val DRAFT_VIBRATE = booleanPreferencesKey("draft_vibrate")
    private val SEEN_PREFIX = "seen_version_"   // + section id: "seen_version_cr" = "2026-09-25"

    val displaySettings: Flow<DisplaySettings> = context.dataStore.data.map { p ->
        DisplaySettings(
            themeMode = enumOr(p[THEME_MODE], ThemeMode.SYSTEM),   // not chosen yet: follow the system, like the first splash
            textScale = enumOr(p[TEXT_SCALE], TextScale.DEFAULT),
            highContrast = p[HIGH_CONTRAST] ?: false,
            keepScreenOn = p[KEEP_SCREEN_ON] ?: false,
        )
    }.distinctUntilChanged()

    val themeMode: Flow<ThemeMode> = displaySettings.map { it.themeMode }.distinctUntilChanged()

    /** Draft timer: short beep with the vibration when a step runs out. Off by default. */
    val draftBeep: Flow<Boolean> = context.dataStore.data.map { it[DRAFT_BEEP] ?: false }.distinctUntilChanged()
    suspend fun setDraftBeep(enabled: Boolean) = set(DRAFT_BEEP, enabled)

    /** Draft timer: one vibration when a step runs out. On by default. */
    val draftVibrate: Flow<Boolean> = context.dataStore.data.map { it[DRAFT_VIBRATE] ?: true }.distinctUntilChanged()
    suspend fun setDraftVibrate(enabled: Boolean) = set(DRAFT_VIBRATE, enabled)

    /** «Updated» label: the version of each section the user last entered, by section id (see [SectionUpdates]). */
    val seenSectionVersions: Flow<Map<String, String>> = context.dataStore.data.map(::seenVersions).distinctUntilChanged()

    /** The user entered [sectionId] showing [version]: its label goes until the next update. */
    suspend fun markSectionSeen(sectionId: String, version: String) = set(seenKey(sectionId), version)

    /**
     * Sections with nothing stored count as seen at their installed version (first run, a section that
     * appears later). One edit reads and writes, so it never overwrites a section marked seen meanwhile.
     */
    suspend fun seedSectionVersions(installed: Map<String, String>) {
        context.dataStore.edit { p ->
            SectionUpdates.unseeded(installed, seenVersions(p)).forEach { (id, version) -> p[seenKey(id)] = version }
        }
    }

    private fun seenKey(sectionId: String) = stringPreferencesKey(SEEN_PREFIX + sectionId)

    private fun seenVersions(p: Preferences): Map<String, String> = p.asMap().entries
        .filter { (key, value) -> key.name.startsWith(SEEN_PREFIX) && value is String }
        .associate { (key, value) -> key.name.removePrefix(SEEN_PREFIX) to value as String }

    suspend fun setThemeMode(mode: ThemeMode) = set(THEME_MODE, mode.name)
    suspend fun setTextScale(scale: TextScale) = set(TEXT_SCALE, scale.name)
    suspend fun setHighContrast(enabled: Boolean) = set(HIGH_CONTRAST, enabled)
    suspend fun setKeepScreenOn(enabled: Boolean) = set(KEEP_SCREEN_ON, enabled)

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    /** Unknown stored value (an enum renamed in a later version) -> default, never a crash. */
    private inline fun <reified E : Enum<E>> enumOr(stored: String?, default: E): E =
        stored?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
