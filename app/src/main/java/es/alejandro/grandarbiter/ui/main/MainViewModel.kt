package es.alejandro.grandarbiter.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
import es.alejandro.grandarbiter.domain.model.DisplaySettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MainViewModel(settingsDataStore: SettingsDataStore) : ViewModel() {
    /** Theme, text size, contrast and keep-screen-on, applied at the app root. */
    val displaySettings: StateFlow<DisplaySettings> = settingsDataStore.displaySettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DisplaySettings())
}
