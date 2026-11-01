package es.alejandro.grandarbiter.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val M3 = Typography()

/**
 * Material 3 sizes; weights decided here, once (design-decisions §9).
 * Phase 2, step 9b: the weights the app already used through `.copy(fontWeight = …)` everywhere
 * (title roles Bold). Step 9d sets the final weights from the canvas type scale.
 * Only native Roboto weights (400/500/700): 600 isn't on every Android version since API 26.
 */
val Typography = Typography(
    displayMedium = M3.displayMedium.copy(fontWeight = FontWeight.Bold),
    headlineMedium = M3.headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = M3.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = M3.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = M3.titleSmall.copy(fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
