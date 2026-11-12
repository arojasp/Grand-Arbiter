package es.alejandro.grandarbiter.ui.tools.deckcounter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.ui.components.Choice
import es.alejandro.grandarbiter.ui.theme.legalColors
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/*
 * Deck Counter, redesigned from Figma 19:893: count a deck check by tapping piles of 1–4 identical
 * cards. Added for real deck checks: main/sideboard piles, format targets (Constructed 60+/15,
 * Limited 40+, Commander 100), undo, a reset that can be undone, haptic tick per tap.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckCounterScreen(
    onBack: () -> Unit,
    viewModel: DeckCounterViewModel = koinViewModel(),
) {
    val main by viewModel.main.collectAsState()
    val side by viewModel.side.collectAsState()
    val pileName by viewModel.pile.collectAsState()
    val formatName by viewModel.format.collectAsState()
    val lastCustom by viewModel.lastCustom.collectAsState()
    var customDialog by rememberSaveable { mutableStateOf(false) }
    val pile = Pile.valueOf(pileName)
    val format = DeckFormat.valueOf(formatName)
    val groups = if (pile == Pile.MAIN) main else side
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resetMessage = stringResource(R.string.deck_reset_done)
    val undoLabel = stringResource(R.string.deck_undo)

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back), tint = colors.primary)
                    }
                },
                title = {
                    Text(stringResource(R.string.tools_deck_counter),
                        style = MaterialTheme.typography.titleLarge, color = colors.primary)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { innerPadding ->
        val total = groups.sum()
        val header: @Composable () -> Unit = {
            // Format + pile
            Choice(
                values = DeckFormat.entries.map { it.ordinal },
                selected = format.ordinal,
                onSelect = { viewModel.selectFormat(DeckFormat.entries[it]) },
                width = null,
                modifier = Modifier.fillMaxWidth(),
            ) { stringResource(DeckFormat.entries[it].label()) }
            if (format.hasSideboard) {
                PileTabs(pile, main.sum(), side.sum(), viewModel::selectPile)
            }
        }
        val count: @Composable (Int) -> Unit = { size ->
            // Total + status
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    total.toString(),
                    // Huge numeral: displayLarge resized (Bold from Type.kt; was Black 900, not a native weight).
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = size.sp, lineHeight = (size + 8).sp, fontFeatureSettings = "tnum"),
                    color = colors.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                StatusLine(DeckCount.status(format, pile, total), format, pile)
            }
        }
        val pads: @Composable (Modifier) -> Unit = { modifier ->
            // 1–4 pads fill the space left: big targets, thumb reach
            Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in listOf(1 to 2, 3 to 4)) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (n in listOf(row.first, row.second)) {
                            CountPad(n, Modifier.weight(1f).fillMaxSize()) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.add(n)
                            }
                        }
                    }
                }
                // Any other pile (basic lands, a Commander deck's 30 basics): judge feedback.
                CustomRow(
                    last = lastCustom,
                    onAgain = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.add(lastCustom)
                    },
                    onOther = { customDialog = true },
                )
            }
        }
        val actions: @Composable () -> Unit = {
            // Undo last + reset (undoable from the snackbar)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(stringResource(R.string.deck_undo_last), enabled = groups.isNotEmpty(), modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.AutoMirrored.Filled.Undo, null, modifier = Modifier.size(18.dp)) }) {
                    viewModel.undo()
                }
                ActionButton(stringResource(R.string.deck_reset), enabled = main.isNotEmpty() || side.isNotEmpty(), modifier = Modifier.weight(1f),
                    destructive = true, icon = { Icon(Icons.Outlined.RestartAlt, null, modifier = Modifier.size(18.dp)) }) {
                    val snapshot = viewModel.reset()
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        val result = snackbar.showSnackbar(resetMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) viewModel.restore(snapshot)
                    }
                }
            }
        }

        if (customDialog) {
            CustomNumberDialog(
                last = lastCustom,
                onAdd = { n ->
                    customDialog = false
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.addCustom(n)
                },
                onDismiss = { customDialog = false },
            )
        }

        val window = LocalWindowInfo.current.containerSize
        if (window.width > window.height) {
            // Sideways: count and controls on the left, the pads on the right at full height.
            Row(
                Modifier.padding(innerPadding).fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    header()
                    count(64)
                    History(groups.toList())
                    actions()
                }
                pads(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(
                Modifier.padding(innerPadding).fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                header()
                count(96)
                // History: what was tapped, newest last (scrolls to the end)
                History(groups.toList())
                pads(Modifier.weight(1f).fillMaxWidth())
                actions()
            }
        }
    }
}

private fun DeckFormat.label(): Int = when (this) {
    DeckFormat.CONSTRUCTED -> R.string.deck_format_constructed
    DeckFormat.LIMITED -> R.string.deck_format_limited
    DeckFormat.COMMANDER -> R.string.deck_format_commander
}

/** Main deck / Sideboard, each with its own running total. */
@Composable
private fun PileTabs(pile: Pile, mainTotal: Int, sideTotal: Int, onSelect: (Pile) -> Unit) {
    val piles = listOf(Pile.MAIN to mainTotal, Pile.SIDEBOARD to sideTotal)
    Choice(
        values = piles.indices.toList(),
        selected = piles.indexOfFirst { it.first == pile },
        onSelect = { onSelect(piles[it].first) },
        width = null,
        modifier = Modifier.fillMaxWidth(),
    ) { i ->
        val (p, total) = piles[i]
        "${stringResource(if (p == Pile.MAIN) R.string.deck_pile_main else R.string.deck_pile_side)}  $total"
    }
}

