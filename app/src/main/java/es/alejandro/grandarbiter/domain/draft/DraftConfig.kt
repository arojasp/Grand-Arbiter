package es.alejandro.grandarbiter.domain.draft

/** What the judge sets up before starting. Values outside the ranges are rejected. */
data class DraftConfig(
    val format: DraftFormat = DraftFormat.BOOSTER,
    val packs: Int = DEFAULT_PACKS,
    /** 14 = Play Boosters; 15 for older boosters or cubes. */
    val cardsPerPack: Int = DEFAULT_CARDS_PER_PACK,
    /** The Head Judge may change it (Appendix B): editable. */
    val constructionMinutes: Int = DEFAULT_CONSTRUCTION_MINUTES,
    /** "Manual start": at 0:00 the timer stops until the judge starts the next step. */
    val manualStart: Boolean = DEFAULT_MANUAL_START,
) {
    init {
        require(packs in PACKS) { "packs $packs not in $PACKS" }
        require(cardsPerPack in CARDS_PER_PACK) { "cardsPerPack $cardsPerPack not in $CARDS_PER_PACK" }
        require(constructionMinutes in CONSTRUCTION_MINUTES) { "constructionMinutes $constructionMinutes not in $CONSTRUCTION_MINUTES" }
    }

    companion object {
        const val DEFAULT_PACKS = 3
        const val DEFAULT_CARDS_PER_PACK = 14
        const val DEFAULT_CONSTRUCTION_MINUTES = 25
        /** On the first time; afterwards the setup remembers the judge's last choice. */
        const val DEFAULT_MANUAL_START = true
        val PACKS = 1..6
        val CARDS_PER_PACK = 5..15
        val CONSTRUCTION_MINUTES = 1..90
    }
}
