package es.alejandro.grandarbiter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.data.releasenotes.ReleaseNotesRepository
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import es.alejandro.grandarbiter.domain.timer.TimerManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(
    rulesRepository: RulesRepository,
    private val timerManager: TimerManager,
    releaseNotes: ReleaseNotesRepository,
) : ViewModel() {

    /** Name of the newest set with release notes, for the Home card. */
    val latestReleaseNotes: StateFlow<String?> = releaseNotes.sets
        .map { it.firstOrNull()?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { releaseNotes.load() }
    }

    /** Effective date of each installed document, by id ("cr", "ipg", …). */
    val effectiveDates: StateFlow<Map<String, LocalDate>> = rulesRepository.observeDocuments()
        .map { docs -> docs.associate { it.id.key to it.effectiveDate } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Ticks only while running AND on screen (WhileSubscribed): no background work. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val elapsedTime: StateFlow<Long> = timerManager.state
        .flatMapLatest { state ->
            if (!state.isRunning) flowOf(state.elapsedAt(System.currentTimeMillis()))
            else flow {
                while (true) {
                    val elapsed = state.elapsedAt(System.currentTimeMillis())
                    emit(elapsed)
                    delay(1_000 - elapsed % 1_000)   // next whole second, so the display never skips one
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val isRunning: StateFlow<Boolean> = timerManager.state
        .map { it.isRunning }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), timerManager.state.value.isRunning)

    fun toggleTimer() = if (timerManager.state.value.isRunning) timerManager.pause() else timerManager.start()
    fun resetTimer() = timerManager.reset()
}
