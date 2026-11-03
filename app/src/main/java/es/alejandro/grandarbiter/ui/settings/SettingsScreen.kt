package es.alejandro.grandarbiter.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.model.DisplaySettings
import es.alejandro.grandarbiter.domain.model.TextScale
import es.alejandro.grandarbiter.domain.model.cards.CardDataInfo
import es.alejandro.grandarbiter.domain.model.cards.CardUpdateState
import es.alejandro.grandarbiter.domain.model.rules.RulesDocumentInfo
import es.alejandro.grandarbiter.domain.model.rules.RulesUpdateState
import es.alejandro.grandarbiter.ui.cardsearch.CardText
import es.alejandro.grandarbiter.ui.components.Label
import es.alejandro.grandarbiter.ui.components.ListDivider
import es.alejandro.grandarbiter.ui.components.Panel
import es.alejandro.grandarbiter.ui.theme.softened
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.koin.androidx.compose.koinViewModel

/*
 * Figma: "Settings" (node 22:1888). Differences on purpose:
 *  - "Check for updates" updates CARD data, so it sits in its own Card database section (the rules
 *    documents ship inside the app and can't be updated from here yet).
 *  - Documents: all four (JAR included), real effective dates; tap opens the official PDF in the browser.
 *  - About: real name and version; footer = the notice Wizards' Fan Content Policy requires.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val display by viewModel.display.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val cardData by viewModel.cardData.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val rulesUpdateState by viewModel.rulesUpdateState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                title = {
                    Text(stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            item { AccessibilitySection(display, viewModel::setTextScale, viewModel::setHighContrast, viewModel::setKeepScreenOn) }
            item { DocumentsSection(documents) }
            item { CardDatabaseSection(cardData, updateState, rulesUpdateState, viewModel::checkForUpdates) }
            item { AboutSection() }
        }
    }
}

// ---------------------------------------------------------------- accessibility

@Composable
private fun AccessibilitySection(
    display: DisplaySettings,
    onTextScale: (TextScale) -> Unit,
    onHighContrast: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SettingsSection(stringResource(R.string.settings_accessibility)) {
        SettingsPanel {
            PanelRow {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_text_size), style = MaterialTheme.typography.titleMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
                        Text(stringResource(display.textScale.label()), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                    Segmented(
                        options = TextScale.entries,
                        selected = display.textScale,
                        label = { stringResource(it.label()) },
                        onSelect = onTextScale,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceContainerLow)
                            .border(1.dp, colors.outlineVariant.softened(0.2f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(stringResource(R.string.settings_text_preview),
                            // Sample of reading text at the chosen size.
                            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                            color = colors.onSurfaceVariant)
                    }
                }
            }
            PanelDivider()
            SwitchRow(stringResource(R.string.settings_high_contrast), stringResource(R.string.settings_high_contrast_hint),
                display.highContrast, onHighContrast)
            PanelDivider()
            SwitchRow(stringResource(R.string.settings_keep_screen_on), stringResource(R.string.settings_keep_screen_on_hint),
                display.keepScreenOn, onKeepScreenOn)
        }
    }
}

private fun TextScale.label(): Int = when (this) {
    TextScale.SMALL -> R.string.settings_text_small
    TextScale.DEFAULT -> R.string.settings_text_default
    TextScale.LARGE -> R.string.settings_text_large
    TextScale.EXTRA_LARGE -> R.string.settings_text_extra_large
}

/** Segmented control: track surfaceContainerHighest, selected segment in primary; 40 dp segments in a 48 dp track. */
@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceContainerHighest)
            .padding(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) colors.primary else colors.surfaceContainerHighest)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TitleAndSubtitle(title, subtitle, Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.outline,
                uncheckedTrackColor = colors.surfaceContainerHighest,
                uncheckedBorderColor = colors.surfaceContainerHighest,
            ),
        )
    }
}

// ---------------------------------------------------------------- documents

