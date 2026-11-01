package es.alejandro.grandarbiter.ui.tools.releasenotes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
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
import es.alejandro.grandarbiter.ui.theme.highContrastBorder

// ---------------------------------------------------------------- what the list shows

/** A row of the set screen; pure, so the accordion and search are unit-tested. */
sealed interface NotesItem {
    val key: String

    /** [cardList]: a "Card-Specific Notes" section: it lists card names and shows [cards]. */
    data class Section(val index: Int, val title: String, val cardList: Boolean, val cards: Int) : NotesItem {
        override val key = "s$index"
    }

    /** Shown in full: General Notes, mechanics (with their example cards). */
    data class Block(val section: Int, val index: Int, val block: ReleaseNotesBlock, val sectionTitle: String) : NotesItem {
        override val key = "b$section-$index"
    }

    /** A card of a Card-Specific section: just its name; tapping opens the card's own screen. */
    data class CardRow(val section: Int, val index: Int, val name: String, val sectionTitle: String) : NotesItem {
        override val key = "c$section-$index"
    }
}

object ReleaseNotesItems {
    /** "Main Set Card-Specific Notes", "Commander Card-Specific Notes"… (not General Notes or mechanics). */
    fun isCardList(sectionTitle: String): Boolean = sectionTitle.contains("card-specific", ignoreCase = true)

    /**
     * All sections in order. With a query: matching cards (name first, then text) and paragraphs;
     * cards of Card-Specific sections as rows (they open their own screen), the rest in full.
     */
    fun build(doc: ReleaseNotesDocument, query: String): List<NotesItem> {
        val all = doc.sections.flatMapIndexed { s, section ->
            val title = shortTitle(section.title, doc.name)
            val cardList = isCardList(section.title)
            section.blocks.mapIndexed { b, block ->
                if (cardList && block is ReleaseNotesBlock.Card) NotesItem.CardRow(s, b, block.name, title)
                else NotesItem.Block(s, b, block, title)
            }
        }
        val q = query.trim()
        if (q.isEmpty()) {
            return buildList {
                doc.sections.forEachIndexed { s, section ->
                    val cardList = isCardList(section.title)
                    add(NotesItem.Section(s, shortTitle(section.title, doc.name), cardList,
                        section.blocks.count { it is ReleaseNotesBlock.Card }))
                    addAll(all.filter { sectionOf(it) == s })
                }
            }
        }
        fun blockOf(item: NotesItem) = doc.sections[sectionOf(item)].blocks[indexOf(item)]
        val byName = all.filter { (blockOf(it) as? ReleaseNotesBlock.Card)?.name?.contains(q, ignoreCase = true) == true }
        val byText = all.filter { it !in byName && text(blockOf(it)).contains(q, ignoreCase = true) }
        return byName + byText
    }

    /** Accordion: every section header, and the content of the [expanded] sections only. */
    fun visible(items: List<NotesItem>, expanded: Set<Int>): List<NotesItem> =
        items.filter { it is NotesItem.Section || sectionOf(it) in expanded }

    /** The card at [section]/[index], or null if the document changed under the link. */
    fun card(doc: ReleaseNotesDocument, section: Int, index: Int): ReleaseNotesBlock.Card? =
        doc.sections.getOrNull(section)?.blocks?.getOrNull(index) as? ReleaseNotesBlock.Card

    private fun sectionOf(item: NotesItem): Int = when (item) {
        is NotesItem.Section -> item.index
        is NotesItem.Block -> item.section
        is NotesItem.CardRow -> item.section
    }

    private fun indexOf(item: NotesItem): Int = when (item) {
        is NotesItem.Section -> -1
        is NotesItem.Block -> item.index
        is NotesItem.CardRow -> item.index
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
    data class Loaded(val doc: ReleaseNotesDocument, val items: List<NotesItem>) : SetNotesState
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
            else -> SetNotesState.Loaded(d, ReleaseNotesItems.build(d, q))
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
 * A set's release notes, offline: collapsible sections (like the rules documents' contents), search
 * by card name or text, cards with mana symbols, copy. "Original" opens Wizards' page in the
 * in-app viewer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseNoteSetScreen(
    onBack: () -> Unit,
    onViewOriginal: (code: String) -> Unit,
    onOpenCard: (code: String, section: Int, index: Int) -> Unit,
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
                is SetNotesState.Loaded -> Notes(s, query, viewModel::onQueryChange) { section, index ->
                    onOpenCard(viewModel.code, section, index)
                }
            }
        }
    }
}

@Composable
private fun Notes(
    state: SetNotesState.Loaded,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenCard: (section: Int, index: Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val copy = rememberCopyText()
    val colors = MaterialTheme.colorScheme
    // Accordion like the rules documents' contents: everything collapsed at first.
    var expanded by rememberSaveable { mutableStateOf(listOf<Int>()) }
    val searching = query.isNotBlank()
    val shown = if (searching) state.items else ReleaseNotesItems.visible(state.items, expanded.toSet())

    Column(Modifier.fillMaxSize()) {
        ReaderSearchField(query, onQueryChange, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            placeholder = stringResource(R.string.release_notes_search))
        if (searching && state.items.isEmpty()) {
            Text(stringResource(R.string.reader_no_results), color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 32.dp))
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.key }) { item ->
                when (item) {
                    is NotesItem.Section -> {
                        val open = item.index in expanded
                        SectionHeader(item, open, Modifier.animateItem()) {
                            expanded = if (open) expanded - item.index else expanded + item.index
                            if (!open) {
                                // Opening a long section: bring its header to the top, content follows.
                                val at = ReleaseNotesItems.visible(state.items, expanded.toSet()).indexOf(item)
                                if (at >= 0) scope.launch { listState.animateScrollToItem(at) }
                            }
                        }
                    }
                    is NotesItem.Block -> SelectionContainer(Modifier.animateItem()) {
                        BlockView(item, showSection = searching) { card ->
                            copy(card.name, ReleaseNotesItems.copyText(card, state.doc.name))
                        }
                    }
                    is NotesItem.CardRow -> CardRowView(item, showSection = searching, Modifier.animateItem()) {
                        onOpenCard(item.section, item.index)
                    }
                }
            }
        }
    }
}

/** Collapsible section header, styled like the rules documents' contents. */
@Composable
private fun SectionHeader(section: NotesItem.Section, expanded: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        border = highContrastBorder(),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(section.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (expanded) colors.primary else colors.onSurface)
                if (section.cardList) {   // only the Card-Specific sections say how many cards they list
                    Text(pluralStringResource(R.plurals.release_notes_cards, section.cards, section.cards),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            Icon(painterResource(if (expanded) R.drawable.ic_expand_more else R.drawable.ic_chevron_right), null,
                tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** A card name in a Card-Specific section (or a search hit): opens the card's own screen. */
@Composable
private fun CardRowView(row: NotesItem.CardRow, showSection: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainer)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (showSection) Label(row.sectionTitle)
            Text(row.name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
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
internal fun formatDate(iso: String): String = remember(iso) {
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(iso)
}
