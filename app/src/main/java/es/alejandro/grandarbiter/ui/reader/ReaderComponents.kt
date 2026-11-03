package es.alejandro.grandarbiter.ui.reader

import es.alejandro.grandarbiter.ui.theme.penaltyColors
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.data.rules.bundle.Block
import es.alejandro.grandarbiter.data.rules.bundle.Penalty
import es.alejandro.grandarbiter.domain.model.rules.SNIPPET_MATCH_END
import es.alejandro.grandarbiter.domain.model.rules.SNIPPET_MATCH_START
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import es.alejandro.grandarbiter.ui.theme.highContrastBorder

// ---------------------------------------------------------------- text

/** Rules text with bold/italics and tappable links. "ref:" links call [onRefClick]; web links open the browser. */
@Composable
fun MarkupText(
    markup: String,
    onRefClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val currentOnRefClick by rememberUpdatedState(onRefClick)
    val glossary = LocalGlossary.current
    val text = remember(markup, linkColor, glossary) {
        val linked = HashSet<String>()   // each glossary term once per text
        buildAnnotatedString {
            for (span in InlineMarkup.parse(markup)) {
                when (span) {
                    is InlineSpan.Text -> withStyle(
                        SpanStyle(
                            fontWeight = if (span.bold) FontWeight.Bold else null,
                            fontStyle = if (span.italic) FontStyle.Italic else null,
                        )
                    ) {
                        if (glossary == null) append(span.text)
                        else appendWithGlossary(span.text, glossary, linked, linkColor)
                    }

                    is InlineSpan.Link -> {
                        val styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                fontWeight = if (span.bold) FontWeight.Bold else FontWeight.Medium,   // native weights only (no SemiBold)
                                textDecoration = TextDecoration.Underline,
                            )
                        )
                        val sectionId = span.sectionId
                        val annotation = if (sectionId != null) {
                            LinkAnnotation.Clickable(tag = sectionId, styles = styles) { currentOnRefClick(sectionId) }
                        } else {
                            LinkAnnotation.Url(url = span.target, styles = styles)
                        }
                        withLink(annotation) { append(span.text) }
                    }
                }
            }
        }
    }
    Text(text = text, modifier = modifier, style = style.copy(lineHeight = 21.sp), color = color)
}

/**
 * Glossary terms as quiet links: Medium weight, not underlined (rule references are Medium +
 * underlined), so the two kinds of link read differently and both stand out from Regular body
 * text even when the link color is the text color (palette Tinta). Tapping one shows its definition.
 */
private fun AnnotatedString.Builder.appendWithGlossary(
    text: String,
    glossary: GlossaryLinks,
    linked: MutableSet<String>,
    color: Color,
) {
    var at = 0
    for (match in glossary.linker.find(text, linked)) {
        append(text.substring(at, match.start))
        val entry = match.entry
        withLink(LinkAnnotation.Clickable(tag = "glossary:${entry.term}", styles = TextLinkStyles(SpanStyle(color = color, fontWeight = FontWeight.Medium))) {
            glossary.onTermClick(entry)
        }) { append(text.substring(match.start, match.end)) }
        at = match.end
    }
    append(text.substring(at))
}

/** "100." for chapters/sections without a dot, "2.1" / "603.2a" as printed. */
fun displayNumber(number: String?): String? = number?.let { if ('.' in it) it else "$it." }

/**
 * IPG infractions are titled "Game Play Error — Missed Trigger". Under their chapter ("Game Play
 * Errors") the category repeats it, so the contents show "Missed Trigger". Titles without " — "
 * (every other document) are unchanged.
 */
fun withoutCategory(title: String): String = title.substringAfter(" — ")

fun sectionLabel(number: String?, title: String?): String =
    listOfNotNull(displayNumber(number), title?.let { InlineMarkup.plainText(it) }).joinToString(" ")

@Composable
fun effectiveLabel(date: LocalDate): String =
    stringResource(R.string.reader_effective, date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))

