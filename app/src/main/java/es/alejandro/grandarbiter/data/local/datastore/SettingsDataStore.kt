package es.alejandro.grandarbiter.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import es.alejandro.grandarbiter.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    private val THEME_MODE = stringPreferencesKey("theme_mode")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val modeString = preferences[THEME_MODE] ?: ThemeMode.DARK.name
        try {
            ThemeMode.valueOf(modeString)
        } catch (e: Exception) {
            ThemeMode.DARK
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.name
        }
    }

}
