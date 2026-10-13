package es.alejandro.grandarbiter.ui.tools.quickrefs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.domain.model.quickrefs.DraftRule
import es.alejandro.grandarbiter.domain.model.quickrefs.QuickReference
import es.alejandro.grandarbiter.domain.model.quickrefs.QuickReferenceDetail
import es.alejandro.grandarbiter.domain.model.quickrefs.QuickReferenceDraft
import es.alejandro.grandarbiter.data.rules.bundle.Block
import es.alejandro.grandarbiter.domain.model.rules.PageRef
import es.alejandro.grandarbiter.domain.model.rules.TocEntry
import es.alejandro.grandarbiter.ui.reader.InlineMarkup
import es.alejandro.grandarbiter.domain.model.rules.RulesSearchResult
import es.alejandro.grandarbiter.domain.repository.QuickReferenceRepository
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import es.alejandro.grandarbiter.ui.reader.sectionLabel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val ARG_REFERENCE = "referenceId"
const val ARG_REFERENCE_DOCUMENT = "documentId"

object QuickReferenceRoutes {
    const val LIST = "quick_references"
    const val DETAIL = "quick_reference/{$ARG_REFERENCE}"
    const val EDIT = "quick_reference_edit?$ARG_REFERENCE={$ARG_REFERENCE}&$ARG_REFERENCE_DOCUMENT={$ARG_REFERENCE_DOCUMENT}"

    fun detail(id: Long) = "quick_reference/$id"
    fun edit(id: Long) = "quick_reference_edit?$ARG_REFERENCE=$id"
    fun create(documentId: DocumentId) = "quick_reference_edit?$ARG_REFERENCE_DOCUMENT=${documentId.key}"
}

// ---------------------------------------------------------------- list

class QuickReferencesViewModel(private val repository: QuickReferenceRepository) : ViewModel() {
    /** Null while loading, so the empty state doesn't flash. */
    val references: StateFlow<List<QuickReference>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val canRestorePresets: StateFlow<Boolean> = repository.observeCanRestorePresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun restorePresets() {
        viewModelScope.launch { repository.restorePresets() }
    }
}

// ---------------------------------------------------------------- detail

sealed interface QuickReferenceDetailState {
    data object Loading : QuickReferenceDetailState
    data object NotFound : QuickReferenceDetailState
    data class Loaded(val detail: QuickReferenceDetail) : QuickReferenceDetailState
}

class QuickReferenceDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: QuickReferenceRepository,
    private val rules: RulesRepository,
) : ViewModel() {
    val referenceId: Long = savedStateHandle.get<String>(ARG_REFERENCE)?.toLongOrNull() ?: -1

    val state: StateFlow<QuickReferenceDetailState> = repository.observeDetail(referenceId)
        .map { it?.let(QuickReferenceDetailState::Loaded) ?: QuickReferenceDetailState.NotFound }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickReferenceDetailState.Loading)

    /** For cross-references inside the official text ("see rule 613.4"). */
    suspend fun resolve(sectionId: String): PageRef? = rules.resolve(sectionId)
}

// ---------------------------------------------------------------- editor

data class EditorRule(val rule: DraftRule, val label: String)

