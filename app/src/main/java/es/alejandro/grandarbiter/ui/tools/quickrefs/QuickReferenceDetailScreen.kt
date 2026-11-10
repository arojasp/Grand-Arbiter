package es.alejandro.grandarbiter.ui.tools.quickrefs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.domain.model.quickrefs.QuickReferenceDetail
import es.alejandro.grandarbiter.domain.model.quickrefs.QuickReferenceRule
import es.alejandro.grandarbiter.domain.model.rules.PageRef
import es.alejandro.grandarbiter.domain.model.rules.PageSection
import es.alejandro.grandarbiter.ui.components.Label
import es.alejandro.grandarbiter.ui.components.ListDivider
import es.alejandro.grandarbiter.ui.components.Panel
import es.alejandro.grandarbiter.ui.reader.BlockView
import es.alejandro.grandarbiter.ui.reader.InlineMarkup
import es.alejandro.grandarbiter.ui.reader.RuleText
import es.alejandro.grandarbiter.ui.reader.displayNumber
import es.alejandro.grandarbiter.ui.reader.rememberCopyText
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/*
 * Reference detail: one card per saved item, the official text as installed — the rule first, then
 * every subrule as a row with its number in a column (deeper levels indented), then the judge's note.
 * Tap opens the reader there; long press copies that rule (the copy buttons were one per row: noise).
 * No text selection here: it would take the long press.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReferenceDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenPage: (PageRef) -> Unit,
    viewModel: QuickReferenceDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val onRefClick: (String) -> Unit = { sectionId -> scope.launch { viewModel.resolve(sectionId)?.let(onOpenPage) } }

    // Deleted from the editor while this screen is in the back stack: nothing to show.
    LaunchedEffect(state) { if (state is QuickReferenceDetailState.NotFound) onBack() }

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
                    val detail = (state as? QuickReferenceDetailState.Loaded)?.detail
                    Column {
                        Text(detail?.title.orEmpty(), style = MaterialTheme.typography.titleLarge,
                            color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        detail?.let {
                            Text(stringResource(it.documentId.titleRes()), style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    if (state is QuickReferenceDetailState.Loaded) {
                        IconButton(onClick = { onEdit(viewModel.referenceId) }) {
                            Icon(Icons.Outlined.Edit, stringResource(R.string.qr_edit), tint = colors.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { innerPadding ->
        when (val s = state) {
            QuickReferenceDetailState.Loading, QuickReferenceDetailState.NotFound ->
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is QuickReferenceDetailState.Loaded -> DetailContent(s.detail, onOpenPage, onRefClick, { onEdit(viewModel.referenceId) },
                Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun DetailContent(
    detail: QuickReferenceDetail,
    onOpenPage: (PageRef) -> Unit,
    onRefClick: (String) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier,
) {
    val copy = rememberCopyText()
    val onCopy: (PageSection) -> Unit = { section ->
        copy(RuleText.reference(section, detail.documentId), RuleText.of(section, detail.documentId))
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (detail.rules.isEmpty()) {
            item { EmptyReference(onEdit) }
        }
        // One card per saved item: 613.1 and 613.1a saved separately are two cards.
        itemsIndexed(detail.rules, key = { i, rule -> "$i:${rule.sectionId}" }) { _, rule ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rule.missing) {
                    MissingRule(rule, detail)
                } else {
                    val target = rule.target
                    RuleCard(rule.sections, detail.documentId, onRefClick, onCopy) { section ->
                        target?.let { onOpenPage(PageRef(it.pageId, section.id)) }
                    }
                }
                rule.note?.takeIf { it.isNotBlank() }?.let { NoteCard(it) }
            }
        }
    }
}

/** [sections] = the saved rule + its descendants, in reading order. */
@Composable
private fun RuleCard(
    sections: List<PageSection>,
    document: DocumentId,
    onRefClick: (String) -> Unit,
    onCopy: (PageSection) -> Unit,
    onOpen: (PageSection) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val root = sections.first()
    Panel {
        Column(
            Modifier.fillMaxWidth().copyableRule(root, document, onCopy, onOpen).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val number = displayNumber(root.number)
            val title = root.title?.let { InlineMarkup.plainText(it) }
            if (number != null || title != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    number?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = colors.primary) }
                    title?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = colors.onSurface) }
                }
            }
            root.blocks.forEach { BlockView(it, onRefClick) }
        }
        sections.drop(1).forEach { sub ->
            ListDivider()
            Row(
                Modifier.fillMaxWidth().copyableRule(sub, document, onCopy, onOpen)
                    .padding(start = 16.dp + 16.dp * (sub.depth - root.depth - 1).coerceAtLeast(0), end = 16.dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(displayNumber(sub.number).orEmpty(), style = MaterialTheme.typography.labelLarge,
                    color = colors.primary, modifier = Modifier.widthIn(min = 56.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sub.title?.let { Text(InlineMarkup.plainText(it), style = MaterialTheme.typography.titleSmall, color = colors.onSurface) }
                    sub.blocks.forEach { BlockView(it, onRefClick) }
                }
            }
        }
    }
}

/** Tap opens the rule in the reader; long press copies it (TalkBack: a named action). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.copyableRule(
    section: PageSection,
    document: DocumentId,
    onCopy: (PageSection) -> Unit,
    onOpen: (PageSection) -> Unit,
): Modifier = combinedClickable(
    onClickLabel = null,
    onLongClickLabel = stringResource(R.string.reader_copy, RuleText.reference(section, document)),
    onLongClick = { onCopy(section) },
    onClick = { onOpen(section) },
)

@Composable
private fun NoteCard(note: String) {
    val colors = MaterialTheme.colorScheme
    Panel(color = colors.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EditNote, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Label(stringResource(R.string.qr_your_note), color = colors.onSurface)
            }
            Text(note, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        }
    }
}

@Composable
private fun MissingRule(rule: QuickReferenceRule, detail: QuickReferenceDetail) {
    val colors = MaterialTheme.colorScheme
    Panel {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Outlined.WarningAmber, null, tint = colors.error, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.qr_rule_missing, rule.number, stringResource(detail.documentId.titleRes())),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyReference(onEdit: () -> Unit) {
    Panel(contentModifier = Modifier.clickable(onClick = onEdit)) {
        Text(stringResource(R.string.qr_detail_empty), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
    }
}
