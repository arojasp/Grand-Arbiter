package es.alejandro.grandarbiter.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
import es.alejandro.grandarbiter.domain.model.DisplaySettings
import es.alejandro.grandarbiter.domain.model.ThemeMode
import es.alejandro.grandarbiter.domain.model.rules.RulesDocumentInfo
import es.alejandro.grandarbiter.domain.model.rules.RulesUpdateState
import es.alejandro.grandarbiter.data.releasenotes.ReleaseNotesRepository
import es.alejandro.grandarbiter.domain.repository.RulesDataUpdater
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    settingsDataStore: SettingsDataStore,
    rulesUpdater: RulesDataUpdater,
    releaseNotes: ReleaseNotesRepository,
) : ViewModel() {
    /** Stored theme only (no stateIn default): MainActivity hands it to the system for the splash. */
    val themeMode: Flow<ThemeMode> = settingsDataStore.themeMode

    /**
     * Theme, text size, contrast and keep-screen-on, applied at the app root. null until DataStore
     * has been read: MainActivity keeps the splash up until then, so the first frame never uses a
     * default theme (it used to: dark for a moment, then the stored light theme). Eagerly, so the read
     * starts with the ViewModel, before setContent.
     */
    val displaySettings: StateFlow<DisplaySettings?> = settingsDataStore.displaySettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The published manifest says this app version must be updated (drives the Play update). */
    val appUpdateRequired: StateFlow<Boolean> = rulesUpdater.appUpdateRequired

    private val _documentsUpdated = Channel<List<RulesDocumentInfo>>(Channel.BUFFERED)
    /** Documents whose effective date changed in an update (a new CR…): shown once, as a snackbar. */
    val documentsUpdated: Flow<List<RulesDocumentInfo>> = _documentsUpdated.receiveAsFlow()

    init {
        // Documents current on every launch: ~300 bytes when nothing changed, ~300 KB when it did.
        // Silent if offline or up to date. Cards (~30 MB) stay manual, from Settings.
        // drop(1): a result from before this screen existed (stale Updated in the app-wide
        // updater) must not show again. Collecting starts before the check, so none is missed.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            rulesUpdater.state.drop(1).collect { state ->
                if (state is RulesUpdateState.Updated && state.changed.isNotEmpty()) _documentsUpdated.send(state.changed)
            }
        }
        rulesUpdater.checkForUpdates()
        // New sets' release notes, ready for offline use before the judge needs them.
        viewModelScope.launch { releaseNotes.refresh() }
    }
}