data class QuickReferenceEditorState(
    val id: Long? = null,
    val loading: Boolean = true,
    val title: String = "",
    val documentId: DocumentId = DocumentId.CR,
    val rules: List<EditorRule> = emptyList(),
    val query: String = "",
    val results: List<RulesSearchResult> = emptyList(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    /** The document's index, to add rules without searching: chapters > pages > rules. */
    val index: List<IndexNode> = emptyList(),
    val expanded: Set<String> = emptySet(),
    val loadedRules: Map<String, List<IndexNode>> = emptyMap(),
) {
    val canSave: Boolean get() = !loading && !saving && title.isNotBlank()
    val addedIds: Set<String> get() = rules.mapTo(HashSet()) { it.rule.sectionId }
    val indexRows: List<IndexRow> get() = QuickReferenceIndex.rows(index, expanded, loadedRules, addedIds)
}

sealed interface EditorEvent {
    /** [created]: a new reference, so the editor is replaced by its detail. */
    data class Saved(val id: Long, val created: Boolean) : EditorEvent
    data object Deleted : EditorEvent
}

@OptIn(FlowPreview::class)
class QuickReferenceEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: QuickReferenceRepository,
    private val rules: RulesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(QuickReferenceEditorState())
    val state: StateFlow<QuickReferenceEditorState> = _state.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        val id = savedStateHandle.get<String>(ARG_REFERENCE)?.toLongOrNull()
        val documentKey = savedStateHandle.get<String>(ARG_REFERENCE_DOCUMENT)
        viewModelScope.launch {
            val draft = id?.let { repository.draft(it) }
            if (draft != null) {
                _state.update {
                    it.copy(id = draft.id, loading = false, title = draft.title, documentId = draft.documentId,
                        rules = draft.rules.map { r -> EditorRule(r, labelOf(r.sectionId)) })
                }
            } else {
                val document = DocumentId.entries.firstOrNull { it.key == documentKey } ?: DocumentId.CR
                _state.update { it.copy(loading = false, documentId = document) }
            }
        }
        viewModelScope.launch {
            // The index of whichever document the reference is for, once known.
            val document = _state.first { !it.loading }.documentId
            val index = rules.contents(document).map { it.toIndexNode() }
            _state.update { it.copy(index = index) }
        }
        viewModelScope.launch {
            _state.map { it.query.trim() to it.documentId }.distinctUntilChanged().debounce(250).collect { (query, document) ->
                val results = if (query.isEmpty()) emptyList() else rules.search(query, document).take(MAX_RESULTS)
                _state.update { if (it.query.trim() == query) it.copy(results = results) else it }
            }
        }
    }

    fun setTitle(title: String) = _state.update { it.copy(title = title, dirty = true) }
    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun add(result: RulesSearchResult) =
        addSection(result.target.anchorId ?: result.target.pageId, sectionLabel(result.number, result.title))

    /** From the index: [label] as shown there ("702.19. Trample"). */
    fun addSection(sectionId: String, label: String) = _state.update { s ->
        if (sectionId in s.addedIds) s
        else s.copy(rules = s.rules + EditorRule(DraftRule(sectionId), label), dirty = true)
    }

    /** Opens or closes an index entry; a page's rules are read the first time it's opened. */
    fun toggle(sectionId: String) {
        val s = _state.value
        if (sectionId in s.expanded) {
            _state.update { it.copy(expanded = it.expanded - sectionId) }
            return
        }
        _state.update { it.copy(expanded = it.expanded + sectionId) }
        if (QuickReferenceIndex.needsLoading(s.index, s.loadedRules, sectionId)) {
            viewModelScope.launch {
                val children = rulesOf(sectionId)
                _state.update { it.copy(loadedRules = it.loadedRules + (sectionId to children)) }
            }
        }
    }

    /** The rules directly under a page: "702.19. Trample", or "100.1" + the start of its text. */
    private suspend fun rulesOf(pageId: String): List<IndexNode> =
        rules.page(pageId)?.sections.orEmpty().filter { it.depth == 1 }.map { section ->
            IndexNode(
                sectionId = section.id,
                label = sectionLabel(section.number, section.title),
                detail = if (section.title != null) null
                    else section.blocks.firstNotNullOfOrNull { (it as? Block.Paragraph)?.text }?.let(InlineMarkup::plainText),
            )
        }

    private fun TocEntry.toIndexNode(): IndexNode = IndexNode(
        sectionId = sectionId,
        label = sectionLabel(number, title),
        addable = opensPage,
        children = children.takeIf { it.isNotEmpty() }?.map { it.toIndexNode() },
        lazyChildren = children.isEmpty() && opensPage,
    )

    fun remove(sectionId: String) = _state.update { s ->
        s.copy(rules = s.rules.filterNot { it.rule.sectionId == sectionId }, dirty = true)
    }

    fun move(sectionId: String, by: Int) = _state.update { s ->
        val from = s.rules.indexOfFirst { it.rule.sectionId == sectionId }
        val to = from + by
        if (from < 0 || to !in s.rules.indices) s
        else s.copy(rules = s.rules.toMutableList().apply { add(to, removeAt(from)) }, dirty = true)
    }

    fun setNote(sectionId: String, note: String) = _state.update { s ->
        s.copy(rules = s.rules.map { if (it.rule.sectionId == sectionId) it.copy(rule = it.rule.copy(note = note)) else it }, dirty = true)
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = repository.save(QuickReferenceDraft(s.id, s.title, s.documentId, s.rules.map { it.rule }))
            _state.update { it.copy(id = id, saving = false, dirty = false) }
            _events.send(EditorEvent.Saved(id, created = s.id == null))
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            repository.delete(id)
            _events.send(EditorEvent.Deleted)
        }
    }

    /** "613.1" plus its title when it has one (MTR "6.1 Tardiness"); just the id if it's gone. */
    private suspend fun labelOf(sectionId: String): String {
        val ref = rules.resolve(sectionId) ?: return sectionId.substringAfter('/')
        val section = rules.page(ref.pageId)?.sections?.firstOrNull { it.id == sectionId }
            ?: return sectionId.substringAfter('/')
        return sectionLabel(section.number, section.title)
    }

    private companion object {
        const val MAX_RESULTS = 30
    }
}
