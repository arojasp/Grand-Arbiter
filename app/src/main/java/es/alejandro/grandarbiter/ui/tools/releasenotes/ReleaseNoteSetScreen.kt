package es.alejandro.grandarbiter.ui.tools.releasenotes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.releasenotes.ReleaseNotesBlock
import es.alejandro.grandarbiter.data.releasenotes.ReleaseNotesDocument
import es.alejandro.grandarbiter.data.releasenotes.ReleaseNotesRepository
import es.alejandro.grandarbiter.ui.cardsearch.OracleText
import es.alejandro.grandarbiter.ui.components.Label
import es.alejandro.grandarbiter.ui.components.Panel
import es.alejandro.grandarbiter.ui.reader.CopyRuleButton
import es.alejandro.grandarbiter.ui.reader.MarkupText
import es.alejandro.grandarbiter.ui.reader.ReaderSearchField
import es.alejandro.grandarbiter.ui.reader.rememberCopyText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ---------------------------------------------------------------- what the list shows

/** A row of the set screen; pure, so search and section jumps are unit-tested. */
sealed interface NotesItem {
    val key: String
    data class Section(val index: Int, val title: String) : NotesItem { override val key = "s$index" }
    data class Block(val section: Int, val index: Int, val block: ReleaseNotesBlock, val sectionTitle: String) : NotesItem {
        override val key = "b$section-$index"
    }
}

object ReleaseNotesItems {
    /** All sections in order; with a query, only matching cards (name first, then text) and paragraphs. */
    fun build(doc: ReleaseNotesDocument, query: String): List<NotesItem> {
        val q = query.trim()
        if (q.isEmpty()) {
            return buildList {
                doc.sections.forEachIndexed { s, section ->
                    add(NotesItem.Section(s, shortTitle(section.title, doc.name)))
                    section.blocks.forEachIndexed { b, block -> add(NotesItem.Block(s, b, block, section.title)) }
                }
            }
        }
        val all = doc.sections.flatMapIndexed { s, section ->
            section.blocks.mapIndexed { b, block -> NotesItem.Block(s, b, block, shortTitle(section.title, doc.name)) }
        }
        val byName = all.filter { (it.block as? ReleaseNotesBlock.Card)?.name?.contains(q, ignoreCase = true) == true }
        val byText = all.filter { it !in byName && text(it.block).contains(q, ignoreCase = true) }
        return byName + byText
    }

    /** "Reality Fracture Main Set Card-Specific Notes" -> "Main Set Card-Specific Notes". */
    fun shortTitle(title: String, setName: String): String =
        title.removePrefix(setName).trim().ifEmpty { title }

    private fun text(block: ReleaseNotesBlock): String = when (block) {
        is ReleaseNotesBlock.Paragraph -> block.text
        is ReleaseNotesBlock.Heading -> block.text
        is ReleaseNotesBlock.Items -> block.items.joinToString("\n")
        is ReleaseNotesBlock.Card -> (listOf(block.name) + block.lines + block.notes).joinToString("\n")
    }

    /** Plain text for the clipboard: card, its text, its notes, and where it comes from. */
    fun copyText(card: ReleaseNotesBlock.Card, setName: String): String = buildString {
        append(card.name)
        card.lines.forEach { append('\n').append(it) }
        if (card.notes.isNotEmpty()) append("\n")
        card.notes.forEach { append("\n• ").append(it) }
        append("\n\n— ").append(setName).append(" Release Notes")
    }
}

// ---------------------------------------------------------------- view model

sealed interface SetNotesState {
    data object Loading : SetNotesState
    /** No offline copy: offer the online viewer. */
    data object OnlineOnly : SetNotesState
    data class Loaded(val doc: ReleaseNotesDocument, val items: List<NotesItem>, val sections: List<NotesItem.Section>) : SetNotesState
}

