package es.alejandro.grandarbiter.ui.tools.drafttimer

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.draft.DraftTimesPreset
import es.alejandro.grandarbiter.domain.draft.UserDraftPreset
import es.alejandro.grandarbiter.domain.draft.UserDraftPresets

/** What the judge chose in the sheet: a built-in preset or one of their own. */
sealed interface PresetChoice {
    data class BuiltIn(val preset: DraftTimesPreset) : PresetChoice
    data class Own(val preset: UserDraftPreset) : PresetChoice
}

/**
 * The Times screen's preset row: the name of what's in use and ▾. It opens a sheet to choose a
 * preset (built-in or the judge's own), rename or delete their own, and save the current times.
 */
@Composable
internal fun PresetPicker(
    m: DraftTimesModel,
    onChoose: (PresetChoice) -> Unit,
    onSave: (String) -> Unit,
    onRename: (UserDraftPreset, String) -> Boolean,
    onDelete: (UserDraftPreset) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf<PresetChoice?>(null) }
    var saving by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<UserDraftPreset?>(null) }
    var deleting by remember { mutableStateOf<UserDraftPreset?>(null) }

    val own = m.userPreset
    val title = own?.name ?: stringResource(when (m.preset) {
        DraftTimesPreset.MTR -> R.string.draft_times_preset_mtr
        DraftTimesPreset.PRO_TOUR -> R.string.draft_times_preset_pro_tour
        null -> R.string.draft_times_custom_title
    })
    val hint = if (own != null) stringResource(R.string.draft_times_preset_own_hint, own.constructionMinutes)
        else stringResource(when (m.preset) {
            DraftTimesPreset.MTR -> R.string.draft_times_preset_mtr_hint
            DraftTimesPreset.PRO_TOUR -> R.string.draft_times_preset_pro_tour_hint
            null -> R.string.draft_times_preset_custom_hint
        })

    DraftCard {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) { open = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }

    if (open) {
        PresetSheet(
            m = m,
            onDismiss = { open = false },
            onChoose = { choice ->
                val custom = m.userPreset == null && m.preset == null
                if (custom) replacing = choice else { open = false; onChoose(choice) }
            },
            onSave = { open = false; saving = true },
            onRename = { renaming = it },
            onDelete = { deleting = it },
        )
    }

    // Custom times are saved as they are edited: a preset would overwrite them with no way back.
    replacing?.let { choice ->
        ConfirmDialog(
            title = stringResource(R.string.draft_times_replace_title),
            confirm = stringResource(R.string.draft_times_replace_confirm),
            dismiss = stringResource(R.string.draft_times_replace_keep),
            onConfirm = { replacing = null; open = false; onChoose(choice) },
            onDismiss = { replacing = null },
        )
    }
    if (saving) SaveDialog(m.userPresets, onDismiss = { saving = false }, onSave = { saving = false; onSave(it) })
    renaming?.let { preset ->
        NameDialog(
            title = stringResource(R.string.draft_times_preset_rename_title),
            initial = preset.name,
            confirm = stringResource(R.string.draft_times_preset_rename_confirm),
            onDismiss = { renaming = null },
            onConfirm = { name -> onRename(preset, name).also { if (it) renaming = null } },
        )
    }
    deleting?.let { preset ->
        ConfirmDialog(
            title = stringResource(R.string.draft_times_preset_delete_title, preset.name),
            confirm = stringResource(R.string.draft_times_preset_delete),
            dismiss = stringResource(R.string.value_cancel),
            destructive = true,
            onConfirm = { deleting = null; onDelete(preset) },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSheet(
    m: DraftTimesModel,
    onDismiss: () -> Unit,
    onChoose: (PresetChoice) -> Unit,
    onSave: () -> Unit,
    onRename: (UserDraftPreset) -> Unit,
    onDelete: (UserDraftPreset) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val notForThesePacks = stringResource(R.string.draft_times_preset_pro_tour_14_only)
    val own = m.userPreset
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerHigh,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.draft_times_section_preset), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp))
            m.presets.forEach { preset ->
                val selected = own == null && m.preset == preset
                val dimmed = preset in m.presetsNotForThesePacks
                ChoiceRow(
                    name = stringResource(if (preset == DraftTimesPreset.MTR) R.string.draft_times_preset_mtr else R.string.draft_times_preset_pro_tour),
                    hint = stringResource(if (preset == DraftTimesPreset.MTR) R.string.draft_times_preset_mtr_hint else R.string.draft_times_preset_pro_tour_hint),
                    selected = selected,
                    dimmed = dimmed,
                    onClick = {
                        when {
                            selected -> onDismiss()
                            dimmed -> Toast.makeText(context, notForThesePacks, Toast.LENGTH_SHORT).show()
                            else -> onChoose(PresetChoice.BuiltIn(preset))
                        }
                    },
                )
            }
            if (m.userPresets.isNotEmpty()) {
                Text(stringResource(R.string.draft_times_preset_yours), style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp))
                m.userPresets.forEach { preset ->
                    val selected = own?.id == preset.id
                    ChoiceRow(
                        name = preset.name,
                        hint = stringResource(R.string.draft_times_preset_own_hint, preset.constructionMinutes),
                        selected = selected,
                        onClick = { if (selected) onDismiss() else onChoose(PresetChoice.Own(preset)) },
                    ) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, stringResource(R.string.draft_times_preset_more, preset.name))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.draft_times_preset_rename)) },
                                    onClick = { menu = false; onRename(preset) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.draft_times_preset_delete), color = colors.error) },
                                    onClick = { menu = false; onDelete(preset) })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.draft_times_preset_save), style = MaterialTheme.typography.labelLarge, color = colors.primary)
            }
        }
    }
}

