package es.alejandro.grandarbiter.data.cards.search

import es.alejandro.grandarbiter.data.cards.local.CardEntity
import es.alejandro.grandarbiter.data.cards.local.CardMapper
import es.alejandro.grandarbiter.data.cards.scryfall.CardFilter
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallCard
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallJson
import es.alejandro.grandarbiter.data.cards.scryfall.forEachJsonLine
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the generated SQL against the REAL card seed in an in-memory SQLite with the same tables
 * Room creates (card + FTS4 card_fts), and compares every filter query with a plain-Kotlin
 * reference over the same ~30k cards. A wrong color or number rule fails here, not at a table.
 */
class CardQuerySqlTest {

    companion object {
        private var db: Connection? = null
        private var cards: List<CardEntity> = emptyList()

        @BeforeClass @JvmStatic
        fun loadSeed() {
            val file = File("src/main/assets/cards/oracle-cards.jsonl.gz")
            if (!file.exists()) return
            val list = ArrayList<CardEntity>(40_000)
            file.inputStream().forEachJsonLine { line ->
                val card = ScryfallJson.decodeFromString<ScryfallCard>(line)
                if (CardFilter.keep(card)) list += CardMapper.toEntity(card)
            }
            cards = list
            val c = DriverManager.getConnection("jdbc:sqlite::memory:")
            c.createStatement().use { s ->
                s.execute(
                    "CREATE TABLE card (oracleId TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, manaCost TEXT, " +
                        "cmc REAL, typeLine TEXT, oracleText TEXT, power TEXT, toughness TEXT, colors TEXT NOT NULL, " +
                        "colorIdentity TEXT NOT NULL, isToken INTEGER NOT NULL)"
                )
                // As Room generates it for CardFts (@Fts4, contentEntity = CardEntity, unicode61).
                s.execute("CREATE VIRTUAL TABLE card_fts USING FTS4(name TEXT, typeLine TEXT, oracleText TEXT, tokenize=unicode61, content=`card`)")
            }
            c.autoCommit = false
            c.prepareStatement("INSERT INTO card VALUES (?,?,?,?,?,?,?,?,?,?,?)").use { p ->
                for (e in list) {
                    p.setString(1, e.oracleId); p.setString(2, e.name); p.setString(3, e.manaCost)
                    if (e.cmc == null) p.setNull(4, java.sql.Types.REAL) else p.setDouble(4, e.cmc)
                    p.setString(5, e.typeLine); p.setString(6, e.oracleText); p.setString(7, e.power)
                    p.setString(8, e.toughness); p.setString(9, e.colors); p.setString(10, e.colorIdentity)
                    p.setInt(11, if (e.isToken) 1 else 0)
                    p.addBatch()
                }
                p.executeBatch()
            }
            c.createStatement().use { it.execute("INSERT INTO card_fts(card_fts) VALUES('rebuild')") }
            c.commit()
            db = c
        }

        @AfterClass @JvmStatic
        fun close() { db?.close() }
    }