/** Search snippet with the matched words highlighted. */
@Composable
fun highlightedSnippet(snippet: String): AnnotatedString {
    val highlight = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    return remember(snippet, highlight) {
        buildAnnotatedString {
            var inMatch = false
            val buf = StringBuilder()
            fun flush() {
                if (buf.isEmpty()) return
                if (inMatch) withStyle(highlight) { append(buf.toString()) } else append(buf.toString())
                buf.clear()
            }
            for (c in snippet) {
                when (c) {
                    SNIPPET_MATCH_START -> { flush(); inMatch = true }
                    SNIPPET_MATCH_END -> { flush(); inMatch = false }
                    else -> buf.append(c)
                }
            }
            flush()
        }
    }
}

// ---------------------------------------------------------------- blocks

@Composable
fun BlockView(block: Block, onRefClick: (String) -> Unit, modifier: Modifier = Modifier) {
    when (block) {
        is Block.Paragraph -> MarkupText(block.text, onRefClick, modifier)
        is Block.Heading -> MarkupText(
            block.text, onRefClick, modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.titleSmall,
        )
        is Block.Example -> ExampleView(block, onRefClick, modifier)
        is Block.BulletList -> ListView(block, onRefClick, modifier)
        is Block.Table -> TableView(block, onRefClick, modifier)
    }
}

@Composable
private fun ExampleView(block: Block.Example, onRefClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = block.label?.let { stringResource(R.string.reader_example_labeled, it) }
                    ?: stringResource(R.string.reader_example),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MarkupText(
                block.text, onRefClick,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ListView(block: Block.BulletList, onRefClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        block.items.forEachIndexed { index, item ->
            Row {
                Text(
                    text = if (block.ordered) "${index + 1}." else "•",
                    modifier = Modifier.width(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                MarkupText(item, onRefClick, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TableView(block: Block.Table, onRefClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    // Up to 3 columns fit the screen; wider tables scroll sideways with fixed-width columns.
    val fixedWidth = if (block.header.size > 3) 150.dp else null
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(if (fixedWidth != null) Modifier.horizontalScroll(scroll) else Modifier)
    ) {
        TableRow(block.header, header = true, fixedWidth = fixedWidth, onRefClick = onRefClick)
        block.rows.forEach { TableRow(it, header = false, fixedWidth = fixedWidth, onRefClick = onRefClick) }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean, fixedWidth: androidx.compose.ui.unit.Dp?, onRefClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .then(if (fixedWidth == null) Modifier.fillMaxWidth() else Modifier)
            .height(IntrinsicSize.Min)
            .background(if (header) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
    ) {
        cells.forEach { cell ->
            Box(
                modifier = (if (fixedWidth == null) Modifier.weight(1f) else Modifier.width(fixedWidth))
                    .fillMaxHeight()
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .padding(8.dp)
            ) {
                MarkupText(
                    cell, onRefClick,
                    style = if (header) MaterialTheme.typography.labelLarge
                    else MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- IPG

@StringRes
fun penaltyLabel(penalty: Penalty): Int = when (penalty) {
    Penalty.NONE -> R.string.penalty_none
    Penalty.WARNING -> R.string.penalty_warning
    Penalty.GAME_LOSS -> R.string.penalty_game_loss
    Penalty.MATCH_LOSS -> R.string.penalty_match_loss
    Penalty.DISQUALIFICATION -> R.string.penalty_disqualification
}

@Composable
fun PenaltyBadge(penalty: Penalty, modifier: Modifier = Modifier, compact: Boolean = false) {
    val (background, content) = penaltyColors(penalty)   // severity scale, independent of the palette
    Surface(color = background, shape = RoundedCornerShape(4.dp), modifier = modifier) {
        Text(
            text = stringResource(penaltyLabel(penalty)).uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = if (compact) 1.dp else 4.dp),
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

@Composable
fun PartCard(
    part: PagePart,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRefClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        border = highContrastBorder(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val title = InlineMarkup.plainText(part.title.orEmpty())
            Text(
                text = if (part.exampleCount > 0) stringResource(R.string.reader_part_with_count, title, part.exampleCount) else title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                painter = painterResource(if (expanded) R.drawable.ic_expand_more else R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                part.blocks.forEach { BlockView(it, onRefClick) }
            }
        }
    }
}

// ---------------------------------------------------------------- chrome

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(title: String, subtitle: String?, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.reader_back),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun ReaderSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.reader_search_placeholder),
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(20.dp))
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.reader_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else null,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        singleLine = true,
    )
}