/** One preset in the sheet: a radio row, its description, and (own presets) a ⋮. */
@Composable
private fun ChoiceRow(
    name: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
    dimmed: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = 12.dp, end = if (trailing != null) 8.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.alpha(if (dimmed) DIMMED else 1f)) {
            RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                modifier = Modifier.padding(12.dp))
        }
        Column(Modifier.weight(1f).alpha(if (dimmed) DIMMED else 1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

/** "Save current times as a preset": a name; a taken name asks before replacing that preset. */
@Composable
private fun SaveDialog(presets: List<UserDraftPreset>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var replacing by remember { mutableStateOf<String?>(null) }
    NameDialog(
        title = stringResource(R.string.draft_times_preset_save_title),
        initial = "",
        confirm = stringResource(R.string.draft_times_preset_save_confirm),
        onDismiss = onDismiss,
        onConfirm = { name ->
            val taken = UserDraftPresets.named(presets, name)
            when {
                UserDraftPresets.isReserved(name) -> false          // MTR / Pro Tour: "has this name"
                taken != null -> { replacing = taken.name; true }
                else -> { onSave(name); true }
            }
        },
        visible = replacing == null,
    )
    replacing?.let { name ->
        ConfirmDialog(
            title = stringResource(R.string.draft_times_preset_replace_title, name),
            confirm = stringResource(R.string.draft_times_replace_confirm),
            dismiss = stringResource(R.string.value_cancel),
            onConfirm = { onSave(name) },
            onDismiss = { replacing = null },
        )
    }
}

/**
 * A preset's name: up to [UserDraftPresets.MAX_NAME_LENGTH] characters. [onConfirm] returns false
 * when the name is taken by another preset (rename), and the dialog says so.
 */
@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean,
    visible: Boolean = true,
) {
    // Focused with the whole name selected: typing replaces it, as in the number dialog.
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    var taken by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    if (!visible) return
    LaunchedEffect(Unit) { focus.requestFocus() }
    val name = field.text
    val valid = UserDraftPresets.isValidName(UserDraftPresets.cleanName(name))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { if (it.text.length <= UserDraftPresets.MAX_NAME_LENGTH) { field = it; taken = false } },
                modifier = Modifier.focusRequester(focus),
                label = { Text(stringResource(R.string.draft_times_preset_name)) },
                singleLine = true,
                isError = taken,
                supportingText = if (taken) ({ Text(stringResource(R.string.draft_times_preset_name_taken)) }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { if (!onConfirm(name)) taken = true }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.value_cancel)) } },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}

/** Disabled-looking content (M3 convention), like the Choice segments. */
private const val DIMMED = 0.4f
