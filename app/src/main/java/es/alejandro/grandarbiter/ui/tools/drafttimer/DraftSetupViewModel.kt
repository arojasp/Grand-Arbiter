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
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.DraftTimesStore
import es.alejandro.grandarbiter.domain.draft.FormatTimes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Setup of one format: packs, cards per pack, beep; Rochester: who picks first and construction minutes. Prefilled with
 * the last draft's config; the edits survive rotation and process death (SavedStateHandle). Booster and Two-Headed
 * Giant take their pick, review and construction times from the Times screen ([DraftTimesStore]).
 */
class DraftSetupViewModel(
    private val state: SavedStateHandle,
    private val store: DraftSessionStore,
    timingsRepository: DraftTimingsRepository,
    private val settings: SettingsDataStore,
    private val timesStore: DraftTimesStore,
) : ViewModel() {

    val format: DraftFormat = DraftFormat.entries.firstOrNull { it.name == state.get<String>(ARG_FORMAT) } ?: DraftFormat.BOOSTER

    /**
     * This format's setup as the judge left it (saved on every change, started or not); before that,
     * the last draft's values if it was this format, else the format's defaults ("Manual start" carries over).
     */
    private val last = store.setup(format)
        ?: store.lastConfig.let { if (it.format == format) it else DraftConfig.defaultsFor(format, it.manualStart) }
    private val packs = state.getStateFlow(KEY_PACKS, last.packs)
    private val cardsPerPack = state.getStateFlow(KEY_CARDS, last.cardsPerPack)
    private val constructionMinutes = state.getStateFlow(KEY_MINUTES, last.constructionMinutes)
    private val manualStart = state.getStateFlow(KEY_MANUAL, last.manualStart)
    /** Rochester: the team picking first (its Player B lays out the first pack). */
    private val firstTeam = state.getStateFlow(KEY_FIRST_TEAM, last.firstTeam)
    /** "Different size per pack": one size per pack round (drafts mixing sets). */
    private val mixedSizes = state.getStateFlow(KEY_MIXED, last.packSizes != null)
    /** Room for the most packs; only the first [packs] are used. */
    private val packSizes = state.getStateFlow(KEY_PACK_SIZES,
        IntArray(DraftConfig.PACKS.last) { last.packSizes?.getOrNull(it) ?: last.cardsPerPack })

    /** Booster and Two-Headed Giant only. */
    val canMixSizes: Boolean = format in DraftConfig.MIXED_SIZE_FORMATS

    private val timings: StateFlow<DraftTimings> = timingsRepository.timings
        .stateIn(viewModelScope, SharingStarted.Eagerly, DraftTimings.FALLBACK)

    /** Booster and Two-Headed Giant: a Times section instead of the construction stepper. */
    val hasTimes: Boolean = format in DraftConfig.CUSTOM_TIMES_FORMATS
    private val storedTimes: StateFlow<FormatTimes> = timesStore.times(format)

    private val baseConfig: StateFlow<DraftConfig> = combine(
        combine(packs, cardsPerPack, constructionMinutes, manualStart, firstTeam) { p, c, m, manual, team ->
            DraftConfig(format, p, c, m, manual, team)
        },
        mixedSizes,
        packSizes,
    ) { c, mixed, sizes ->
        if (mixed && canMixSizes) c.copy(packSizes = sizes.take(c.packs)) else c
    }.stateIn(viewModelScope, SharingStarted.Eagerly, last)

    /** The Times section: what the Times screen shows for these packs ([DraftTimesModel]). Null in Rochester. */
    val times: StateFlow<DraftTimesModel?> = combine(baseConfig, storedTimes, timings) { c, stored, t ->
        if (hasTimes) DraftTimesModel.of(format, (1..c.packs).map(c::cardsInPack), stored, t, last.constructionMinutes) else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Starting a draft stores this as the last config: that is how "Manual start" is remembered. */
    val config: StateFlow<DraftConfig> = combine(baseConfig, times) { c, t ->
        if (t == null) c else c.copy(times = t.times, constructionMinutes = t.constructionMinutes)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, last)

    /** Allowed cards per pack: Two-Headed Giant only 14 or 15 (the sizes the MTR has times for). */
    val cardsRange: IntRange = DraftConfig.cardsRange(format)

    /** Draft time with this config: picks + reviews + construction. Null until the first value. */
    val estimate: StateFlow<DraftEstimate?> = combine(config, timings) { c, t ->
        DraftEstimate.of(DraftPlans.create(c, t))
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

    /** Turning it on starts every pack at the current "Cards per pack". */
    fun setMixedSizes(enabled: Boolean) {
        if (enabled) state[KEY_PACK_SIZES] = IntArray(DraftConfig.PACKS.last) { cardsPerPack.value }
        state[KEY_MIXED] = enabled
    }

    /** [pack] is 1-based. */
    fun setPackSize(pack: Int, value: Int) {
        state[KEY_PACK_SIZES] = packSizes.value.copyOf().also { it[pack - 1] = value.coerceIn(cardsRange) }
    }
    init {
        // Every change is kept at once: going back and opening the setup again shows it.
        viewModelScope.launch { baseConfig.drop(1).collect(store::saveSetup) }
    }

    /** "Reset to MTR" in the Times section: picks, reviews and construction. */
    fun resetTimes() {
        val t = times.value ?: return
        timesStore.save(format, FormatTimes(DraftTimes.MTR, t.mtrConstructionMinutes))
    }

    fun setBeep(enabled: Boolean) = viewModelScope.launch { settings.setDraftBeep(enabled) }
    fun setVibrate(enabled: Boolean) = viewModelScope.launch { settings.setDraftVibrate(enabled) }

    /** Starts the draft now (the first pick's time runs from this moment). */
    fun start() {
        store.save(DraftSession(config.value).started(System.currentTimeMillis()))
    }

    companion object {
        const val ARG_FORMAT = "format"
        private const val KEY_PACKS = "packs"
        private const val KEY_CARDS = "cards_per_pack"
        private const val KEY_MINUTES = "construction_minutes"
        private const val KEY_MANUAL = "manual_start"
        private const val KEY_FIRST_TEAM = "first_team"
        private const val KEY_MIXED = "mixed_pack_sizes"
        private const val KEY_PACK_SIZES = "pack_sizes"
    }
}
