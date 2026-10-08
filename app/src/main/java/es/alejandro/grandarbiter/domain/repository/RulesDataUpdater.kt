package es.alejandro.grandarbiter.domain.repository

import es.alejandro.grandarbiter.domain.model.rules.RulesUpdateState
import kotlinx.coroutines.flow.StateFlow

/** Updates the installed rules documents without an app update. Today: manual, from Settings. */
interface RulesDataUpdater {
    val state: StateFlow<RulesUpdateState>

    /** Starts a check (and update if a newer bundle is published). Ignored while one is running. */
    fun checkForUpdates()
}
