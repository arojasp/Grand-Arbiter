package es.alejandro.grandarbiter.ui.cardsearch

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import java.time.LocalDate
import java.time.OffsetDateTime

/** Oracle text with reminder text (parenthesised) in italics, as printed. Pure, unit-tested. */
object CardText {

    /** Inline-content id prefix for a mana symbol: "mana:R" for {R}. */
    const val SYMBOL_PREFIX = "mana:"
    private val SYMBOL = Regex("""\{([^{}]{1,10})\}""")

    /** The symbols in a text, without braces: "{T}: Add {G}." -> [T, G]. */
    fun symbols(text: String): List<String> = SYMBOL.findAll(text).map { it.groupValues[1] }.distinct().toList()

    /**
     * Oracle text with reminder text (parenthesised) in italics, as printed. With [inlineSymbols],
     * every {X} becomes an inline-content placeholder ([SYMBOL_PREFIX] + X) drawn as a mana symbol.
     */
    fun oracle(text: String, inlineSymbols: Boolean = false): AnnotatedString = buildAnnotatedString {
        val italic = SpanStyle(fontStyle = FontStyle.Italic)
        var depth = 0
        val buf = StringBuilder()
        fun flush() {
            if (buf.isEmpty()) return
            if (depth > 0) pushStyle(italic)
            append(buf.toString())
            if (depth > 0) pop()
            buf.clear()
        }
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '{' && inlineSymbols) {
                val match = SYMBOL.matchAt(text, i)
                if (match != null) {
                    flush()
                    appendInlineContent(SYMBOL_PREFIX + match.groupValues[1], match.value)
                    i = match.range.last + 1
                    continue
                }
            }
            when (ch) {
                '(' -> { flush(); depth++; buf.append(ch) }
                ')' -> { buf.append(ch); flush(); if (depth > 0) depth-- }
                '\n' -> { flush(); depth = 0; buf.append(ch) }   // never carry italics across lines
                else -> buf.append(ch)
            }
            i++
        }
        flush()
    }

    /** "{2}{U} // {3}" -> [[2, U], [3]]: one list per face. */
    fun manaCost(cost: String): List<List<String>> =
        cost.split(" // ").map { face -> SYMBOL.findAll(face).map { it.groupValues[1] }.toList() }.filter { it.isNotEmpty() }

    /** "3/2", or null for non-creatures. */
    fun powerToughness(power: String?, toughness: String?): String? =
        if (power != null && toughness != null) "$power/$toughness" else null

    /** Scryfall's updated_at -> date. Null if unparseable. */
    fun date(updatedAt: String): LocalDate? = runCatching { OffsetDateTime.parse(updatedAt).toLocalDate() }.getOrNull()

    /** Scryfall legality keys -> names judges use. */
    fun formatName(key: String): String = when (key) {
        "standard" -> "Standard"
        "pioneer" -> "Pioneer"
        "modern" -> "Modern"
        "legacy" -> "Legacy"
        "vintage" -> "Vintage"
        "pauper" -> "Pauper"
        "commander" -> "Commander"
        "oathbreaker" -> "Oathbreaker"
        "duel" -> "Duel Commander"
        "premodern" -> "Premodern"
        "oldschool" -> "Old School"
        "predh" -> "PreDH"
        "paupercommander" -> "Pauper Commander"
        else -> key.replaceFirstChar { it.uppercase() }
    }
}