@Composable
private fun DocumentsSection(documents: List<RulesDocumentInfo>) {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    SettingsSection(stringResource(R.string.settings_documents)) {
        SettingsPanel {
            if (documents.isEmpty()) {
                PanelRow { Text(stringResource(R.string.reader_not_installed), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            }
            documents.forEachIndexed { i, doc ->
                if (i > 0) PanelDivider()
                Row(
                    // The official PDF in the browser (the in-app reader is reached from Home).
                    Modifier.fillMaxWidth().clickable(role = Role.Button) {
                        runCatching { uriHandler.openUri(doc.pdfUrl) }   // no browser installed: do nothing
                    }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TitleAndSubtitle(
                        doc.title,
                        stringResource(R.string.reader_effective, doc.effectiveDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))),
                        Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.settings_open_pdf),
                        tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- card database

@Composable
private fun CardDatabaseSection(info: CardDataInfo?, state: CardUpdateState, rulesState: RulesUpdateState, onCheck: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val busy = state is CardUpdateState.Checking || state is CardUpdateState.Downloading || state is CardUpdateState.Importing ||
        rulesState is RulesUpdateState.Checking || rulesState is RulesUpdateState.Downloading || rulesState is RulesUpdateState.Importing
    SettingsSection(stringResource(R.string.settings_card_data)) {
        SettingsPanel {
            if (info == null) {
                PanelRow { Text(stringResource(R.string.cards_preparing), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            } else {
                InfoRow(stringResource(R.string.settings_cards), NumberFormat.getIntegerInstance().format(info.cardCount))
                PanelDivider()
                InfoRow(stringResource(R.string.settings_scryfall_date), formatDate(info.oracleUpdatedAt))
                PanelDivider()
                InfoRow(
                    stringResource(R.string.settings_source),
                    stringResource(if (info.source == "scryfall") R.string.settings_source_scryfall else R.string.settings_source_seed),
                )
                PanelDivider()
                InfoRow(stringResource(R.string.settings_installed), formatInstant(info.importedAt))
            }
        }
        // Full-width update button: primary, 48 dp, 12 dp corners, sync icon.
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (busy) colors.primary.copy(alpha = 0.5f) else colors.primary)
                .clickable(enabled = !busy, role = Role.Button, onClick = onCheck)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Sync, null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_check_updates),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onPrimary)
        }
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state is CardUpdateState.Idle && rulesState is RulesUpdateState.Idle) {
                Text(stringResource(R.string.settings_update_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            } else {
                RulesUpdateStatus(rulesState)
                UpdateStatus(state)
            }
        }
    }
}

/** "Documents: …" line of the update status. */
@Composable
private fun RulesUpdateStatus(state: RulesUpdateState) {
    val colors = MaterialTheme.colorScheme
    val small = MaterialTheme.typography.bodySmall
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            RulesUpdateState.Idle -> Unit
            RulesUpdateState.Checking, RulesUpdateState.Downloading, RulesUpdateState.Importing -> {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.primary)
                Text(stringResource(R.string.settings_rules_checking), style = small, color = colors.onSurfaceVariant)
            }
            RulesUpdateState.UpToDate ->
                Text(stringResource(R.string.settings_rules_up_to_date), style = small, color = colors.primary)
            is RulesUpdateState.Updated -> {
                val changed = state.changed.joinToString(", ") {
                    "${it.title} (${it.effectiveDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))})"
                }
                Text(
                    if (changed.isEmpty()) stringResource(R.string.settings_rules_updated_fixes)
                    else stringResource(R.string.settings_rules_updated, changed),
                    style = small, color = colors.primary,
                )
            }
            RulesUpdateState.NeedsAppUpdate ->
                Text(stringResource(R.string.settings_rules_needs_app_update), style = small, color = colors.error)
            is RulesUpdateState.Failed -> {
                Text(stringResource(R.string.settings_rules_failed), style = small, color = colors.error)
                Text(state.reason, style = small, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UpdateStatus(state: CardUpdateState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val small = MaterialTheme.typography.bodySmall
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            CardUpdateState.Idle -> Unit
            CardUpdateState.Checking -> {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.primary)
                Text(stringResource(R.string.settings_checking), style = small, color = colors.onSurfaceVariant)
            }
            is CardUpdateState.Downloading -> {
                val total = state.totalBytes
                if (total != null && total > 0) {
                    LinearProgressIndicator(progress = { (state.bytes.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(), color = colors.primary)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.primary)
                }
                val amount = if (total != null) "${mb(state.bytes)} / ${mb(total)} MB" else "${mb(state.bytes)} MB"
                val label = if (state.part == CardUpdateState.Downloading.Part.CARDS) R.string.settings_downloading_cards
                else R.string.settings_downloading_rulings
                Text(stringResource(label, amount), style = small, color = colors.onSurfaceVariant)
            }
            CardUpdateState.Importing -> {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.primary)
                Text(stringResource(R.string.settings_importing), style = small, color = colors.onSurfaceVariant)
            }
            is CardUpdateState.UpToDate ->
                Text(stringResource(R.string.settings_up_to_date), style = small, color = colors.primary)
            is CardUpdateState.Updated ->
                Text(stringResource(R.string.settings_updated, NumberFormat.getIntegerInstance().format(state.cards),
                    formatDate(state.oracleUpdatedAt)), style = small, color = colors.primary)
            is CardUpdateState.Failed -> {
                Text(stringResource(R.string.settings_failed), style = small, color = colors.error)
                Text(state.reason, style = small, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ---------------------------------------------------------------- about

@Composable
private fun AboutSection() {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    SettingsSection(stringResource(R.string.settings_about)) {
        SettingsPanel {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.primary.copy(alpha = 0.1f))
                        .border(1.dp, colors.primary.softened(0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_launcher_foreground), null, tint = colors.primary, modifier = Modifier.size(56.dp))
                }
                Spacer(Modifier.width(16.dp))
                TitleAndSubtitle(stringResource(R.string.app_name), stringResource(R.string.settings_version, version))
            }
            PanelDivider()
            PanelRow {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_credits), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text(stringResource(R.string.settings_credits_text), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Text(stringResource(R.string.settings_scryfall_credit), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
        }
        // Wording required by the Wizards of the Coast Fan Content Policy. Kept legible on purpose:
        // no alpha (the Figma's #4D4637 on the old navy was ~2:1, and a 0.7 alpha undid the fix).
        Text(
            stringResource(R.string.settings_fan_content),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp),
        )
    }
}

// ---------------------------------------------------------------- building blocks

/** A section: its Label (like every group label in the app), then its content 16 dp apart. */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(title, Modifier.padding(start = 4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
    }
}

/** The settings panels are the app's Panel, on surfaceContainer. */
@Composable
private fun SettingsPanel(content: @Composable () -> Unit) {
    Panel(color = MaterialTheme.colorScheme.surfaceContainer) { content() }
}

@Composable
private fun PanelRow(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp)) { content() }
}

@Composable
private fun PanelDivider() = ListDivider()

@Composable
private fun TitleAndSubtitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- formatting

private fun formatDate(scryfallTimestamp: String): String =
    CardText.date(scryfallTimestamp)?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: scryfallTimestamp

private fun formatInstant(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

private fun mb(bytes: Long): String = String.format(Locale.getDefault(), "%.1f", bytes / 1_000_000.0)
