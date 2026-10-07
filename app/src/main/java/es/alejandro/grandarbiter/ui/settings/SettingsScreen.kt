package es.alejandro.grandarbiter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.model.cards.CardDataInfo
import es.alejandro.grandarbiter.domain.model.cards.CardUpdateState
import es.alejandro.grandarbiter.ui.cardsearch.CardText
import es.alejandro.grandarbiter.ui.components.Panel
import es.alejandro.grandarbiter.ui.components.Section
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** No Figma frame for Settings yet: built from the same blocks as the card detail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val dataInfo by viewModel.dataInfo.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                },
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { CardDataSection(dataInfo, updateState, viewModel::checkForUpdates) }
            item { AboutSection() }
        }
    }
}

// ---------------------------------------------------------------- card data

@Composable
private fun CardDataSection(info: CardDataInfo?, state: CardUpdateState, onCheck: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val busy = state is CardUpdateState.Checking || state is CardUpdateState.Downloading || state is CardUpdateState.Importing
    Section(stringResource(R.string.settings_card_data)) {
        Panel(contentModifier = Modifier.padding(16.dp)) {
            if (info == null) {
                Text(stringResource(R.string.cards_preparing), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            } else {
                InfoRow(stringResource(R.string.settings_cards), NumberFormat.getIntegerInstance().format(info.cardCount))
                InfoRow(stringResource(R.string.settings_scryfall_date), formatDate(info.oracleUpdatedAt))
                InfoRow(
                    stringResource(R.string.settings_source),
                    stringResource(if (info.source == "scryfall") R.string.settings_source_scryfall else R.string.settings_source_seed),
                )
                InfoRow(stringResource(R.string.settings_installed), formatInstant(info.importedAt))
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = colors.outlineVariant.copy(alpha = 0.3f))
            Button(
                onClick = onCheck,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            ) {
                Text(stringResource(R.string.settings_check_updates), fontWeight = FontWeight.Bold)
            }
            UpdateStatus(state, Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun UpdateStatus(state: CardUpdateState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val small = MaterialTheme.typography.bodySmall
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            CardUpdateState.Idle ->
                Text(stringResource(R.string.settings_update_hint), style = small, color = colors.onSurfaceVariant)
            CardUpdateState.Checking -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.settings_checking), style = small, color = colors.onSurfaceVariant)
            }
            is CardUpdateState.Downloading -> {
                val total = state.totalBytes
                if (total != null && total > 0) {
                    LinearProgressIndicator(progress = { (state.bytes.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                val amount = if (total != null) "${mb(state.bytes)} / ${mb(total)} MB" else "${mb(state.bytes)} MB"
                val label = if (state.part == CardUpdateState.Downloading.Part.CARDS) R.string.settings_downloading_cards
                else R.string.settings_downloading_rulings
                Text(stringResource(label, amount), style = small, color = colors.onSurfaceVariant)
            }
            CardUpdateState.Importing -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.settings_importing), style = small, color = colors.onSurfaceVariant)
            }
            is CardUpdateState.UpToDate ->
                Text(stringResource(R.string.settings_up_to_date), style = small, color = colors.primary)
            is CardUpdateState.Updated ->
                Text(
                    stringResource(R.string.settings_updated, NumberFormat.getIntegerInstance().format(state.cards), formatDate(state.oracleUpdatedAt)),
                    style = small, color = colors.primary,
                )
            is CardUpdateState.Failed -> {
                Text(stringResource(R.string.settings_failed), style = small, color = colors.error)
                Text(state.reason, style = small, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(value, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
    }
}

// ---------------------------------------------------------------- about

@Composable
private fun AboutSection() {
    val colors = MaterialTheme.colorScheme
    Section(stringResource(R.string.settings_about)) {
        Panel(contentModifier = Modifier.padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Wording required by the Wizards of the Coast Fan Content Policy.
                Text(stringResource(R.string.settings_fan_content), style = MaterialTheme.typography.bodySmall, color = colors.onSurface)
                Text(stringResource(R.string.settings_scryfall_credit), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------- formatting

private fun formatDate(scryfallTimestamp: String): String =
    CardText.date(scryfallTimestamp)?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: scryfallTimestamp

private fun formatInstant(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

private fun mb(bytes: Long): String = String.format(Locale.getDefault(), "%.1f", bytes / 1_000_000.0)
