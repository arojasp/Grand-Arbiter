package es.alejandro.grandarbiter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** A container and its content color, for a state that has no Material 3 role. */
data class StatusColors(val container: Color, val content: Color)

/**
 * "Legal" in a format (card legality). M3 has no "success" role, so it is a fixed semantic color,
 * checked for contrast in each theme (design-decisions §1): dark keeps the original look; light
 * uses dark green on pale green (the light green text was ~1.5:1 there).
 */
@Composable
@ReadOnlyComposable
fun legalColors(): StatusColors =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) StatusColors(Color(0xFFDCFCE7), Color(0xFF166534))
    else StatusColors(Color(0x6614532D), Color(0xFF86EFAC))   // Figma: rgba(20,83,45,0.4) + green-300
