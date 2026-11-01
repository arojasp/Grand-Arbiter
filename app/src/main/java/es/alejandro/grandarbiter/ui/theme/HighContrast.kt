package es.alejandro.grandarbiter.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The user's "High contrast mode" setting, provided by [GrandArbiterTheme]. */
val LocalHighContrast = staticCompositionLocalOf { false }

/**
 * This color at [alpha], except in high contrast, where it stays at full strength.
 * For borders, dividers and dimmed states. Not for ordinary text: secondary text uses
 * `onSurfaceVariant` as is (with alpha it falls below WCAG AA).
 */
@Composable
@ReadOnlyComposable
fun Color.softened(alpha: Float): Color = if (LocalHighContrast.current) this else copy(alpha = alpha)

/** For containers drawn without a border (Card): a visible edge in high contrast, none otherwise. */
@Composable
@ReadOnlyComposable
fun highContrastBorder(): BorderStroke? =
    if (LocalHighContrast.current) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
