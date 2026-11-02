package es.alejandro.grandarbiter.ui.tools.announcement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.model.rules.PageRef
import es.alejandro.grandarbiter.ui.reader.BlockView
import es.alejandro.grandarbiter.ui.theme.GoldDeep
import es.alejandro.grandarbiter.ui.theme.OnGoldDeep
import org.koin.androidx.compose.koinViewModel
import es.alejandro.grandarbiter.ui.theme.softened

/*
 * Figma: "Head Judge Announcement - Refined Layout" (node 19:1441). A script for the HJ:
 *  - topics are tickable (reset each round), REL and round time are selectable;
 *  - tardiness text follows the REL (IPG 3.1 / JAR), the rounds table is MTR Appendix E as installed
 *    (the Figma's hardcoded table didn't match the current MTR);
 *  - venue info is the HJ's own note, saved on the device.
 */

private val Label = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.1.sp)
private val Body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadJudgeAnnouncementScreen(
    onBack: () -> Unit,
    onOpenRules: (PageRef) -> Unit,
    viewModel: AnnouncementViewModel = koinViewModel(),
) {
    val checked by viewModel.checked.collectAsState()
    val rel by viewModel.rel.collectAsState()
    val minutes by viewModel.roundMinutes.collectAsState()
    val rounds by viewModel.roundsTable.collectAsState()
    val venue by viewModel.venueNote.collectAsState()
    val colors = MaterialTheme.colorScheme

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
                    Text(stringResource(R.string.hj_title),
                        style = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.7).sp, fontWeight = FontWeight.Bold),
                        color = colors.primary)
                },
                actions = {
                    IconButton(onClick = viewModel::resetChecklist, enabled = checked.isNotEmpty()) {
                        Icon(Icons.Outlined.RestartAlt, stringResource(R.string.hj_reset), tint = colors.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { TopicsCard(checked, viewModel::toggle) }
            item { RelCard(rel?.let { Rel.valueOf(it) }, viewModel::setRel) }
            item { TimeCard(rel?.let { Rel.valueOf(it) }, minutes, viewModel::setRoundMinutes) }
            rounds?.let { r -> item { RoundsCard(r, onOpenRules) } }
            item { Spacer(Modifier.size(8.dp)) }
            items(REMINDERS.size) { i -> Reminder(stringResource(REMINDERS[i])) }
            item { PlayerInstruction() }
            item { VenueCard(venue, viewModel::setVenueNote) }
        }
    }
}

private val REMINDERS = listOf(R.string.hj_reminder_shuffle, R.string.hj_reminder_pace, R.string.hj_reminder_communication, R.string.hj_reminder_results)

@Composable
private fun Panel(modifier: Modifier = Modifier, borderAlpha: Float = 0.3f, background: Color = MaterialTheme.colorScheme.surfaceContainer,
                  content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.softened(borderAlpha), shape)
    ) { content() }
}

// ---------------------------------------------------------------- topics

@Composable
private fun TopicsCard(checked: Set<String>, onToggle: (AnnouncementTopic) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Panel(Modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            AnnouncementTopic.entries.forEachIndexed { i, topic ->
                if (i > 0) HorizontalDivider(color = colors.outlineVariant.softened(0.2f))
                val done = topic.name in checked
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onToggle(topic) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (done) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null,
                        tint = if (done) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(topic.label()).uppercase(),
                        style = Body.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
                        color = if (done) colors.onSurfaceVariant.softened(0.6f) else colors.onSurfaceVariant)
                }
            }
        }
    }
}

private fun AnnouncementTopic.label(): Int = when (this) {
    AnnouncementTopic.WELCOME -> R.string.hj_topic_welcome
    AnnouncementTopic.JUDGES -> R.string.hj_topic_judges
    AnnouncementTopic.CHECK_DECKLISTS -> R.string.hj_topic_check_decklists
    AnnouncementTopic.COLLECT_DECKLISTS -> R.string.hj_topic_collect_decklists
    AnnouncementTopic.PLAYERS -> R.string.hj_topic_players
    AnnouncementTopic.ROUNDS -> R.string.hj_topic_rounds
}

