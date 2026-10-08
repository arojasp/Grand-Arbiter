package es.alejandro.grandarbiter.ui.cardsearch

import androidx.compose.ui.text.font.FontStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CardTextTest {

    private fun italics(text: String) = CardText.oracle(text).let { a ->
        a.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }.map { a.text.substring(it.start, it.end) }
    }

    @Test fun reminderText_isItalic() =
        assertEquals(listOf("(reminder)"), italics("Flying (reminder)\nOther text"))

    @Test fun textIsUnchanged() =
        assertEquals("A (b (c) d) e", CardText.oracle("A (b (c) d) e").text)

    @Test fun unclosedParenthesis_doesNotLeakToNextLine() =
        assertEquals(listOf("(open"), italics("x (open\nnext line"))

    @Test fun powerToughness() {
        assertEquals("*/1+*", CardText.powerToughness("*", "1+*"))
        assertNull(CardText.powerToughness(null, "1"))
    }

    @Test fun date_parsesScryfallTimestamp() =
        assertEquals(LocalDate.of(2026, 10, 7), CardText.date("2026-10-07T09:01:59.955+00:00"))

    @Test fun formatNames() = assertEquals("Duel Commander", CardText.formatName("duel"))

    @Test fun symbols_areFoundOnce() =
        assertEquals(listOf("T", "G", "W/U"), CardText.symbols("{T}: Add {G}. {G}{W/U}"))

    @Test fun manaCost_splitsFaces() =
        assertEquals(listOf(listOf("R"), listOf("1", "R")), CardText.manaCost("{R} // {1}{R}"))

    @Test fun inlineSymbols_becomePlaceholders_andKeepItalics() {
        val a = CardText.oracle("{T}: Add {G}. (Reminder {G}.)", inlineSymbols = true)
        val ids = a.getStringAnnotations(0, a.length).map { it.item }
        assertEquals(listOf("mana:T", "mana:G", "mana:G"), ids)
        assertEquals(listOf("(Reminder ", ".)"), a.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
            .map { a.text.substring(it.start, it.end) })
    }

    @Test fun braceWithoutSymbol_staysText() =
        assertEquals("a { b", CardText.oracle("a { b", inlineSymbols = true).text)
}
