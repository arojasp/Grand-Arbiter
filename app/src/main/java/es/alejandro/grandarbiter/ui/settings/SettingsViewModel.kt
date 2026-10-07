package es.alejandro.grandarbiter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.domain.model.cards.CardDataInfo
import es.alejandro.grandarbiter.domain.model.cards.CardUpdateState
import es.alejandro.grandarbiter.domain.repository.CardDataUpdater
import es.alejandro.grandarbiter.domain.repository.CardRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    cards: CardRepository,
    private val updater: CardDataUpdater,
) : ViewModel() {

    /** Re-emits after an update, so the "installed" panel refreshes by itself. */
    val dataInfo: StateFlow<CardDataInfo?> = cards.observeDataInfo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Lives in the updater (app scope): leaving Settings mid-update and coming back shows it. */
    val updateState: StateFlow<CardUpdateState> = updater.state

    fun checkForUpdates() = updater.checkForUpdates()
}
