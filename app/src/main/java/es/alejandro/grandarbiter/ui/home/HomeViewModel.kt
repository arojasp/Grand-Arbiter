package es.alejandro.grandarbiter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import es.alejandro.grandarbiter.domain.timer.TimerManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class HomeViewModel(
    rulesRepository: RulesRepository,
    private val timerManager: TimerManager
) : ViewModel() {

    /** Effective date of each installed document, by id ("cr", "ipg", …). */
    val effectiveDates: StateFlow<Map<String, LocalDate>> = rulesRepository.observeDocuments()
        .map { docs -> docs.associate { it.id.key to it.effectiveDate } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val elapsedTime: StateFlow<Long> = timerManager.elapsedTime
    val isRunning: StateFlow<Boolean> = timerManager.isRunning

    fun toggleTimer() {
        if (isRunning.value) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        timerManager.startTimer()
    }

    fun pauseTimer() {
        timerManager.pauseTimer()
    }

    fun resetTimer() {
        timerManager.resetTimer()
    }
}
