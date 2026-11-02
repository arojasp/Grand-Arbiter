package es.alejandro.grandarbiter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.ui.theme.softened

/*
 * Building blocks of the Figma design (card detail, settings…): uppercase section labels,
 * bordered panels and small tags. Colors come from the theme.
 */

/** Section label: labelSmall (11 sp Medium) with wider tracking, uppercase. */
val LabelStyle: TextStyle
    @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp)

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = LabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** A section heading above its content. */
@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(title, Modifier.padding(start = 4.dp))
        content()
    }
}

/**
 * Bordered surface: the Figma blocks (1dp outlineVariant border). [contentModifier] styles the
 * inside (padding, click), not the panel itself: it isn't the usual `modifier`, on purpose.
 */
@Suppress("ModifierParameter")
@Composable
fun Panel(
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape: Shape = RoundedCornerShape(12.dp),
    contentModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color)
            .outlined(shape, MaterialTheme.colorScheme.outlineVariant)
            .then(contentModifier)
    ) { content() }
}

/** Small uppercase tag: TOKEN, SCRYFALL NOTE, RESERVED LIST… */
@Composable
fun Tag(
    text: String,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(shape = RoundedCornerShape(4.dp), color = container) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * Title above a group of blocks on a top-level screen ("Document Library", Tools' "In tournament").
 * titleLarge at 20 sp: one step under the screen title, above the rows' titleMedium/Large.
 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), modifier = modifier)
}

/** Divider between the rows of a grouped list (Panel or [groupedItem]): inset 12 dp, softened. */
@Composable
fun ListDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.softened(0.4f))
}

/**
 * One row of a grouped list inside a LazyColumn: looks like a [Panel] list (rounded outer corners,
 * 1 dp outlineVariant edge), but every row stays a lazy item. For long lists (search results);
 * short ones use a Panel. Rows after the first draw a [ListDivider] themselves.
 */
@Composable
fun Modifier.groupedItem(
    index: Int,
    count: Int,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    radius: Dp = 16.dp,
): Modifier {
    val first = index == 0
    val last = index == count - 1
    val shape = RoundedCornerShape(
        topStart = if (first) radius else 0.dp, topEnd = if (first) radius else 0.dp,
        bottomStart = if (last) radius else 0.dp, bottomEnd = if (last) radius else 0.dp,
    )
    val edge = MaterialTheme.colorScheme.outlineVariant
    return this
        .fillMaxWidth()
        .clip(shape)
        .background(color)
        .drawWithContent {
            drawContent()
            // The outline of a taller shape: the edges shared with the next/previous row fall outside the clip.
            val stroke = 1.dp.toPx()
            val extraTop = if (first) 0f else stroke * 2
            val extraBottom = if (last) 0f else stroke * 2
            val outlineSize = Size(size.width - stroke, size.height - stroke + extraTop + extraBottom)
            translate(stroke / 2, stroke / 2 - extraTop) {
                drawOutline(shape.createOutline(outlineSize, layoutDirection, this), edge, style = Stroke(stroke))
            }
        }
}

/** 1dp outline in the design's border color. */
fun Modifier.outlined(shape: Shape, color: Color): Modifier = border(1.dp, color, shape)
