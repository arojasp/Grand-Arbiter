package es.alejandro.grandarbiter.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.ui.theme.GrandArbiterTheme
import es.alejandro.grandarbiter.domain.model.ThemeMode
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import es.alejandro.grandarbiter.ui.theme.softened
import es.alejandro.grandarbiter.ui.components.Panel
import androidx.compose.material3.HorizontalDivider
import es.alejandro.grandarbiter.domain.updates.SectionUpdates
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToDocument: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel()
) {
    val elapsedTime by viewModel.elapsedTime.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val effectiveDates by viewModel.effectiveDates.collectAsState()
    val latestReleaseNotes by viewModel.latestReleaseNotes.collectAsState()
    val updatedSections by viewModel.updatedSections.collectAsState()

    HomeScreenContent(
        effectiveDates = effectiveDates,
        latestReleaseNotes = latestReleaseNotes,
        updatedSections = updatedSections,
        elapsedTime = elapsedTime,
        isRunning = isRunning,
        onToggleTimer = viewModel::toggleTimer,
        onResetTimer = viewModel::resetTimer,
        onNavigateToDocument = onNavigateToDocument,
        onOpenSettings = onOpenSettings,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    effectiveDates: Map<String, LocalDate>,
    elapsedTime: Long,
    isRunning: Boolean,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onNavigateToDocument: (String) -> Unit,
    modifier: Modifier = Modifier,
    latestReleaseNotes: String? = null,
    updatedSections: Set<String> = emptySet(),
    onOpenSettings: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        val documentItems = getDocumentLibraryItems(effectiveDates, latestReleaseNotes, updatedSections)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                JudgeCallTimerCard(
                    elapsedTime = elapsedTime,
                    isRunning = isRunning,
                    onToggleTimer = onToggleTimer,
                    onResetTimer = onResetTimer
                )
            }

            item {
                Text(
                    text = stringResource(R.string.home_document_library),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                DocumentLibraryList(items = documentItems, onOpen = onNavigateToDocument)
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Judge call timer: title, what it is for, then the time and its controls on one row. With a large
 * text size the controls wrap under the time instead of squeezing it (FlowRow).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JudgeCallTimerCard(
    elapsedTime: Long,
    isRunning: Boolean,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Panel(color = colors.surfaceContainer, shape = RoundedCornerShape(16.dp), contentModifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.home_judge_call),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            Text(
                text = stringResource(R.string.home_judge_call_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(elapsedTime),
                    // Tabular figures: the digits keep their width, so the time doesn't jiggle while counting.
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                    color = if (isRunning) colors.primary else colors.onSurface,   // running is a real state
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onResetTimer,
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.home_reset_timer))
                    }
                    Button(
                        onClick = onToggleTimer,
                        modifier = Modifier.height(48.dp).widthIn(min = 112.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) colors.secondary else colors.primary,
                            contentColor = if (isRunning) colors.onSecondary else colors.onPrimary
                        )
                    ) {
                        Icon(if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(if (isRunning) R.string.home_pause_timer else R.string.home_start_timer),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

/** The documents as one grouped list (Panel + dividers), like the other lists in the app. */
@Composable
fun DocumentLibraryList(items: List<DocumentLibraryItem>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Panel(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
        Column(modifier) {
            items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.softened(0.4f))
                DocumentRow(item, onClick = { onOpen(item.id) })
            }
        }
    }
}

/** One row: acronym (+ «Updated») and date on top, full name under it. The whole row is one button. */
@Composable
private fun DocumentRow(item: DocumentLibraryItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Judges say "IPG", not "Infraction Procedure Guide": the acronym is the title.
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (item.updated) UpdatedLabel()
                Spacer(Modifier.weight(1f))
                if (item.date.isNotEmpty()) {
                    Text(
                        text = item.date,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            item.name?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,   // the row's text names it
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** «Updated»: the section changed since the user last entered it (SectionVersions). */
@Composable
private fun UpdatedLabel() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Text(
            text = stringResource(R.string.home_updated),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** [label]: the acronym, or the name when there is no acronym in use; [name]: the line under it; [date]: effective date. */
data class DocumentLibraryItem(
    val id: String,
    val label: String,
    val name: String?,
    val date: String,
    val updated: Boolean = false,
)

@Composable
fun getDocumentLibraryItems(
    effectiveDates: Map<String, LocalDate>,
    latestReleaseNotes: String? = null,
    updatedSections: Set<String> = emptySet(),
): List<DocumentLibraryItem> {
    // Effective date, without the word: next to a rules document it reads as its version date.
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    fun date(id: String) = effectiveDates[id]?.format(format).orEmpty()
    return listOf(
        DocumentLibraryItem("cr", stringResource(R.string.home_badge_cr), stringResource(R.string.home_cr_title), date("cr")),
        DocumentLibraryItem("ipg", stringResource(R.string.home_badge_ipg), stringResource(R.string.home_ipg_title), date("ipg")),
        DocumentLibraryItem("mtr", stringResource(R.string.home_badge_mtr), stringResource(R.string.home_mtr_title), date("mtr")),
        DocumentLibraryItem("jar", stringResource(R.string.home_badge_jar), stringResource(R.string.home_jar_title), date("jar")),
        // No acronym in common use ("RN"): the name is the title; the newest set goes under it.
        DocumentLibraryItem(
            RELEASE_NOTES_ID,
            stringResource(R.string.tools_release_notes),
            latestReleaseNotes?.let { stringResource(R.string.home_release_notes_latest, it) },
            date = "",
        ),
    ).map { it.copy(updated = it.id in updatedSections) }
}

/** Not a rules document: Home routes it to the release notes list. */
const val RELEASE_NOTES_ID = SectionUpdates.RELEASE_NOTES

private fun formatTime(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    GrandArbiterTheme {
        HomeScreenContent(
            effectiveDates = emptyMap(),
            elapsedTime = 0L,
            isRunning = false,
            onToggleTimer = {},
            onResetTimer = {},
            onNavigateToDocument = {}
        )
    }
}

/** «Updated» on the CR, in dark and high contrast (the label only shows after a real update). */
@Preview(showBackground = true)
@Composable
fun HomeScreenUpdatedPreview() {
    GrandArbiterTheme(highContrast = true) {
        HomeScreenContent(
            effectiveDates = mapOf("cr" to LocalDate.of(2026, 9, 25), "ipg" to LocalDate.of(2024, 9, 23)),
            elapsedTime = 0L,
            isRunning = false,
            onToggleTimer = {},
            onResetTimer = {},
            onNavigateToDocument = {},
            latestReleaseNotes = "Star Trek",
            updatedSections = setOf("cr"),
        )
    }
}

/** Timer running, light theme. */
@Preview(showBackground = true)
@Composable
fun HomeScreenRunningLightPreview() {
    GrandArbiterTheme(themeMode = ThemeMode.LIGHT) {
        HomeScreenContent(
            effectiveDates = mapOf("cr" to LocalDate.of(2026, 9, 25)),
            elapsedTime = 157_000L,
            isRunning = true,
            onToggleTimer = {},
            onResetTimer = {},
            onNavigateToDocument = {},
            latestReleaseNotes = "Star Trek",
        )
    }
}