    private fun run(query: String, limit: Int = 1_000_000): List<Pair<String, String>> {
        val conn = db
        assumeTrue("no card seed", conn != null)
        val parsed = CardQueryParser.parse(query)
        assertFalse("$query should be offline", parsed.needsOnline)
        val sql = CardQuerySql.build(parsed, limit) ?: return emptyList()
        return conn!!.prepareStatement(sql.text).use { p ->
            sql.args.forEachIndexed { i, a -> p.setObject(i + 1, a) }
            p.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString("oracleId") to rs.getString("name")) }
            }
        }
    }

    // ---- plain-Kotlin reference for the filter terms (names are checked with known cards)

    private val leadingNumber = Regex("""^\d+(\.\d+)?""")
    private fun num(s: String?): Double? = s?.let { leadingNumber.find(it)?.value?.toDouble() }

    private fun Cmp.test(a: Double, b: Double) = when (this) {
        Cmp.EQ -> a == b; Cmp.NE -> a != b; Cmp.LT -> a < b; Cmp.LE -> a <= b; Cmp.GT -> a > b; Cmp.GE -> a >= b
    }

    private fun matches(e: CardEntity, t: QueryTerm): Boolean = when (t) {
        is QueryTerm.Name -> error("reference doesn't do names")
        is QueryTerm.RulesText -> e.oracleText?.lowercase()?.contains(t.text.lowercase()) == true
        is QueryTerm.Type -> e.typeLine?.lowercase()?.contains(t.text.lowercase()) == true
        is QueryTerm.Colors -> {
            val have = (if (t.field == ColorField.COLOR) e.colors else e.colorIdentity).toSet()
            val want = t.colors.toSet()
            when (t.cmp) {
                Cmp.EQ -> have == want
                Cmp.NE -> have != want
                Cmp.GE -> have.containsAll(want)
                Cmp.LE -> want.containsAll(have)
                Cmp.GT -> have.containsAll(want) && have.size > want.size
                Cmp.LT -> want.containsAll(have) && have.size < want.size
            }
        }
        is QueryTerm.Number -> {
            val v = when (t.field) {
                NumberField.MANA_VALUE -> e.cmc
                NumberField.POWER -> num(e.power)
                NumberField.TOUGHNESS -> num(e.toughness)
            }
            v != null && t.cmp.test(v, t.value)
        }
    }

    private fun assertSameAsReference(query: String) {
        val terms = CardQueryParser.parse(query).terms
        val expected = cards.filter { e -> terms.all { matches(e, it) } }.map { it.oracleId }.toSet()
        val actual = run(query).map { it.first }.toSet()
        assertEquals("$query: SQL and reference disagree", expected, actual)
        assertTrue("$query matched nothing: a useless test", expected.isNotEmpty())
    }

    @Test fun filters_matchTheReference_onEveryCard() {
        listOf(
            "o:flying t:creature c:u mv=3",
            "c:c t:artifact",
            "c=wu",
            "c<=r t:instant",
            "c>ub",
            "c<wub t:creature",
            "c!=w t:sorcery mv=1",
            "id:wu t:legendary",
            "id=g",
            "pow>=10",
            "tou<1 t:creature",
            "mv>=12",
            "o:\"draw a card\" c:g mv<=2",
            "o:+1/+1 pow=1 tou=1",
        ).forEach(::assertSameAsReference)
    }

    @Test fun formExample_findsTheFaeries() {
        val names = run("o:flying t:creature c:u mv=3").map { it.second }
        for (n in listOf("Brazen Borrower // Petty Theft", "Pestermite", "Vendilion Clique")) {
            assertTrue("missing $n", n in names)
        }
        assertFalse("Spellstutter Sprite is {1}{U}, mana value 2", "Spellstutter Sprite" in names)
    }

    @Test fun names_exactFirst_likeBefore() {
        assertEquals("Lightning Bolt", run("lightning bolt", limit = 5).first().second)
        // No exact or prefix match: alphabetical, as before ("Emeritus of Conflict // Lightning Bolt" first).
        assertTrue("Lightning Bolt" in run("light bol", limit = 5).map { it.second })
    }

    @Test fun names_combineWithFilters() {
        val names = run("bolt c:r mv=1").map { it.second }
        assertTrue("Lightning Bolt" in names)
        assertTrue(names.all { "bolt" in it.lowercase() })
    }

    @Test fun colorless_isNotEverything() {
        val names = run("c:c").map { it.second }.toSet()
        assertTrue("Sol Ring" in names)
        assertFalse("Lightning Bolt" in names)
    }

    @Test fun identity_fitsIn() {
        val names = run("id:wu").map { it.second }.toSet()
        assertTrue("Swords to Plowshares" in names)
        assertFalse("Lightning Bolt" in names)
    }

    @Test fun likeWildcards_areLiteral() {
        // "_" and "%" typed by the judge must not act as SQL wildcards.
        val underscore = cards.count { it.oracleText?.contains("_") == true }
        assertEquals(underscore, run("o:_").size)
    }

    @Test fun limit_isApplied() {
        assertEquals(10, run("t:creature", limit = 10).size)
    }

    @Test fun blank_hasNoSql_andOnlineIsRefused() {
        assertNull(CardQuerySql.build(CardQueryParser.parse("o:"), 10))
        val online = CardQueryParser.parse("set:dmu cn:47")
        assertTrue(runCatching { CardQuerySql.build(online, 10) }.isFailure)
    }
}
