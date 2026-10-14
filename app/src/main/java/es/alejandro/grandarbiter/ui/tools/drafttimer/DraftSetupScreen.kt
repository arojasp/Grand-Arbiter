package es.alejandro.grandarbiter.ui.tools.drafttimer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.draft.DraftConfig
import es.alejandro.grandarbiter.domain.draft.DraftEstimate
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.ui.theme.GoldDeep
import es.alejandro.grandarbiter.ui.theme.OnGoldDeep
import org.koin.androidx.compose.koinViewModel

/** Figma "Draft Timer - Setup": format values, alerts, estimated duration, Start. */
@Composable
fun DraftSetupScreen(
    onBack: () -> Unit,
    onStarted: () -> Unit,
    viewModel: DraftSetupViewModel = koinViewModel(),
) {
    val colors = MaterialTheme.colorScheme
    val config by viewModel.config.collectAsState()
    val estimate by viewModel.estimate.collectAsState()
    val beep by viewModel.beep.collectAsState()
    val vibrate by viewModel.vibrate.collectAsState()

    Scaffold(
        containerColor = colors.background,
        topBar = { DraftTopBar(stringResource(viewModel.format.title()), onBack) },
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(colors.background).padding(16.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(GoldDeep)
                        .clickable(role = Role.Button) { viewModel.start(); onStarted() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.draft_start), style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = OnGoldDeep)
                }
            }
        },
    ) { innerPadding ->
        Column(
            Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DraftCard {
                DraftLabel(stringResource(R.string.draft_section_format))
                val rochester = viewModel.format == DraftFormat.ROCHESTER
                if (rochester) {
                    // MTR 8.5: Player B of the team that picks first lays out the first pack.
                    SettingRow(stringResource(R.string.draft_first_team), stringResource(R.string.draft_first_team_hint, "${config.firstTeam}B")) {
                        Choice(listOf(1, 2), config.firstTeam, viewModel::setFirstTeam, width = 76) { stringResource(R.string.draft_team_n, it) }
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                    Stepper(stringResource(R.string.draft_packs_per_player), stringResource(R.string.draft_packs_at_table, config.totalPacks),
                        config.packs, DraftConfig.PACKS, viewModel::setPacks)
                } else {
                    Stepper(stringResource(R.string.draft_packs), null, config.packs, DraftConfig.PACKS, viewModel::setPacks)
                }
                HorizontalDivider(color = colors.outlineVariant)
                if (viewModel.cardsRange.count() <= 2) {
                    // Two-Headed Giant: only the booster sizes the MTR has times for (14 or 15).
                    SettingRow(stringResource(R.string.draft_cards_per_pack), null) {
                        Choice(viewModel.cardsRange.toList(), config.cardsPerPack, viewModel::setCardsPerPack)
                    }
                } else {
                    Stepper(stringResource(R.string.draft_cards_per_pack), if (rochester) null else stringResource(R.string.draft_cards_per_pack_hint),
                        config.cardsPerPack, viewModel.cardsRange, viewModel::setCardsPerPack)
                }
                HorizontalDivider(color = colors.outlineVariant)
                Stepper(stringResource(R.string.draft_construction), stringResource(R.string.draft_minutes),
                    config.constructionMinutes, DraftConfig.CONSTRUCTION_MINUTES, viewModel::setConstructionMinutes)
            }
            DraftCard {
                DraftLabel(stringResource(R.string.draft_section_timer))
                SettingRow(stringResource(R.string.draft_manual_start), stringResource(R.string.draft_manual_start_hint)) {
                    Switch(
                        checked = config.manualStart, onCheckedChange = { viewModel.setManualStart(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = GoldDeep, checkedThumbColor = OnGoldDeep),
                    )
                }
            }
            DraftCard {
                DraftLabel(stringResource(R.string.draft_section_alerts))
                SettingRow(stringResource(R.string.draft_vibrate), stringResource(R.string.draft_vibrate_hint)) {
                    Switch(
                        checked = vibrate, onCheckedChange = { viewModel.setVibrate(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = GoldDeep, checkedThumbColor = OnGoldDeep),
                    )
                }
                HorizontalDivider(color = colors.outlineVariant)
                SettingRow(stringResource(R.string.draft_beep), stringResource(R.string.draft_beep_hint)) {
                    Switch(
                        checked = beep, onCheckedChange = { viewModel.setBeep(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = GoldDeep, checkedThumbColor = OnGoldDeep),
                    )
                }
            }
            estimate?.let { EstimateCard(it) }
        }
    }
}

@Composable
private fun Stepper(label: String, hint: String?, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    SettingRow(label, hint) {
        val colors = MaterialTheme.colorScheme
        Row(
            Modifier.clip(CircleShape).background(colors.surfaceContainerHighest).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton("−", stringResource(R.string.draft_decrease, label), enabled = value > range.first) { onChange(value - 1) }
            Text(value.toString(), Modifier.width(56.dp), textAlign = TextAlign.Center,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = colors.onSurface)
            StepButton("+", stringResource(R.string.draft_increase, label), enabled = value < range.last) { onChange(value + 1) }
        }
    }
}

/** Pick one of a few values (Two-Headed Giant booster size: 14 | 15; Rochester: Team 1 | Team 2). */
@Composable
private fun Choice(
    values: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    width: Int = 56,
    label: @Composable (Int) -> String = { it.toString() },
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.clip(CircleShape).background(colors.surfaceContainerHighest).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        values.forEach { value ->
            val on = value == selected
            Box(
                Modifier.width(width.dp).height(36.dp).clip(CircleShape)
                    .background(if (on) GoldDeep else colors.surfaceContainerHighest)
                    .clickable(role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(value), style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = if (on) OnGoldDeep else colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(36.dp).alpha(if (enabled) 1f else 0.4f).clip(CircleShape).background(colors.surfaceContainerHigh)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium), color = colors.primary)
    }
}

@Composable
private fun SettingRow(label: String, hint: String?, trailing: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium), color = colors.onSurface)
            if (hint != null) Text(hint, style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp), color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun EstimateCard(e: DraftEstimate) {
    val colors = MaterialTheme.colorScheme
    DraftCard {
        DraftLabel(stringResource(if (e.atMost) R.string.draft_section_estimate_at_most else R.string.draft_section_estimate))
        if (e.layOutSeconds.isNotEmpty()) {
            // Rochester: every pack reviewed once laid out, then picks of 5 s AT MOST (they end on "Picked").
            EstimateRow(stringResource(R.string.draft_estimate_layout, e.layOutSeconds.size, e.layOutSeconds.first()),
                formatClock(e.layOutSeconds.sum() * 1000L))
        }
        val picksLabel = if (e.atMost) stringResource(R.string.draft_estimate_picks_rochester, e.packs, e.timedPicksPerPack, e.pickSeconds)
            else if (e.cardsPerPick > 1) stringResource(R.string.draft_estimate_picks_multi, e.packs, e.timedPicksPerPack, e.cardsPerPick)
            else stringResource(R.string.draft_estimate_picks, e.packs, e.timedPicksPerPack)
        EstimateRow(picksLabel, formatClock(e.picksMs))
        if (e.reviewSeconds.isNotEmpty()) {
            // "60 s + 90 s"; the same every time (2HG): "5 × 60 s".
            val reviews = if (e.reviewSeconds.size > 2 && e.reviewSeconds.distinct().size == 1) "${e.reviewSeconds.size} × ${e.reviewSeconds[0]} s"
                else e.reviewSeconds.joinToString(" + ") { "$it s" }
            EstimateRow(stringResource(R.string.draft_estimate_review, reviews), formatClock(e.reviewSeconds.sum() * 1000L))
        }
        EstimateRow(stringResource(R.string.draft_construction), formatClock(e.constructionMs))
        HorizontalDivider(color = colors.outlineVariant)
        EstimateRow(stringResource(R.string.draft_total), (if (e.atMost) "≤ " else "≈ ") + formatClock(e.totalMs), emphasis = true)
        if (e.atMost) {
            Text(stringResource(R.string.draft_estimate_at_most_note, e.pickSeconds),
                style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp), color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun EstimateRow(label: String, value: String, emphasis: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Normal),
            color = if (emphasis) colors.onSurface else colors.onSurfaceVariant)
        Text(value, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Medium),
            color = if (emphasis) colors.primary else colors.onSurface)
    }
}
