package es.alejandro.grandarbiter.data.cards.search

import es.alejandro.grandarbiter.data.cards.search.QueryTerm.Colors
import es.alejandro.grandarbiter.data.cards.search.QueryTerm.Name
import es.alejandro.grandarbiter.data.cards.search.QueryTerm.Number
import es.alejandro.grandarbiter.data.cards.search.QueryTerm.RulesText
import es.alejandro.grandarbiter.data.cards.search.QueryTerm.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardQueryParserTest {

    private fun terms(q: String) = CardQueryParser.parse(q).also { assertFalse("$q went online", it.needsOnline) }.terms
    private fun online(q: String) = CardQueryParser.parse(q).onlineParts

    @Test fun plainWords_areNameWords_likeBefore() {
        assertEquals(listOf(Name("light"), Name("bol")), terms("light bol"))
    }

    @Test fun quotedPhrase_isOneNameTerm() {
        assertEquals(listOf(Name("lightning bolt")), terms("\"lightning bolt\""))
    }

    @Test fun formExample_isAllOffline() {
        assertEquals(
            listOf(
                RulesText("flying"), Type("creature"),
                Colors(ColorField.COLOR, Cmp.GE, "U"), Number(NumberField.MANA_VALUE, Cmp.EQ, 3.0),
            ),
            terms("o:flying t:creature c:u mv=3"),
        )
    }

    @Test fun rulesTextPhrase_inQuotes() {
        assertEquals(listOf(RulesText("draw a card")), terms("o:\"draw a card\""))
        assertEquals(listOf(RulesText("draw a card")), terms("oracle:\"draw a card\""))
    }

    @Test fun keys_areCaseInsensitive_andHaveLongForms() {
        assertEquals(listOf(Type("elf")), terms("TYPE:elf"))
        assertEquals(listOf(Number(NumberField.MANA_VALUE, Cmp.LE, 2.0)), terms("cmc<=2"))
        assertEquals(listOf(Number(NumberField.POWER, Cmp.GE, 4.0)), terms("power>=4"))
        assertEquals(listOf(Number(NumberField.TOUGHNESS, Cmp.LT, 2.0)), terms("tou<2"))
    }

    @Test fun color_colonMeansIncludes_asScryfall() {
        assertEquals(listOf(Colors(ColorField.COLOR, Cmp.GE, "UR")), terms("c:ru"))   // WUBRG order
        assertEquals(listOf(Colors(ColorField.COLOR, Cmp.EQ, "WU")), terms("c=wu"))
        assertEquals(listOf(Colors(ColorField.COLOR, Cmp.LE, "B")), terms("color<=black"))
        assertEquals(listOf(Colors(ColorField.COLOR, Cmp.EQ, "")), terms("c:c"))     // colorless, not "everything"
        assertEquals(listOf(Colors(ColorField.COLOR, Cmp.EQ, "")), terms("c:colorless"))
        assertEquals(listOf(Colors(ColorField.IDENTITY, Cmp.LE, "")), terms("id:c"))
    }

    @Test fun identity_colonMeansFitsIn_asScryfall() {
        assertEquals(listOf(Colors(ColorField.IDENTITY, Cmp.LE, "WUB")), terms("id:wub"))
        assertEquals(listOf(Colors(ColorField.IDENTITY, Cmp.EQ, "G")), terms("ci=g"))
    }

    @Test fun halfTyped_saysNothingYet() {
        assertTrue(CardQueryParser.parse("o:").isBlank)
        assertTrue(CardQueryParser.parse("mv>=").isBlank)
        assertEquals(listOf(Name("bolt")), terms("bolt t:"))
    }

    @Test fun halfTypedNegation_saysNothingYet() {
        assertEquals(listOf(Type("creature")), terms("t:creature -"))
        assertEquals(listOf(Type("creature")), terms("t:creature -t:"))
        assertEquals(listOf("-t:faerie"), online("t:creature -t:faerie"))
    }

    @Test fun unclosedQuotesOrParentheses_areNotComplete() {
        assertFalse(CardQueryParser.parse("(c:u or c:w").isComplete)
        assertFalse(CardQueryParser.parse("o:\"draw a").isComplete)
        assertTrue(CardQueryParser.parse("(c:u or c:w)").isComplete)
        assertTrue(CardQueryParser.parse("o:\"draw a card\" -t:land").isComplete)
    }

    @Test fun nameWithAColon_staysAName() {
        // A plain query keeps its words as typed; the name search only looks at letters and digits.
        assertEquals(listOf(Name("Protection:"), Name("red")), terms("Protection: red"))
        assertEquals(listOf(Name("Protection"), Name("red")), terms("Protection: red t:enchantment").take(2))
    }

    @Test fun plainNames_keepOrAndAnd_asWords() {
        assertEquals(listOf(Name("fight"), Name("or"), Name("flight")), terms("fight or flight"))
        assertEquals(listOf(Name("Sword"), Name("of"), Name("Fire"), Name("and"), Name("Ice")), terms("Sword of Fire and Ice"))
        assertEquals(listOf(Name("Ajani's"), Name("Pridemate")), terms("Ajani's Pridemate"))
    }

    @Test fun or_withSyntax_isScryfallsOperator() {
        assertEquals(listOf("or"), online("t:elf or t:goblin"))
    }

    @Test fun and_isWhatWeDoAnyway() {
        assertEquals(listOf(Type("elf"), Type("warrior")), terms("t:elf and t:warrior"))
    }

    @Test fun negationOrParenthesesExact_goOnline() {
        assertEquals(listOf("-t:faerie"), online("t:creature -t:faerie"))
        assertEquals(listOf("or"), online("c:u or c:w"))
        assertEquals(listOf("(c:u", "c:w)"), online("(c:u or c:w)").filter { it != "or" })
        assertEquals(listOf("!\"Lightning Bolt\""), online("!\"Lightning Bolt\""))
    }

    @Test fun otherKeys_andPrintings_goOnline() {
        assertEquals(listOf("set:dmu", "cn:47"), online("set:dmu cn:47"))
        assertEquals(listOf("r:mythic"), online("t:dragon r:mythic"))
        assertEquals(listOf("f:modern"), online("f:modern"))
    }

    @Test fun valuesWeDontModel_goOnline() {
        assertEquals(listOf("c:m"), online("c:m"))
        assertEquals(listOf("id:azorius"), online("id:azorius"))
        assertEquals(listOf("pow>tou"), online("pow>tou"))
        assertEquals(listOf("mv:even"), online("mv:even"))
        assertEquals(listOf("o>flying"), online("o>flying"))
    }

    @Test fun onlineKeepsTheRawQuery() {
        val q = CardQueryParser.parse("t:creature -t:faerie")
        assertTrue(q.needsOnline)
        assertEquals("t:creature -t:faerie", q.raw)
    }

    @Test fun tokens_keepQuotedSpaces() {
        assertEquals(listOf("o:\"draw a card\"", "t:wizard"), CardQueryParser.tokens("  o:\"draw a card\"   t:wizard "))
    }
}
