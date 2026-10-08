package es.alejandro.grandarbiter.domain.repository

import es.alejandro.grandarbiter.domain.model.cards.CardUpdateState
import kotlinx.coroutines.flow.StateFlow

/** Updates the installed card data from Scryfall. Today: manual, from Settings. */
interface CardDataUpdater {
    val state: StateFlow<CardUpdateState>

    /** Starts a check (and update if Scryfall has newer data). Ignored while one is running. */
    fun checkForUpdates()
}
