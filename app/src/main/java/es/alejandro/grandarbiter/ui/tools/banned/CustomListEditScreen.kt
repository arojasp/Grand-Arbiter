package es.alejandro.grandarbiter.ui.tools.banned

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.model.cards.CardSummary
import es.alejandro.grandarbiter.domain.model.customlists.CustomListEntry
import es.alejandro.grandarbiter.domain.model.customlists.ListStatus
import es.alejandro.grandarbiter.ui.reader.ReaderSearchField
import es.alejandro.grandarbiter.ui.theme.highContrastBorder
import es.alejandro.grandarbiter.ui.tools.drafttimer.Choice
import es.alejandro.grandarbiter.ui.tools.drafttimer.StepperControl
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomListEditScreen(
    onClose: () -> Unit,
    viewModel: CustomListEditViewModel = koinViewModel(),
) {
    val form by viewModel.form.collectAsState()
    val initial by viewModel.initial.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val changed = form != null && form != initial

    LaunchedEffect(viewModel) { viewModel.eventFlow.collect { onClose() } }
    BackHandler(enabled = adding) { adding = false }
    BackHandler(enabled = !adding && changed) { confirmDiscard = true }

    val current = form ?: return
    if (adding) {
        AddCardContent(viewModel, current, onBack = { adding = false }, onAdded = { adding = false })
    } else {
        EditContent(
            form = current,
            isNew = viewModel.id == null,
            title = initial?.name?.takeIf { it.isNotBlank() },
            onBack = { if (changed) confirmDiscard = true else onClose() },
            onDelete = { confirmDelete = true },
            onAdd = { viewModel.setQuery(""); adding = true },
            viewModel = viewModel,
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.custom_discard_title)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text(stringResource(R.string.custom_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.custom_keep_editing)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.custom_delete_title, initial?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.custom_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.custom_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditContent(
    form: CustomListForm,
    isNew: Boolean,
    title: String?,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onAdd: () -> Unit,
    viewModel: CustomListEditViewModel,
) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var pointsFor by remember { mutableStateOf<CustomListEntry?>(null) }
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
                    Text(title ?: stringResource(R.string.custom_new_list), style = MaterialTheme.typography.titleLarge,
                        color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    if (!isNew) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.custom_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.custom_delete_list)) }, onClick = { menu = false; onDelete() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
        bottomBar = {
            Surface(color = colors.surfaceContainerLow, border = highContrastBorder(), modifier = Modifier.imePadding()) {
                Button(onClick = viewModel::save, enabled = form.canSave,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.custom_save), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(form.name, viewModel::setName, label = { Text(stringResource(R.string.custom_name)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(form.note, viewModel::setNote, label = { Text(stringResource(R.string.custom_note)) },
                    minLines = 2, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth())
            }
            item {
                Text(pluralStringResource(R.plurals.custom_cards, form.entries.size, form.entries.size),
                    style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
            item {
                OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Add, null)
                    Text(stringResource(R.string.custom_add_card), Modifier.padding(start = 8.dp))
                }
            }
            items(form.sorted, key = { it.oracleId }) { entry ->
                Column {
                    EntryEditRow(entry, onStatus = { viewModel.put(entry.oracleId, entry.name, it) },
                        onPoints = { pointsFor = entry }, onRemove = { viewModel.remove(entry.oracleId) })
                    HorizontalDivider(color = colors.outlineVariant)
                }
            }
        }
    }

    pointsFor?.let { entry ->
        PointsDialog(entry, onDismiss = { pointsFor = null }) { value ->
            viewModel.put(entry.oracleId, entry.name, ListStatus.Points(value)); pointsFor = null
        }
    }
}

@Composable
private fun statusLabel(status: ListStatus): String = when (status) {
    ListStatus.Banned -> stringResource(R.string.legality_banned)
    ListStatus.Restricted -> stringResource(R.string.legality_restricted)
    is ListStatus.Points -> pluralStringResource(R.plurals.custom_points, status.value, status.value)
}

@Composable
private fun EntryEditRow(entry: CustomListEntry, onStatus: (ListStatus) -> Unit, onPoints: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val label = statusLabel(entry.status)
    val description = stringResource(R.string.custom_status_of, entry.name, label)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { menu = true }, contentPadding = PaddingValues(start = 12.dp, end = 8.dp),
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description }) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.legality_banned)) }, onClick = { menu = false; onStatus(ListStatus.Banned) })
                DropdownMenuItem(text = { Text(stringResource(R.string.legality_restricted)) }, onClick = { menu = false; onStatus(ListStatus.Restricted) })
                DropdownMenuItem(text = { Text(stringResource(R.string.custom_points_menu)) }, onClick = { menu = false; onPoints() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.custom_remove)) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
private fun PointsDialog(entry: CustomListEntry, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    var value by rememberSaveable { mutableStateOf((entry.status as? ListStatus.Points)?.value ?: 1) }
    val label = stringResource(R.string.custom_points_label)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StepperControl(label, value, ListStatus.POINTS_RANGE, { value = it })
            }
        },
        confirmButton = { TextButton(onClick = { onSet(value) }) { Text(stringResource(R.string.custom_set)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.custom_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCardContent(viewModel: CustomListEditViewModel, form: CustomListForm, onBack: () -> Unit, onAdded: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    var picking by remember { mutableStateOf<CardSummary?>(null) }
    val inList = form.entries.associateBy { it.oracleId }
    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back), tint = colors.primary)
                    }
                },
                title = { Text(stringResource(R.string.custom_add_card), style = MaterialTheme.typography.titleLarge, color = colors.primary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            ReaderSearchField(query, viewModel::setQuery, Modifier.padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
                placeholder = stringResource(R.string.custom_search_placeholder))
            LazyColumn(Modifier.fillMaxSize().imePadding()) {
                items(results, key = { it.oracleId }) { card ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { picking = card }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(card.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        inList[card.oracleId]?.let { StatusTag(it.status) }
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                }
            }
        }
    }

    picking?.let { card ->
        StatusSheet(card.name, current = inList[card.oracleId]?.status, onDismiss = { picking = null }) { status ->
            viewModel.put(card.oracleId, card.name, status)
            picking = null
            onAdded()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusSheet(name: String, current: ListStatus?, onDismiss: () -> Unit, onDone: (ListStatus) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(when (current) { ListStatus.Restricted -> 1; is ListStatus.Points -> 2; else -> 0 }) }
    var points by rememberSaveable { mutableStateOf((current as? ListStatus.Points)?.value ?: 1) }
    val pointsLabel = stringResource(R.string.custom_points_label)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Choice(
                values = listOf(0, 1, 2), selected = kind, onSelect = { kind = it }, width = null, modifier = Modifier.fillMaxWidth(),
                label = {
                    stringResource(when (it) { 0 -> R.string.legality_banned; 1 -> R.string.legality_restricted; else -> R.string.custom_points_label })
                },
            )
            if (kind == 2) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pointsLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    StepperControl(pointsLabel, points, ListStatus.POINTS_RANGE, { points = it })
                }
            }
            Button(
                onClick = { onDone(when (kind) { 0 -> ListStatus.Banned; 1 -> ListStatus.Restricted; else -> ListStatus.Points(points) }) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(stringResource(if (current == null) R.string.custom_add else R.string.custom_update), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
