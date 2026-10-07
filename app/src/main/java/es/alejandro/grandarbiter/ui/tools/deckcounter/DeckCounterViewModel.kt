package es.alejandro.grandarbiter.ui.tools.deckcounter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/** A full snapshot, so "Reset" can be undone from the snackbar. */
data class CountSnapshot(val main: IntArray, val side: IntArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CountSnapshot

        if (!main.contentEquals(other.main)) return false
        if (!side.contentEquals(other.side)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = main.contentHashCode()
        result = 31 * result + side.contentHashCode()
        return result
    }
}

/**
 * Everything lives in SavedStateHandle: a judge can leave mid-check (to look a card up) or the
 * process can be killed, and the count is still there when they come back.
 */
class DeckCounterViewModel(private val state: SavedStateHandle) : ViewModel() {

    val main: StateFlow<IntArray> = state.getStateFlow(KEY_MAIN, IntArray(0))
    val side: StateFlow<IntArray> = state.getStateFlow(KEY_SIDE, IntArray(0))
    val pile: StateFlow<String> = state.getStateFlow(KEY_PILE, Pile.MAIN.name)
    val format: StateFlow<String> = state.getStateFlow(KEY_FORMAT, DeckFormat.CONSTRUCTED.name)

    private val pileKey get() = if (pile.value == Pile.SIDEBOARD.name) KEY_SIDE else KEY_MAIN
    private fun groups(key: String): IntArray = state.get<IntArray>(key) ?: IntArray(0)

    fun add(cards: Int) { state[pileKey] = groups(pileKey) + cards }

    fun undo() {
        val g = groups(pileKey)
        if (g.isNotEmpty()) state[pileKey] = g.copyOf(g.size - 1)
    }

    fun selectPile(p: Pile) { state[KEY_PILE] = p.name }

    fun selectFormat(f: DeckFormat) {
        state[KEY_FORMAT] = f.name
        if (!f.hasSideboard) state[KEY_PILE] = Pile.MAIN.name
    }

    /** Clears both piles; returns what was there for the snackbar's Undo. */
    fun reset(): CountSnapshot {
        val snapshot = CountSnapshot(groups(KEY_MAIN), groups(KEY_SIDE))
        state[KEY_MAIN] = IntArray(0)
        state[KEY_SIDE] = IntArray(0)
        return snapshot
    }

    fun restore(snapshot: CountSnapshot) {
        state[KEY_MAIN] = snapshot.main
        state[KEY_SIDE] = snapshot.side
    }

    private companion object {
        const val KEY_MAIN = "main"
        const val KEY_SIDE = "side"
        const val KEY_PILE = "pile"
        const val KEY_FORMAT = "format"
    }
}
