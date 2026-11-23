package es.alejandro.grandarbiter.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.ui.theme.Motion

/**
 * Content that opens below a row. It is uncovered from the top down and fades in, so the first
 * line shows first; closing runs the other way, a little faster. Every expandable list in the app
 * uses this one, with [ExpandChevron] in its row.
 */
@Composable
fun ExpandableContent(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = expanded,
        modifier = modifier,
        enter = expandVertically(tween(Motion.EXPAND_MS, easing = Motion.Enter), expandFrom = Alignment.Top) +
            fadeIn(tween(Motion.EXPAND_MS, easing = Motion.Enter)),
        exit = shrinkVertically(tween(Motion.COLLAPSE_MS, easing = Motion.Exit), shrinkTowards = Alignment.Top) +
            fadeOut(tween(Motion.COLLAPSE_MS, easing = Motion.Exit)),
    ) {
        Column(content = content)
    }
}

/**
 * For lazy lists whose rows come and go (an open section's rows, a filtered list): rows fade and
 * move with the same timing as [ExpandableContent].
 */
fun LazyItemScope.animateListItem(modifier: Modifier = Modifier): Modifier = modifier.animateItem(
    fadeInSpec = tween(Motion.EXPAND_MS, easing = Motion.Enter),
    placementSpec = tween(Motion.EXPAND_MS, easing = Motion.Standard),
    fadeOutSpec = tween(Motion.COLLAPSE_MS, easing = Motion.Exit),
)

/**
 * The chevron of an expandable row: points to the end when closed and turns down when open, with
 * the same timing as [ExpandableContent]. Silent by default: the row's text says what it does.
 */
@Composable
fun ExpandChevron(expanded: Boolean, modifier: Modifier = Modifier, contentDescription: String? = null) {
    // The icon is mirrored in right-to-left layouts, so it turns the other way to point down.
    val down = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -90f else 90f
    val rotation by animateFloatAsState(
        targetValue = if (expanded) down else 0f,
        animationSpec = tween(Motion.EXPAND_MS, easing = Motion.Standard),
        label = "chevron",
    )
    Icon(
        painterResource(R.drawable.ic_chevron_right), contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(20.dp).rotate(rotation),
    )
}
