package es.alejandro.grandarbiter.ui.tools.banned

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.model.cards.BanCategory
import es.alejandro.grandarbiter.domain.model.cards.BanListEntry
import es.alejandro.grandarbiter.domain.model.cards.Legality
import es.alejandro.grandarbiter.ui.cardsearch.CardText
import es.alejandro.grandarbiter.ui.components.Label
import es.alejandro.grandarbiter.ui.components.ListDivider
import es.alejandro.grandarbiter.ui.components.Tag
import es.alejandro.grandarbiter.ui.components.bannedTagColors
import es.alejandro.grandarbiter.ui.components.restrictedTagColors
import es.alejandro.grandarbiter.ui.components.groupedItem
import es.alejandro.grandarbiter.ui.reader.ReaderSearchField
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.text.style.TextAlign
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/*
 * Figma: "Banned & Restricted Lists" (node 19:1296). Built from the card data's legalities
 * (Scryfall), so it updates with Settings › Check for updates. Not shown: Canadian Highlander's
 * points list (Scryfall has no such data).
 * Layout (canvas "Lote F · Banned"): the formats are one grouped list, like the rules contents and
 * Release Notes; an open format's cards are indented rows inside it. Search matches: tinted row,
 * bold name in primary (no side stripe).
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BannedListsScreen(
    onBack: () -> Unit,
    onOpenCard: (oracleId: String) -> Unit,
    onNewList: () -> Unit,
    onEditList: (id: Long) -> Unit,
    viewModel: BannedListsViewModel = koinViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val views by viewModel.views.collectAsState()
    val custom by viewModel.customViews.collectAsState()
    val dataInfo by viewModel.dataInfo.collectAsState()
    // Formats the judge opened/closed by hand. Search matches open by themselves; a tap flips that.
    var toggled by rememberSaveable { mutableStateOf(setOf<String>()) }
    var categoriesToggled by rememberSaveable { mutableStateOf(setOf<String>()) }
    LaunchedEffect(query) { if (query.isNotBlank()) { toggled = emptySet(); categoriesToggled = emptySet() } }
    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back), tint = colors.primary)
                    }
                },
                title = {
                    Text(stringResource(R.string.banned_title), style = MaterialTheme.typography.titleLarge, color = colors.primary)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            ReaderSearchField(query, viewModel::onQueryChange, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                placeholder = stringResource(R.string.banned_search_placeholder))
            dataInfo?.let { info ->
                CardText.date(info.oracleUpdatedAt)?.let { date ->
                    Text(stringResource(R.string.banned_data_from, date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
                }
            }
            val list = views
            when {
                list == null -> Box(Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.Center) {
                    // Same as Card Search's: 32 dp from the edges, centered.
                    Column(Modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.cards_preparing), color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
                list.isEmpty() && custom.isEmpty() -> Text(stringResource(R.string.cards_no_results), color = colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp))
                // No spacedBy: each group is one grouped list (groupedItem), a gap would split it.
                else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp)) {
                    val official = list.filter { it.list.official }
                    val community = list.filter { !it.list.official }
                    val format: @Composable (FormatBanView, Modifier, Boolean) -> Unit = { view, modifier, divider ->
                        FormatItem(view, expanded = (view.list.format in toggled) != view.autoExpand,
                            categoriesExpanded = (view.list.format in categoriesToggled) != view.autoExpandCategories,
                            divider = divider, modifier = modifier,
                            onToggle = { toggled = toggled.toggle(view.list.format) },
                            onToggleCategories = { categoriesToggled = categoriesToggled.toggle(view.list.format) },
                            onOpenCard = onOpenCard)
                    }
                    itemsIndexed(official, key = { _, it -> it.list.format }) { i, view ->
                        format(view, Modifier.groupedItem(i, official.size), i > 0)
                    }
                    if (community.isNotEmpty()) {
                        item(key = "community") {
                            Label(stringResource(R.string.banned_community), Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp))
                        }
                        itemsIndexed(community, key = { _, it -> it.list.format }) { i, view ->
                            format(view, Modifier.groupedItem(i, community.size), i > 0)
                        }
                    }
                    // While searching, only the lists that match; "New list" only without a search.
                    val showNew = query.isBlank()
                    if (custom.isNotEmpty() || showNew) {
                        val rows = custom.size + if (showNew) 1 else 0
                        item(key = "custom") {
                            Label(stringResource(R.string.custom_lists), Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp))
                        }
                        itemsIndexed(custom, key = { _, it -> it.key }) { i, view ->
                            CustomListItem(view, expanded = (view.key in toggled) != view.autoExpand, divider = i > 0,
                                modifier = Modifier.groupedItem(i, rows),
                                onToggle = { toggled = toggled.toggle(view.key) },
                                onEdit = { view.list.id?.let(onEditList) },
                                onOpenCard = onOpenCard)
                        }
                        if (showNew) {
                            item(key = "custom:new") {
                                NewListRow(divider = custom.isNotEmpty(), modifier = Modifier.groupedItem(rows - 1, rows), onClick = onNewList)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Set<String>.toggle(item: String): Set<String> = if (item in this) this - item else this + item

/** Indent of the rows inside an open format (cards, categories), and their divider. */
private val RowIndent = 32.dp

