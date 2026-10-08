package es.alejandro.grandarbiter.domain.timer

import kotlinx.coroutines.flow.StateFlow

interface TimerManager {
    val elapsedTime: StateFlow<Long>
    val isRunning: StateFlow<Boolean>
    
    fun startTimer()
    fun pauseTimer()
    fun resetTimer()
    fun updateElapsedTime(time: Long)
    fun setRunning(running: Boolean)
}
