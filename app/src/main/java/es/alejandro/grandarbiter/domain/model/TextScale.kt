package es.alejandro.grandarbiter.domain.model

/** App-wide text size, on top of the system font size (Settings › Accessibility). */
enum class TextScale(val factor: Float) {
    SMALL(0.85f),
    DEFAULT(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f),
}

/** Everything that changes how the whole app looks or behaves, read once at the root. */
data class DisplaySettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val textScale: TextScale = TextScale.DEFAULT,
    val highContrast: Boolean = false,
    val keepScreenOn: Boolean = false,
)
