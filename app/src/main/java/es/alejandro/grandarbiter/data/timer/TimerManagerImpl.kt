package es.alejandro.grandarbiter.data.timer

import android.content.Context
import android.content.Intent
import es.alejandro.grandarbiter.domain.timer.TimerManager
import es.alejandro.grandarbiter.service.TimerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TimerManagerImpl(private val context: Context) : TimerManager {
    private val _elapsedTime = MutableStateFlow(0L)
    override val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    override fun startTimer() {
        sendCommand(TimerService.ACTION_START)
    }

    override fun pauseTimer() {
        sendCommand(TimerService.ACTION_PAUSE)
    }

    override fun resetTimer() {
        sendCommand(TimerService.ACTION_RESET)
    }

    override fun updateElapsedTime(time: Long) {
        _elapsedTime.value = time
    }

    override fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    private fun sendCommand(action: String) {
        val intent = Intent(context, TimerService::class.java).apply {
            this.action = action
        }
        context.startService(intent)
    }
}
