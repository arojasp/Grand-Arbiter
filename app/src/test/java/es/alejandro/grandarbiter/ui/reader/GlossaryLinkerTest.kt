package es.alejandro.grandarbiter.ui.reader

import es.alejandro.grandarbiter.data.rules.bundle.GlossaryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlossaryLinkerTest {

    private fun e(term: String) = GlossaryEntry(term, "def of $term")

    private val linker = GlossaryLinker(listOf(
        e("Mana"), e("Mana Value"), e("Trample"), e("Token"), e("Dies"), e("Combat Damage"),
        e("If"), e("Player"), e("Interrupt (Obsolete)"), e("Banding, “Bands with Other”"), e("Hexproof (keyword)"), e("Counter (noun)"),
    ))

    private fun terms(text: String) = linker.find(text).map { text.substring(it.start, it.end) to it.entry.term }

    @Test fun longestTermWins_caseInsensitive() =
        assertEquals(listOf("mana value" to "Mana Value"), terms("Compare its mana value."))

    @Test fun simplePlurals() =
        assertEquals(listOf("tokens" to "Token"), terms("Create two tokens."))

    @Test fun eachTermOncePerText() =
        assertEquals(listOf("trample" to "Trample"), terms("trample and trample again"))

    @Test fun wholeWordsOnly() =
        assertTrue(terms("Tokenize the mana-value studies").isEmpty())

    @Test fun commonAndObsoleteTermsAreNotLinked() {
        assertTrue(terms("If a player casts an interrupt").isEmpty())
        assertEquals(listOf("banding" to "Banding, “Bands with Other”"), terms("It has banding."))
        assertEquals(listOf("hexproof" to "Hexproof (keyword)"), terms("It has hexproof."))   // "(…)" stripped
        assertTrue(terms("Put a +1/+1 counter on it.").isEmpty())                            // "counter": too common
    }

    @Test fun multiWordTerms_andPositions() {
        val text = "Trample changes how combat damage is assigned; it dies."
        val found = linker.find(text)
        assertEquals(listOf("Trample", "Combat Damage", "Dies"), found.map { it.entry.term })
        assertEquals("combat damage", text.substring(found[1].start, found[1].end))
    }

    @Test fun sharedSetLinksATermOnlyOnceAcrossSpans() {
        val linked = HashSet<String>()
        assertEquals(1, linker.find("a token", linked).size)
        assertEquals(0, linker.find("another token", linked).size)
    }

    @Test fun subtypesAndTokens_linkOnlyCapitalized() {
        val l = GlossaryLinker(listOf(
            GlossaryEntry("Case", "An enchantment subtype. See rule [714](ref:cr/714)."),
            GlossaryEntry("Map", "A Map token is a colorless Map artifact token with …"),
            GlossaryEntry("Role", "Roles are colorless enchantment tokens. Each one has …"),
            GlossaryEntry("Artifact", "A card type. An artifact is a permanent."),
            GlossaryEntry("Afterlife", "A keyword ability that leaves behind Spirit creature tokens."),
        ))
        fun t(text: String) = l.find(text).map { it.entry.term }
        assertTrue(t("In this case, a map of the role.").isEmpty())
        assertEquals(listOf("Case", "Map", "Role"), t("A Case, a Map token and a Role."))
        assertEquals(listOf("Artifact", "Afterlife"), t("an artifact with afterlife"))   // card types / keywords: lowercase in the CR
    }
}
