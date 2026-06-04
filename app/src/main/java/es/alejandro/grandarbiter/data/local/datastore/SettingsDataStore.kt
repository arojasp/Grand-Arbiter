package es.alejandro.grandarbiter.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import es.alejandro.grandarbiter.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    private val LAST_UPDATE_CHECK = stringPreferencesKey("last_update_check")
    private val IS_SET_UP_DONE = booleanPreferencesKey("setup_completed")
    private val THEME_MODE = stringPreferencesKey("theme_mode")

    val lastUpdateCheck: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_UPDATE_CHECK]
    }

    val isSetUpDone: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_SET_UP_DONE] ?: false
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val modeString = preferences[THEME_MODE] ?: ThemeMode.DARK.name
        try {
            ThemeMode.valueOf(modeString)
        } catch (e: Exception) {
            ThemeMode.DARK
        }
    }

    suspend fun saveLastUpdateCheck(timestamp: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_UPDATE_CHECK] = timestamp
        }
    }

    suspend fun setSetUpDone(done: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_SET_UP_DONE] = done
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.name
        }
    }

}
