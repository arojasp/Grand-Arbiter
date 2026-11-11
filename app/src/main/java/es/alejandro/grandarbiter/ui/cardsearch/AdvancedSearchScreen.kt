package es.alejandro.grandarbiter.ui.cardsearch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.cards.search.AdvancedSearchForm
import es.alejandro.grandarbiter.data.cards.search.ColorField
import es.alejandro.grandarbiter.data.cards.search.ColorMatch
import es.alejandro.grandarbiter.data.cards.search.NumberFilter
import es.alejandro.grandarbiter.data.cards.search.NumberOp
import es.alejandro.grandarbiter.ui.theme.highContrastBorder
import es.alejandro.grandarbiter.ui.components.Choice
import es.alejandro.grandarbiter.ui.components.StepperControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.androidx.compose.koinViewModel

/** A form that writes the search syntax. Opens with the current query read into its fields. */
class AdvancedSearchViewModel(private val state: SavedStateHandle) : ViewModel() {
    /** The form as it opened, to tell whether anything changed. */
    val initial: AdvancedSearchForm = AdvancedSearchForm.fromQuery(state.get<String>(ARG_QUERY).orEmpty())
    private val _form = MutableStateFlow(state.get<String>(KEY_FORM)?.let(AdvancedSearchForm::fromQuery) ?: initial)
    val form: StateFlow<AdvancedSearchForm> = _form

    fun update(change: (AdvancedSearchForm) -> AdvancedSearchForm) {
        _form.value = change(_form.value)
        state[KEY_FORM] = _form.value.toQuery()
    }

    companion object {
        const val ARG_QUERY = "q"
        private const val KEY_FORM = "form"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSearchScreen(
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    viewModel: AdvancedSearchViewModel = koinViewModel(),
) {
    val form by viewModel.form.collectAsState()
    val changed = form != viewModel.initial
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val leave = { if (changed) confirmDiscard = true else onBack() }
    BackHandler(enabled = changed) { confirmDiscard = true }
    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back), tint = colors.primary)
                    }
                },
                title = { Text(stringResource(R.string.cards_adv_title), style = MaterialTheme.typography.titleLarge, color = colors.primary) },
                actions = {
                    if (!form.isEmpty) TextButton(onClick = { viewModel.update { AdvancedSearchForm() } }) { Text(stringResource(R.string.cards_adv_clear)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
        bottomBar = { Footer(form.toQuery()) { onSearch(form.toQuery()) } },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            TextGroup(form, viewModel::update)
            ColorGroup(form, viewModel::update)
            NumberGroup(form, viewModel::update)
            PrintingGroup(form, viewModel::update)
            if (form.extra.isNotBlank() || viewModel.initial.extra.isNotBlank()) {
                Field(stringResource(R.string.cards_adv_other), form.extra, { v -> viewModel.update { it.copy(extra = v) } })
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.cards_adv_discard_title)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onBack() }) { Text(stringResource(R.string.cards_adv_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cards_adv_keep_editing)) } },
        )
    }
}

private typealias Update = ((AdvancedSearchForm) -> AdvancedSearchForm) -> Unit

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, caps: KeyboardCapitalization = KeyboardCapitalization.None) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = caps, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextGroup(form: AdvancedSearchForm, update: Update) {
    Group {
        Field(stringResource(R.string.cards_adv_name), form.name, { v -> update { it.copy(name = v) } }, KeyboardCapitalization.Words)
        Field(stringResource(R.string.cards_adv_rules_text), form.rulesText, { v -> update { it.copy(rulesText = v) } })
        Field(stringResource(R.string.cards_adv_type), form.type, { v -> update { it.copy(type = v) } })
    }
}

