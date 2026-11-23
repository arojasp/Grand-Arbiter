package es.alejandro.grandarbiter.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Durations and curves for every animation in the app, from the Material 3 motion tokens.
 * Short on purpose: the app is used in a hurry, so nothing should make the judge wait.
 */
object Motion {
    /** Fades: screen changes, a hold-to-confirm button letting go. M3 Short3. */
    const val FADE_MS = 150

    /** Content opening below a row. M3 Medium1. */
    const val EXPAND_MS = 250

    /** Content closing: a little faster than opening, as M3 does for exits. M3 Short4. */
    const val COLLAPSE_MS = 200

    /** Things arriving: start fast, settle slowly. M3 emphasized decelerate. */
    val Enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Things leaving: start slowly, end fast. M3 emphasized accelerate. */
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Something that stays on screen and changes (a chevron turning). M3 standard. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
