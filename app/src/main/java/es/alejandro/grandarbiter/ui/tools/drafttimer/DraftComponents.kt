package es.alejandro.grandarbiter.ui.tools.drafttimer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.alejandro.grandarbiter.R
import java.util.Locale
import kotlin.math.ceil
import es.alejandro.grandarbiter.ui.theme.softened
import es.alejandro.grandarbiter.ui.components.LabelStyle

/* Shared pieces of the Draft Timer screens (Figma section "Draft Timer", node 206:2). */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DraftTopBar(title: String, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back), tint = colors.primary)
            }
        },
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.primary)
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
    )
}

/** Rounded surface card with a 1 dp outline, like the rest of the app. */
@Composable
internal fun DraftCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    background: Color = MaterialTheme.colorScheme.surfaceContainer,
    verticalSpacing: Int = 12,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(background).border(1.dp, borderColor, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing.dp),
        content = content,
    )
}

/** Small uppercase label: "FORMAT", "UP NEXT"... Same style as the app's section labels. */
@Composable
internal fun DraftLabel(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text.uppercase(), style = LabelStyle, color = color)
}

/** Pill: "PACK 1 OF 3", "COMING SOON". */
@Composable
internal fun DraftChip(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, outlined: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.clip(CircleShape)
            .background(if (outlined) colors.surfaceContainer else colors.surfaceContainerHighest)
            .border(1.dp, if (outlined) color.softened(0.6f) else colors.outlineVariant, CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Thin progress track, filled to [fraction] (0..1). */
@Composable
internal fun DraftProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(color))
    }
}

/** "0:24", "24:13": remaining time rounded UP, so it never shows 0:00 before the step ends. */
internal fun formatClock(ms: Long): String {
    val totalSeconds = ceil(ms.coerceAtLeast(0) / 1000.0).toLong()
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

/** Time spent: "0:47", "12:05", rounded DOWN (it counts up). */
internal fun formatElapsed(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

/** Durations that are whole minutes or seconds: "25 min", "40 s", else "2:30". */
internal fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return when {
        s >= 60 && s % 60 == 0L -> "${s / 60} min"
        s < 60 -> "$s s"
        else -> formatClock(ms)
    }
}
