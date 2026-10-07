package es.alejandro.grandarbiter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.domain.model.DisplaySettings
import es.alejandro.grandarbiter.domain.model.TextScale
import es.alejandro.grandarbiter.domain.model.cards.CardDataInfo
import es.alejandro.grandarbiter.domain.model.cards.CardUpdateState
import es.alejandro.grandarbiter.domain.model.rules.RulesDocumentInfo
import es.alejandro.grandarbiter.domain.repository.CardDataUpdater
import es.alejandro.grandarbiter.domain.repository.RulesDataUpdater
import es.alejandro.grandarbiter.domain.model.rules.RulesUpdateState
import es.alejandro.grandarbiter.domain.repository.CardRepository
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    cards: CardRepository,
    rules: RulesRepository,
    private val updater: CardDataUpdater,
    private val rulesUpdater: RulesDataUpdater,
    private val settings: SettingsDataStore,
) : ViewModel() {

    val display: StateFlow<DisplaySettings> = settings.displaySettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DisplaySettings())

    /** Installed rules documents in the order shown in Settings. */
    val documents: StateFlow<List<RulesDocumentInfo>> = rules.observeDocuments()
        .map { docs -> ORDER.mapNotNull { id -> docs.firstOrNull { it.id == id } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Re-emits after an update, so the card panel refreshes by itself. */
    val cardData: StateFlow<CardDataInfo?> = cards.observeDataInfo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Lives in the updater (app scope): leaving Settings mid-update and coming back shows it. */
    val updateState: StateFlow<CardUpdateState> = updater.state
    val rulesUpdateState: StateFlow<RulesUpdateState> = rulesUpdater.state

    /** One button, both checks: rules documents (small, GitHub) and cards (~30 MB, Scryfall). */
    fun checkForUpdates() {
        rulesUpdater.checkForUpdates()
        updater.checkForUpdates()
    }
    fun setTextScale(scale: TextScale) = viewModelScope.launch { settings.setTextScale(scale) }
    fun setHighContrast(enabled: Boolean) = viewModelScope.launch { settings.setHighContrast(enabled) }
    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { settings.setKeepScreenOn(enabled) }

    private companion object {
        val ORDER = listOf(DocumentId.CR, DocumentId.IPG, DocumentId.MTR, DocumentId.JAR)
    }
}
