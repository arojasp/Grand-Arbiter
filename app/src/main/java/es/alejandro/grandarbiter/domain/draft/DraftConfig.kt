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
    /** Rochester: the team that picks first (1 or 2); its Player B lays out the first pack (MTR 8.5). */
    val firstTeam: Int = 1,
) {
    /** Packs at the table: Rochester counts [packs] per player, six players. */
    val totalPacks: Int get() = if (format == DraftFormat.ROCHESTER) packs * RochesterOrder.PLAYERS else packs

    init {
        require(packs in PACKS) { "packs $packs not in $PACKS" }
        require(cardsPerPack in cardsRange(format)) { "cardsPerPack $cardsPerPack not in ${cardsRange(format)} for $format" }
        require(constructionMinutes in CONSTRUCTION_MINUTES) { "constructionMinutes $constructionMinutes not in $CONSTRUCTION_MINUTES" }
        require(firstTeam in 1..2) { "firstTeam $firstTeam" }
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

        /** Two-Headed Giant: only the booster sizes Appendix B has times for. */
        fun cardsRange(format: DraftFormat): IntRange = when (format) {
            DraftFormat.TWO_HEADED_GIANT -> 14..15
            DraftFormat.BOOSTER, DraftFormat.ROCHESTER -> CARDS_PER_PACK
        }

        /** A format's starting values (MTR): 2HG six packs (9.6) and 40 minutes (Appendix B). */
        fun defaultsFor(format: DraftFormat, manualStart: Boolean = DEFAULT_MANUAL_START): DraftConfig = when (format) {
            DraftFormat.TWO_HEADED_GIANT -> DraftConfig(format, packs = 6, cardsPerPack = 14, constructionMinutes = 40, manualStart = manualStart)
            // Rochester: 3 packs per player (18 at the table), 15-card packs, "Team Draft—40 minutes".
            DraftFormat.ROCHESTER -> DraftConfig(format, packs = 3, cardsPerPack = 15, constructionMinutes = 40, manualStart = manualStart)
            DraftFormat.BOOSTER -> DraftConfig(format, manualStart = manualStart)
        }
    }
}
