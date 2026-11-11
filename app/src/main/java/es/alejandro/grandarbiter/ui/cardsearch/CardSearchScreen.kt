package es.alejandro.grandarbiter.ui.cardsearch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.model.cards.CardSearchResult
import es.alejandro.grandarbiter.domain.model.cards.CardSummary
import es.alejandro.grandarbiter.ui.components.Tag
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import es.alejandro.grandarbiter.ui.components.Panel
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.koin.androidx.compose.koinViewModel

/** Figma: "Oracle Search - Results State" (node 19:820). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardSearchScreen(
    onOpenCard: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdvanced: (String) -> Unit,
    queryFromForm: String?,
    onQueryFromFormUsed: () -> Unit,
    viewModel: CardSearchViewModel = koinViewModel(),
) {
    val query by viewModel.query.collectAsState()
    // The Advanced search form's "Search": its query replaces the field's.
    LaunchedEffect(queryFromForm) {
        if (queryFromForm != null) {
            viewModel.onQueryChange(queryFromForm)
            onQueryFromFormUsed()
        }
    }
    val dataState by viewModel.dataState.collectAsState()
    val results by viewModel.results.collectAsState()
    val autoOnline by viewModel.autoOnline.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.cards_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.cards_settings),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            // Nothing to search until the first import is done (an update keeps the old data: Ready).
            SearchHeader(query, viewModel::onQueryChange, viewModel::retryOnline, { onOpenAdvanced(query) },
                enabled = dataState is CardDataState.Ready, autoOnline = autoOnline)
            when {
                dataState is CardDataState.Checking -> Unit
                dataState is CardDataState.Preparing -> Centered {
                    val soFar = (dataState as CardDataState.Preparing).cardsSoFar
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 32.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.cards_preparing), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                        if (soFar != null) {
                            Text(stringResource(R.string.cards_preparing_count, java.text.NumberFormat.getIntegerInstance().format(soFar)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                query.isBlank() -> (dataState as? CardDataState.Ready)?.let { IdleState(it) }
                else -> when (val s = results) {
                    SearchState.Pending -> Unit
                    SearchState.SearchingOnline -> SearchingOnline()
                    is SearchState.AskToSearchOnline -> InfoPanel(
                        stringResource(R.string.cards_ask_online_title),
                        stringResource(R.string.cards_ask_online_body, s.parts.joinToString(" ")),
                        stringResource(R.string.cards_search_on_scryfall), viewModel::retryOnline,
                    )
                    is SearchState.Shown -> when (val r = s.result) {
                        is CardSearchResult.Offline -> Results(r.cards, onOpenCard) { ResultsMeta(offlineLabel(r.cards.size)) }
                        is CardSearchResult.NeedsOnline -> WaitingToClose()   // quotes/parentheses still open
                        is CardSearchResult.Online -> OnlineResults(r, onOpenCard)
                        is CardSearchResult.OnlineFailed -> OnlineFailed(r, viewModel::retryOnline)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- search header

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenAdvanced: () -> Unit,
    enabled: Boolean,
    autoOnline: Boolean,
) {
    val border = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .drawBehind { drawLine(border, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { CardSearchField(query, onQueryChange, onSearch, enabled) }
            // Icon only (Alejandro's call; canvas Busqueda2_inicio). 48 dp, tonal, with its name for TalkBack.
            FilledTonalIconButton(onClick = onOpenAdvanced, enabled = enabled, modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.cards_adv_open))
            }
        }
        // Shown while there's nothing typed. With automatic search on, it also says which searches go to Scryfall.
        if (query.isEmpty() && enabled) {
            Text(stringResource(if (autoOnline) R.string.cards_syntax_hint_online else R.string.cards_syntax_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 48dp field (a Material TextField is 56dp min): Figma "Search Bar". */
@Composable
private fun CardSearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    // Disabled = M3's dimmed content (the "disabled controls" exception); it also doesn't respond.
    val content = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f)
    val secondary = if (enabled) colors.onSurfaceVariant else colors.onSurface.copy(alpha = 0.38f)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = content),   // typed text: body, Regular
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // Results come by themselves; after a failed online search the key tries again.
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSearch() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .background(colors.surfaceContainerHighest, RoundedCornerShape(8.dp))
                    .padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_search), null, tint = secondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.cards_search_placeholder),
                            style = MaterialTheme.typography.bodyLarge, color = secondary, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    field()
                }
                if (query.isNotEmpty() && enabled) {
                    Icon(
                        Icons.Outlined.Cancel,
                        contentDescription = stringResource(R.string.reader_clear_search),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).clickable { onQueryChange("") },
                    )
                }
            }
        },
    )
}

// ---------------------------------------------------------------- results

