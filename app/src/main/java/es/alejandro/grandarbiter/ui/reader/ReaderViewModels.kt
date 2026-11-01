package es.alejandro.grandarbiter.ui.reader

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.data.updates.SectionVersions
import es.alejandro.grandarbiter.data.rules.bundle.GlossaryEntry
import es.alejandro.grandarbiter.domain.model.rules.PageRef
import es.alejandro.grandarbiter.domain.model.rules.RulesDocumentInfo
import es.alejandro.grandarbiter.domain.model.rules.RulesPage
import es.alejandro.grandarbiter.domain.model.rules.RulesSearchResult
import es.alejandro.grandarbiter.domain.model.rules.TocEntry
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val ARG_DOCUMENT = "documentId"
const val ARG_PAGE = "pageId"
const val ARG_ANCHOR = "anchor"

/** Routes of the reader. Ids travel without their document prefix: "cr/603.2a" -> "603.2a". */
object ReaderRoutes {
    const val PAGE = "reader/{$ARG_DOCUMENT}/page/{$ARG_PAGE}?$ARG_ANCHOR={$ARG_ANCHOR}"

    fun page(ref: PageRef): String {
        val base = "reader/${ref.documentId.key}/page/${Uri.encode(ref.pageId.substringAfter('/'))}"
        return ref.anchorId?.let { "$base?$ARG_ANCHOR=${Uri.encode(it.substringAfter('/'))}" } ?: base
    }
}

// ---------------------------------------------------------------- contents + search

data class ReaderContentsUiState(
    val document: RulesDocumentInfo? = null,
    val contents: List<TocEntry> = emptyList(),
    val isLoading: Boolean = true,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ReaderContentsViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: RulesRepository,
    sectionVersions: SectionVersions,
) : ViewModel() {

    val documentId: DocumentId =
        DocumentId.entries.firstOrNull { it.key == savedStateHandle.get<String>(ARG_DOCUMENT) } ?: DocumentId.CR

    init {
        viewModelScope.launch { sectionVersions.markSeen(documentId.key) }   // its «Updated» label goes
    }

    /** Re-emits when a bundle import finishes (first launch), so the contents appear by themselves. */
    val uiState: StateFlow<ReaderContentsUiState> = repository.observeDocuments()
        .map { docs ->
            ReaderContentsUiState(
                document = docs.firstOrNull { it.id == documentId },
                contents = repository.contents(documentId),
                isLoading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderContentsUiState())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<RulesSearchResult>> = _query
        .debounce(250)
        .mapLatest { q -> if (q.isBlank()) emptyList() else repository.search(q, documentId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** CR only: glossary terms matching the search, shown above the rule results. */
    val glossaryHits: StateFlow<List<GlossaryEntry>> = _query
        .debounce(250)
        .mapLatest { q -> if (q.isBlank() || documentId != DocumentId.CR) emptyList() else GlossarySearch.filter(glossary(), q, limit = 5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var glossaryCache: List<GlossaryEntry>? = null
    private suspend fun glossary(): List<GlossaryEntry> = glossaryCache ?: repository.glossary(documentId).also { glossaryCache = it }

    fun onQueryChange(query: String) {
        _query.value = query
    }
}

// ---------------------------------------------------------------- page

data class ReaderPageUiState(
    val page: RulesPage? = null,
    val anchorId: String? = null,
    val isLoading: Boolean = true,
)

class ReaderPageViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: RulesRepository,
    private val glossaryProvider: GlossaryProvider,
    sectionVersions: SectionVersions,
) : ViewModel() {

    private val documentKey: String = checkNotNull(savedStateHandle.get<String>(ARG_DOCUMENT))
    private val pageId: String = "$documentKey/" + checkNotNull(savedStateHandle.get<String>(ARG_PAGE))
    private val anchorId: String? = savedStateHandle.get<String>(ARG_ANCHOR)?.let { "$documentKey/$it" }

    private val _uiState = MutableStateFlow(ReaderPageUiState(anchorId = anchorId))
    val uiState: StateFlow<ReaderPageUiState> = _uiState.asStateFlow()

    private val _glossary = MutableStateFlow<GlossaryLinker?>(null)
    /** CR pages only (the glossary is the CR's). Null until built: the text shows, links come after. */
    val glossary: StateFlow<GlossaryLinker?> = _glossary.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = ReaderPageUiState(page = repository.page(pageId), anchorId = anchorId, isLoading = false)
        }
        // Pages are reached from search, Quick References, links…: any of them counts as entering the document.
        viewModelScope.launch { sectionVersions.markSeen(documentKey) }
        if (documentKey == DocumentId.CR.key) {
            viewModelScope.launch { _glossary.value = glossaryProvider.linker() }
        }
    }

    /** Where a tapped "ref:" link goes; null if the target isn't in the installed rules. */
    suspend fun resolve(sectionId: String): PageRef? = repository.resolve(sectionId)
}
