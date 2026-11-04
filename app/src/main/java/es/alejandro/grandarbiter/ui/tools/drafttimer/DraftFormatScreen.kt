package es.alejandro.grandarbiter.ui.tools.drafttimer

import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftPosition
import es.alejandro.grandarbiter.domain.draft.DraftSnapshot
import es.alejandro.grandarbiter.domain.draft.DraftStep
import org.koin.androidx.compose.koinViewModel
import es.alejandro.grandarbiter.ui.theme.softened

/**
 * Figma "Draft Timer - Format picker": the formats, only the available ones can be opened.
 * With a draft still in progress it asks first: resume it, or discard it to set up a new one.
 */
@Composable
fun DraftFormatScreen(
    onBack: () -> Unit,
    onOpenFormat: (DraftFormat) -> Unit,
    onResume: () -> Unit,
    viewModel: DraftFormatViewModel = koinViewModel(),
) {
    val colors = MaterialTheme.colorScheme
    val active by viewModel.activeDraft.collectAsState()
    active?.let { draft ->
        AlertDialog(
            onDismissRequest = onBack,   // neither choice: leave, the draft stays as it is
            title = { Text(stringResource(R.string.draft_active_title)) },
            text = { Text(stringResource(R.string.draft_active_text, describe(draft))) },
            confirmButton = { TextButton(onClick = onResume) { Text(stringResource(R.string.draft_resume)) } },
            dismissButton = {
                TextButton(onClick = viewModel::discard) { Text(stringResource(R.string.draft_discard), color = colors.error) }
            },
        )
    }
    Scaffold(
        containerColor = colors.background,
        topBar = { DraftTopBar(stringResource(R.string.tools_draft_timer), onBack) },
    ) { innerPadding ->
        LazyColumn(
            Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DraftLabel(stringResource(R.string.draft_choose_format)) }
            items(DraftFormat.entries) { format -> FormatCard(format) { onOpenFormat(format) } }
        }
    }
}

@Composable
private fun FormatCard(format: DraftFormat, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val enabled = format.available
    Row(
        Modifier.fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(1.dp, if (enabled) colors.primary.softened(0.6f) else colors.outlineVariant, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(format.title()), style = MaterialTheme.typography.titleSmall,
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant)
            Text(stringResource(format.description()), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (!enabled) DraftChip(stringResource(R.string.tools_coming_soon))
        }
        if (enabled) Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

internal fun DraftFormat.title(): Int = when (this) {
    DraftFormat.BOOSTER -> R.string.draft_format_booster
    DraftFormat.TWO_HEADED_GIANT -> R.string.draft_format_2hg
    DraftFormat.ROCHESTER -> R.string.draft_format_rochester
}

private fun DraftFormat.description(): Int = when (this) {
    DraftFormat.BOOSTER -> R.string.draft_format_booster_desc
    DraftFormat.TWO_HEADED_GIANT -> R.string.draft_format_2hg_desc
    DraftFormat.ROCHESTER -> R.string.draft_format_rochester_desc
}

/** "Pack 2 · Pick 5 · 0:12 left", "Waiting: Start pack 2", "Deck construction · 18:40 left (paused)". */
@Composable
private fun describe(s: DraftSnapshot): String {
    val text = when (val p = s.position) {
        DraftPosition.Finished -> stringResource(R.string.draft_active_finished)
        is DraftPosition.Waiting -> (p.step as? DraftStep.Pick)?.actor
            ?.let { actor -> stringResource(R.string.draft_active_rochester_pick, p.step.pack, actor, formatClock(0)) }
            ?: stringResource(R.string.draft_active_waiting, startLabel(p.step, s.nextStep))
        is DraftPosition.Running -> {
            val left = formatClock(p.remainingMs ?: p.elapsedInStepMs)
            when (val step = p.step) {
                is DraftStep.Pick -> step.actor?.let { stringResource(R.string.draft_active_rochester_pick, step.pack, it, left) }
                    ?: stringResource(R.string.draft_active_pick, step.pack, step.pick, left)
                is DraftStep.LayOut -> stringResource(R.string.draft_active_layout, step.pack, left)
                is DraftStep.Review -> stringResource(R.string.draft_active_review, step.afterPack, left)
                is DraftStep.Construction -> stringResource(R.string.draft_active_construction, left)
            }
        }
    }
    val paused = !s.isRunning && s.position is DraftPosition.Running
    return if (paused) stringResource(R.string.draft_active_paused, text) else text
}
