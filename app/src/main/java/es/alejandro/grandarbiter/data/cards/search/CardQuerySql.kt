package es.alejandro.grandarbiter.data.cards.search

/** SQL text + bound arguments, for a Room @RawQuery. */
data class Sql(val text: String, val args: List<Any>)

/**
 * Offline [CardQuery] -> SQL over cards.db (tables `card` and `card_fts`). Pure, unit-tested
 * against the real card seed (CardQuerySqlTest).
 *
 * - Names: the FTS index, prefix per word (as the search always did), exact/prefix names first.
 * - Rules text and type: substring, case-insensitive (Scryfall's o: and t: are substrings too).
 *   Multi-faced cards hold all faces' text and type line, so either face matches.
 * - Colors: the stored "WUBRG"-ordered letters.
 * - Numbers: mana value; power/toughness only when they start with a digit ("*" never matches).
 *   Known gap: multi-faced cards keep power/toughness per face, so pow/tou don't see them.
 */
object CardQuerySql {

    private val WORD = Regex("""[\p{L}\p{N}]+""")
    private const val COLUMNS = "c.oracleId, c.name, c.manaCost, c.typeLine, c.isToken"

    fun build(query: CardQuery, limit: Int): Sql? {
        require(!query.needsOnline) { "online queries go to Scryfall" }
        if (query.terms.isEmpty()) return null
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()
        val names = query.terms.filterIsInstance<QueryTerm.Name>()
        val nameWords = names.flatMap { WORD.findAll(it.text.lowercase()).map { m -> m.value }.toList() }

        for (term in query.terms) when (term) {
            is QueryTerm.Name -> Unit
            is QueryTerm.RulesText -> { where += "lower(c.oracleText) LIKE ? ESCAPE '\\'"; args += contains(term.text) }
            is QueryTerm.Type -> { where += "lower(c.typeLine) LIKE ? ESCAPE '\\'"; args += contains(term.text) }
            is QueryTerm.Colors -> where += colors(term)
            is QueryTerm.Number -> { where += number(term); args += term.value }
        }

        return if (nameWords.isNotEmpty()) {
            val typed = names.joinToString(" ") { it.text }.trim().lowercase()
            val match = nameWords.joinToString(" ") { "name:$it*" }
            Sql(
                "SELECT $COLUMNS FROM card_fts JOIN card c ON c.rowid = card_fts.rowid " +
                    "WHERE card_fts MATCH ?" + where.joinToString("") { " AND $it" } +
                    " ORDER BY (lower(c.name) = ?) DESC, (lower(c.name) LIKE ? || '%') DESC, c.isToken, c.name LIMIT ?",
                listOf<Any>(match) + args + listOf(typed, typed, limit),
            )
        } else if (where.isEmpty()) {
            null                                          // only name terms with no letters ("!!")
        } else {
            Sql(
                "SELECT $COLUMNS FROM card c WHERE " + where.joinToString(" AND ") +
                    " ORDER BY c.isToken, c.name LIMIT ?",
                args + limit,
            )
        }
    }

    private fun contains(text: String): String {
        val escaped = text.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return "%$escaped%"
    }

    /** Letters are WUBRG constants from the parser, never user text: safe to inline. */
    private fun colors(t: QueryTerm.Colors): String {
        val col = if (t.field == ColorField.COLOR) "c.colors" else "c.colorIdentity"
        val has = t.colors.map { "$col LIKE '%$it%'" }
        val lacks = "WUBRG".filter { it !in t.colors }.map { "$col NOT LIKE '%$it%'" }
        val n = t.colors.length
        val parts = when (t.cmp) {
            Cmp.EQ -> listOf("$col = '${t.colors}'")
            Cmp.NE -> listOf("$col <> '${t.colors}'")
            Cmp.GE -> has
            Cmp.LE -> lacks
            Cmp.GT -> has + "length($col) > $n"
            Cmp.LT -> lacks + "length($col) < $n"
        }
        return if (parts.isEmpty()) "1" else "(" + parts.joinToString(" AND ") + ")"
    }

    private fun number(t: QueryTerm.Number): String {
        val op = when (t.cmp) { Cmp.EQ -> "="; Cmp.NE -> "<>"; Cmp.LT -> "<"; Cmp.LE -> "<="; Cmp.GT -> ">"; Cmp.GE -> ">=" }
        return when (t.field) {
            NumberField.MANA_VALUE -> "(c.cmc IS NOT NULL AND c.cmc $op ?)"
            NumberField.POWER -> "(c.power GLOB '[0-9]*' AND CAST(c.power AS REAL) $op ?)"
            NumberField.TOUGHNESS -> "(c.toughness GLOB '[0-9]*' AND CAST(c.toughness AS REAL) $op ?)"
        }
    }
}
