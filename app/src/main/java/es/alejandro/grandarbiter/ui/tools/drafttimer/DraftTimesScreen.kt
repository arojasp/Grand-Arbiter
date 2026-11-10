package es.alejandro.grandarbiter.ui.tools.drafttimer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.ui.components.Section
import es.alejandro.grandarbiter.domain.draft.DraftConfig
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import org.koin.androidx.compose.koinViewModel

/**
 * Canvas "Tiempos · Pantalla Booster / 2HG": timed picks on/off, all picks ±5 s, one list per pack
 * size in use (by pick number), reviews, deck construction and "Reset to MTR". Changes are saved at once.
 */
@Composable
fun DraftTimesScreen(
    onBack: () -> Unit,
    viewModel: DraftTimesViewModel = koinViewModel(),
) {
    val colors = MaterialTheme.colorScheme
    val m by viewModel.model.collectAsState()
    val seconds = stringResource(R.string.draft_unit_seconds)

    Scaffold(
        containerColor = colors.background,
        topBar = { DraftTopBar(stringResource(R.string.draft_times_title), onBack) },
    ) { innerPadding ->
        Column(
            Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            // Each card under its Label, outside it (as in Settings); 24 dp between groups.
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section(stringResource(R.string.draft_times_section_picks)) { DraftCard {
                SettingRow(stringResource(R.string.draft_times_timed), stringResource(R.string.draft_times_timed_hint)) {
                    Switch(
                        checked = m.timedPicks, onCheckedChange = viewModel::setTimedPicks,
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary),
                    )
                }
                if (m.timedPicks) {
                    HorizontalDivider(color = colors.outlineVariant)
                    SettingRow(stringResource(R.string.draft_times_all_picks), stringResource(R.string.draft_times_all_picks_hint)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.allPicks(-STEP_SECONDS) }) { Text(stringResource(R.string.draft_times_minus)) }
                            OutlinedButton(onClick = { viewModel.allPicks(STEP_SECONDS) }) { Text(stringResource(R.string.draft_times_plus)) }
                        }
                    }
                    if (m.lists.size > 1) {
                        Text(stringResource(R.string.draft_times_lists_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            } }
            if (m.timedPicks) {
                m.lists.forEach { list ->
                    Section(stringResource(R.string.draft_times_list, list.packSize)) { DraftCard(Modifier, verticalSpacing = 4) {
                        list.rows.forEach { row ->
                            val value = m.times.secondsFor(row)
                            val cards = stringResource(if (m.cardsPerPick > 1) R.string.draft_times_row_cards_take else R.string.draft_times_row_cards, row.cardsLeft)
                            val label = stringResource(R.string.draft_times_pick, row.pick)
                            TimeRow(label, if (m.times.isChanged(row)) "$cards · " + stringResource(R.string.draft_times_mtr, "${row.mtrSeconds} $seconds") else cards,
                                changed = m.times.isChanged(row)) {
                                StepperControl(label, value, DraftTimes.SECONDS, { viewModel.setPick(row, it) }, STEP_SECONDS, seconds)
                            }
                        }
                    } }
                }
                if (m.reviews.isNotEmpty()) {
                    Section(stringResource(R.string.draft_times_section_review)) { DraftCard(Modifier, verticalSpacing = 4) {
                        m.reviews.forEach { review ->
                            val label = if (review.pack == DraftTimes.EVERY_REVIEW) stringResource(R.string.draft_times_review_every)
                                else stringResource(R.string.draft_times_review_after, review.pack)
                            TimeRow(label, if (review.isChanged) stringResource(R.string.draft_times_mtr, "${review.mtrSeconds} $seconds") else null,
                                changed = review.isChanged) {
                                StepperControl(label, review.seconds, DraftTimes.SECONDS, { viewModel.setReview(review, it) }, REVIEW_STEP_SECONDS, seconds)
                            }
                        }
                    } }
                }
            }
            Section(stringResource(R.string.draft_construction)) { DraftCard(Modifier, verticalSpacing = 4) {
                val minutes = stringResource(R.string.draft_unit_minutes)
                val label = stringResource(R.string.draft_times_construction_duration)
                TimeRow(label,
                    if (m.constructionChanged) stringResource(R.string.draft_times_mtr, "${m.mtrConstructionMinutes} $minutes")
                        else if (!m.timedPicks) stringResource(R.string.draft_times_construction_untimed_hint) else null,
                    changed = m.constructionChanged) {
                    StepperControl(label, m.constructionMinutes, DraftConfig.CONSTRUCTION_MINUTES, viewModel::setConstructionMinutes, 1, minutes)
                }
            } }
            OutlinedButton(onClick = viewModel::resetToMtr, enabled = !m.isMtr, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.draft_times_reset))
            }
        }
    }
}

/** A time row; changed from the MTR: a dot, a tinted background and the MTR value in the hint. */
@Composable
private fun TimeRow(label: String, hint: String?, changed: Boolean, control: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(8.dp))
            .background(if (changed) colors.surfaceContainerHigh else colors.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (changed) colors.primary else colors.surfaceContainer))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        control()
    }
}

private const val STEP_SECONDS = 5
private const val REVIEW_STEP_SECONDS = 10
