package es.alejandro.grandarbiter.domain.repository

import es.alejandro.grandarbiter.domain.model.rules.RulesUpdateState
import kotlinx.coroutines.flow.StateFlow

/** Updates the installed rules documents without an app update: at app start and from Settings. */
interface RulesDataUpdater {
    val state: StateFlow<RulesUpdateState>

    /**
     * True once a check finds that this app version must be updated: the published manifest asks
     * for a newer versionCode, or the bundle uses a schema this app can't read. Never goes back to
     * false in the same process (only an app update fixes it).
     */
    val appUpdateRequired: StateFlow<Boolean>

    /** Starts a check (and update if a newer bundle is published). Ignored while one is running. */
    fun checkForUpdates()
}
