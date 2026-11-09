package es.alejandro.grandarbiter.domain.model.cards

/** What a card search gives back. */
sealed interface CardSearchResult {
    /** Searched in the cards on the phone. */
    data class Offline(val cards: List<CardSummary>) : CardSearchResult

    /** The query uses [parts] only Scryfall understands: it can only be searched online, on request. */
    data class NeedsOnline(val parts: List<String>) : CardSearchResult

    /**
     * Scryfall's answer. [cards] is its first page; [total] counts them all. [warnings]: parts of
     * the query Scryfall ignored (shown, so a judge never trusts a half-read search).
     */
    data class Online(val cards: List<CardSummary>, val total: Int, val warnings: List<String>) : CardSearchResult

    /** The online search didn't happen. [parts] are the online-only parts of the query. */
    data class OnlineFailed(val reason: Reason, val parts: List<String>, val details: String = "") : CardSearchResult {
        enum class Reason { NO_CONNECTION, REJECTED, UNAVAILABLE }
    }

    companion object {
        val Empty: CardSearchResult = Offline(emptyList())
    }
}