// ---------------------------------------------------------------- REL + time

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelCard(rel: Rel?, onSelect: (Rel) -> Unit) {
    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.hj_rel).uppercase(), style = Label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Rel.entries.forEach { r -> Chip(stringResource(r.label()), selected = r == rel) { onSelect(r) } }
            }
        }
    }
}

private fun Rel.label(): Int = when (this) {
    Rel.REGULAR -> R.string.hj_rel_regular
    Rel.COMPETITIVE -> R.string.hj_rel_competitive
    Rel.PROFESSIONAL -> R.string.hj_rel_professional
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.clip(CircleShape)
            .background(if (selected) GoldDeep else colors.surfaceContainerHighest)
            .border(1.dp, if (selected) GoldDeep else colors.outlineVariant, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
            color = if (selected) OnGoldDeep else colors.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeCard(rel: Rel?, minutes: Int, onMinutes: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Schedule, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.hj_round_time).uppercase(), style = Label.copy(letterSpacing = 0.5.sp), color = colors.onSurfaceVariant)
                    Text(stringResource(R.string.hj_minutes, minutes),
                        style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
                        color = colors.onSurface)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnnouncementViewModel.ROUND_MINUTES.forEach { m ->
                            Chip(stringResource(R.string.hj_minutes, m), selected = m == minutes) { onMinutes(m) }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.WarningAmber, null, tint = colors.error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.hj_tardiness).uppercase(), style = Label.copy(letterSpacing = 0.5.sp),
                        color = colors.error)
                    Text(
                        stringResource(
                            when (rel) {
                                null -> R.string.hj_tardiness_pick_rel
                                Rel.REGULAR -> R.string.hj_tardiness_regular
                                Rel.COMPETITIVE, Rel.PROFESSIONAL -> R.string.hj_tardiness_competitive
                            }
                        ),
                        style = Body, color = if (rel == null) colors.onSurfaceVariant else colors.onSurface,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- rounds (MTR Appendix E)

@Composable
private fun RoundsCard(rounds: RoundsTable, onOpenRules: (PageRef) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Panel(borderAlpha = 0.4f, background = colors.surfaceContainerHigh) {
        Row(
            Modifier.fillMaxWidth().background(colors.surfaceContainerHighest.copy(alpha = 0.3f))
                .clickable(role = Role.Button) { onOpenRules(PageRef(rounds.pageId)) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.hj_rounds).uppercase(),
                style = Body.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.1.sp), color = colors.primary, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.hj_rounds_source), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val onRef: (String) -> Unit = { id -> onOpenRules(PageRef(id)) }
            BlockView(rounds.table, onRefClick = onRef)
            rounds.notes.forEach { BlockView(it, onRefClick = onRef) }
        }
    }
}

// ---------------------------------------------------------------- general information

@Composable
private fun Reminder(text: String) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant.softened(0.2f), shape).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.CheckBox, null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = Body, color = colors.onSurface)
    }
}

@Composable
private fun PlayerInstruction() {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(colors.primary.copy(alpha = 0.1f))
            .border(2.dp, colors.primary.softened(0.2f), shape).padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Campaign, null, tint = colors.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(stringResource(R.string.hj_player_instruction).uppercase(),
                style = Body.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.1.sp), color = colors.primary)
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.hj_call_judge))
                    append("\n")
                    withStyle(SpanStyle(color = colors.primary)) { append(stringResource(R.string.hj_judge)) }
                },
                style = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold), color = colors.onSurface,
            )
        }
    }
}

@Composable
private fun VenueCard(note: String, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    // Typing edits local state; the saved note (DataStore, async) only seeds it. Binding the field
    // straight to the stored flow would make the cursor jump while typing.
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    val text = draft ?: note
    Row(
        Modifier.fillMaxWidth().clip(shape).background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant.softened(0.2f), shape).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Info, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.hj_venue).uppercase(), style = Label.copy(letterSpacing = 0.5.sp), color = colors.onSurfaceVariant)
            BasicTextField(
                value = text,
                onValueChange = { draft = it; onChange(it) },
                textStyle = Body.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { field ->
                    Box {
                        if (text.isEmpty()) Text(stringResource(R.string.hj_venue_hint), style = Body, color = colors.onSurfaceVariant)
                        field()
                    }
                },
            )
        }
    }
}
