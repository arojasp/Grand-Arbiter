package es.alejandro.grandarbiter.ui.tools.deckcounter

/** Deck-size rules a deck check validates against. Pure, unit-tested. */
enum class DeckFormat(
    /** Main deck minimum, or exact size when [mainExact]. */
    val mainSize: Int,
    val mainExact: Boolean,
    /** Sideboard maximum; null = no limit. */
    val sideboardMax: Int?,
    val hasSideboard: Boolean,
) {
    /** At least 60 cards, no maximum; sideboard up to 15 (MTR 6.1). */
    CONSTRUCTED(60, mainExact = false, sideboardMax = 15, hasSideboard = true),
    /** At least 40 cards; every unused card of the pool is the sideboard (MTR 7.1). */
    LIMITED(40, mainExact = false, sideboardMax = null, hasSideboard = true),
    /** Exactly 100 cards including the commander; no sideboard (Commander format rules). */
    COMMANDER(100, mainExact = true, sideboardMax = null, hasSideboard = false),
}

enum class Pile { MAIN, SIDEBOARD }

/** Where a count stands against the format. */
sealed interface CountStatus {
    data class Short(val missing: Int) : CountStatus
    data object Ok : CountStatus
    data class Over(val extra: Int) : CountStatus
    /** No rule for this pile (e.g. a Limited sideboard): just the number. */
    data object NoLimit : CountStatus
}

object DeckCount {

    fun status(format: DeckFormat, pile: Pile, count: Int): CountStatus = when (pile) {
        Pile.MAIN -> when {
            count < format.mainSize -> CountStatus.Short(format.mainSize - count)
            format.mainExact && count > format.mainSize -> CountStatus.Over(count - format.mainSize)
            else -> CountStatus.Ok
        }
        Pile.SIDEBOARD -> {
            val max = format.sideboardMax
            when {
                max == null -> CountStatus.NoLimit
                count > max -> CountStatus.Over(count - max)
                else -> CountStatus.Ok
            }
        }
    }

    /** "4+4+3+…" as the judge counted it, for reading the count back. */
    fun history(groups: List<Int>): String = groups.joinToString("+")

    /** Largest pile the "Other number" dialog takes: a whole Commander deck. */
    const val MAX_CUSTOM = 100

    /** The dialog's text as a pile size, or null when it isn't a number from 1 to [MAX_CUSTOM]. */
    fun parseCustom(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..MAX_CUSTOM }
}
