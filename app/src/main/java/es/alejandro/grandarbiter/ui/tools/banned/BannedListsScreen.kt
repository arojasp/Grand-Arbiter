package es.alejandro.grandarbiter.ui.tools.banned

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.model.cards.BanCategory
import es.alejandro.grandarbiter.domain.model.cards.BanListEntry
import es.alejandro.grandarbiter.domain.model.cards.Legality
import es.alejandro.grandarbiter.ui.cardsearch.CardText
import es.alejandro.grandarbiter.ui.theme.GoldDeep
import es.alejandro.grandarbiter.ui.theme.OnGoldDeep
import org.koin.androidx.compose.koinViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import es.alejandro.grandarbiter.ui.theme.softened

/*
 * Figma: "Banned & Restricted Lists" (node 19:1296). Built from the card data's legalities
 * (Scryfall), so it updates with Settings › Check for updates. Not shown: Canadian Highlander's
 * points list (Scryfall has no such data). The Figma's highlighted row is used for search matches.
 */

private val Body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BannedListsScreen(
    onBack: () -> Unit,
    onOpenCard: (oracleId: String) -> Unit,
    viewModel: BannedListsViewModel = koinViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val views by viewModel.views.collectAsState()
    val dataInfo by viewModel.dataInfo.collectAsState()
    // Formats the judge opened/closed by hand. Search matches open by themselves; a tap flips that.
    var toggled by rememberSaveable { mutableStateOf(setOf<String>()) }
    var categoriesToggled by rememberSaveable { mutableStateOf(setOf<String>()) }
    LaunchedEffect(query) { if (query.isNotBlank()) { toggled = emptySet(); categoriesToggled = emptySet() } }

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
                    Text(stringResource(R.string.banned_title),
                        style = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.55).sp),
                        color = MaterialTheme.colorScheme.primary)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(query, viewModel::onQueryChange)
                dataInfo?.let { info ->
                    CardText.date(info.oracleUpdatedAt)?.let { date ->
                        Text(stringResource(R.string.banned_data_from, date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            val list = views
            when {
                list == null -> Box(Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.cards_preparing), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                list.isEmpty() -> Text(stringResource(R.string.cards_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp))
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val official = list.filter { it.list.official }
                    val community = list.filter { !it.list.official }
                    items(official, key = { it.list.format }) { view ->
                        FormatItem(view, expanded = (view.list.format in toggled) != view.autoExpand,
                            categoriesExpanded = (view.list.format in categoriesToggled) != view.autoExpandCategories,
                            onToggle = { toggled = toggled.toggle(view.list.format) },
                            onToggleCategories = { categoriesToggled = categoriesToggled.toggle(view.list.format) },
                            onOpenCard = onOpenCard)
                    }
                    if (community.isNotEmpty()) {
                        item(key = "community") { CommunityHeader() }
                        items(community, key = { it.list.format }) { view ->
                            FormatItem(view, expanded = (view.list.format in toggled) != view.autoExpand,
                                categoriesExpanded = (view.list.format in categoriesToggled) != view.autoExpandCategories,
                                onToggle = { toggled = toggled.toggle(view.list.format) },
                                onToggleCategories = { categoriesToggled = categoriesToggled.toggle(view.list.format) },
                                onOpenCard = onOpenCard)
                        }
                    }
                }
            }
        }
    }
}

private fun Set<String>.toggle(item: String): Set<String> = if (item in this) this - item else this + item

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = Body.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { field ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainer)
                    .padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_search), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.banned_search_placeholder), style = Body, color = colors.onSurfaceVariant)
                    }
                    field()
                }
                if (query.isNotEmpty()) {
                    Icon(Icons.Outlined.Cancel, stringResource(R.string.reader_clear_search), tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).clickable { onQueryChange("") })
                }
            }
        },
    )
}

@Composable
private fun FormatItem(
    view: FormatBanView,
    expanded: Boolean,
    categoriesExpanded: Boolean,
    onToggle: () -> Unit,
    onToggleCategories: () -> Unit,
    onOpenCard: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    val named = view.list.named
    val byCategory = view.list.byCategory
    val count = named.size   // the badge counts real bans; categories are summarized below them
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainerLow)
            .then(if (expanded) Modifier.border(1.dp, colors.primary.softened(0.2f), shape) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = view.list.entries.isNotEmpty(), role = Role.Button, onClick = onToggle).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(CardText.formatName(view.list.format),
                style = Body.copy(fontWeight = if (expanded) FontWeight.Bold else FontWeight.Normal),
                color = if (expanded) colors.primary else colors.onSurface, modifier = Modifier.weight(1f))
            if (view.list.entries.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).rotate(rotation))
                Spacer(Modifier.width(8.dp))
            }
            Box(
                Modifier.clip(CircleShape).background(if (expanded) GoldDeep else colors.surfaceContainerHighest)
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text(count.toString(), style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
                    color = if (expanded) OnGoldDeep else colors.onSurfaceVariant)
            }
        }
        AnimatedVisibility(expanded) {
            Column(
                Modifier.fillMaxWidth().background(colors.surfaceContainerLowest).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                named.forEach { entry ->
                    EntryRow(entry, view.list.format, highlighted = entry.oracleId in view.matches) { onOpenCard(entry.oracleId) }
                }
                if (byCategory.isNotEmpty()) {
                    CategoryBlock(byCategory, view, categoriesExpanded, onToggleCategories, onOpenCard)
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: BanListEntry, format: String, highlighted: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(4.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(if (highlighted) GoldDeep.copy(alpha = 0.1f) else colors.surfaceContainerLowest)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (highlighted) Box(Modifier.width(4.dp).fillMaxHeight().background(colors.primary))
        Row(
            Modifier.weight(1f).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(entry.name, style = Body.copy(fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium),
                color = if (highlighted) colors.primary else colors.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            val (bg, fg, label) = if (entry.legality == Legality.RESTRICTED) {
                Triple(GoldDeep, OnGoldDeep, CardText.restrictedLabel(format))
            } else {
                Triple(colors.errorContainer, colors.onErrorContainer, R.string.legality_banned)
            }
            Box(Modifier.clip(shape).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(stringResource(label).uppercase(),
                    style = TextStyle(fontSize = 10.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp), color = fg)
            }
        }
    }
}

@Composable
private fun CommunityHeader() {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.banned_community).uppercase(),
            style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.4.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.softened(0.3f))
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
            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable(role = Role.Button, onClick = onToggle).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(pluralStringResource(R.plurals.banned_categories, entries.size, entries.size), style = Body.copy(fontWeight = FontWeight.Medium),
                    color = colors.onSurfaceVariant)
                Text(BanCategory.entries.filter { it in groups }.map { categoryLabel(it) }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(rotation))
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BanCategory.entries.filter { it in groups }.forEach { category ->
                    Text(categoryLabel(category).uppercase(),
                        style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.1.sp),
                        color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp))
                    groups.getValue(category).forEach { entry ->
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
