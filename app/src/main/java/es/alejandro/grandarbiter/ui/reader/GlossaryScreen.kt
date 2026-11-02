package es.alejandro.grandarbiter.ui.reader

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.data.rules.bundle.GlossaryEntry
import es.alejandro.grandarbiter.domain.model.rules.PageRef
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.compose.foundation.lazy.itemsIndexed
import es.alejandro.grandarbiter.ui.components.ListDivider
import es.alejandro.grandarbiter.ui.components.groupedItem

const val ARG_TERM = "term"

object GlossaryRoutes {
    const val GLOSSARY = "glossary?$ARG_TERM={$ARG_TERM}"
    fun glossary(term: String? = null) = if (term == null) "glossary" else "glossary?$ARG_TERM=${Uri.encode(term)}"
}

/** Term search, shared by the glossary screen and the CR contents search. Pure: unit-tested. */
object GlossarySearch {
    /** Terms containing [query], those starting with it first; blank = everything. */
    fun filter(entries: List<GlossaryEntry>, query: String, limit: Int = Int.MAX_VALUE): List<GlossaryEntry> {
        val q = query.trim()
        if (q.isEmpty()) return entries.take(limit)
        val (starts, contains) = entries.filter { it.term.contains(q, ignoreCase = true) }
            .partition { it.term.startsWith(q, ignoreCase = true) }
        return (starts + contains).take(limit)
    }
}

class GlossaryViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: RulesRepository,
    glossaryProvider: GlossaryProvider,
) : ViewModel() {
    /** Opened from a search hit: that term starts expanded and in view. */
    val initialTerm: String? = savedStateHandle.get<String>(ARG_TERM)

    private val entries = MutableStateFlow<List<GlossaryEntry>?>(null)
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _linker = MutableStateFlow<GlossaryLinker?>(null)
    val linker: StateFlow<GlossaryLinker?> = _linker.asStateFlow()

    /** Null while loading. */
    val visible: StateFlow<List<GlossaryEntry>?> = combine(entries, _query) { all, q -> all?.let { GlossarySearch.filter(it, q) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { entries.value = repository.glossary(DocumentId.CR) }
        viewModelScope.launch { _linker.value = glossaryProvider.linker() }
    }

    fun onQueryChange(q: String) { _query.value = q }
    suspend fun resolve(sectionId: String): PageRef? = repository.resolve(sectionId)
}

/** The CR glossary: every term, searchable; tap to read the definition, whose links work. */
@Composable
fun GlossaryScreen(
    onBack: () -> Unit,
    onOpenPage: (PageRef) -> Unit,
    viewModel: GlossaryViewModel = koinViewModel(),
) {
    val entries by viewModel.visible.collectAsState()
    val query by viewModel.query.collectAsState()
    val linker by viewModel.linker.collectAsState()
    var open by rememberSaveable { mutableStateOf(viewModel.initialTerm) }
    val scope = rememberCoroutineScope()
    val onRefClick: (String) -> Unit = { id -> scope.launch { viewModel.resolve(id)?.let(onOpenPage) } }
    // A glossary term inside a definition opens that term here.
    val links = remember(linker) { linker?.let { GlossaryLinks(it) { entry -> open = entry.term; viewModel.onQueryChange("") } } }
    val listState = rememberLazyListState()

    // Bring the open term into view (from a search hit, or a term tapped inside a definition).
    // Only when it isn't on screen: a term tapped in the list is already there, and scrolling it to
    // the top while it expands looked like opening twice.
    LaunchedEffect(open, entries) {
        val list = entries ?: return@LaunchedEffect
        val index = list.indexOfFirst { it.term == open }
        if (index < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == index }
        if (!visible) listState.animateScrollToItem(index)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ReaderTopBar(title = stringResource(R.string.reader_glossary), subtitle = stringResource(R.string.home_cr_title), onBack = onBack) },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize().padding(horizontal = 16.dp)) {
            ReaderSearchField(query, viewModel::onQueryChange, Modifier.padding(vertical = 12.dp),
                placeholder = stringResource(R.string.glossary_search_placeholder))
            val list = entries
            when {
                list == null -> Unit
                list.isEmpty() -> Text(stringResource(R.string.reader_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp).align(Alignment.CenterHorizontally))
                else -> CompositionLocalProvider(LocalGlossary provides links) {
                    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                        // 700+ terms: one grouped list that stays lazy (design-decisions §3).
                        itemsIndexed(list, key = { _, it -> it.term }) { i, entry ->
                            TermRow(entry, expanded = entry.term == open, onRefClick, Modifier.animateItem().groupedItem(i, list.size), divider = i > 0) {
                                open = if (open == entry.term) null else entry.term
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TermRow(
    entry: GlossaryEntry,
    expanded: Boolean,
    onRefClick: (String) -> Unit,
    modifier: Modifier,
    divider: Boolean,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.animateContentSize()) {
        if (divider) ListDivider()
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Gold while open: a real state (design-decisions §1).
            Text(entry.term, style = MaterialTheme.typography.titleMedium,
                color = if (expanded) colors.primary else colors.onSurface, modifier = Modifier.weight(1f))
            Icon(painterResource(if (expanded) R.drawable.ic_expand_more else R.drawable.ic_chevron_right), null,
                tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        if (expanded) {
            SelectionContainer {
                MarkupText(entry.definition, onRefClick, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
            }
        }
    }
}