class ReleaseNoteSetViewModel(savedStateHandle: SavedStateHandle, repository: ReleaseNotesRepository) : ViewModel() {
    val code: String = savedStateHandle.get<String>(ARG_SET_CODE).orEmpty()
    private val doc = MutableStateFlow<ReleaseNotesDocument?>(null)
    private val loaded = MutableStateFlow(false)
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val state: StateFlow<SetNotesState> = combine(doc, loaded, _query) { d, done, q ->
        when {
            !done -> SetNotesState.Loading
            d == null -> SetNotesState.OnlineOnly
            else -> SetNotesState.Loaded(d, ReleaseNotesItems.build(d, q),
                ReleaseNotesItems.build(d, "").filterIsInstance<NotesItem.Section>())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetNotesState.Loading)

    init {
        viewModelScope.launch {
            doc.value = repository.document(code)
            loaded.value = true
        }
    }

    fun onQueryChange(q: String) { _query.value = q }
}

// ---------------------------------------------------------------- screen

/**
 * A set's release notes, offline: sections with quick jumps, search by card name or text, cards
 * with mana symbols, copy. "Original" opens Wizards' page in the in-app viewer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseNoteSetScreen(
    onBack: () -> Unit,
    onViewOriginal: (code: String) -> Unit,
    viewModel: ReleaseNoteSetViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val query by viewModel.query.collectAsState()
    val colors = MaterialTheme.colorScheme
    val doc = (state as? SetNotesState.Loaded)?.doc

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back), tint = colors.primary)
                    }
                },
                title = {
                    Column {
                        Text(doc?.name.orEmpty(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(doc?.lastModified?.let { stringResource(R.string.release_notes_modified, formatDate(it)) }
                            ?: stringResource(R.string.tools_release_notes),
                            style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { onViewOriginal(viewModel.code) }) {
                        Icon(Icons.Outlined.Language, stringResource(R.string.release_notes_view_original), tint = colors.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            when (val s = state) {
                SetNotesState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                SetNotesState.OnlineOnly -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.release_notes_online_only), textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
                    OutlinedButton(onClick = { onViewOriginal(viewModel.code) }) { Text(stringResource(R.string.release_notes_view_original)) }
                }
                is SetNotesState.Loaded -> Notes(s, query, viewModel::onQueryChange)
            }
        }
    }
}

@Composable
private fun Notes(state: SetNotesState.Loaded, query: String, onQueryChange: (String) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val copy = rememberCopyText()
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        ReaderSearchField(query, onQueryChange, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            placeholder = stringResource(R.string.release_notes_search))
        if (query.isBlank()) {
            // Quick jumps: General Notes, mechanics, the card-specific sections.
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.sections, key = { it.key }) { section ->
                    AssistChip(
                        onClick = {
                            val index = state.items.indexOf(section)
                            if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                        },
                        label = { Text(section.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        } else if (state.items.isEmpty()) {
            Text(stringResource(R.string.reader_no_results), color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 32.dp))
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(state.items, key = { _, item -> item.key }) { _, item ->
                when (item) {
                    is NotesItem.Section -> Text(item.title.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary, modifier = Modifier.padding(top = 16.dp, start = 4.dp))
                    is NotesItem.Block -> SelectionContainer {
                        BlockView(item, showSection = query.isNotBlank()) { card ->
                            copy(card.name, ReleaseNotesItems.copyText(card, state.doc.name))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockView(item: NotesItem.Block, showSection: Boolean, onCopy: (ReleaseNotesBlock.Card) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val noRefs: (String) -> Unit = {}
    when (val block = item.block) {
        is ReleaseNotesBlock.Paragraph -> MarkupText(block.text, noRefs)
        is ReleaseNotesBlock.Heading -> MarkupText(block.text, noRefs, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        is ReleaseNotesBlock.Items -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEach { Row { Text("•  ", color = colors.onSurfaceVariant); MarkupText(it, noRefs) } }
        }
        is ReleaseNotesBlock.Card -> Panel {
            Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(top = 8.dp)) {
                        if (showSection) Label(item.sectionTitle)
                        Text(block.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.onSurface)
                    }
                    CopyRuleButton(block.name, { onCopy(block) })
                }
                Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    block.lines.forEach { OracleText(it, MaterialTheme.typography.bodyMedium, colors.onSurfaceVariant) }
                }
                if (block.notes.isNotEmpty()) {
                    Column(Modifier.padding(end = 12.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        block.notes.forEach { note ->
                            Row {
                                Text("•  ", color = colors.primary)
                                OracleText(note, MaterialTheme.typography.bodyMedium, colors.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun formatDate(iso: String): String = remember(iso) {
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(iso)
}
