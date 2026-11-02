package es.alejandro.grandarbiter.ui.tools.releasenotes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.releasenotes.ReleaseNotesBlock
import es.alejandro.grandarbiter.data.releasenotes.ReleaseNotesRepository
import es.alejandro.grandarbiter.ui.cardsearch.ManaCostSymbols
import es.alejandro.grandarbiter.ui.cardsearch.OracleText
import es.alejandro.grandarbiter.ui.components.Label
import es.alejandro.grandarbiter.ui.components.Panel
import es.alejandro.grandarbiter.ui.reader.rememberCopyText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

const val ARG_SECTION = "section"
const val ARG_CARD_INDEX = "card"

/** A card from a set's Card-Specific Notes, with where it comes from. */
data class ReleaseNoteCard(
    val card: ReleaseNotesBlock.Card,
    val setName: String,
    val sectionTitle: String,
    val lastModified: String?,
)

sealed interface CardNoteState {
    data object Loading : CardNoteState
    data object NotFound : CardNoteState
    data class Loaded(val note: ReleaseNoteCard) : CardNoteState
}

class ReleaseNoteCardViewModel(savedStateHandle: SavedStateHandle, repository: ReleaseNotesRepository) : ViewModel() {
    private val code = savedStateHandle.get<String>(ARG_SET_CODE).orEmpty()
    private val section = savedStateHandle.get<String>(ARG_SECTION)?.toIntOrNull() ?: -1
    private val index = savedStateHandle.get<String>(ARG_CARD_INDEX)?.toIntOrNull() ?: -1

    private val _state = MutableStateFlow<CardNoteState>(CardNoteState.Loading)
    val state: StateFlow<CardNoteState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val doc = repository.document(code)
            val card = doc?.let { ReleaseNotesItems.card(it, section, index) }
            _state.value = if (doc == null || card == null) CardNoteState.NotFound
            else CardNoteState.Loaded(ReleaseNoteCard(card, doc.name,
                ReleaseNotesItems.shortTitle(doc.sections[section].title, doc.name), doc.lastModified))
        }
    }
}

/** A mana cost line ("{1}{W}", "{2}{U} // {G}"): drawn as symbols next to the name, like card detail. */
private val MANA_COST = Regex("""^(\{[^}]+\})+(\s*//\s*(\{[^}]+\})+)*$""")

/**
 * One card's release notes, on its own screen: the card text with mana symbols and every note
 * about it. Selectable, and the top bar copies it all (card, notes, source).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseNoteCardScreen(onBack: () -> Unit, viewModel: ReleaseNoteCardViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val copy = rememberCopyText()
    val colors = MaterialTheme.colorScheme
    val note = (state as? CardNoteState.Loaded)?.note

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
                        Text(note?.setName.orEmpty(), style = MaterialTheme.typography.titleLarge,
                            color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(note?.sectionTitle.orEmpty(), style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    note?.let { n ->
                        IconButton(onClick = { copy(n.card.name, ReleaseNotesItems.copyText(n.card, n.setName)) }) {
                            Icon(Icons.Outlined.ContentCopy, stringResource(R.string.reader_copy, n.card.name), tint = colors.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            when (val s = state) {
                CardNoteState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                CardNoteState.NotFound -> Text(stringResource(R.string.release_notes_card_not_found),
                    color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).padding(32.dp))
                is CardNoteState.Loaded -> CardNote(s.note)
            }
        }
    }
}

@Composable
private fun CardNote(note: ReleaseNoteCard) {
    val colors = MaterialTheme.colorScheme
    val card = note.card
    val cost = card.lines.firstOrNull()?.takeIf { MANA_COST.matches(it.trim()) }
    val text = if (cost != null) card.lines.drop(1) else card.lines

    SelectionContainer {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Panel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(card.name, style = MaterialTheme.typography.titleLarge,
                            color = colors.onSurface, modifier = Modifier.weight(1f))
                        if (cost != null) {
                            Spacer(Modifier.width(8.dp))
                            ManaCostSymbols(cost, size = 22.dp)
                        }
                    }
                    text.forEach { OracleText(it, MaterialTheme.typography.bodyMedium, colors.onSurfaceVariant) }
                }
            }
            if (card.notes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Label(stringResource(R.string.release_notes_notes), Modifier.padding(start = 4.dp))
                    card.notes.forEach { item ->
                        Row {
                            Text("•  ", color = colors.primary)
                            OracleText(item, MaterialTheme.typography.bodyLarge, colors.onSurface)
                        }
                    }
                }
            }
            val revision = note.lastModified?.let { formatDate(it) }
            Text(
                revision?.let { stringResource(R.string.release_notes_card_source_dated, note.setName, it) }
                    ?: stringResource(R.string.release_notes_card_source, note.setName),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
        }
    }
}
