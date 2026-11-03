package es.alejandro.grandarbiter.ui.cardsearch

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.model.cards.CardSummary
import es.alejandro.grandarbiter.ui.components.Tag
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
    viewModel: CardSearchViewModel = koinViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val inText by viewModel.inText.collectAsState()
    val dataState by viewModel.dataState.collectAsState()
    val results by viewModel.results.collectAsState()

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
            SearchHeader(query, viewModel::onQueryChange, inText)
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
                else -> Results(results, inText, viewModel::onInTextChange, onOpenCard)
            }
        }
    }
}

// ---------------------------------------------------------------- search header

@Composable
private fun SearchHeader(query: String, onQueryChange: (String) -> Unit, inText: Boolean) {
    val border = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .drawBehind { drawLine(border, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
    ) {
        CardSearchField(query, onQueryChange, inText)
    }
}

/** 48dp field (a Material TextField is 56dp min): Figma "Search Bar". */
@Composable
private fun CardSearchField(query: String, onQueryChange: (String) -> Unit, inText: Boolean) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),   // typed text: body, Regular
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
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
                Icon(painterResource(R.drawable.ic_search), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(if (inText) R.string.cards_search_text_placeholder else R.string.cards_search_placeholder),
                            style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    field()
                }
                if (query.isNotEmpty()) {
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
    inText: Boolean,
    onInTextChange: (Boolean) -> Unit,
    onOpenCard: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { ResultsMeta(results.size, inText, onInTextChange) }
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
            item { EndOfRecords(stringResource(R.string.cards_end), Modifier.padding(top = 32.dp, bottom = 24.dp)) }
        }
    }
}

/** "4 CARDS FOUND" + the filter menu (search rules text). */
@Composable
private fun ResultsMeta(count: Int, inText: Boolean, onInTextChange: (Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val label = if (count >= CardSearchViewModel.LIMIT) {
        stringResource(R.string.cards_found_many, CardSearchViewModel.LIMIT)
    } else {
        pluralStringResource(R.plurals.cards_found, count, count)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.55.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,   // was onSurface at 0.6 alpha (no alpha on text)
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(
                onClick = { menu = true },
                // A filled tonal circle when a filter is on, so it's never applied without the judge
                // noticing (it was gold; with a neutral palette a tint alone isn't enough).
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (inText) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                ),
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = stringResource(R.string.cards_filter),
                    tint = if (inText) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cards_search_in_text)) },
                    leadingIcon = { Checkbox(checked = inText, onCheckedChange = null) },
                    onClick = { onInTextChange(!inText); menu = false },
                )
            }
        }
    }
}

@Composable
private fun ResultRow(card: CardSummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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

/** Figma "Decorative Authority Element": open book + caption, 20% opacity. */
@Composable
private fun EndOfRecords(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().alpha(0.2f).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(56.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
            color = MaterialTheme.colorScheme.onSurface,
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
