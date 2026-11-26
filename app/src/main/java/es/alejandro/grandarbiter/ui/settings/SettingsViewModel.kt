package es.alejandro.grandarbiter.ui.settings

import es.alejandro.grandarbiter.domain.model.ThemeMode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.domain.draft.BeepVolume
import es.alejandro.grandarbiter.domain.draft.DraftAlerts
import es.alejandro.grandarbiter.domain.draft.VibrationStrength
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    cards: CardRepository,
    rules: RulesRepository,
    private val updater: CardDataUpdater,
    private val rulesUpdater: RulesDataUpdater,
    private val settings: SettingsDataStore,
    private val alerts: DraftAlerts,
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
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setHighContrast(enabled: Boolean) = viewModelScope.launch { settings.setHighContrast(enabled) }
    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { settings.setKeepScreenOn(enabled) }

    val autoOnlineSearch: StateFlow<Boolean> = settings.autoOnlineSearch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setAutoOnlineSearch(enabled: Boolean) = viewModelScope.launch { settings.setAutoOnlineSearch(enabled) }

    val hiddenCommunityFormats: StateFlow<Set<String>> = settings.hiddenCommunityFormats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    fun setCommunityFormatShown(format: String, shown: Boolean) =
        viewModelScope.launch { settings.setCommunityFormatShown(format, shown) }

    /** Draft Timer alerts. Always adjustable: the draft setup's switches decide whether they play. */
    val vibrationStrength: StateFlow<VibrationStrength> = settings.draftVibrationStrength
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VibrationStrength.DEFAULT)
    val beepVolume: StateFlow<BeepVolume> = settings.draftBeepVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BeepVolume.DEFAULT)
    fun setVibrationStrength(strength: VibrationStrength) = viewModelScope.launch { settings.setDraftVibrationStrength(strength) }
    fun setBeepVolume(volume: BeepVolume) = viewModelScope.launch { settings.setDraftBeepVolume(volume) }
    fun testVibration() = alerts.vibrate(vibrationStrength.value)
    fun testBeep() = alerts.beep(beepVolume.value)
    val beepMuted: StateFlow<Boolean> = alerts.beepMuted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * The Text size preview, picked once per visit (this ViewModel lives as long as the screen).
     * Null while it loads. Starts at a random card and takes the next one that is installed and fits.
     */
    val textSample: StateFlow<TextSample?> = flow {
        val start = TextSizeSamples.nextIndex()
        val names = TextSizeSamples.names
        val found = names.indices.asSequence().map { names[(start + it) % names.size] }.firstNotNullOfOrNull { name ->
            runCatching { cards.findByName(name)?.let { cards.card(it.oracleId) } }.getOrNull()
                ?.takeIf { it.oracleText != null && TextSizeSamples.fits(it.oracleText) }
                ?.let { TextSample(it.name, it.oracleText) }
        }
        emit(found ?: TextSample(null, null))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private companion object {
        val ORDER = listOf(DocumentId.CR, DocumentId.IPG, DocumentId.MTR, DocumentId.JAR)
    }
}
