package es.alejandro.grandarbiter.ui.tools.drafttimer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A tap on the draft notification: MainActivity asks, the navigation opens the running draft once. */
class DraftOpenRequests {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() { _pending.value = true }
    fun consume() { _pending.value = false }
}
