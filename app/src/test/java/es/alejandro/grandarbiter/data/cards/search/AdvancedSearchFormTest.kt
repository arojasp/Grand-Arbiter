package es.alejandro.grandarbiter.data.cards.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedSearchFormTest {

    private fun form(q: String) = AdvancedSearchForm.fromQuery(q)

    // ---- query -> fields

    @Test fun formExample_fillsTheFields() {
        assertEquals(
            AdvancedSearchForm(
                rulesText = "flying", type = "creature", colors = setOf('U'), colorMatch = ColorMatch.INCLUDES,
                manaValue = NumberFilter(NumberOp.EQ, 3),
            ),
            form("o:flying t:creature c:u mv=3"),
        )
    }

    @Test fun plainWords_areTheName() {
        assertEquals(AdvancedSearchForm(name = "lightning bolt"), form("lightning bolt"))
        assertEquals(AdvancedSearchForm(name = "Fight or Flight"), form("Fight or Flight"))   // a name, not an "or"
    }

    @Test fun phrases_andNameKey() {
        assertEquals("draw a card", form("o:\"draw a card\"").rulesText)
        assertEquals("draw a card", form("o:“draw a card”").rulesText)
        assertEquals("sword of fire", form("name:\"sword of fire\" t:equipment").name)
    }

    @Test fun colors_eachMatchAndField() {
        assertEquals(setOf('U', 'R') to ColorMatch.INCLUDES, form("c:ur").let { it.colors to it.colorMatch })
        assertEquals(setOf('W', 'U') to ColorMatch.EXACTLY, form("c=wu").let { it.colors to it.colorMatch })
        assertEquals(setOf('B') to ColorMatch.AT_MOST, form("c<=b").let { it.colors to it.colorMatch })
        form("id:wub").let {
            assertEquals(ColorField.IDENTITY, it.colorField)
            assertEquals(setOf('W', 'U', 'B'), it.colors)
            assertEquals(ColorMatch.AT_MOST, it.colorMatch)                 // id: = fits in
        }
        assertEquals(setOf('C'), form("c:c").colors)
        assertEquals(setOf('C'), form("id:c").colors)
    }

    @Test fun printing_goesToItsFields() {
        form("set:DMU cn:47").let {
            assertEquals("dmu", it.setCode)
            assertEquals("47", it.collectorNumber)
            assertEquals("", it.extra)
        }
    }

    @Test fun whatTheFormCantShow_isKeptAsTyped() {
        assertEquals("-t:goblin", form("t:elf -t:goblin").extra)
        assertEquals("elf", form("t:elf -t:goblin").type)
        assertEquals("c!=w mv>3 pow>tou f:modern", form("c!=w mv>3 pow>tou f:modern").extra)
        assertEquals("t:warrior", form("t:elf t:warrior").extra)            // one Type field: the 2nd stays
        assertEquals("o:haste", form("o:flying o:haste").extra)
    }

    @Test fun orAndParentheses_areNeverSplit() {
        form("c:u or c:w t:elf").let {
            assertEquals("c:u or c:w t:elf", it.extra)
            assertTrue(it.copy(extra = "").isEmpty)
        }
        assertEquals("(t:elf or t:goblin) c:g", form("(t:elf or t:goblin) c:g").extra)
    }

    @Test fun and_isDropped_notAName() {
        assertEquals(AdvancedSearchForm(type = "elf", extra = "t:warrior"), form("t:elf and t:warrior"))
    }

    @Test fun halfTyped_isDropped() {
        assertEquals(AdvancedSearchForm(type = "elf"), form("t:elf -t:"))
        assertEquals(AdvancedSearchForm(), form("o:"))
    }

    // ---- fields -> query

    @Test fun toQuery_writesTheSyntax() {
        assertEquals(
            "o:flying t:creature c:u mv=3",
            AdvancedSearchForm(rulesText = "flying", type = "creature", colors = setOf('U'), manaValue = NumberFilter(NumberOp.EQ, 3)).toQuery(),
        )
        assertEquals("o:\"draw a card\" t:\"legendary creature\"", AdvancedSearchForm(rulesText = "draw a card", type = "legendary creature").toQuery())
        assertEquals("id:wub", AdvancedSearchForm(colorField = ColorField.IDENTITY, colors = setOf('B', 'W', 'U'), colorMatch = ColorMatch.AT_MOST).toQuery())
        assertEquals("id>=g", AdvancedSearchForm(colorField = ColorField.IDENTITY, colors = setOf('G'), colorMatch = ColorMatch.INCLUDES).toQuery())
        assertEquals("c<=r pow>=4 tou<=2", AdvancedSearchForm(colors = setOf('R'), colorMatch = ColorMatch.AT_MOST,
            power = NumberFilter(NumberOp.GE, 4), toughness = NumberFilter(NumberOp.LE, 2)).toQuery())
        assertEquals("set:dmu cn:47", AdvancedSearchForm(setCode = "DMU", collectorNumber = "47").toQuery())
        assertEquals("", AdvancedSearchForm().toQuery())
    }

    @Test fun nameAlone_isPlainWords_withOthers_isQuoted() {
        assertEquals("Fight or Flight", AdvancedSearchForm(name = "Fight or Flight").toQuery())
        assertEquals("name:\"Fight or Flight\" t:sorcery", AdvancedSearchForm(name = "Fight or Flight", type = "sorcery").toQuery())
        assertEquals("bolt c:r", AdvancedSearchForm(name = "bolt", colors = setOf('R')).toQuery())
    }

    @Test fun booleanExtra_getsParentheses_whenFieldsAreAdded() {
        // "c:u or c:w t:elf" + mv=3 must mean (that) AND mv=3, not c:u OR (... mv=3).
        assertEquals("mv=3 (c:u or c:w t:elf)", form("c:u or c:w t:elf").copy(manaValue = NumberFilter(NumberOp.EQ, 3)).toQuery())
        assertEquals("c:u or c:w t:elf", form("c:u or c:w t:elf").toQuery())   // untouched
    }

    // ---- round trip

    @Test fun fieldsSurviveARoundTrip() {
        listOf(
            AdvancedSearchForm(name = "lightning bolt"),
            AdvancedSearchForm(name = "Fight or Flight", type = "sorcery"),
            AdvancedSearchForm(rulesText = "draw a card", type = "legendary creature", colors = setOf('G', 'U'), colorMatch = ColorMatch.EXACTLY),
            AdvancedSearchForm(colorField = ColorField.IDENTITY, colors = setOf('W', 'U', 'B'), colorMatch = ColorMatch.AT_MOST),
            AdvancedSearchForm(colorField = ColorField.IDENTITY, colors = setOf('R'), colorMatch = ColorMatch.INCLUDES),
            AdvancedSearchForm(colors = setOf('C')),
            AdvancedSearchForm(colorField = ColorField.IDENTITY, colors = setOf('C'), colorMatch = ColorMatch.AT_MOST),
            AdvancedSearchForm(manaValue = NumberFilter(NumberOp.LE, 2), power = NumberFilter(NumberOp.GE, 4), toughness = NumberFilter(NumberOp.EQ, 1)),
            AdvancedSearchForm(setCode = "dmu", collectorNumber = "47"),
            AdvancedSearchForm(type = "elf", extra = "-t:goblin"),
            AdvancedSearchForm(extra = "c:u or c:w t:elf"),
        ).forEach { f -> assertEquals("round trip of $f", f, AdvancedSearchForm.fromQuery(f.toQuery())) }
    }

    @Test fun queriesSurviveARoundTrip_inMeaning() {
        // Order may change; what the parser reads must not.
        listOf(
            "o:flying t:creature c:u mv=3",
            "t:elf -t:goblin id:bg",
            "c:u or c:w t:elf",
            "set:dmu cn:47",
            "pow>tou o:trample",
        ).forEach { q ->
            val again = AdvancedSearchForm.fromQuery(q).toQuery()
            val a = CardQueryParser.parse(q)
            val b = CardQueryParser.parse(again)
            assertEquals("terms of [$q] -> [$again]", a.terms.toSet(), b.terms.toSet())
            assertEquals("online parts of [$q] -> [$again]", a.onlineParts.toSet(), b.onlineParts.toSet())
        }
    }

    @Test fun empty() {
        assertTrue(AdvancedSearchForm().isEmpty)
        assertFalse(AdvancedSearchForm(type = "elf").isEmpty)
    }
}
