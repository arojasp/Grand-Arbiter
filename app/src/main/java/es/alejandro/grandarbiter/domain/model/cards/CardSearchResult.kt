package es.alejandro.grandarbiter.domain.model.cards

/** What a card search gives back. */
sealed interface CardSearchResult {
    /** Searched in the cards on the phone. */
    data class Offline(val cards: List<CardSummary>) : CardSearchResult

    /** The query uses [parts] only Scryfall understands, so it can only be searched online. */
    data class NeedsOnline(val parts: List<String>) : CardSearchResult

    companion object {
        val Empty: CardSearchResult = Offline(emptyList())
    }
}
