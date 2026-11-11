package es.alejandro.grandarbiter.data.cards.scryfall

import es.alejandro.grandarbiter.domain.model.cards.FormatLegality

/**
 * Which Scryfall oracle cards belong in a PAPER judge app. The single source of truth: the seed
 * builder doesn't filter, so the seed and a Scryfall update always produce the same card set.
 */
object CardFilter {

    /** Formats shown in the app. Digital-only formats (alchemy, historic, timeless, brawl…) are out. */
    val PAPER_FORMATS = listOf(
        "standard", "pioneer", "modern", "legacy", "vintage", "pauper", "commander",
    ) + FormatLegality.COMMUNITY_FORMATS

    /** Not game pieces: art cards and the "front card" memorabilia (no rules text). */
    private val DROPPED_LAYOUTS = setOf("art_series", "front_card")

    /** Null = keep; otherwise why it was dropped (for logs and tests). */
    fun dropReason(card: ScryfallCard): String? {
        if (card.oracleId == null) return "no oracle_id"
        if (card.layout in DROPPED_LAYOUTS) return "layout:${card.layout}"
        // Scryfall's oracle-cards file picks ONE printing per card, sometimes an MTGO-only one
        // (Palinchron). So "no paper printing" alone isn't enough: also require no paper legality.
        val paperPrint = "paper" in card.games
        val paperLegal = PAPER_FORMATS.any { (card.legalities[it] ?: "not_legal") != "not_legal" }
        if (!paperPrint && !paperLegal) return "digital-only:${card.setType}"
        return null
    }

    fun keep(card: ScryfallCard): Boolean = dropReason(card) == null
}
