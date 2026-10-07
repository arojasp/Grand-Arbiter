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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.rules.bundle.Penalty
import es.alejandro.grandarbiter.domain.model.rules.PageRef
import es.alejandro.grandarbiter.domain.model.rules.RulesSearchResult
import es.alejandro.grandarbiter.domain.model.rules.TocEntry
import org.koin.androidx.compose.koinViewModel

@Composable
fun ReaderContentsScreen(
    onBack: () -> Unit,
    onOpenPage: (PageRef) -> Unit,
    viewModel: ReaderContentsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
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
                query.isNotBlank() -> SearchResults(results, onOpenPage)
                state.isLoading -> Centered { CircularProgressIndicator() }
                state.contents.isEmpty() -> Centered {
                    Text(stringResource(R.string.reader_not_installed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.contents, key = { it.sectionId }) { entry ->
                        TocGroup(
                            entry = entry,
                            expanded = entry.sectionId in expanded,
                            onToggle = {
                                expanded = if (entry.sectionId in expanded) expanded - entry.sectionId else expanded + entry.sectionId
                            },
                            onOpenPage = onOpenPage,
                        )
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
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (hasChildren) onToggle() else onOpenPage(PageRef(entry.sectionId)) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = sectionLabel(entry.number, entry.title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(if (hasChildren && expanded) R.drawable.ic_expand_more else R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (hasChildren && expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                if (entry.opensPage) {
                    TocRow(stringResource(R.string.reader_overview), penalty = null) { onOpenPage(PageRef(entry.sectionId)) }
                }
                entry.children.forEach { child ->
                    TocRow(sectionLabel(child.number, child.title), child.penalty) { onOpenPage(PageRef(child.sectionId)) }
                }
            }
        }
    }
}

@Composable
private fun TocRow(label: String, penalty: Penalty?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (penalty != null) PenaltyBadge(penalty, compact = true)
    }
}

@Composable
private fun SearchResults(results: List<RulesSearchResult>, onOpenPage: (PageRef) -> Unit) {
    if (results.isEmpty()) {
        Centered { Text(stringResource(R.string.reader_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(results, key = { it.target.anchorId ?: it.target.pageId }) { result ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenPage(result.target) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = sectionLabel(result.number, result.title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = highlightedSnippet(result.snippet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
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
