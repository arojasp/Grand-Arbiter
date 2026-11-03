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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import es.alejandro.grandarbiter.ui.components.SelectChip
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckFormat.entries.forEach { f ->
                    SelectChip(stringResource(f.label()), selected = f == format) { viewModel.selectFormat(f) }
                }
            }
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
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerHighest).padding(4.dp)
    ) {
        listOf(Pile.MAIN to mainTotal, Pile.SIDEBOARD to sideTotal).forEach { (p, total) ->
            val selected = p == pile
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                    .background(if (selected) colors.primary else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(p) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(if (p == Pile.MAIN) R.string.deck_pile_main else R.string.deck_pile_side),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.onPrimary else colors.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(total.toString(), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = if (selected) colors.onPrimary else colors.onSurface)
            }
        }
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