private val COLOR_CHOICES = listOf('W' to R.string.cards_adv_white, 'U' to R.string.cards_adv_blue, 'B' to R.string.cards_adv_black,
    'R' to R.string.cards_adv_red, 'G' to R.string.cards_adv_green, 'C' to R.string.cards_adv_colorless)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorGroup(form: AdvancedSearchForm, update: Update) {
    val colors = MaterialTheme.colorScheme
    Group {
        Choice(
            values = listOf(0, 1),
            selected = if (form.colorField == ColorField.COLOR) 0 else 1,
            onSelect = { i ->
                // Identity is usually asked as "fits in this deck", so it starts at At most.
                update {
                    if (i == 0) it.copy(colorField = ColorField.COLOR, colorMatch = ColorMatch.INCLUDES)
                    else it.copy(colorField = ColorField.IDENTITY, colorMatch = ColorMatch.AT_MOST)
                }
            },
            width = null,
            modifier = Modifier.fillMaxWidth(),
            label = { stringResource(if (it == 0) R.string.cards_adv_card_colors else R.string.cards_adv_identity) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            COLOR_CHOICES.forEach { (letter, name) ->
                val on = letter in form.colors
                FilterChip(
                    selected = on,
                    onClick = {
                        update {
                            // Colorless and the five colors exclude each other.
                            val next = when {
                                on -> it.colors - letter
                                letter == 'C' -> setOf('C')
                                else -> it.colors - 'C' + letter
                            }
                            it.copy(colors = next)
                        }
                    },
                    label = { Text(stringResource(name), style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = { ManaSymbol(letter.toString(), Modifier.size(18.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primary,
                        selectedLabelColor = colors.onPrimary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true, selected = on,
                        borderColor = colors.outline, selectedBorderColor = colors.primary,
                    ),
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if (form.colors.isNotEmpty() && form.colors != setOf('C')) {
            Choice(
                values = ColorMatch.entries.map { it.ordinal },
                selected = form.colorMatch.ordinal,
                onSelect = { i -> update { it.copy(colorMatch = ColorMatch.entries[i]) } },
                width = null,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    stringResource(
                        when (ColorMatch.entries[it]) {
                            ColorMatch.INCLUDES -> R.string.cards_adv_includes
                            ColorMatch.EXACTLY -> R.string.cards_adv_exactly
                            ColorMatch.AT_MOST -> R.string.cards_adv_at_most
                        }
                    )
                },
            )
        }
    }
}

@Composable
private fun NumberGroup(form: AdvancedSearchForm, update: Update) {
    Column {
        NumberRow(stringResource(R.string.cards_adv_mana_value), form.manaValue) { f -> update { it.copy(manaValue = f) } }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NumberRow(stringResource(R.string.cards_adv_power), form.power) { f -> update { it.copy(power = f) } }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NumberRow(stringResource(R.string.cards_adv_toughness), form.toughness) { f -> update { it.copy(toughness = f) } }
    }
}

@Composable
private fun NumberRow(label: String, filter: NumberFilter, onChange: (NumberFilter) -> Unit) {
    var menu by rememberSaveable { mutableStateOf(false) }
    val opLabel: @Composable (NumberOp) -> String = { op ->
        when (op) {
            NumberOp.ANY -> stringResource(R.string.cards_adv_any)
            NumberOp.EQ -> "="
            NumberOp.LE -> "≤"
            NumberOp.GE -> "≥"
        }
    }
    // Read alone, the button would say only "≤".
    val opDescription = stringResource(R.string.cards_adv_operator, label, opLabel(filter.op))
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(
                onClick = { menu = true },
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = opDescription },
            ) {
                Text(opLabel(filter.op), style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                NumberOp.entries.forEach { op ->
                    DropdownMenuItem(text = { Text(opLabel(op)) }, onClick = { menu = false; onChange(filter.copy(op = op)) })
                }
            }
        }
        if (filter.op != NumberOp.ANY) {
            StepperControl(label, filter.value, 0..99, { v -> onChange(filter.copy(value = v)) })
        }
    }
}

@Composable
private fun PrintingGroup(form: AdvancedSearchForm, update: Update) {
    Group {
        Field(stringResource(R.string.cards_adv_set), form.setCode, { v -> update { it.copy(setCode = v.trim()) } }, KeyboardCapitalization.Characters)
        Field(stringResource(R.string.cards_adv_number), form.collectorNumber, { v -> update { it.copy(collectorNumber = v.trim()) } })
    }
}

@Composable
private fun Footer(query: String, onSearch: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = highContrastBorder(),
        modifier = Modifier.imePadding(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (query.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.cards_adv_syntax), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(query, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Button(onClick = onSearch, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.cards_adv_search), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
