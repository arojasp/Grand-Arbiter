package es.alejandro.grandarbiter.ui.tools.drafttimer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.draft.DraftPosition
import es.alejandro.grandarbiter.domain.draft.DraftSnapshot
import es.alejandro.grandarbiter.domain.draft.DraftStep
import es.alejandro.grandarbiter.domain.draft.PassDirection
import es.alejandro.grandarbiter.ui.components.HoldState
import es.alejandro.grandarbiter.ui.components.HoldToConfirmButton
import es.alejandro.grandarbiter.ui.theme.GoldDeep
import es.alejandro.grandarbiter.ui.theme.OnGoldDeep
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/*
 * Figma "Draft Timer - Pick / Review / Deck construction / Time up". The judge keeps time with it:
 * big countdown, what's on now, what comes next. Screen kept on while visible.
 */

@Composable
fun DraftRunScreen(
    onBack: () -> Unit,
    onNoDraft: () -> Unit,
    viewModel: DraftRunViewModel = koinViewModel(),
) {
    val colors = MaterialTheme.colorScheme
    val state by viewModel.state.collectAsState()
    var confirmEnd by rememberSaveable { mutableStateOf(false) }

    KeepScreenOn()

    if (state == DraftRunState.NoDraft) LaunchedEffect(Unit) { onNoDraft() }

    Scaffold(
        containerColor = colors.background,
        topBar = { DraftTopBar(stringResource(R.string.tools_draft_timer), onBack) },
    ) { innerPadding ->
        val active = state as? DraftRunState.Active ?: return@Scaffold
        val s = active.snapshot
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val p = s.position) {
                    DraftPosition.Finished -> TimeUpContent(s)
                    is DraftPosition.Running -> when (val step = p.step) {
                        is DraftStep.Pick -> PickContent(s, p, step)
                        is DraftStep.Review -> ReviewContent(s, p, step)
                        is DraftStep.Construction -> ConstructionContent(s, p)
                    }
                }
            }
            Footer(s, viewModel, onEnd = { confirmEnd = true })
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(stringResource(R.string.draft_end_title)) },
            text = { Text(stringResource(R.string.draft_end_text)) },
            confirmButton = {
                TextButton(onClick = { confirmEnd = false; viewModel.endDraft() }) {
                    Text(stringResource(R.string.draft_end), color = colors.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.draft_cancel)) } },
        )
    }
}

/**
 * The view's own keep-screen-on flag, not the window flag: MainActivity drives the window flag
 * from the global "keep screen on" setting, and clearing it here would switch that off.
 */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

// ---- Steps ----

@Composable
private fun PickContent(s: DraftSnapshot, p: DraftPosition.Running, step: DraftStep.Pick) {
    StatusRow(
        left = { DraftChip(stringResource(R.string.draft_pack_of, step.pack, s.config.packs), MaterialTheme.colorScheme.primary, outlined = true) },
        right = { DraftChip(directionText(step.direction), MaterialTheme.colorScheme.onSurface) },
    )
    Countdown(stringResource(R.string.draft_pick_label, step.pick, step.cardsLeft), p, s.isRunning,
        stringResource(R.string.draft_of, formatDuration(step.durationMs)))
    DraftCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DraftLabel(stringResource(R.string.draft_pack_progress))
            Text(stringResource(R.string.draft_pick_of, step.pick, s.config.cardsPerPack),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PickSegments(done = step.pick - 1, total = s.config.cardsPerPack)
    }
    NextCard(s.nextStep)
}

@Composable
private fun ReviewContent(s: DraftSnapshot, p: DraftPosition.Running, step: DraftStep.Review) {
    val colors = MaterialTheme.colorScheme
    StatusRow(
        left = { DraftChip(stringResource(R.string.draft_pack_complete, step.afterPack), colors.primary, outlined = true) },
        right = { DraftChip(stringResource(R.string.draft_review)) },
    )
    Countdown(stringResource(R.string.draft_review_label), p, s.isRunning, stringResource(R.string.draft_of, formatDuration(step.durationMs)))
    DraftCard {
        DraftLabel(stringResource(R.string.draft_up_next))
        Text(stringResource(R.string.draft_pack_n, step.nextPack), style = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
            color = colors.onSurface)
        Text(directionText(step.nextDirection), style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = colors.primary)
        (s.nextStep as? DraftStep.Pick)?.let { first ->
            Text(stringResource(R.string.draft_first_pick, first.cardsLeft, first.durationMs / 1000),
                style = TextStyle(fontSize = 14.sp), color = colors.onSurfaceVariant)
        }
    }
    DraftCard(background = colors.surfaceContainerLow) {
        Text(stringResource(R.string.draft_review_note), style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = colors.onSurfaceVariant)
    }
}

@Composable
private fun ConstructionContent(s: DraftSnapshot, p: DraftPosition.Running) {
    val colors = MaterialTheme.colorScheme
    StatusRow(
        left = { DraftChip(stringResource(R.string.draft_complete), colors.primary, outlined = true) },
        right = { DraftChip(stringResource(R.string.draft_build)) },
    )
    Countdown(stringResource(R.string.draft_construction), p, s.isRunning, stringResource(R.string.draft_of, formatClock(p.step.durationMs)), size = 80)
    DraftCard {
        DraftLabel(stringResource(R.string.draft_when_time_is_up))
        Text(stringResource(R.string.draft_when_time_is_up_text), style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = colors.onSurfaceVariant)
    }
}

