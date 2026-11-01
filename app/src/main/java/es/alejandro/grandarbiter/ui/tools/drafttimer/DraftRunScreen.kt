package es.alejandro.grandarbiter.ui.tools.drafttimer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftPosition
import es.alejandro.grandarbiter.domain.draft.DraftSnapshot
import es.alejandro.grandarbiter.domain.draft.DraftStep
import es.alejandro.grandarbiter.domain.draft.PassDirection
import es.alejandro.grandarbiter.domain.draft.RochesterOrder
import es.alejandro.grandarbiter.ui.components.HoldState
import es.alejandro.grandarbiter.ui.components.HoldToConfirmButton
import es.alejandro.grandarbiter.ui.theme.GoldDeep
import es.alejandro.grandarbiter.ui.theme.OnGoldDeep
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import es.alejandro.grandarbiter.ui.theme.softened

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
        val content: @Composable () -> Unit = {
            when (val p = s.position) {
                DraftPosition.Finished -> TimeUpContent(s)
                is DraftPosition.Running -> StepContent(s, p, waiting = false)
                // Manual start: the step that ran out, at 0:00, until "Start…".
                is DraftPosition.Waiting -> StepContent(s, DraftPosition.Running(p.index, p.step, p.step.durationMs, 0), waiting = true)
            }
        }
        val window = LocalWindowInfo.current.containerSize
        if (window.width > window.height) {
            // Sideways: the step on the left (scrolls if needed), the controls always visible on the right.
            Row(Modifier.padding(innerPadding).fillMaxSize()) {
                Column(
                    Modifier.weight(1.4f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { content() }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Footer(s, viewModel, onEnd = { confirmEnd = true })
                }
            }
        } else {
            Column(Modifier.padding(innerPadding).fillMaxSize()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { content() }
                Footer(s, viewModel, onEnd = { confirmEnd = true })
            }
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
private fun StepContent(s: DraftSnapshot, p: DraftPosition.Running, waiting: Boolean) = when (val step = p.step) {
    is DraftStep.Pick -> if (step.actor != null) RochesterPickContent(s, p, step, step.actor, waiting) else PickContent(s, p, step, waiting)
    is DraftStep.LayOut -> LayOutContent(s, p, step, waiting)
    is DraftStep.Review -> ReviewContent(s, p, step, waiting)
    is DraftStep.Construction -> ConstructionContent(s, p, waiting)
}

/** Rochester: clockwise packs go left, as in a booster draft. */
@Composable
private fun rotationText(direction: PassDirection): String =
    stringResource(if (direction == PassDirection.LEFT) R.string.draft_clockwise else R.string.draft_counterclockwise)

/** Figma "Rochester - Pack review": the pack laid out face up, 20 s to look before the first pick. */
@Composable
private fun LayOutContent(s: DraftSnapshot, p: DraftPosition.Running, step: DraftStep.LayOut, waiting: Boolean) {
    val colors = MaterialTheme.colorScheme
    StatusRow(
        left = { DraftChip(stringResource(R.string.draft_pack_of, step.pack, s.config.totalPacks), colors.primary, outlined = true) },
        right = { DraftChip(stringResource(R.string.draft_laid_out_by, step.opener)) },
    )
    Countdown(stringResource(R.string.draft_layout_label), p, s.isRunning, waiting,
        stringResource(R.string.draft_layout_sub, formatDuration(step.durationMs)))
    DraftCard {
        DraftLabel(stringResource(R.string.draft_up_next))
        Text(stringResource(R.string.draft_picks_first, step.opener), style = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
            color = colors.onSurface)
        // The way around the table: the other five seats; the last one takes two and the picks come back.
        val clockwise = step.direction == PassDirection.LEFT
        val then = RochesterOrder.picks(step.opener, clockwise, RochesterOrder.PLAYERS).drop(1).joinToString(" → ")
        Text(stringResource(R.string.draft_picks_then, then), style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = colors.onSurfaceVariant)
        val group = (step.pack - 1) / RochesterOrder.PLAYERS
        Text(stringResource(R.string.draft_rotation_packs, rotationText(step.direction),
                group * RochesterOrder.PLAYERS + 1, minOf((group + 1) * RochesterOrder.PLAYERS, s.config.totalPacks)),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = colors.primary)
    }
}

/**
 * Figma "Rochester - Pick" / "Pick time out": who picks now, big; 5 s at most. At 0:00 it waits
 * (an official gives the player the oldest card) until "Picked".
 */
@Composable
private fun RochesterPickContent(s: DraftSnapshot, p: DraftPosition.Running, step: DraftStep.Pick, actor: String, waiting: Boolean) {
    val colors = MaterialTheme.colorScheme
    StatusRow(
        left = { DraftChip(stringResource(R.string.draft_pack_of, step.pack, s.config.totalPacks), colors.primary, outlined = true) },
        right = { DraftChip(rotationText(step.direction)) },
    )
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(colors.surfaceContainerLow).border(1.dp, colors.primary.softened(0.6f), shape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DraftLabel(stringResource(R.string.draft_now_picking), colors.onSurfaceVariant)
        Text(actor, style = TextStyle(fontSize = 72.sp, lineHeight = 80.sp, fontWeight = FontWeight.Bold), color = colors.primary)
        Text(stringResource(R.string.draft_card_of, step.pick, s.config.cardsPerPack),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = colors.onSurface)
    }
    Countdown(
        label = stringResource(if (waiting) R.string.draft_time else R.string.draft_pick_short),
        p = p, running = s.isRunning, waiting = waiting,
        sub = stringResource(R.string.draft_of, formatDuration(step.durationMs)),
        size = 56,
        waitingSub = stringResource(R.string.draft_official_picks, actor),
        verticalPadding = 16,
    )
    RochesterNextCard(s.upcoming)
}

/** "2A · then 2B, 2C"; at the end of a pack, the next pack and who lays it out. */
@Composable
private fun RochesterNextCard(upcoming: List<DraftStep>) {
    val next = upcoming.firstOrNull() ?: return
    val text = when (next) {
        is DraftStep.Pick -> {
            val seats = upcoming.takeWhile { it is DraftStep.Pick && it.pack == next.pack }.mapNotNull { (it as DraftStep.Pick).actor }
            val then = seats.drop(1).take(2)
            if (then.isEmpty()) seats.firstOrNull().orEmpty() else stringResource(R.string.draft_next_seat, seats.first(), then.joinToString(", "))
        }
        else -> nextText(next) ?: return
    }
    DraftCard { SummaryRow(stringResource(R.string.draft_next), text) }
}

@Composable
private fun PickContent(s: DraftSnapshot, p: DraftPosition.Running, step: DraftStep.Pick, waiting: Boolean) {
    StatusRow(
        left = { DraftChip(stringResource(R.string.draft_pack_of, step.pack, s.config.totalPacks), MaterialTheme.colorScheme.primary, outlined = true) },
        right = { DraftChip(directionText(step.direction), MaterialTheme.colorScheme.onSurface) },
    )
    // Picks in a pack, the untimed last one included: 14 (Booster), 7 for 14 cards two at a time (2HG).
    val picksInPack = (s.config.cardsInPack(step.pack) + step.cardsTaken - 1) / step.cardsTaken
    val label = if (step.cardsTaken > 1) stringResource(R.string.draft_pick_take_label, step.pick, step.cardsTaken, step.cardsLeft)
        else stringResource(R.string.draft_pick_label, step.pick, step.cardsLeft)
    Countdown(label, p, s.isRunning, waiting,
        stringResource(R.string.draft_of, formatDuration(step.durationMs)))
    DraftCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DraftLabel(stringResource(R.string.draft_pack_progress))
            Text(stringResource(R.string.draft_pick_of, step.pick, picksInPack),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PickSegments(done = step.pick - 1, total = picksInPack)
    }
    NextCard(s.nextStep)
}

@Composable
private fun ReviewContent(s: DraftSnapshot, p: DraftPosition.Running, step: DraftStep.Review, waiting: Boolean) {
    val colors = MaterialTheme.colorScheme
    StatusRow(
        left = { DraftChip(stringResource(R.string.draft_pack_complete, step.afterPack), colors.primary, outlined = true) },
        right = { DraftChip(stringResource(R.string.draft_review)) },
    )
    Countdown(stringResource(R.string.draft_review_label), p, s.isRunning, waiting, stringResource(R.string.draft_of, formatDuration(step.durationMs)))
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
private fun ConstructionContent(s: DraftSnapshot, p: DraftPosition.Running, waiting: Boolean) {
    val colors = MaterialTheme.colorScheme
    StatusRow(
        left = { DraftChip(stringResource(R.string.draft_complete), colors.primary, outlined = true) },
        right = { DraftChip(stringResource(R.string.draft_build)) },
    )
    Countdown(stringResource(R.string.draft_construction), p, s.isRunning, waiting, stringResource(R.string.draft_of, formatClock(p.step.durationMs)), size = 80)
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
        color = colors.error, size = 80, borderColor = colors.error.softened(0.6f))
    val constructionMs = s.config.constructionMinutes * 60_000L
    // Rochester picks end on "Picked": the timeline counts each at its 5 s maximum, so these are upper bounds.
    val bound = if (s.config.format == DraftFormat.ROCHESTER) "≤ " else ""
    DraftCard {
        DraftLabel(stringResource(R.string.draft_summary))
        SummaryRow(pluralStringResource(R.plurals.draft_summary_draft, s.config.totalPacks, s.config.totalPacks), bound + formatClock(s.totalMs - constructionMs))
        SummaryRow(stringResource(R.string.draft_construction), formatClock(constructionMs))
        HorizontalDivider(color = colors.outlineVariant)
        SummaryRow(stringResource(R.string.draft_total), bound + formatClock(s.totalMs), emphasis = true)
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
private fun Countdown(
    label: String,
    p: DraftPosition.Running,
    running: Boolean,
    waiting: Boolean,
    sub: String,
    size: Int = 96,
    /** Shown at 0:00 instead of [sub]; default "Time is up". */
    waitingSub: String? = null,
    verticalPadding: Int = 28,
) {
    val colors = MaterialTheme.colorScheme
    val paused = !running && !waiting
    CountdownFrame(
        label = if (paused) stringResource(R.string.draft_paused_label, label) else label,
        time = formatClock(p.remainingMs),
        fraction = smoothFraction(p.index, p.remainingMs, p.step.durationMs, running = running && !waiting),
        sub = if (waiting) waitingSub ?: stringResource(R.string.draft_time_is_up) else sub,
        color = when {
            waiting -> colors.primary
            paused -> colors.onSurfaceVariant.softened(0.6f)
            else -> colors.onSurface
        },
        labelColor = if (paused) colors.primary else colors.onSurfaceVariant,
        size = size,
        borderColor = if (waiting) colors.primary.softened(0.6f) else colors.outlineVariant,
        verticalPadding = verticalPadding,
        subColor = if (waiting && waitingSub != null) colors.primary else colors.onSurfaceVariant,
    )
}

/**
 * The progress bar's fill, moving continuously instead of jumping once per tick. Each tick gives
 * the exact remaining time; from there the bar glides linearly to where the next tick will be
 * (the next whole second), so tick and animation meet and nothing drifts. Paused, waiting or a new
 * step: it snaps to the exact value. The countdown digits still change once per second.
 */
@Composable
private fun smoothFraction(stepIndex: Int, remainingMs: Long, durationMs: Long, running: Boolean): Float {
    val exact = (remainingMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val fraction = remember(stepIndex) { Animatable(exact) }
    LaunchedEffect(stepIndex, remainingMs, running) {
        fraction.snapTo(exact)
        if (running && remainingMs > 0) {
            val toNextTick = (remainingMs % 1_000).takeIf { it > 0 } ?: 1_000
            val next = ((remainingMs - toNextTick).toFloat() / durationMs).coerceIn(0f, 1f)
            fraction.animateTo(next, tween(durationMillis = toNextTick.toInt(), easing = LinearEasing))
        }
    }
    return fraction.value
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
    verticalPadding: Int = 28,
    subColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(colors.surfaceContainerLow).border(1.dp, borderColor, shape)
            .padding(horizontal = 16.dp, vertical = verticalPadding.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DraftLabel(label, labelColor)
        Text(time, style = TextStyle(fontSize = size.sp, lineHeight = (size + 8).sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp), color = color)
        DraftProgress(fraction)
        Text(sub, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = subColor, textAlign = TextAlign.Center)
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
private fun nextText(next: DraftStep?): String? = when (next) {
    is DraftStep.Pick -> stringResource(R.string.draft_next_pick_value, next.cardsLeft, next.durationMs / 1000)
    is DraftStep.LayOut -> stringResource(R.string.draft_next_layout_value, next.pack, next.opener)
    is DraftStep.Review -> stringResource(R.string.draft_next_review_value, formatDuration(next.durationMs))
    is DraftStep.Construction -> stringResource(R.string.draft_next_construction_value, formatDuration(next.durationMs))
    null -> null
}

@Composable
private fun NextCard(next: DraftStep?) {
    val text = nextText(next) ?: return
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
        val position = s.position
        if (position == DraftPosition.Finished) {
            // Time up: one action.
            PrimaryButton(stringResource(R.string.draft_finish), viewModel::endDraft)
            return@Column
        }
        val current = (position as? DraftPosition.Running)?.step ?: (position as? DraftPosition.Waiting)?.step
        if (current is DraftStep.Pick && current.waitsForPicked) {
            // Rochester: "Picked" is THE action, a plain tap, running or at 0:00; going back needs a hold.
            RochesterPickFooter(s, viewModel, onEnd)
            return@Column
        }
        if (position is DraftPosition.Waiting) {
            // Manual start: the judge starts what comes next. A plain tap: it's the expected action.
            PrimaryButton(startLabel(position.step, s.nextStep), viewModel::startNext)
            EndDraftLink(onEnd)
            return@Column
        }
        val running = position as DraftPosition.Running

        PauseButton(s.isRunning, viewModel::togglePause)

        val step = running.step
        if (step !is DraftStep.Construction) {
            var hold by remember { mutableStateOf(HoldState.IDLE) }
            LaunchedEffect(hold) { if (hold == HoldState.TAPPED) { delay(1_500); hold = HoldState.IDLE } }
            val nextLabel = when (step) {
                is DraftStep.Review -> stringResource(R.string.draft_start_pack, step.nextPack)
                is DraftStep.LayOut -> stringResource(R.string.draft_start_picks)
                else -> stringResource(R.string.draft_next_pick_button)
            }
            val previousLabel = stringResource(if (step is DraftStep.LayOut) R.string.draft_previous else R.string.draft_previous_pick)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HoldToConfirmButton("‹  $previousLabel", viewModel::previous, Modifier.weight(1f),
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

        EndDraftLink(onEnd)
    }
}

/** Figma "Rochester - Pick": "Picked ›" big; pause and a held "‹ Previous pick" below. */
@Composable
private fun RochesterPickFooter(s: DraftSnapshot, viewModel: DraftRunViewModel, onEnd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    PrimaryButton(stringResource(R.string.draft_picked) + "  ›", viewModel::startNext)
    var hold by remember { mutableStateOf(HoldState.IDLE) }
    LaunchedEffect(hold) { if (hold == HoldState.TAPPED) { delay(1_500); hold = HoldState.IDLE } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        PauseButton(s.isRunning, viewModel::togglePause, size = 52)
        HoldToConfirmButton("‹  " + stringResource(R.string.draft_previous_pick), viewModel::previous, Modifier.weight(1f),
            onStateChange = { hold = it })
    }
    Text(
        stringResource(when (hold) {
            HoldState.IDLE -> R.string.draft_picked_hint
            HoldState.HOLDING -> R.string.draft_keep_holding
            HoldState.TAPPED -> R.string.draft_hold_to_confirm
        }),
        style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
        color = if (hold == HoldState.IDLE) colors.onSurfaceVariant else colors.primary,
    )
    EndDraftLink(onEnd)
}

@Composable
internal fun startLabel(ended: DraftStep, next: DraftStep?): String = when (next) {
    is DraftStep.Pick -> when {
        ended is DraftStep.Review -> stringResource(R.string.draft_start_pack, next.pack)
        ended is DraftStep.LayOut -> stringResource(R.string.draft_start_picks)
        else -> stringResource(R.string.draft_start_pick, next.pick)
    }
    is DraftStep.LayOut -> stringResource(R.string.draft_start_pack, next.pack)
    is DraftStep.Review -> stringResource(R.string.draft_start_review)
    is DraftStep.Construction -> stringResource(R.string.draft_start_construction)
    null -> stringResource(R.string.draft_finish)
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(GoldDeep).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = OnGoldDeep)
    }
}

@Composable
private fun EndDraftLink(onEnd: () -> Unit) {
    Text(
        stringResource(R.string.draft_end),
        Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onEnd).padding(horizontal = 16.dp, vertical = 8.dp),
        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun PauseButton(running: Boolean, onClick: () -> Unit, size: Int = 80) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(GoldDeep).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(if (running) R.string.draft_pause else R.string.draft_resume),
            tint = OnGoldDeep,
            modifier = Modifier.size((size * 0.45f).dp),
        )
    }
}
