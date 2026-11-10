package es.alejandro.grandarbiter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import es.alejandro.grandarbiter.data.rules.bundle.Penalty

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

/**
 * IPG penalties as a severity scale, the same in every palette: No Penalty grey, Warning amber,
 * Game Loss orange, Match Loss red-orange, Disqualification solid red. Before, they borrowed theme
 * roles (secondary blue, tertiary gold, error) and changed with the palette. All ≥ 6.5:1.
 */
@Composable
@ReadOnlyComposable
fun penaltyColors(penalty: Penalty): StatusColors {
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    return when (penalty) {
        Penalty.NONE -> if (light) StatusColors(Color(0xFFE6E6E2), Color(0xFF3A3A36)) else StatusColors(Color(0xFF2E2E2E), Color(0xFFCFCFCF))
        Penalty.WARNING -> if (light) StatusColors(Color(0xFFFFE8A3), Color(0xFF4A3600)) else StatusColors(Color(0xFF4A3A00), Color(0xFFFFD970))
        Penalty.GAME_LOSS -> if (light) StatusColors(Color(0xFFFFD8C2), Color(0xFF5A2400)) else StatusColors(Color(0xFF5A2A00), Color(0xFFFFB68A))
        Penalty.MATCH_LOSS -> if (light) StatusColors(Color(0xFFFFD2CB), Color(0xFF6E1C14)) else StatusColors(Color(0xFF6E1C14), Color(0xFFFFB4A6))
        Penalty.DISQUALIFICATION -> StatusColors(Color(0xFFB3261E), Color(0xFFFFFFFF))
    }
}

/**
 * "Restricted" (one copy allowed). Amber, the same values as the IPG Warning: neutral containers
 * read as plain data and solid ink shouted louder than Banned. Sharing amber with Warning is
 * a known overlap, to settle in the planned review of status colors (IPG and ban lists).
 */
@Composable
@ReadOnlyComposable
fun restrictedColors(): StatusColors = penaltyColors(Penalty.WARNING)
