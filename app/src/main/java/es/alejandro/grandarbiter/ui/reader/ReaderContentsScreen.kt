package es.alejandro.grandarbiter.ui.reader

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.rules.bundle.GlossaryEntry
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.data.rules.bundle.Penalty
import es.alejandro.grandarbiter.domain.model.rules.PageRef
import es.alejandro.grandarbiter.domain.model.rules.RulesSearchResult
import es.alejandro.grandarbiter.domain.model.rules.TocEntry
import org.koin.androidx.compose.koinViewModel
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import es.alejandro.grandarbiter.ui.components.ListDivider
import es.alejandro.grandarbiter.ui.components.Panel
import es.alejandro.grandarbiter.ui.components.Section
import es.alejandro.grandarbiter.ui.components.groupedItem

@Composable
fun ReaderContentsScreen(
    onBack: () -> Unit,
    onOpenPage: (PageRef) -> Unit,
    onOpenGlossary: (term: String?) -> Unit,
    viewModel: ReaderContentsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val glossaryHits by viewModel.glossaryHits.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ReaderTopBar(
                title = state.document?.title ?: viewModel.documentId.key.uppercase(),
                subtitle = state.document?.let { effectiveLabel(it.effectiveDate) },
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            ReaderSearchField(query, viewModel::onQueryChange, Modifier.padding(vertical = 12.dp))
            when {
                query.isNotBlank() -> SearchResults(results, glossaryHits, onOpenPage, onOpenGlossary)
                state.isLoading -> Centered { CircularProgressIndicator() }
                state.contents.isEmpty() -> Centered {
                    Text(stringResource(R.string.reader_not_installed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn {
                    // One grouped list (design-decisions §3); the contents are short enough for a Panel.
                    item(key = "contents") {
                        Panel(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.animateContentSize()) {
                                state.contents.forEachIndexed { i, entry ->
                                    if (i > 0) ListDivider()
                                    TocGroup(
                                        entry = entry,
                                        expanded = entry.sectionId in expanded,
                                        onToggle = {
                                            expanded = if (entry.sectionId in expanded) expanded - entry.sectionId else expanded + entry.sectionId
                                        },
                                        onOpenPage = onOpenPage,
                                    )
                                }
                                // The CR ends with its glossary, as the document does.
                                if (viewModel.documentId == DocumentId.CR) {
                                    ListDivider()
                                    TocTopRow(number = null, title = stringResource(R.string.reader_glossary), expanded = null) { onOpenGlossary(null) }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TocGroup(entry: TocEntry, expanded: Boolean, onToggle: () -> Unit, onOpenPage: (PageRef) -> Unit) {
    val hasChildren = entry.children.isNotEmpty()
    TocTopRow(
        number = entry.number,
        title = entry.title?.let { InlineMarkup.plainText(it) }.orEmpty(),
        expanded = if (hasChildren) expanded else null,
    ) { if (hasChildren) onToggle() else onOpenPage(PageRef(entry.sectionId)) }
    if (hasChildren && expanded) {
        Column(Modifier.padding(bottom = 8.dp)) {
            if (entry.opensPage) {
                TocChildRow(number = null, title = stringResource(R.string.reader_overview), penalty = null) { onOpenPage(PageRef(entry.sectionId)) }
            }
            entry.children.forEach { child ->
                TocChildRow(
                    number = child.number,
                    // Under its chapter, an IPG infraction's category repeats the chapter title.
                    title = child.title?.let { withoutCategory(InlineMarkup.plainText(it)) }.orEmpty(),
                    penalty = child.penalty,
                ) { onOpenPage(PageRef(child.sectionId)) }
            }
        }
    }
}

/** A chapter row. [expanded] null: it opens a page (chevron); true/false: it expands its sections. */
@Composable
private fun TocTopRow(number: String?, title: String, expanded: Boolean?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionNumber(number)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(if (expanded == true) R.drawable.ic_expand_more else R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A section inside an expanded chapter: indented by the chapter's number column. */
@Composable
private fun TocChildRow(number: String?, title: String, penalty: Penalty?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.width(SECTION_NUMBER_WIDTH))
        SectionNumber(number)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (penalty != null) PenaltyBadge(penalty, compact = true)
    }
}

/** Section number column: rule numbers are gold (design-decisions §9); empty keeps titles aligned. */
@Composable
private fun SectionNumber(number: String?) {
    Text(
        text = displayNumber(number).orEmpty(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.widthIn(min = SECTION_NUMBER_WIDTH),
    )
}

private val SECTION_NUMBER_WIDTH = 32.dp

@Composable
private fun SearchResults(
    results: List<RulesSearchResult>,
    glossaryHits: List<GlossaryEntry>,
    onOpenPage: (PageRef) -> Unit,
    onOpenGlossary: (String) -> Unit,
) {
    if (results.isEmpty() && glossaryHits.isEmpty()) {
        Centered { Text(stringResource(R.string.reader_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    LazyColumn {
        if (glossaryHits.isNotEmpty()) {
            item(key = "glossary-hits") {
                Section(stringResource(R.string.reader_glossary)) {
                    Panel(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
                        glossaryHits.forEachIndexed { i, entry ->
                            if (i > 0) ListDivider()
                            Text(
                                text = entry.term,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth().clickable { onOpenGlossary(entry.term) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
            item(key = "glossary-gap") { Spacer(Modifier.height(16.dp)) }
        }
        // Up to 100 results: one grouped list, but every row stays a lazy item.
        itemsIndexed(results, key = { _, it -> it.target.anchorId ?: it.target.pageId }) { i, result ->
            Column(Modifier.groupedItem(i, results.size).clickable { onOpenPage(result.target) }) {
                if (i > 0) ListDivider()
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SectionNumber(result.number)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Search keeps the full title: no chapter around it gives the category.
                        result.title?.let {
                            Text(
                                text = InlineMarkup.plainText(it),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = highlightedSnippet(result.snippet),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.Center) { content() }
}