@Composable
private fun Results(
    results: List<CardSummary>,
    onOpenCard: (String) -> Unit,
    footer: (@Composable () -> Unit)? = null,
    header: @Composable () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { header() }
        if (results.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.cards_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(results, key = { it.oracleId }) { card -> ResultRow(card) { onOpenCard(card.oracleId) } }
            footer?.let { item { it() } }
            item { EndOfRecords(stringResource(R.string.cards_end), Modifier.padding(top = 32.dp, bottom = 24.dp)) }
        }
    }
}

@Composable
private fun offlineLabel(count: Int): String =
    if (count >= CardSearchViewModel.LIMIT) stringResource(R.string.cards_found_many, CardSearchViewModel.LIMIT)
    else pluralStringResource(R.plurals.cards_found, count, count)

/** "4 cards found" / "23 cards on Scryfall · online": a plain caption, as the string reads. */
@Composable
private fun ResultsMeta(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,   // was onSurface at 0.6 alpha (no alpha on text)
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

// ---------------------------------------------------------------- online (canvas Busqueda2_online)

/** A block under the search header: title, text, an optional action. */
@Composable
private fun InfoPanel(title: String, body: String?, action: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.padding(16.dp)) {
        Panel(contentModifier = Modifier.padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (action != null) {
                    OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(action, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** An online query with quotes or parentheses still open: nothing is sent until they close. */
@Composable
private fun WaitingToClose() {
    Text(stringResource(R.string.cards_online_waiting), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(16.dp))
}

@Composable
private fun SearchingOnline() {
    Row(
        Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(stringResource(R.string.cards_searching_online), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Same rows as offline; the label says where they came from, and what Scryfall ignored. */
@Composable
private fun OnlineResults(r: CardSearchResult.Online, onOpenCard: (String) -> Unit) {
    val footer: (@Composable () -> Unit)? = if (r.cards.size < r.total) {
        {
            Text(stringResource(R.string.cards_online_partial, r.cards.size, r.total),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp))
        }
    } else null
    Results(r.cards, onOpenCard, footer) {
        Column {
            if (r.warnings.isNotEmpty()) {
                // A half-read search can look right: say what Scryfall left out.
                InfoPanel(stringResource(R.string.cards_online_warnings), r.warnings.joinToString("\n"))
            }
            ResultsMeta(pluralStringResource(R.plurals.cards_found_online, r.total, r.total))
        }
    }
}

@Composable
private fun OnlineFailed(r: CardSearchResult.OnlineFailed, onRetry: () -> Unit) {
    val retry = stringResource(R.string.cards_try_again)
    when (r.reason) {
        CardSearchResult.OnlineFailed.Reason.NO_CONNECTION -> InfoPanel(
            stringResource(R.string.cards_needs_connection),
            stringResource(R.string.cards_needs_connection_body, r.parts.joinToString(" ")),
            retry, onRetry,
        )
        CardSearchResult.OnlineFailed.Reason.UNAVAILABLE -> InfoPanel(
            stringResource(R.string.cards_online_unavailable), stringResource(R.string.cards_online_unavailable_body), retry, onRetry,
        )
        // Retrying the same text gets the same answer: the judge has to change the search.
        CardSearchResult.OnlineFailed.Reason.REJECTED -> InfoPanel(
            stringResource(R.string.cards_online_rejected), r.details.ifBlank { null },
        )
    }
}

@Composable
private fun ResultRow(card: CardSummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // Not in the Figma: tokens share names with cards (Adorned Pouncer), so mark them.
                if (card.isToken) Tag(stringResource(R.string.cards_token))
            }
            // The cost tells similar names apart at a glance (judge feedback). Already in the
            // search query, so it costs nothing; lands and tokens have none.
            card.manaCost?.takeIf { it.isNotBlank() }?.let { ManaCostSymbols(it, size = 16.dp) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ---------------------------------------------------------------- idle / end

/** Before typing: the decorative element with what's installed. */
@Composable
private fun IdleState(ready: CardDataState.Ready) {
    val count = NumberFormat.getIntegerInstance().format(ready.info.cardCount)
    val date = CardText.date(ready.info.oracleUpdatedAt)?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val text = listOfNotNull(stringResource(R.string.cards_count, count), date?.let { stringResource(R.string.cards_data_from, it) })
        .joinToString("\n")
    EndOfRecords(text, Modifier.padding(top = 48.dp))
}

/**
 * Open book + caption, under the results and on the empty state. The book is decoration, in the
 * faint outlineVariant; the caption is information (card count, data date) and stays readable.
 */
@Composable
private fun EndOfRecords(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(56.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ---------------------------------------------------------------- shared bits

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.Center) { content() }
}