@Composable
private fun StatusLine(status: CountStatus, format: DeckFormat, pile: Pile) {
    val colors = MaterialTheme.colorScheme
    val (text, color) = when (status) {
        is CountStatus.Short -> stringResource(R.string.deck_status_short, status.missing, format.mainSize) to colors.onSurfaceVariant
        CountStatus.Ok -> stringResource(
            when {
                pile == Pile.SIDEBOARD -> R.string.deck_status_side_ok
                format.mainExact -> R.string.deck_status_exact_ok
                else -> R.string.deck_status_ok
            }, if (pile == Pile.SIDEBOARD) format.sideboardMax ?: 0 else format.mainSize,
        ) to legalColors().content   // "OK": the same green as "Legal", readable in both themes
        is CountStatus.Over -> stringResource(
            if (pile == Pile.SIDEBOARD) R.string.deck_status_side_over else R.string.deck_status_over,
            status.extra, if (pile == Pile.SIDEBOARD) format.sideboardMax ?: 0 else format.mainSize,
        ) to colors.error
        CountStatus.NoLimit -> stringResource(R.string.deck_status_no_limit) to colors.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.titleSmall, color = color)
}

@Composable
private fun History(groups: List<Int>) {
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    LaunchedEffect(groups.size) { if (groups.isNotEmpty()) listState.animateScrollToItem(groups.size - 1) }
    Box(Modifier.fillMaxWidth().heightIn(min = 28.dp), contentAlignment = Alignment.CenterStart) {
        if (groups.isEmpty()) {
            Text(stringResource(R.string.deck_history_empty), style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
        } else {
            LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.semantics { contentDescription = DeckCount.history(groups) }) {
                itemsIndexed(groups) { i, n ->
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (i == groups.lastIndex) colors.primary.copy(alpha = 0.2f) else colors.surfaceContainerHigh)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(n.toString(), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = if (i == groups.lastIndex) colors.primary else colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CountPad(n: Int, modifier: Modifier, onClick: () -> Unit) {
    val addLabel = stringResource(R.string.deck_add, n)
    Box(
        // Tonal, not solid: four big solid blocks in black/white weigh too much (Alejandro).
        modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(role = Role.Button, onClickLabel = addLabel, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(n.toString(), style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp, lineHeight = 52.sp), color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/**
 * Below the 1–4 pads. Before any custom pile: one wide "Other number…" pad. After: "+N again"
 * (one tap repeats the last custom pile, e.g. 17 basics deck after deck) and a separate edit button.
 */
@Composable
private fun CustomRow(last: Int, onAgain: () -> Unit, onOther: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (last <= 0) {
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(shape).background(colors.primaryContainer)
                    .clickable(role = Role.Button, onClick = onOther),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.deck_other_number), style = MaterialTheme.typography.titleMedium,
                    color = colors.onPrimaryContainer)
            }
        } else {
            val againLabel = stringResource(R.string.deck_add_again, last)
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(shape).background(colors.primaryContainer)
                    .clickable(role = Role.Button, onClick = onAgain)
                    .semantics { contentDescription = againLabel },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The row's description says it once ("Add 17 again"); the visible bits stay silent.
                Text("+$last", style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 32.sp, lineHeight = 40.sp, fontFeatureSettings = "tnum"), color = colors.onPrimaryContainer,
                    modifier = Modifier.clearAndSetSemantics {})
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.deck_again), style = MaterialTheme.typography.labelLarge,
                    color = colors.onPrimaryContainer, modifier = Modifier.clearAndSetSemantics {})
            }
            Box(
                Modifier.size(64.dp).clip(shape).background(colors.surfaceContainerHighest)
                    .border(1.dp, colors.outlineVariant, shape)
                    .clickable(role = Role.Button, onClick = onOther),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Edit, stringResource(R.string.deck_other_number_desc), tint = colors.onSurface)
            }
        }
    }
}

/** Number entry for any pile size, the last one pre-filled and selected (typing replaces it). */
@Composable
private fun CustomNumberDialog(last: Int, onAdd: (Int) -> Unit, onDismiss: () -> Unit) {
    val initial = if (last > 0) last.toString() else ""
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val number = DeckCount.parseCustom(value.text)
    val invalid = value.text.isNotEmpty() && number == null
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deck_custom_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { new ->
                    val digits = new.text.filter(Char::isDigit).take(3)
                    value = if (digits == new.text) new else TextFieldValue(digits, TextRange(digits.length))
                },
                label = { Text(stringResource(R.string.deck_custom_label)) },
                supportingText = { Text(stringResource(R.string.deck_custom_range, DeckCount.MAX_CUSTOM)) },
                isError = invalid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { number?.let(onAdd) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { number?.let(onAdd) }, enabled = number != null) {
                Text(stringResource(R.string.deck_custom_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.deck_custom_cancel)) }
        },
    )
}

@Composable
private fun ActionButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier,
    destructive: Boolean = false,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (destructive) colors.error else colors.onSurface
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.height(52.dp).alpha(if (enabled) 1f else 0.4f).clip(shape).background(colors.surfaceContainerHighest)
            .border(1.dp, colors.outlineVariant, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides content) { icon() }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}
