package es.alejandro.grandarbiter.ui.tools.drafttimer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.draft.DraftPresetShareCodec
import es.alejandro.grandarbiter.data.draft.SharedDraftPreset
import es.alejandro.grandarbiter.data.draft.SharedPresetResult
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import kotlinx.coroutines.launch

/**
 * "Import preset": paste what another judge shared, see what it is, import it. Import is enabled
 * only once the text reads as a preset; what's wrong is said under the field.
 */
@Composable
internal fun ImportDialog(format: DraftFormat, onDismiss: () -> Unit, onImport: (SharedDraftPreset) -> Unit) {
    var text by remember { mutableStateOf("") }
    val result = remember(text) { if (text.isBlank()) null else DraftPresetShareCodec.decode(text) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.draft_times_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= DraftPresetShareCodec.MAX_TEXT_LENGTH) text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    label = { Text(stringResource(R.string.draft_times_import_field)) },
                    trailingIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)?.text?.toString()?.let { text = it.take(DraftPresetShareCodec.MAX_TEXT_LENGTH) }
                            }
                        }) { Icon(Icons.Outlined.ContentPaste, stringResource(R.string.draft_times_import_paste)) }
                    },
                    isError = result != null && result !is SharedPresetResult.Read,
                    supportingText = when (result) {
                        null -> ({ Text(stringResource(R.string.draft_times_import_hint)) })
                        is SharedPresetResult.Read -> null
                        SharedPresetResult.NotAPreset -> ({ Text(stringResource(R.string.draft_times_import_not_preset)) })
                        SharedPresetResult.NewerVersion -> ({ Text(stringResource(R.string.draft_times_import_newer)) })
                        SharedPresetResult.Invalid -> ({ Text(stringResource(R.string.draft_times_import_invalid)) })
                    },
                    maxLines = 6,
                )
                (result as? SharedPresetResult.Read)?.preset?.let { Preview(it, format) }
            }
        },
        confirmButton = {
            val read = result as? SharedPresetResult.Read
            TextButton(enabled = read != null, onClick = { read?.let { onImport(it.preset) } }) {
                Text(stringResource(R.string.draft_times_import_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.value_cancel)) } },
    )
}

/** What will be imported: name, format, what it changes, construction; and where it goes if not here. */
@Composable
private fun Preview(preset: SharedDraftPreset, screenFormat: DraftFormat) {
    val colors = MaterialTheme.colorScheme
    val picks = preset.times.pickSeconds.values.sumOf { it.size }
    val reviews = preset.times.reviewSeconds.size
    val details = buildList {
        if (!preset.times.timedPicks) add(stringResource(R.string.draft_times_untimed_title))
        else if (picks > 0) add(pluralStringResource(R.plurals.draft_times_summary_picks, picks, picks))
        if (preset.times.timedPicks && reviews > 0) add(pluralStringResource(R.plurals.draft_times_summary_reviews, reviews, reviews))
        if (isEmpty()) add(stringResource(R.string.draft_times_import_mtr))
        add(stringResource(R.string.draft_times_preset_own_hint, preset.constructionMinutes))
    }.joinToString(" · ")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(preset.name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Text(formatName(preset.format), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Text(details, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        if (preset.format != screenFormat) {
            Text(stringResource(R.string.draft_times_import_other_format, formatName(preset.format)),
                style = MaterialTheme.typography.bodySmall, color = colors.primary)
        }
    }
}

@Composable
internal fun formatName(format: DraftFormat): String = stringResource(when (format) {
    DraftFormat.BOOSTER -> R.string.draft_format_booster
    DraftFormat.TWO_HEADED_GIANT -> R.string.draft_format_2hg
    DraftFormat.ROCHESTER -> R.string.draft_format_rochester
})
