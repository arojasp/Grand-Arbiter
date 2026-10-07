package es.alejandro.grandarbiter.ui.cardsearch

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import java.time.LocalDate
import java.time.OffsetDateTime

/** Oracle text with reminder text (parenthesised) in italics, as printed. Pure, unit-tested. */
object CardText {

    fun oracle(text: String): AnnotatedString = buildAnnotatedString {
        var depth = 0
        val italic = SpanStyle(fontStyle = FontStyle.Italic)
        val buf = StringBuilder()
        fun flush(inReminder: Boolean) {
            if (buf.isEmpty()) return
            if (inReminder) pushStyle(italic)
            append(buf.toString())
            if (inReminder) pop()
            buf.clear()
        }
        for (ch in text) {
            when (ch) {
                '(' -> { flush(depth > 0); depth++; buf.append(ch) }
                ')' -> { buf.append(ch); flush(depth > 0); if (depth > 0) depth-- }
                '\n' -> { flush(depth > 0); depth = 0; buf.append(ch) }   // never carry italics across lines
                else -> buf.append(ch)
            }
        }
        flush(depth > 0)
    }

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
