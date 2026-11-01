package es.alejandro.grandarbiter.ui.tools.quickrefs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.model.rules.RulesSearchResult
import es.alejandro.grandarbiter.ui.components.Label
import es.alejandro.grandarbiter.ui.components.Panel
import es.alejandro.grandarbiter.ui.components.Section
import es.alejandro.grandarbiter.ui.reader.ReaderSearchField
import es.alejandro.grandarbiter.ui.reader.highlightedSnippet
import es.alejandro.grandarbiter.ui.reader.sectionLabel
import org.koin.androidx.compose.koinViewModel
import es.alejandro.grandarbiter.ui.theme.softened

/*
 * Create / edit a quick reference: title, the rules (in order, each with an optional note) and a
 * search over the reference's document to add more. The document is fixed once chosen in the FAB menu.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReferenceEditScreen(
    onClose: () -> Unit,
    onSaved: (id: Long, created: Boolean) -> Unit,
    onDeleted: () -> Unit,
    viewModel: QuickReferenceEditViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val close = { if (state.dirty) confirmDiscard = true else onClose() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorEvent.Saved -> onSaved(event.id, event.created)
                EditorEvent.Deleted -> onDeleted()
            }
        }
    }
    BackHandler(enabled = state.dirty) { confirmDiscard = true }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = close) { Icon(Icons.Default.Close, stringResource(R.string.qr_cancel), tint = colors.primary) }
                },
                title = {
                    Text(stringResource(if (state.id == null) R.string.qr_new_title else R.string.qr_edit_title),
                        style = MaterialTheme.typography.titleLarge, color = colors.primary)
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.canSave) { Text(stringResource(R.string.qr_save)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { innerPadding ->
        if (state.loading) return@Scaffold
        LazyColumn(
            modifier = Modifier.padding(innerPadding).imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text(stringResource(R.string.qr_field_title)) },
                    supportingText = { Text("${state.documentId.shortLabel()} · ${stringResource(state.documentId.titleRes())}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { Label(stringResource(R.string.qr_rules_count, state.rules.size), Modifier.padding(start = 4.dp, top = 8.dp)) }
            if (state.rules.isEmpty()) {
                item {
                    Text(stringResource(R.string.qr_rules_empty), style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            items(state.rules, key = { it.rule.sectionId }) { rule ->
                val index = state.rules.indexOf(rule)
                RuleEditor(
                    rule = rule,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.rules.lastIndex,
                    onMove = { viewModel.move(rule.rule.sectionId, it) },
                    onRemove = { viewModel.remove(rule.rule.sectionId) },
                    onNote = { viewModel.setNote(rule.rule.sectionId, it) },
                    modifier = Modifier.animateItem(),
                )
            }

            item {
                Section(stringResource(R.string.qr_add_rules)) {
                    ReaderSearchField(
                        query = state.query,
                        onQueryChange = viewModel::setQuery,
                        placeholder = stringResource(R.string.qr_search_placeholder, state.documentId.shortLabel()),
                    )
                }
            }
            if (state.results.isNotEmpty()) {
                item {
                    val added = state.addedIds
                    Panel {
                        state.results.forEachIndexed { i, result ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = colors.outlineVariant.softened(0.4f))
                            SearchResultRow(result, added = (result.target.anchorId ?: result.target.pageId) in added) { viewModel.add(result) }
                        }
                    }
                }
            }

            // No search: the document's index, to add rules by browsing (chapters > pages > rules).
            if (state.query.isBlank() && state.index.isNotEmpty()) {
                item {
                    Label(stringResource(R.string.qr_browse, state.documentId.shortLabel()), Modifier.padding(start = 4.dp, top = 8.dp))
                }
                items(state.indexRows, key = { "index:" + it.sectionId }) { row ->
                    IndexRowView(
                        row = row,
                        onToggle = { viewModel.toggle(row.sectionId) },
                        onAdd = { viewModel.addSection(row.sectionId, row.label) },
                    )
                }
            }

            if (state.id != null) {
                item {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Icon(Icons.Outlined.Delete, null, tint = colors.error)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.qr_delete), color = colors.error)
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.qr_discard_title)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text(stringResource(R.string.qr_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.qr_keep_editing)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.qr_delete_title, state.title)) },
            text = { Text(stringResource(R.string.qr_delete_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.qr_delete), color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.qr_cancel)) } },
        )
    }
}

@Composable
private fun RuleEditor(
    rule: EditorRule,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Panel {
            Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rule.label, style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) { Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.qr_move_up)) }
                    IconButton(onClick = { onMove(1) }, enabled = canMoveDown) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.qr_move_down)) }
                    IconButton(onClick = onRemove) { Icon(Icons.Default.Close, stringResource(R.string.qr_remove_rule), tint = colors.onSurfaceVariant) }
                }
                OutlinedTextField(
                    value = rule.rule.note,
                    onValueChange = onNote,
                    placeholder = { Text(stringResource(R.string.qr_note_placeholder)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: RulesSearchResult, added: Boolean, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !added, onClick = onAdd).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(sectionLabel(result.number, result.title), style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface)
            Text(highlightedSnippet(result.snippet), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            if (added) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
            stringResource(if (added) R.string.qr_added else R.string.qr_add_rule),
            tint = if (added) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** One index entry: tap opens/closes it (chapters, pages); the + adds it (pages, rules). */
@Composable
private fun IndexRowView(row: IndexRow, onToggle: () -> Unit, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = row.expandable || (row.addable && !row.added), onClick = if (row.expandable) onToggle else onAdd)
            .padding(start = 4.dp + 16.dp * row.depth, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                row.loading -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                row.expandable -> Icon(
                    if (row.expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    stringResource(if (row.expanded) R.string.qr_collapse else R.string.qr_expand),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (row.depth == 0) FontWeight.Bold else FontWeight.Medium),
                color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            row.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (row.addable) {
            IconButton(onClick = onAdd, enabled = !row.added) {
                Icon(
                    if (row.added) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                    stringResource(if (row.added) R.string.qr_added else R.string.qr_add_rule),
                    tint = if (row.added) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
