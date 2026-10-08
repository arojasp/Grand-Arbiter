package es.alejandro.grandarbiter.data.releasenotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ReleaseNotesParserTest {

    private fun file(vararg sets: String, schema: Int = 1) = """{"schemaVersion":$schema,"sets":[${sets.joinToString(",")}]}"""
    private fun set(code: String, url: String = "https://magic.wizards.com/en/news/feature/$code-release-notes") =
        """{"code":"$code","name":"Set $code","url":"$url"}"""

    @Test fun parses_inOrder_ignoringUnknownFields() {
        val sets = ReleaseNotesParser.parse("""{"schemaVersion":1,"extra":true,"sets":[${set("FRA")},${set("HOB")}]}""")!!
        assertEquals(listOf("FRA", "HOB"), sets.map { it.code })
    }

    @Test fun rejectsAnythingThatIsntAGoodList() {
        assertNull(ReleaseNotesParser.parse("<html>captive portal</html>"))
        assertNull(ReleaseNotesParser.parse(file()))                                    // empty
        assertNull(ReleaseNotesParser.parse(file(set("FRA"), schema = 2)))             // newer format
        // One non-official link rejects the whole file: never open a page that isn't Wizards'.
        assertNull(ReleaseNotesParser.parse(file(set("FRA"), set("BAD", url = "https://evil.example/x"))))
        assertNull(ReleaseNotesParser.parse(file(set("BAD", url = "http://magic.wizards.com/x"))))
    }

    /** What ships in the APK and is published from main must parse, or the app shows nothing. */
    @Test fun bundledAsset_isValid() {
        val asset = File("src/main/assets/rules/release-notes.json")
        assumeTrue(asset.exists())
        val sets = ReleaseNotesParser.parse(asset.readText())!!
        assertEquals("codes are unique", sets.size, sets.map { it.code }.toSet().size)
    }

    /** Each offline document in the APK must match the SHA the index gives it, or the app ignores it. */
    @Test fun bundledDocuments_matchTheIndex() {
        val index = File("src/main/assets/rules/release-notes.json")
        assumeTrue(index.exists())
        for (set in ReleaseNotesParser.parse(index.readText())!!.filter { it.sha != null }) {
            val file = File("src/main/assets/rules/releasenotes/${set.code}.json")
            assertTrue("${set.code}: missing ${file.name}", file.exists())
            val doc = ReleaseNotesParser.parseDocument(file.readText(), set.sha)
            assertNotNull("${set.code}: SHA or format doesn't match the index (re-run build_release_notes.py)", doc)
            assertEquals(set.code, doc!!.code)
        }
    }

    @Test fun document_rejectsWrongSha_andCrlfDoesNotMatter() {
        val text = """{"schemaVersion":1,"code":"X","name":"X","url":"u","sections":[{"title":"General Notes","blocks":[{"type":"p","text":"a"}]}]}""" + "\n"
        val sha = ReleaseNotesParser.sha16(text)
        assertNotNull(ReleaseNotesParser.parseDocument(text, sha))
        assertNotNull(ReleaseNotesParser.parseDocument(text.replace("\n", "\r\n"), sha))   // Windows checkout
        assertNull(ReleaseNotesParser.parseDocument(text, "0000000000000000"))
    }
}
