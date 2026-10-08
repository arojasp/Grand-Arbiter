package es.alejandro.grandarbiter.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.CompositionLocalProvider
import es.alejandro.grandarbiter.data.rules.bundle.GlossaryEntry
import es.alejandro.grandarbiter.ui.components.Label
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.rules.bundle.Block
import es.alejandro.grandarbiter.domain.model.rules.PageRef
import es.alejandro.grandarbiter.domain.model.rules.PageSection
import es.alejandro.grandarbiter.domain.model.rules.RulesPage
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun ReaderPageScreen(
    onBack: () -> Unit,
    onOpenPage: (PageRef) -> Unit,
    viewModel: ReaderPageViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val onRefClick: (String) -> Unit = { sectionId ->
        scope.launch { viewModel.resolve(sectionId)?.let(onOpenPage) }
    }
    val page = state.page
    val glossaryLinker by viewModel.glossary.collectAsState()
    var openTerm by remember { mutableStateOf<GlossaryEntry?>(null) }
    val glossaryLinks = remember(glossaryLinker) { glossaryLinker?.let { GlossaryLinks(it) { entry -> openTerm = entry } } }

    CompositionLocalProvider(LocalGlossary provides glossaryLinks) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ReaderTopBar(
                    title = page?.let { "${it.documentId.key.uppercase()} ${displayNumber(it.root.number).orEmpty()}".trim() }.orEmpty(),
                    subtitle = null,
                    onBack = onBack,
                )
            },
        ) { innerPadding ->
            when {
                state.isLoading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                page == null -> Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.reader_page_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> PageContent(page, state.anchorId, onRefClick, Modifier.padding(innerPadding))
            }
        }
        openTerm?.let { entry ->
            GlossarySheet(entry, onDismiss = { openTerm = null }, onRefClick = { sectionId -> openTerm = null; onRefClick(sectionId) })
        }
    }
}

/** A glossary definition over the page; its rule links navigate, its glossary terms switch the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlossarySheet(entry: GlossaryEntry, onDismiss: () -> Unit, onRefClick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        SelectionContainer {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Label(stringResource(R.string.reader_glossary))
                Text(entry.term, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface)
                MarkupText(entry.definition, onRefClick)
            }
        }
    }
}

/** What the page's list shows, in order. */
private sealed interface PageItem {
    val key: String

    data class Header(val section: PageSection) : PageItem { override val key = "header" }
    data class Part(val part: PagePart, val index: Int) : PageItem { override val key = "part-$index" }
    data class RootBlocks(val blocks: List<Block>) : PageItem { override val key = "root" }
    data class Section(val section: PageSection) : PageItem { override val key = section.id }
}

@Composable
private fun PageContent(page: RulesPage, anchorId: String?, onRefClick: (String) -> Unit, modifier: Modifier) {
    val items = remember(page) {
        buildList {
            add(PageItem.Header(page.root))
            val parts = groupIntoParts(page.root.blocks)
            if (parts.any { it.role != null }) {
                parts.forEachIndexed { i, part -> add(PageItem.Part(part, i)) }   // IPG infraction
            } else if (page.root.blocks.isNotEmpty()) {
                add(PageItem.RootBlocks(page.root.blocks))
            }
            page.sections.drop(1).forEach { add(PageItem.Section(it)) }
        }
    }
    val listState = rememberLazyListState()
    var scrolledToAnchor by rememberSaveable(anchorId) { mutableStateOf(false) }
    LaunchedEffect(items, anchorId) {
        if (anchorId != null && !scrolledToAnchor) {
            val index = items.indexOfFirst { it is PageItem.Section && it.section.id == anchorId }
            if (index >= 0) listState.scrollToItem(index)
            scrolledToAnchor = true
        }
    }
    val expanded = remember(page) { mutableStateMapOf<Int, Boolean>() }
    val copy = rememberCopyText()
    val penalty = page.root.infraction?.let { stringResource(penaltyLabel(it.penalty)) }
    val copySection: (PageSection) -> Unit = { section ->
        copy(RuleText.reference(section, page.documentId),
            RuleText.of(section, page.documentId, penalty.takeIf { section.id == page.root.id }))
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.key }) { item ->
            // Per item: Compose can't keep a selection across lazy items that scroll away.
            SelectionContainer { PageItemView(item, page, anchorId, expanded, onRefClick, copySection) }
        }
    }
}

@Composable
private fun PageItemView(
    item: PageItem,
    page: RulesPage,
    anchorId: String?,
    expanded: MutableMap<Int, Boolean>,
    onRefClick: (String) -> Unit,
    onCopy: (PageSection) -> Unit,
) {
    when (item) {
        is PageItem.Header -> PageHeader(item.section) { onCopy(page.root) }
        is PageItem.Part ->
            if (item.part.role == null) {
                BlockColumn(item.part.blocks, onRefClick)
            } else {
                val isExpanded = expanded[item.index] ?: item.part.expandedByDefault
                PartCard(item.part, isExpanded, onToggle = { expanded[item.index] = !isExpanded }, onRefClick = onRefClick)
            }
        is PageItem.RootBlocks -> BlockColumn(item.blocks, onRefClick)
        is PageItem.Section -> SectionRow(item.section, page, highlighted = item.section.id == anchorId, onRefClick = onRefClick) {
            onCopy(item.section)
        }
    }
}

@Composable
private fun PageHeader(section: PageSection, onCopy: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = sectionLabel(section.number, section.title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(top = 6.dp),
            )
            // The whole page root: an IPG infraction with its penalty, an MTR section…
            CopyRuleButton(sectionLabel(section.number, section.title), onCopy)
        }
        section.infraction?.let { PenaltyBadge(it.penalty) }
    }
}

@Composable
private fun BlockColumn(blocks: List<Block>, onRefClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { BlockView(it, onRefClick) }
    }
}

/** A rule/subrule (CR) or a nested titled section, indented by depth; the linked one is highlighted. */
@Composable
private fun SectionRow(section: PageSection, page: RulesPage, highlighted: Boolean, onRefClick: (String) -> Unit, onCopy: () -> Unit) {
    val reference = RuleText.reference(section, page.documentId)
    val indent = ((section.depth - 1).coerceAtLeast(0) * 16).dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent)
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .padding(if (highlighted) 8.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (section.title != null) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = sectionLabel(section.number, section.title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                )
                CopyRuleButton(reference, onCopy)
            }
            BlockColumn(section.blocks, onRefClick)
        } else {
            Row {
                Text(
                    text = section.number.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Column(modifier = Modifier.weight(1f)) { BlockColumn(section.blocks, onRefClick) }
                // Negative top offset: the 40dp touch target centred on the first text line.
                CopyRuleButton(reference, onCopy, Modifier.offset(y = (-10).dp))
            }
        }
    }
}