@Composable
private fun TimeUpContent(s: DraftSnapshot) {
    val colors = MaterialTheme.colorScheme
    StatusRow(
        left = { DraftChip(stringResource(R.string.draft_complete), colors.primary, outlined = true) },
        right = { DraftChip(stringResource(R.string.draft_time), colors.error, outlined = true) },
    )
    CountdownFrame(stringResource(R.string.draft_construction), "0:00", 0f, stringResource(R.string.draft_time_is_up),
        color = colors.error, size = 80, borderColor = colors.error.copy(alpha = 0.6f))
    val constructionMs = s.config.constructionMinutes * 60_000L
    DraftCard {
        DraftLabel(stringResource(R.string.draft_summary))
        SummaryRow(stringResource(R.string.draft_summary_draft, s.config.packs), formatClock(s.totalMs - constructionMs))
        SummaryRow(stringResource(R.string.draft_construction), formatClock(constructionMs))
        HorizontalDivider(color = colors.outlineVariant)
        SummaryRow(stringResource(R.string.draft_total), formatClock(s.totalMs), emphasis = true)
    }
}

// ---- Pieces ----

@Composable
private fun StatusRow(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        left(); right()
    }
}

@Composable
private fun Countdown(label: String, p: DraftPosition.Running, running: Boolean, sub: String, size: Int = 96) {
    val colors = MaterialTheme.colorScheme
    CountdownFrame(
        label = if (running) label else stringResource(R.string.draft_paused_label, label),
        time = formatClock(p.remainingMs),
        fraction = p.remainingMs.toFloat() / p.step.durationMs,
        sub = sub,
        color = if (running) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.6f),
        labelColor = if (running) colors.onSurfaceVariant else colors.primary,
        size = size,
    )
}

@Composable
private fun CountdownFrame(
    label: String,
    time: String,
    fraction: Float,
    sub: String,
    color: Color,
    size: Int,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(colors.surfaceContainerLow).border(1.dp, borderColor, shape)
            .padding(horizontal = 16.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DraftLabel(label, labelColor)
        Text(time, style = TextStyle(fontSize = size.sp, lineHeight = (size + 8).sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp), color = color)
        DraftProgress(fraction)
        Text(sub, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = colors.onSurfaceVariant)
    }
}

@Composable
private fun PickSegments(done: Int, total: Int) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { i ->
            val color = when {
                i < done -> colors.primary
                i == done -> colors.primary.copy(alpha = 0.45f)
                else -> colors.surfaceContainerHighest
            }
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

@Composable
private fun NextCard(next: DraftStep?) {
    val text = when (next) {
        is DraftStep.Pick -> stringResource(R.string.draft_next_pick_value, next.cardsLeft, next.durationMs / 1000)
        is DraftStep.Review -> stringResource(R.string.draft_next_review_value, formatDuration(next.durationMs))
        is DraftStep.Construction -> stringResource(R.string.draft_next_construction_value, formatDuration(next.durationMs))
        null -> return
    }
    val label = if (next is DraftStep.Pick) R.string.draft_next_pick else R.string.draft_next
    DraftCard {
        SummaryRow(stringResource(label), text)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, emphasis: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Normal),
            color = if (emphasis) colors.onSurface else colors.onSurfaceVariant)
        Text(value, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Medium),
            color = if (emphasis) colors.primary else colors.onSurface)
    }
}

@Composable
private fun directionText(direction: PassDirection): String =
    stringResource(if (direction == PassDirection.LEFT) R.string.draft_pass_left else R.string.draft_pass_right)

// ---- Footer: controls ----

@Composable
private fun Footer(s: DraftSnapshot, viewModel: DraftRunViewModel, onEnd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val running = s.position as? DraftPosition.Running
        if (running == null) {
            // Time up: one action.
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(GoldDeep)
                    .clickable(role = Role.Button) { viewModel.endDraft() },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.draft_finish), style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = OnGoldDeep)
            }
            return@Column
        }

        PauseButton(s.isRunning, viewModel::togglePause)

        val step = running.step
        if (step !is DraftStep.Construction) {
            var hold by remember { mutableStateOf(HoldState.IDLE) }
            LaunchedEffect(hold) { if (hold == HoldState.TAPPED) { delay(1_500); hold = HoldState.IDLE } }
            val nextLabel = if (step is DraftStep.Review) stringResource(R.string.draft_start_pack, step.nextPack)
                else stringResource(R.string.draft_next_pick_button)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HoldToConfirmButton("‹  " + stringResource(R.string.draft_previous_pick), viewModel::previous, Modifier.weight(1f),
                    onStateChange = { hold = it })
                HoldToConfirmButton("$nextLabel  ›", viewModel::next, Modifier.weight(1f), ringAtEnd = true,
                    onStateChange = { hold = it })
            }
            Text(
                stringResource(when (hold) {
                    HoldState.IDLE -> R.string.draft_hold_hint
                    HoldState.HOLDING -> R.string.draft_keep_holding
                    HoldState.TAPPED -> R.string.draft_hold_to_confirm
                }),
                style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
                color = if (hold == HoldState.IDLE) colors.onSurfaceVariant else colors.primary,
            )
        }

        Text(
            stringResource(R.string.draft_end),
            Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onEnd).padding(horizontal = 16.dp, vertical = 8.dp),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = colors.error,
        )
    }
}

@Composable
private fun PauseButton(running: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(80.dp).clip(CircleShape).background(GoldDeep).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(if (running) R.string.draft_pause else R.string.draft_resume),
            tint = OnGoldDeep,
            modifier = Modifier.size(36.dp),
        )
    }
}