@Composable
private fun IndentedDivider() = ListDivider(Modifier.padding(start = RowIndent - 12.dp))

@Composable
private fun FormatItem(
    view: FormatBanView,
    expanded: Boolean,
    categoriesExpanded: Boolean,
    divider: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit,
    onToggleCategories: () -> Unit,
    onOpenCard: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    val named = view.list.named
    val byCategory = view.list.byCategory
    val count = named.size   // the badge counts real bans; categories are summarized below them
    Column(modifier) {
        if (divider) ListDivider()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clickable(enabled = view.list.entries.isNotEmpty(), role = Role.Button, onClick = onToggle)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(CardText.formatName(view.list.format), style = MaterialTheme.typography.titleMedium,
                color = if (expanded) colors.primary else colors.onSurface, modifier = Modifier.weight(1f))
            if (expanded) Tag(count.toString(), container = colors.primary, content = colors.onPrimary) else Tag(count.toString())
            if (view.list.entries.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).rotate(rotation))
            }
        }
        AnimatedVisibility(expanded) {
            Column {
                named.forEach { entry ->
                    IndentedDivider()
                    EntryRow(entry, view.list.format, highlighted = entry.oracleId in view.matches) { onOpenCard(entry.oracleId) }
                }
                if (byCategory.isNotEmpty()) {
                    IndentedDivider()
                    CategoryBlock(byCategory, view, categoriesExpanded, onToggleCategories, onOpenCard)
                }
            }
        }
    }
}

/** A banned/restricted card. A search match gets a tinted row and a bold name in primary. */
@Composable
private fun EntryRow(entry: BanListEntry, format: String, highlighted: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (highlighted) Modifier.background(colors.primary.copy(alpha = 0.12f)) else Modifier)   // a fill, not content
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(start = RowIndent, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(entry.name,
            style = if (highlighted) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)   // search match
            else MaterialTheme.typography.bodyLarge,
            color = if (highlighted) colors.primary else colors.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        val (container, content) = if (entry.legality == Legality.RESTRICTED) restrictedTagColors() else bannedTagColors()
        Tag(stringResource(if (entry.legality == Legality.RESTRICTED) CardText.restrictedLabel(format) else R.string.legality_banned),
            container = container, content = content)
    }
}

/**
 * Ante, Conspiracy and Un-cards, folded: Wizards lists them as categories, not by name, and shown
 * one by one they bury the real bans (Vintage: 10 named bans vs 91 category cards).
 */
@Composable
private fun CategoryBlock(
    entries: List<BanListEntry>,
    view: FormatBanView,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenCard: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "categories")
    val groups = entries.groupBy { it.category }
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle)
                .padding(start = RowIndent, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(pluralStringResource(R.plurals.banned_categories, entries.size, entries.size),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Text(BanCategory.entries.filter { it in groups }.map { categoryLabel(it) }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(rotation))
        }
        AnimatedVisibility(expanded) {
            Column {
                BanCategory.entries.filter { it in groups }.forEach { category ->
                    Label(categoryLabel(category), Modifier.padding(start = RowIndent, top = 12.dp, bottom = 4.dp))
                    groups.getValue(category).forEachIndexed { i, entry ->
                        if (i > 0) IndentedDivider()
                        EntryRow(entry, view.list.format, highlighted = entry.oracleId in view.matches) { onOpenCard(entry.oracleId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun categoryLabel(category: BanCategory): String = stringResource(
    when (category) {
        BanCategory.UN_CARD -> R.string.banned_category_un
        BanCategory.CONSPIRACY -> R.string.banned_category_conspiracy
        BanCategory.ANTE -> R.string.banned_category_ante
        BanCategory.NONE -> R.string.banned_title
    }
)
