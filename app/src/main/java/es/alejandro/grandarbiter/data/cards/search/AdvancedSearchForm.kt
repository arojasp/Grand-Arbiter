package es.alejandro.grandarbiter.data.cards.search

/*
 * The Advanced search form is a way to WRITE the search syntax (canvas Busqueda2_avanzada_revisada):
 * opening it reads the current query into fields, "Search" writes the fields back as a query.
 * What the form can't show is kept as typed ([AdvancedSearchForm.extra]), never dropped. A query
 * with "or" or parentheses is never split into fields, because moving its parts around would
 * change its meaning; it stays whole and new fields are added around it in parentheses.
 * Pure, unit-tested.
 */

enum class ColorMatch { INCLUDES, EXACTLY, AT_MOST }

enum class NumberOp { ANY, EQ, LE, GE }

data class NumberFilter(val op: NumberOp = NumberOp.ANY, val value: Int = 0)

data class AdvancedSearchForm(
    val name: String = "",
    val rulesText: String = "",
    val type: String = "",
    val colorField: ColorField = ColorField.COLOR,
    /** "WUBRG" letters, or "C" alone for colorless. */
    val colors: Set<Char> = emptySet(),
    val colorMatch: ColorMatch = ColorMatch.INCLUDES,
    val manaValue: NumberFilter = NumberFilter(),
    val power: NumberFilter = NumberFilter(),
    val toughness: NumberFilter = NumberFilter(),
    val setCode: String = "",
    val collectorNumber: String = "",
    /** Parts the form has no field for, as typed (e.g. "-t:goblin", "c:u or c:w"). */
    val extra: String = "",
) {
    val isEmpty: Boolean get() = this == AdvancedSearchForm()

    fun toQuery(): String {
        val parts = mutableListOf<String>()
        val name = name.replace("\"", "").trim()
        val onlyName = name.isNotEmpty() && copy(name = "") == AdvancedSearchForm()
        when {
            name.isEmpty() -> Unit
            onlyName || (' ' !in name && name.lowercase() !in OPERATORS) -> parts += name   // plain words: the old name search
            else -> parts += "name:\"$name\""                         // "or"/"and" in a name stay words
        }
        rulesText.trim().takeIf { it.isNotEmpty() }?.let { parts += "o:" + quoted(it) }
        type.trim().takeIf { it.isNotEmpty() }?.let { parts += "t:" + quoted(it) }
        colorsPart()?.let { parts += it }
        numberPart("mv", manaValue)?.let { parts += it }
        numberPart("pow", power)?.let { parts += it }
        numberPart("tou", toughness)?.let { parts += it }
        setCode.trim().takeIf { it.isNotEmpty() }?.let { parts += "set:" + it.lowercase() }
        collectorNumber.trim().takeIf { it.isNotEmpty() }?.let { parts += "cn:$it" }
        extra.trim().takeIf { it.isNotEmpty() }?.let {
            parts += if (parts.isNotEmpty() && isBoolean(it)) "($it)" else it
        }
        return parts.joinToString(" ")
    }

    private fun colorsPart(): String? {
        if (colors.isEmpty()) return null
        val key = if (colorField == ColorField.COLOR) "c" else "id"
        if (colors == setOf('C')) return "$key:c"
        val letters = "WUBRG".filter { it in colors }.lowercase()
        val op = when (colorMatch) {
            ColorMatch.INCLUDES -> if (colorField == ColorField.COLOR) ":" else ">="
            ColorMatch.EXACTLY -> "="
            ColorMatch.AT_MOST -> if (colorField == ColorField.COLOR) "<=" else ":"
        }
        return "$key$op$letters"
    }

    private fun numberPart(key: String, f: NumberFilter): String? = when (f.op) {
        NumberOp.ANY -> null
        NumberOp.EQ -> "$key=${f.value}"
        NumberOp.LE -> "$key<=${f.value}"
        NumberOp.GE -> "$key>=${f.value}"
    }

    companion object {
        private val SET_KEYS = setOf("set", "s", "e", "edition")
        private val OPERATORS = setOf("or", "and")
        private val SIMPLE_VALUE = Regex("""^[A-Za-z0-9★-]+$""")

        private fun quoted(s: String) = if (' ' in s) "\"${s.replace("\"", "")}\"" else s

        /** "or" or parentheses: an expression the form must keep whole. */
        fun isBoolean(query: String): Boolean =
            CardQueryParser.tokens(query).any { it.equals("or", ignoreCase = true) || '(' in it || ')' in it }

        fun fromQuery(query: String): AdvancedSearchForm {
            val q = CardQueryParser.normalize(query).trim()
            if (q.isEmpty()) return AdvancedSearchForm()
            // Only plain words: a card name, even with "or" in it (Fight or Flight).
            val whole = CardQueryParser.parse(q)
            if (!whole.needsOnline && whole.terms.isNotEmpty() && whole.terms.all { it is QueryTerm.Name }) {
                return AdvancedSearchForm(name = whole.terms.joinToString(" ") { (it as QueryTerm.Name).text })
            }
            if (isBoolean(q)) return AdvancedSearchForm(extra = q)
            var form = AdvancedSearchForm()
            val names = mutableListOf<String>()
            val extra = mutableListOf<String>()
            for (token in CardQueryParser.tokens(q)) {
                val key = token.substringBefore(':', "").lowercase()
                val value = token.substringAfter(':', "")
                when {
                    token.equals("and", ignoreCase = true) -> Unit         // what every search does anyway
                    key in SET_KEYS && form.setCode.isEmpty() && SIMPLE_VALUE.matches(value) -> form = form.copy(setCode = value.lowercase())
                    key == "cn" && form.collectorNumber.isEmpty() && SIMPLE_VALUE.matches(value) -> form = form.copy(collectorNumber = value)
                    else -> {
                        val parsed = CardQueryParser.parse(token)
                        val term = parsed.terms.singleOrNull()
                        val next = if (parsed.needsOnline || term == null) null else form.with(term, names)
                        if (next != null) form = next
                        else if (parsed.terms.isNotEmpty() || parsed.needsOnline) extra += token
                    }
                }
            }
            return form.copy(name = names.joinToString(" "), extra = extra.joinToString(" "))
        }

        /** [term] in its field, or null when the field is taken or can't show it. */
        private fun AdvancedSearchForm.with(term: QueryTerm, names: MutableList<String>): AdvancedSearchForm? = when (term) {
            is QueryTerm.Name -> { names += term.text; this }
            is QueryTerm.RulesText -> if (rulesText.isEmpty()) copy(rulesText = term.text) else null
            is QueryTerm.Type -> if (type.isEmpty()) copy(type = term.text) else null
            is QueryTerm.Colors -> if (colors.isNotEmpty()) null else colorsFrom(term)?.let { (set, match) ->
                copy(colorField = term.field, colors = set, colorMatch = match)
            }
            is QueryTerm.Number -> {
                val f = numberFrom(term) ?: return null
                when (term.field) {
                    NumberField.MANA_VALUE -> if (manaValue.op == NumberOp.ANY) copy(manaValue = f) else null
                    NumberField.POWER -> if (power.op == NumberOp.ANY) copy(power = f) else null
                    NumberField.TOUGHNESS -> if (toughness.op == NumberOp.ANY) copy(toughness = f) else null
                }
            }
        }

        private fun colorsFrom(t: QueryTerm.Colors): Pair<Set<Char>, ColorMatch>? {
            if (t.colors.isEmpty()) {
                // Colorless: one way to write it per field ("c:c", "id:c").
                val canonical = if (t.field == ColorField.COLOR) Cmp.EQ else Cmp.LE
                return if (t.cmp == canonical) setOf('C') to (if (t.field == ColorField.COLOR) ColorMatch.INCLUDES else ColorMatch.AT_MOST) else null
            }
            val match = when (t.cmp) {
                Cmp.GE -> ColorMatch.INCLUDES
                Cmp.EQ -> ColorMatch.EXACTLY
                Cmp.LE -> ColorMatch.AT_MOST
                else -> return null                          // != < > stay as typed
            }
            return t.colors.toSet() to match
        }

        private fun numberFrom(t: QueryTerm.Number): NumberFilter? {
            if (t.value != Math.floor(t.value)) return null       // 2.5: kept as typed
            val op = when (t.cmp) { Cmp.EQ -> NumberOp.EQ; Cmp.LE -> NumberOp.LE; Cmp.GE -> NumberOp.GE; else -> return null }
            return NumberFilter(op, t.value.toInt())
        }
    }
}
