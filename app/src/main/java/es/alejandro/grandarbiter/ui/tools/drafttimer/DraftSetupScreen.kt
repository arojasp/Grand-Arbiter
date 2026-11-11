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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.ui.components.Choice
import es.alejandro.grandarbiter.ui.components.Section
import es.alejandro.grandarbiter.ui.components.SettingRow
import es.alejandro.grandarbiter.ui.components.Stepper
import es.alejandro.grandarbiter.domain.draft.DraftConfig
import es.alejandro.grandarbiter.domain.draft.DraftEstimate
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import org.koin.androidx.compose.koinViewModel

/** Figma "Draft Timer - Setup": format values, alerts, estimated duration, Start. */
@Composable
fun DraftSetupScreen(
    onBack: () -> Unit,
    onStarted: () -> Unit,
    /** The Times screen of this format, with each pack's size (15, 14, 14). */
    onOpenTimes: (DraftFormat, List<Int>) -> Unit,
    viewModel: DraftSetupViewModel = koinViewModel(),
) {
    val colors = MaterialTheme.colorScheme
    val config by viewModel.config.collectAsState()
    val estimate by viewModel.estimate.collectAsState()
    val times by viewModel.times.collectAsState()
    val beep by viewModel.beep.collectAsState()
    val vibrate by viewModel.vibrate.collectAsState()

    Scaffold(
        containerColor = colors.background,
        topBar = { DraftTopBar(stringResource(viewModel.format.title()), onBack) },
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(colors.background).padding(16.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(colors.primary)
                        .clickable(role = Role.Button) { viewModel.start(); onStarted() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.draft_start), style = MaterialTheme.typography.labelLarge, color = colors.onPrimary)
                }
            }
        },
    ) { innerPadding ->
        Column(
            Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            // Each card under its Label, outside it (as in Settings); 24 dp between groups.
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section(stringResource(R.string.draft_section_format)) { DraftCard {
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
                val sizes = config.packSizes
                if (viewModel.canMixSizes) {
                    // Drafts mixing sets: every player opens the same pack at once, but each round can differ (15, 14, 14).
                    // The switch stays in place; below it, one size for all or one per pack.
                    SettingRow(stringResource(R.string.draft_mixed_sizes), stringResource(R.string.draft_mixed_sizes_hint)) {
                        Switch(
                            checked = sizes != null, onCheckedChange = { viewModel.setMixedSizes(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary),
                        )
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                }
                if (sizes != null) {
                    sizes.forEachIndexed { i, size ->
                        PackSize(stringResource(R.string.draft_pack_n, i + 1), null, size, viewModel.cardsRange) { viewModel.setPackSize(i + 1, it) }
                    }
                } else {
                    PackSize(stringResource(R.string.draft_cards_per_pack), if (rochester) null else stringResource(R.string.draft_cards_per_pack_hint),
                        config.cardsPerPack, viewModel.cardsRange, viewModel::setCardsPerPack)
                }
                if (!viewModel.hasTimes) {
                    // Rochester: no Times screen; its construction minutes stay here.
                    HorizontalDivider(color = colors.outlineVariant)
                    Stepper(stringResource(R.string.draft_construction), null,
                        config.constructionMinutes, DraftConfig.CONSTRUCTION_MINUTES, viewModel::setConstructionMinutes,
                        unit = stringResource(R.string.draft_unit_minutes))
                }
            } }
            times?.let { t ->
                Section(stringResource(R.string.draft_section_times)) {
                    TimesCard(t, onOpen = { onOpenTimes(viewModel.format, (1..config.packs).map(config::cardsInPack)) }, onReset = viewModel::resetTimes)
                }
            }
            // Untimed picks: "Manual start" would only affect the end of deck construction, the last step.
            if (times?.timedPicks != false) {
                Section(stringResource(R.string.draft_section_timer)) { DraftCard {
                    SettingRow(stringResource(R.string.draft_manual_start), stringResource(R.string.draft_manual_start_hint)) {
                        Switch(
                            checked = config.manualStart, onCheckedChange = { viewModel.setManualStart(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary),
                        )
                    }
                } }
            }
            Section(stringResource(R.string.draft_section_alerts)) { DraftCard {
                SettingRow(stringResource(R.string.draft_vibrate), stringResource(R.string.draft_vibrate_hint)) {
                    Switch(
                        checked = vibrate, onCheckedChange = { viewModel.setVibrate(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary),
                    )
                }
                HorizontalDivider(color = colors.outlineVariant)
                SettingRow(stringResource(R.string.draft_beep), stringResource(R.string.draft_beep_hint)) {
                    Switch(
                        checked = beep, onCheckedChange = { viewModel.setBeep(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary),
                    )
                }
            } }
            estimate?.let { e ->
                Section(stringResource(if (e.atMost) R.string.draft_section_estimate_at_most else R.string.draft_section_estimate)) { EstimateCard(e) }
            }
        }
    }
}

/**
 * Canvas "Tiempos · Setup": the times this draft uses (MTR / custom / untimed) and what differs;
 * the whole card above the reset opens the Times screen (the ripple fills it, edge to edge).
 * "Reset to MTR" only when something differs.
 */
@Composable
private fun TimesCard(t: DraftTimesModel, onOpen: () -> Unit, onReset: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    // Like DraftCard, but the padding is inside the clickable area.
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.surfaceContainer).border(1.dp, colors.outlineVariant, shape)) {
        val title = stringResource(when {
            !t.timedPicks -> R.string.draft_times_untimed_title
            t.differsInUse -> R.string.draft_times_custom_title
            else -> R.string.draft_times_mtr_title
        })
        val parts = buildList {
            when {
                !t.timedPicks -> add(stringResource(R.string.draft_times_summary_untimed))
                !t.differsInUse -> add(stringResource(R.string.draft_times_summary_mtr))
                else -> {
                    if (t.changedPicksInUse > 0) add(pluralStringResource(R.plurals.draft_times_summary_picks, t.changedPicksInUse, t.changedPicksInUse))
                    if (t.changedReviews > 0) add(pluralStringResource(R.plurals.draft_times_summary_reviews, t.changedReviews, t.changedReviews))
                }
            }
            add(stringResource(R.string.draft_times_summary_construction, t.constructionMinutes))
        }
        Column(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.onSurfaceVariant)
            }
        }
        if (t.differsInUse) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant)
            TextButton(onClick = onReset, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.draft_times_reset), style = MaterialTheme.typography.labelLarge, color = colors.primary)
            }
        }
    }
}

