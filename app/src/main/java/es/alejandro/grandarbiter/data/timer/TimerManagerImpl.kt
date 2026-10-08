package es.alejandro.grandarbiter.data.timer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import es.alejandro.grandarbiter.domain.timer.TimerManager
import es.alejandro.grandarbiter.domain.timer.TimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the judge call timer in SharedPreferences: two numbers, written on start/pause/reset
 * only. No service, no notification, no permissions: the time is derived from the clock.
 */
class TimerManagerImpl(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : TimerManager {

    private val prefs: SharedPreferences = context.getSharedPreferences("judge_call_timer", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    override val state: StateFlow<TimerState> = _state.asStateFlow()

    override fun start() = update { it.started(clock()) }
    override fun pause() = update { it.paused(clock()) }
    override fun reset() = update { TimerState() }

    @Synchronized
    private fun update(change: (TimerState) -> TimerState) {
        val next = change(_state.value)
        _state.value = next
        prefs.edit {
            putLong(KEY_ACCUMULATED, next.accumulatedMs)
            if (next.runningSince != null) putLong(KEY_SINCE, next.runningSince) else remove(KEY_SINCE)
        }
    }

    private fun load() = TimerState(
        accumulatedMs = prefs.getLong(KEY_ACCUMULATED, 0),
        runningSince = if (prefs.contains(KEY_SINCE)) prefs.getLong(KEY_SINCE, 0) else null,
    )

    private companion object {
        const val KEY_ACCUMULATED = "accumulated_ms"
        const val KEY_SINCE = "running_since"
    }
}
