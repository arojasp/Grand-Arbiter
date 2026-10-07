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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val TEXT_SCALE = stringPreferencesKey("text_scale")
    private val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
    private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")

    val displaySettings: Flow<DisplaySettings> = context.dataStore.data.map { p ->
        DisplaySettings(
            themeMode = enumOr(p[THEME_MODE], ThemeMode.DARK),
            textScale = enumOr(p[TEXT_SCALE], TextScale.DEFAULT),
            highContrast = p[HIGH_CONTRAST] ?: false,
            keepScreenOn = p[KEEP_SCREEN_ON] ?: false,
        )
    }.distinctUntilChanged()

    val themeMode: Flow<ThemeMode> = displaySettings.map { it.themeMode }.distinctUntilChanged()

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