/** A pack size: a stepper, or 14 | 15 in Two-Headed Giant (the only sizes the MTR has times for). */
@Composable
private fun PackSize(label: String, hint: String?, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    if (range.count() <= 2) SettingRow(label, hint) { Choice(range.toList(), value, onChange) }
    else Stepper(label, hint, value, range, onChange)
}

@Composable
private fun EstimateCard(e: DraftEstimate) {
    val colors = MaterialTheme.colorScheme
    DraftCard {
        if (e.layOutSeconds.isNotEmpty()) {
            // Rochester: every pack reviewed once laid out, then picks of 5 s AT MOST (they end on "Picked").
            EstimateRow(stringResource(R.string.draft_estimate_layout, e.layOutSeconds.size, e.layOutSeconds.first()),
                formatClock(e.layOutSeconds.sum() * 1000L))
        }
        // Packs of different sizes: "Picks (14 + 13 + 13)".
        val byPack = e.timedPicksByPack.takeIf { it.distinct().size > 1 }?.joinToString(" + ")
        val picksLabel = if (e.atMost) stringResource(R.string.draft_estimate_picks_rochester, e.packs, e.timedPicksPerPack, e.pickSeconds)
            else if (byPack != null && e.cardsPerPick > 1) stringResource(R.string.draft_estimate_picks_by_pack_multi, byPack, e.cardsPerPick)
            else if (byPack != null) stringResource(R.string.draft_estimate_picks_by_pack, byPack)
            else if (e.cardsPerPick > 1) pluralStringResource(R.plurals.draft_estimate_picks_multi, e.packs, e.packs, e.timedPicksPerPack, e.cardsPerPick)
            else pluralStringResource(R.plurals.draft_estimate_picks, e.packs, e.packs, e.timedPicksPerPack)
        val untimed = stringResource(R.string.draft_estimate_untimed)
        EstimateRow(picksLabel, if (e.untimedPicks) untimed else formatClock(e.picksMs))
        if (e.untimedPicks && e.packs > 1) {
            EstimateRow(stringResource(R.string.draft_estimate_review_untimed), untimed)
        } else if (e.reviewSeconds.isNotEmpty()) {
            // "60 s + 90 s"; the same every time (2HG): "5 × 60 s".
            val reviews = if (e.reviewSeconds.size > 2 && e.reviewSeconds.distinct().size == 1) "${e.reviewSeconds.size} × ${e.reviewSeconds[0]} s"
                else e.reviewSeconds.joinToString(" + ") { "$it s" }
            EstimateRow(stringResource(R.string.draft_estimate_review, reviews), formatClock(e.reviewSeconds.sum() * 1000L))
        }
        EstimateRow(stringResource(R.string.draft_construction), formatClock(e.constructionMs))
        HorizontalDivider(color = colors.outlineVariant)
        EstimateRow(stringResource(R.string.draft_total),
            if (e.untimedPicks) stringResource(R.string.draft_estimate_total_untimed, formatClock(e.totalMs))
            else (if (e.atMost) "≤ " else "≈ ") + formatClock(e.totalMs), emphasis = true)
        if (e.atMost) {
            Text(stringResource(R.string.draft_estimate_at_most_note, e.pickSeconds),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun EstimateRow(label: String, value: String, emphasis: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        // The total stands out by role and color, not by Bold (design-decisions §9).
        Text(label, style = if (emphasis) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (emphasis) colors.onSurface else colors.onSurfaceVariant)
        Text(value, style = (if (emphasis) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge).copy(fontFeatureSettings = "tnum"),
            color = if (emphasis) colors.primary else colors.onSurface)
    }
}
