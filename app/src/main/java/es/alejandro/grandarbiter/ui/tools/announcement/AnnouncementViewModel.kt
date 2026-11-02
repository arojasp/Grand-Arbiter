package es.alejandro.grandarbiter.ui.tools.announcement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.data.rules.bundle.Block
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * What the screen needs from the REL: which tardiness rule applies. Competitive and Professional
 * share the IPG (3.1), so they are one option; Regular follows the JAR.
 */
enum class Rel { REGULAR, COMPETITIVE_PROFESSIONAL }

/** The topics a Head Judge covers, in order. Ticked as they're announced; reset each round. */
enum class AnnouncementTopic { WELCOME, JUDGES, CHECK_DECKLISTS, COLLECT_DECKLISTS, PLAYERS, ROUNDS }

/** MTR Appendix E as installed: the table and the note on byes, plus the page to open. */
data class RoundsTable(val table: Block.Table, val notes: List<Block>, val pageId: String)

@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementViewModel(
    private val state: SavedStateHandle,
    rules: RulesRepository,
) : ViewModel() {

    val checked: StateFlow<Set<String>> = state.getStateFlow(KEY_CHECKED, emptySet<String>())
    val rel: StateFlow<String?> = state.getStateFlow(KEY_REL, null as String?)

    /**
     * Read from the installed MTR, not hardcoded: it changes with the MTR (a rules update brings
     * it). Re-read whenever the installed documents change. Null if the MTR has no such table.
     */
    val roundsTable: StateFlow<RoundsTable?> = rules.observeDocuments()
        .flatMapLatest {
            flow {
                val page = rules.page(APPENDIX_E)
                val blocks = page?.root?.blocks.orEmpty()
                val table = blocks.filterIsInstance<Block.Table>().firstOrNull()
                emit(table?.let { RoundsTable(it, blocks.dropWhile { b -> b !== it }.drop(1), APPENDIX_E) })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(topic: AnnouncementTopic) {
        val now = checked.value
        state[KEY_CHECKED] = if (topic.name in now) now - topic.name else now + topic.name
    }

    fun resetChecklist() { state[KEY_CHECKED] = emptySet<String>() }
    fun setRel(value: Rel) { state[KEY_REL] = value.name }

    companion object {
        const val APPENDIX_E = "mtr/appendix-e"
        private const val KEY_CHECKED = "checked"
        private const val KEY_REL = "rel"
    }
}
