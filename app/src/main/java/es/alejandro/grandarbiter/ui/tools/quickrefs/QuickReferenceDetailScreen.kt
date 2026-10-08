package es.alejandro.grandarbiter.ui.tools.quickrefs

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.model.quickrefs.QuickReferenceDetail
import es.alejandro.grandarbiter.domain.model.quickrefs.QuickReferenceRule
import es.alejandro.grandarbiter.domain.model.rules.PageRef
import es.alejandro.grandarbiter.domain.model.rules.PageSection
import es.alejandro.grandarbiter.ui.components.Label
import es.alejandro.grandarbiter.ui.components.Panel
import es.alejandro.grandarbiter.ui.components.Tag
import es.alejandro.grandarbiter.ui.components.outlined
import es.alejandro.grandarbiter.ui.reader.BlockView
import es.alejandro.grandarbiter.ui.reader.CopyRuleButton
import es.alejandro.grandarbiter.ui.reader.RuleText
import es.alejandro.grandarbiter.ui.reader.rememberCopyText
import es.alejandro.grandarbiter.ui.reader.InlineMarkup
import es.alejandro.grandarbiter.ui.reader.displayNumber
import es.alejandro.grandarbiter.ui.theme.GoldDeep
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/*
 * Figma: reference detail (19:1103). For each saved rule, the official text as installed:
 *  - the rule itself in a card with a gold edge;
 *  - its subrules as cards with the number tag; deeper levels (613.4a…) as rows inside them;
 *  - then the judge's note, if any.
 * Tapping any of it opens the reader there.
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
                    val title = (state as? QuickReferenceDetailState.Loaded)?.detail?.title.orEmpty()
                    Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DocumentChip(detail) }
        if (detail.rules.isEmpty()) {
            item { EmptyReference(onEdit) }
        }
        detail.rules.forEachIndexed { index, rule ->
            if (index > 0) item(key = "$index:gap") { Spacer(Modifier.height(8.dp)) }
            if (rule.missing) {
                item(key = "$index:missing") { MissingRule(rule, detail) }
            } else {
                val root = rule.sections.first()
                val target = rule.target
                item(key = "$index:lead") {
                    LeadCard(root, onRefClick, onCopy) { target?.let { onOpenPage(PageRef(it.pageId, root.id)) } }
                }
                // Children of the rule, each with its own descendants.
                val groups = groupChildren(rule.sections)
                items(groups, key = { "$index:${it.first().id}" }) { group ->
                    SubruleCard(group, onRefClick, onCopy) { section -> target?.let { onOpenPage(PageRef(it.pageId, section.id)) } }
                }
            }
            rule.note?.takeIf { it.isNotBlank() }?.let { note ->
                item(key = "$index:note") { NoteCard(note) }
            }
        }
    }
}

/** [sections] = rule + descendants; returns each direct child with its own descendants. */
internal fun groupChildren(sections: List<PageSection>): List<List<PageSection>> {
    if (sections.size < 2) return emptyList()
    val childDepth = sections.first().depth + 1
    val groups = mutableListOf<MutableList<PageSection>>()
    sections.drop(1).forEach { s ->
        if (s.depth <= childDepth || groups.isEmpty()) groups += mutableListOf(s) else groups.last() += s
    }
    return groups
}

@Composable
private fun DocumentChip(detail: QuickReferenceDetail) {
    val colors = MaterialTheme.colorScheme
    val numbers = detail.rules.map { it.number }
    val shown = if (numbers.size <= 3) numbers.joinToString(", ") else numbers.take(3).joinToString(", ") + "…"
    val text = listOf(stringResource(detail.documentId.titleRes()), shown).filter { it.isNotEmpty() }.joinToString(" · ")
    Surface(shape = RoundedCornerShape(8.dp), color = colors.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun LeadCard(section: PageSection, onRefClick: (String) -> Unit, onCopy: (PageSection) -> Unit, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).background(colors.surfaceContainerHigh)
            .outlined(shape, colors.outlineVariant).clickable(onClick = onClick)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(GoldDeep))
        SelectionContainer {
            Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HeadingWithCopy(section, prominent = true) { onCopy(section) }
                Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    section.blocks.forEach { BlockView(it, onRefClick) }
                }
            }
        }
    }
}

@Composable
private fun SubruleCard(group: List<PageSection>, onRefClick: (String) -> Unit, onCopy: (PageSection) -> Unit, onOpen: (PageSection) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val head = group.first()
    Panel { SelectionContainer { Column {
        Column(Modifier.fillMaxWidth().clickable { onOpen(head) }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HeadingWithCopy(head, prominent = false) { onCopy(head) }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                head.blocks.forEach { BlockView(it, onRefClick) }
            }
        }
        group.drop(1).forEachIndexed { i, sub ->
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant.copy(alpha = if (i == 0) 0.6f else 0.3f))
            Row(Modifier.fillMaxWidth().clickable { onOpen(sub) }.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
                Text(displayNumber(sub.number).orEmpty(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.primary, modifier = Modifier.widthIn(min = 48.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sub.title?.let { Text(InlineMarkup.plainText(it), style = MaterialTheme.typography.titleSmall, color = colors.onSurface) }
                    sub.blocks.forEach { BlockView(it, onRefClick) }
                }
                CopyRuleButton(displayNumber(sub.number).orEmpty(), { onCopy(sub) }, Modifier.offset(y = (-10).dp))
            }
        }
    } } }
}

@Composable
private fun HeadingWithCopy(section: PageSection, prominent: Boolean, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SectionHeading(section, prominent) }
        CopyRuleButton(displayNumber(section.number) ?: section.title.orEmpty(), onCopy)
    }
}

@Composable
private fun SectionHeading(section: PageSection, prominent: Boolean) {
    val number = displayNumber(section.number)
    val title = section.title?.let { InlineMarkup.plainText(it) }
    if (number == null && title == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        number?.let { Tag(it, content = if (prominent) GoldDeep else MaterialTheme.colorScheme.onSurfaceVariant) }
        if (number != null && title != null) Spacer(Modifier.width(8.dp))
        title?.let {
            Text(it, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun NoteCard(note: String) {
    val colors = MaterialTheme.colorScheme
    Panel(color = colors.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EditNote, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Label(stringResource(R.string.qr_your_note))
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
