package es.alejandro.grandarbiter.ui.tools.banned

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.domain.model.cards.CardSearchResult
import es.alejandro.grandarbiter.domain.model.cards.CardSummary
import es.alejandro.grandarbiter.domain.model.customlists.CustomList
import es.alejandro.grandarbiter.domain.model.customlists.CustomListEntry
import es.alejandro.grandarbiter.domain.model.customlists.ListStatus
import es.alejandro.grandarbiter.domain.repository.CardRepository
import es.alejandro.grandarbiter.domain.repository.CustomListRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object CustomListRoutes {
    const val ARG_ID = "listId"
    const val EDIT = "custom_list_edit?$ARG_ID={$ARG_ID}"
    const val CREATE = "custom_list_edit"
    fun edit(id: Long) = "custom_list_edit?$ARG_ID=$id"
}

/** The editor's form. [initial] is what was loaded, to tell whether anything changed. */
data class CustomListForm(val name: String = "", val note: String = "", val entries: List<CustomListEntry> = emptyList()) {
    val sorted: List<CustomListEntry> get() = CustomList(null, name, note, entries).sorted
    val canSave: Boolean get() = name.isNotBlank()
}

sealed interface CustomListEvent {
    data object Done : CustomListEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CustomListEditViewModel(
    private val state: SavedStateHandle,
    private val repository: CustomListRepository,
    private val cards: CardRepository,
) : ViewModel() {

    val id: Long? = state.get<String>(CustomListRoutes.ARG_ID)?.toLongOrNull()

    private val _initial = MutableStateFlow<CustomListForm?>(null)
    val initial: StateFlow<CustomListForm?> = _initial
    private val _form = MutableStateFlow<CustomListForm?>(null)
    /** Null while loading. */
    val form: StateFlow<CustomListForm?> = _form

    private val events = Channel<CustomListEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /** Cards on the phone whose name matches [query] (tokens left out: lists are about real cards). */
    val results: StateFlow<List<CardSummary>> = _query.debounce(150).mapLatest { q ->
        if (q.trim().length < 2) emptyList()
        else (cards.search(q.trim(), limit = 50) as? CardSearchResult.Offline)?.cards.orEmpty().filterNot { it.isToken }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val loaded = id?.let { repository.get(it) }?.let { CustomListForm(it.name, it.note, it.entries) } ?: CustomListForm()
            _initial.value = loaded
            _form.value = FormState.restore(state) ?: loaded
        }
    }

    private fun change(f: (CustomListForm) -> CustomListForm) {
        _form.update { it?.let(f) }
        _form.value?.let { FormState.save(state, it) }
    }

    fun setName(v: String) = change { it.copy(name = v) }
    fun setNote(v: String) = change { it.copy(note = v) }
    fun setQuery(v: String) { _query.value = v }

    /** Adds the card, or changes its status when it is already in the list. */
    fun put(oracleId: String, name: String, status: ListStatus) = change { f ->
        f.copy(entries = f.entries.filterNot { it.oracleId == oracleId } + CustomListEntry(oracleId, name, status))
    }

    fun remove(oracleId: String) = change { f -> f.copy(entries = f.entries.filterNot { it.oracleId == oracleId }) }

    fun save() {
        val f = _form.value?.takeIf { it.canSave } ?: return
        viewModelScope.launch {
            repository.save(CustomList(id, f.name, f.note, f.entries))
            events.send(CustomListEvent.Done)
        }
    }

    fun delete() {
        val listId = id ?: return
        viewModelScope.launch {
            repository.delete(listId)
            events.send(CustomListEvent.Done)
        }
    }
}

/** The form in the SavedStateHandle, so a long list survives process death. Pure, unit-tested. */
internal object FormState {
    private const val KEY_NAME = "name"
    private const val KEY_NOTE = "note"
    private const val KEY_ENTRIES = "entries"

    fun save(state: SavedStateHandle, form: CustomListForm) {
        state[KEY_NAME] = form.name
        state[KEY_NOTE] = form.note
        state[KEY_ENTRIES] = ArrayList(form.entries.map(::encode))
    }

    fun restore(state: SavedStateHandle): CustomListForm? {
        val name = state.get<String>(KEY_NAME) ?: return null
        val entries = state.get<ArrayList<String>>(KEY_ENTRIES).orEmpty().mapNotNull(::decode)
        return CustomListForm(name, state.get<String>(KEY_NOTE).orEmpty(), entries)
    }

    fun encode(e: CustomListEntry): String {
        val status = when (val s = e.status) {
            ListStatus.Banned -> "B"
            ListStatus.Restricted -> "R"
            is ListStatus.Points -> s.value.toString()
        }
        return listOf(e.oracleId, status, e.name).joinToString("\t")
    }

    fun decode(s: String): CustomListEntry? {
        val parts = s.split('\t', limit = 3).takeIf { it.size == 3 } ?: return null
        val status = when (val v = parts[1]) {
            "B" -> ListStatus.Banned
            "R" -> ListStatus.Restricted
            else -> v.toIntOrNull()?.takeIf { it in ListStatus.POINTS_RANGE }?.let { ListStatus.Points(it) } ?: return null
        }
        return CustomListEntry(parts[0], parts[2], status)
    }
}
