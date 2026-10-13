package es.alejandro.grandarbiter.ui.tools.drafttimer

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.draft.DraftFormat

/** Figma "Draft Timer - Format picker": the formats, only the available ones can be opened. */
@Composable
fun DraftFormatScreen(onBack: () -> Unit, onOpenFormat: (DraftFormat) -> Unit) {
    val colors = MaterialTheme.colorScheme
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
            .border(1.dp, if (enabled) colors.primary.copy(alpha = 0.6f) else colors.outlineVariant, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(format.title()), style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant)
            Text(stringResource(format.description()), style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = colors.onSurfaceVariant)
            if (!enabled) DraftChip(stringResource(R.string.tools_coming_soon))
        }
        if (enabled) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.primary)
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
