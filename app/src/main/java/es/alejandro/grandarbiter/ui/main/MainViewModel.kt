package es.alejandro.grandarbiter.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
import es.alejandro.grandarbiter.domain.model.ThemeMode
import es.alejandro.grandarbiter.ui.navigation.Screen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val settingsDataStore: SettingsDataStore) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settingsDataStore.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.DARK
        )

    val startDestination: StateFlow<String?> = settingsDataStore.isSetUpDone
        .map { isDone ->
            if (isDone) Screen.Home.route else Screen.SetUp.route
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun getStartDestination(): String {
        return startDestination.value ?: Screen.SetUp.route
    }

    fun setSetUpDone() {
        viewModelScope.launch {
            settingsDataStore.setSetUpDone(true)
        }
    }
}
