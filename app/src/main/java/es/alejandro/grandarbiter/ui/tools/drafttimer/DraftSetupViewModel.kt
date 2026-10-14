package es.alejandro.grandarbiter.ui.tools.drafttimer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.data.draft.DraftTimingsRepository
import es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
import es.alejandro.grandarbiter.domain.draft.DraftTimings
import es.alejandro.grandarbiter.domain.draft.DraftConfig
import es.alejandro.grandarbiter.domain.draft.DraftEstimate
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftPlans
import es.alejandro.grandarbiter.domain.draft.DraftSession
import es.alejandro.grandarbiter.domain.draft.DraftSessionStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Setup of one format: packs, cards per pack, construction minutes, beep; Rochester: who picks first. Prefilled with the
 * last draft's config; the edits survive rotation and process death (SavedStateHandle).
 */
class DraftSetupViewModel(
    private val state: SavedStateHandle,
    private val store: DraftSessionStore,
    timingsRepository: DraftTimingsRepository,
    private val settings: SettingsDataStore,
) : ViewModel() {

    val format: DraftFormat = DraftFormat.entries.firstOrNull { it.name == state.get<String>(ARG_FORMAT) } ?: DraftFormat.BOOSTER

    /** The last draft's values if it was this format; otherwise the format's own defaults ("Manual start" carries over). */
    private val last = store.lastConfig.let { if (it.format == format) it else DraftConfig.defaultsFor(format, it.manualStart) }
    private val packs = state.getStateFlow(KEY_PACKS, last.packs)
    private val cardsPerPack = state.getStateFlow(KEY_CARDS, last.cardsPerPack)
    private val constructionMinutes = state.getStateFlow(KEY_MINUTES, last.constructionMinutes)
    private val manualStart = state.getStateFlow(KEY_MANUAL, last.manualStart)
    /** Rochester: the team picking first (its Player B lays out the first pack). */
    private val firstTeam = state.getStateFlow(KEY_FIRST_TEAM, last.firstTeam)

    /** Starting a draft stores this as the last config: that is how "Manual start" is remembered. */
    val config: StateFlow<DraftConfig> = combine(packs, cardsPerPack, constructionMinutes, manualStart, firstTeam) { p, c, m, manual, team ->
        DraftConfig(format, p, c, m, manual, team)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, last)

    private val timings: StateFlow<DraftTimings> = timingsRepository.timings
        .stateIn(viewModelScope, SharingStarted.Eagerly, DraftTimings.FALLBACK)

    /** Allowed cards per pack: Two-Headed Giant only 14 or 15 (the sizes the MTR has times for). */
    val cardsRange: IntRange = DraftConfig.cardsRange(format)

    /** Draft time with this config: picks + reviews + construction. Null for formats not available. */
    val estimate: StateFlow<DraftEstimate?> = combine(config, timings) { c, t ->
        if (c.format.available) DraftEstimate.of(DraftPlans.create(c, t)) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val beep: StateFlow<Boolean> = settings.draftBeep
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val vibrate: StateFlow<Boolean> = settings.draftVibrate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setPacks(value: Int) { state[KEY_PACKS] = value.coerceIn(DraftConfig.PACKS) }
    fun setCardsPerPack(value: Int) { state[KEY_CARDS] = value.coerceIn(cardsRange) }
    fun setConstructionMinutes(value: Int) { state[KEY_MINUTES] = value.coerceIn(DraftConfig.CONSTRUCTION_MINUTES) }
    fun setManualStart(enabled: Boolean) { state[KEY_MANUAL] = enabled }
    fun setFirstTeam(team: Int) { state[KEY_FIRST_TEAM] = team.coerceIn(1, 2) }
    fun setBeep(enabled: Boolean) = viewModelScope.launch { settings.setDraftBeep(enabled) }
    fun setVibrate(enabled: Boolean) = viewModelScope.launch { settings.setDraftVibrate(enabled) }

    /** Starts the draft now (the first pick's time runs from this moment). */
    fun start() {
        if (!format.available) return
        store.save(DraftSession(config.value).started(System.currentTimeMillis()))
    }

    companion object {
        const val ARG_FORMAT = "format"
        private const val KEY_PACKS = "packs"
        private const val KEY_CARDS = "cards_per_pack"
        private const val KEY_MINUTES = "construction_minutes"
        private const val KEY_MANUAL = "manual_start"
        private const val KEY_FIRST_TEAM = "first_team"
    }
}
