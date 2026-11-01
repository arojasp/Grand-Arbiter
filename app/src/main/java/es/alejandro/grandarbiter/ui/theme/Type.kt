package es.alejandro.grandarbiter.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val M3 = Typography()

/**
 * Material 3 sizes; weights decided here, once (design-decisions §9).
 * Bold 700: the top level (displayMedium, headlineMedium, titleLarge). Medium 500: titleMedium,
 * titleSmall and the labels (M3 defaults). Regular 400: body. Canvas: "Paso 9a · Escala tipográfica".
 * Only native Roboto weights (400/500/700): 600 isn't on every Android version since API 26.
 */
val Typography = Typography(
    displayMedium = M3.displayMedium.copy(fontWeight = FontWeight.Bold),
    headlineMedium = M3.headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = M3.titleLarge.copy(fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
