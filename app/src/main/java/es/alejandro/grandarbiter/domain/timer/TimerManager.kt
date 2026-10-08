package es.alejandro.grandarbiter.domain.timer

import kotlinx.coroutines.flow.StateFlow

/**
 * The judge call timer, as data: time accumulated in earlier runs plus, while running, the
 * moment it was (re)started. Elapsed time is computed from the clock, so nothing has to tick in
 * the background: it stays exact if the app is left or killed. Pure: unit-tested.
 */
data class TimerState(
    val accumulatedMs: Long = 0,
    /** Wall-clock millis when the current run started; null while paused. */
    val runningSince: Long? = null,
) {
    val isRunning: Boolean get() = runningSince != null

    /** Never negative, even if the device clock was moved back meanwhile. */
    fun elapsedAt(now: Long): Long = accumulatedMs + (runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0)

    fun started(now: Long): TimerState = if (isRunning) this else copy(runningSince = now)
    fun paused(now: Long): TimerState = if (!isRunning) this else TimerState(accumulatedMs = elapsedAt(now))
}

interface TimerManager {
    val state: StateFlow<TimerState>
    fun start()
    fun pause()
    fun reset()
}
