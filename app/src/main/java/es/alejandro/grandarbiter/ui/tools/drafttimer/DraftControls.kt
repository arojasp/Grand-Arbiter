package es.alejandro.grandarbiter.ui.tools.drafttimer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R

/* Setting rows and value controls of the Draft Timer setup and Times screens. */

/** A setting: label, optional hint below it, and its control on the right. */
@Composable
internal fun SettingRow(label: String, hint: String?, trailing: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** A setting with a − value + control (see [StepperControl]). */
@Composable
internal fun Stepper(
    label: String,
    hint: String?,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    step: Int = 1,
    unit: String? = null,
) {
    SettingRow(label, hint) { StepperControl(label, value, range, onChange, step, unit) }
}

/**
 * − value +. Each button moves [step] (clamped to [range]). With [unit], the value can also be
 * typed: tapping it opens a field for any value in [range] ("45 s", "300 s", "30 min").
 */
@Composable
internal fun StepperControl(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    step: Int = 1,
    unit: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    var typing by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.clip(CircleShape).background(colors.surfaceContainerHighest).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", stringResource(R.string.draft_decrease, label), enabled = value > range.first) {
            onChange((value - step).coerceIn(range))
        }
        val text = if (unit != null) "$value $unit" else value.toString()
        val typeLabel = stringResource(R.string.draft_type_value, label)
        Box(
            Modifier.width(if (unit != null) 64.dp else 56.dp).height(36.dp)
                .then(if (unit != null) Modifier.clip(CircleShape).clickable(role = Role.Button, onClickLabel = typeLabel) { typing = true } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = colors.onSurface)
        }
        StepButton("+", stringResource(R.string.draft_increase, label), enabled = value < range.last) {
            onChange((value + step).coerceIn(range))
        }
    }
    if (typing && unit != null) {
        NumberDialog(label, value, range, unit, onDismiss = { typing = false }) { onChange(it); typing = false }
    }
}

/** Type a value: digits only, as many as [range]'s top has; "Set" only when it is in [range]. */
@Composable
private fun NumberDialog(title: String, value: Int, range: IntRange, unit: String, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    val start = value.toString()
    var field by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
    val parsed = field.text.toIntOrNull()?.takeIf { it in range }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { new ->
                    if (new.text.length <= range.last.toString().length && new.text.all(Char::isDigit)) field = new
                },
                modifier = Modifier.focusRequester(focus),
                singleLine = true,
                suffix = { Text(unit) },
                supportingText = { Text(stringResource(R.string.draft_value_range, range.first, range.last, unit)) },
                isError = parsed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onSet) }, enabled = parsed != null) { Text(stringResource(R.string.draft_set)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.draft_cancel)) } },
    )
}

/** Pick one of a few values (Two-Headed Giant booster size: 14 | 15; Rochester: Team 1 | Team 2). */
@Composable
internal fun Choice(
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
                    .background(if (on) colors.primary else colors.surfaceContainerHighest)
                    .clickable(role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(value), style = MaterialTheme.typography.labelLarge,
                    color = if (on) colors.onPrimary else colors.onSurfaceVariant)
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
        Text(symbol, style = MaterialTheme.typography.titleMedium, color = colors.primary)
    }
}
