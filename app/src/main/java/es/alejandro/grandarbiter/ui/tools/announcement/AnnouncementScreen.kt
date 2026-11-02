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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.model.rules.PageRef
import es.alejandro.grandarbiter.ui.components.Label
import es.alejandro.grandarbiter.ui.components.ListDivider
import es.alejandro.grandarbiter.ui.components.Panel
import es.alejandro.grandarbiter.ui.components.SelectChip
import es.alejandro.grandarbiter.ui.reader.BlockView
import es.alejandro.grandarbiter.ui.theme.softened
import org.koin.androidx.compose.koinViewModel

/*
 * A script for the Head Judge (canvas "Lote G"):
 *  - topics are tickable (reset each round), REL and round time are selectable;
 *  - tardiness text follows the REL (IPG 3.1 / JAR), the rounds table is MTR Appendix E as installed;
 *  - reminders are one list; the player instruction is the one emphasized block (it is read aloud);
 *  - venue info is the HJ's own note, saved on the device.
 */

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
                    Text(stringResource(R.string.hj_title), style = MaterialTheme.typography.titleLarge, color = colors.primary)
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
            item { Reminders() }
            item { PlayerInstruction() }
            item { VenueCard(venue, viewModel::setVenueNote) }
        }
    }
}

private val REMINDERS = listOf(R.string.hj_reminder_shuffle, R.string.hj_reminder_pace, R.string.hj_reminder_communication, R.string.hj_reminder_results)

@Composable
private fun AnnouncementPanel(content: @Composable () -> Unit) {
    Panel(color = MaterialTheme.colorScheme.surfaceContainer) { content() }
}

// ---------------------------------------------------------------- topics

@Composable
private fun TopicsCard(checked: Set<String>, onToggle: (AnnouncementTopic) -> Unit) {
    val colors = MaterialTheme.colorScheme
    AnnouncementPanel {
        AnnouncementTopic.entries.forEachIndexed { i, topic ->
            if (i > 0) ListDivider()
            val done = topic.name in checked
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Checkbox) { onToggle(topic) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(if (done) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null,
                    tint = if (done) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
                // Done: grey, the check tells it apart (no alpha on text).
                Text(stringResource(topic.label()), style = MaterialTheme.typography.titleMedium,
                    color = if (done) colors.onSurfaceVariant else colors.onSurface)
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
    AnnouncementPanel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(stringResource(R.string.hj_rel))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Rel.entries.forEach { r -> SelectChip(stringResource(r.label()), selected = r == rel) { onSelect(r) } }
            }
        }
    }
}

private fun Rel.label(): Int = when (this) {
    Rel.REGULAR -> R.string.hj_rel_regular
    Rel.COMPETITIVE -> R.string.hj_rel_competitive
    Rel.PROFESSIONAL -> R.string.hj_rel_professional
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeCard(rel: Rel?, minutes: Int, onMinutes: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    AnnouncementPanel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Schedule, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label(stringResource(R.string.hj_round_time))
                    Text(stringResource(R.string.hj_minutes, minutes),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = colors.onSurface)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnnouncementViewModel.ROUND_MINUTES.forEach { m ->
                            SelectChip(stringResource(R.string.hj_minutes, m), selected = m == minutes) { onMinutes(m) }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Red: it names a penalty.
                Icon(Icons.Outlined.WarningAmber, null, tint = colors.error, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label(stringResource(R.string.hj_tardiness), color = colors.error)
                    Text(
                        stringResource(
                            when (rel) {
                                null -> R.string.hj_tardiness_pick_rel
                                Rel.REGULAR -> R.string.hj_tardiness_regular
                                Rel.COMPETITIVE, Rel.PROFESSIONAL -> R.string.hj_tardiness_competitive
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium, color = if (rel == null) colors.onSurfaceVariant else colors.onSurface,
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
    AnnouncementPanel {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onOpenRules(PageRef(rounds.pageId)) }
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.hj_rounds), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(stringResource(R.string.hj_rounds_source), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        ListDivider()
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val onRef: (String) -> Unit = { id -> onOpenRules(PageRef(id)) }
            BlockView(rounds.table, onRefClick = onRef)
            rounds.notes.forEach { BlockView(it, onRefClick = onRef) }
        }
    }
}

// ---------------------------------------------------------------- general information

/** The four reminders: one list with gold bullets (they were cards with a checkbox that couldn't be ticked). */
@Composable
private fun Reminders() {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(stringResource(R.string.hj_reminders), Modifier.padding(start = 4.dp, top = 8.dp))
        AnnouncementPanel {
            REMINDERS.forEachIndexed { i, res ->
                if (i > 0) ListDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                    Text(stringResource(res), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                }
            }
        }
    }
}

/** What is read aloud to the players: the one emphasized block of the screen. */
@Composable
private fun PlayerInstruction() {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(colors.primary.copy(alpha = 0.1f))   // a fill, not content
            .border(1.dp, colors.primary.softened(0.4f), shape).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Outlined.Campaign, null, tint = colors.primary, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(stringResource(R.string.hj_player_instruction), color = colors.primary)
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.hj_call_judge))
                    append("\n")
                    withStyle(SpanStyle(color = colors.primary)) { append(stringResource(R.string.hj_judge)) }
                },
                style = MaterialTheme.typography.titleLarge, color = colors.onSurface,
            )
        }
    }
}

@Composable
private fun VenueCard(note: String, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Typing edits local state; the saved note (DataStore, async) only seeds it. Binding the field
    // straight to the stored flow would make the cursor jump while typing.
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    val text = draft ?: note
    AnnouncementPanel {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.Info, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(stringResource(R.string.hj_venue))
                BasicTextField(
                    value = text,
                    onValueChange = { draft = it; onChange(it) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { field ->
                        Box {
                            if (text.isEmpty()) {
                                Text(stringResource(R.string.hj_venue_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                            field()
                        }
                    },
                )
            }
        }
    }
}
