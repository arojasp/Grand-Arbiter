package es.alejandro.grandarbiter.ui.home

import androidx.lifecycle.ViewModel
import es.alejandro.grandarbiter.domain.repository.CRRepository
import es.alejandro.grandarbiter.domain.timer.TimerManager
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(
    private val crRepository: CRRepository,
    private val timerManager: TimerManager
) : ViewModel() {

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
