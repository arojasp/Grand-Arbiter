package es.alejandro.grandarbiter.ui.settings

/**
 * What the Text size preview shows: a card's name and Oracle text from cards.db, or, while the
 * card data isn't installed, the app's own sample sentence ([name] and [text] null).
 */
data class TextSample(val name: String?, val text: String?)

/**
 * The cards the Text size preview picks from, one at random each time Settings opens. Their text
 * comes from cards.db, so it follows Oracle updates; mana symbols are drawn as in card details.
 * Iconic cards with short text; a card whose current text no longer [fits] is skipped.
 */
internal object TextSizeSamples {

    val names: List<String> = listOf(
        "Black Lotus", "Lightning Bolt", "Wrath of God", "Dark Ritual", "Blood Moon",
        "Armageddon", "Counterspell", "Wheel of Fortune", "Gaea's Cradle", "Birds of Paradise",
        "Ancestral Recall", "Time Walk", "Terminate", "Lightning Helix", "Undermine",
        "Absorb", "Serra Angel", "Llanowar Elves", "Demonic Tutor", "Doom Blade",
        "Terror", "Shivan Dragon", "Sol Ring", "Tarmogoyf", "Griselbrand",
        "Pacifism", "Exploration", "Brainstorm",
    )

    /** Short enough for the preview box at the largest text size. */
    fun fits(text: String): Boolean = text.isNotBlank() && text.length <= MAX_LENGTH

    private const val MAX_LENGTH = 160
    private var last = -1

    /** A random index into [names], never the one picked last time (while the app process lives). */
    @Synchronized
    fun nextIndex(random: kotlin.random.Random = kotlin.random.Random): Int {
        var i = random.nextInt(names.size)
        if (names.size > 1 && i == last) i = (i + 1 + random.nextInt(names.size - 1)) % names.size
        last = i
        return i
    }
}
