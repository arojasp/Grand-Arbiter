package es.alejandro.grandarbiter.domain.model.customlists

import es.alejandro.grandarbiter.domain.model.cards.CardSummary

/** What a paste gives: the cards found, and the lines that matched no card (to fix and paste again). */
data class PasteResult(val found: List<CustomListEntry>, val notFound: List<String>)

/** Pasted text → cards, using [find] (exact name lookup). Pure apart from [find], unit-tested. */
object CustomListImport {

    private val typographic = mapOf('’' to '\'', '‘' to '\'', '‚' to '\'', '′' to '\'', '“' to '"', '”' to '"')
    private val bullet = Regex("""^[-*•]\s+""")

    suspend fun resolve(text: String, find: suspend (String) -> CardSummary?): PasteResult {
        val found = LinkedHashMap<String, CustomListEntry>()      // a card twice: the last line wins
        val notFound = mutableListOf<String>()
        for (pasted in CustomListText.parse(text)) {
            val name = clean(pasted.name)
            val whole = clean(pasted.line.replace(bullet, ""))
            var status = pasted.status
            var card = find(name)
            // A name ending in a number or a status word was read as points/status: try the whole line.
            if (card == null && whole != name) {
                card = find(whole)
                if (card != null) status = ListStatus.Banned
            }
            if (card == null) {
                notFound += pasted.line
            } else {
                found.remove(card.oracleId)
                found[card.oracleId] = CustomListEntry(card.oracleId, card.name, status)
            }
        }
        return PasteResult(found.values.toList(), notFound)
    }

    private fun clean(s: String): String = s.map { typographic[it] ?: it }.joinToString("").trim()
}
