package es.alejandro.grandarbiter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Building blocks of the Figma design (card detail, settings…): uppercase section labels,
 * bordered panels and small tags. Colors come from the theme.
 */

/** Figma section label: 11sp, medium, 1.1sp tracking, uppercase. */
val LabelStyle = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.1.sp)

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

/** Bordered surface: the Figma blocks (1dp outlineVariant border). */
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
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 15.sp),
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** 1dp outline in the design's border color. */
fun Modifier.outlined(shape: Shape, color: Color): Modifier = border(1.dp, color, shape)
