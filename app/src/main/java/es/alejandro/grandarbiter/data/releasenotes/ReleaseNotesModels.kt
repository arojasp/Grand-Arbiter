package es.alejandro.grandarbiter.data.releasenotes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** One set in the index (release-notes.json). [sha]: of its offline document; null = online only. */
@Serializable
data class ReleaseNoteSet(val code: String, val name: String, val url: String, val sha: String? = null)

@Serializable
internal data class ReleaseNotesIndex(val schemaVersion: Int, val sets: List<ReleaseNoteSet>)

/** A set's release notes, as built by tools/releasenotes/build_release_notes.py. */
@Serializable
data class ReleaseNotesDocument(
    val schemaVersion: Int,
    val code: String,
    val name: String,
    val url: String,
    /** ISO date of Wizards' "Document last modified". */
    val lastModified: String? = null,
    val sections: List<ReleaseNotesSection>,
)

@Serializable
data class ReleaseNotesSection(val title: String, val blocks: List<ReleaseNotesBlock>)

/** p / heading / list use the rules' inline markup; card text is plain, drawn with mana symbols. */
@Serializable
sealed interface ReleaseNotesBlock {
    @Serializable @SerialName("p") data class Paragraph(val text: String) : ReleaseNotesBlock
    @Serializable @SerialName("heading") data class Heading(val text: String) : ReleaseNotesBlock
    @Serializable @SerialName("list") data class Items(val items: List<String>) : ReleaseNotesBlock
    @Serializable @SerialName("card") data class Card(
        val name: String,
        val lines: List<String>,
        val notes: List<String>,
    ) : ReleaseNotesBlock
}

/**
 * Validation for everything that comes from the repo (index and documents). Null = unusable, so a
 * broken publish never replaces good data. Index links must be https on magic.wizards.com: the app
 * only ever opens official Wizards pages from it. Pure: unit-tested.
 */
object ReleaseNotesParser {
    const val SUPPORTED_SCHEMA_VERSION = 1
    private const val OFFICIAL_PREFIX = "https://magic.wizards.com/"
    private val json = Json { ignoreUnknownKeys = true }

    /** A set code is also a file name (`<code>.json`), so it can't hold a path. */
    private val SET_CODE = Regex("^[A-Z0-9]{2,6}$")

    fun parse(text: String): List<ReleaseNoteSet>? {
        val index = runCatching { json.decodeFromString<ReleaseNotesIndex>(text) }.getOrNull() ?: return null
        if (index.schemaVersion > SUPPORTED_SCHEMA_VERSION) return null
        val sets = index.sets.filter { SET_CODE.matches(it.code) && it.name.isNotBlank() && it.url.startsWith(OFFICIAL_PREFIX) }
        return sets.takeIf { it.isNotEmpty() && it.size == index.sets.size }
    }

    /** [expectedSha]: the index's value; a document that doesn't match is a broken or stale download. */
    fun parseDocument(text: String, expectedSha: String?): ReleaseNotesDocument? {
        if (expectedSha != null && sha16(text) != expectedSha) return null
        val doc = runCatching { json.decodeFromString<ReleaseNotesDocument>(text) }.getOrNull() ?: return null
        if (doc.schemaVersion > SUPPORTED_SCHEMA_VERSION || doc.sections.isEmpty()) return null
        return doc
    }

    /**
     * First 16 hex of SHA-256, as build_release_notes.py writes it in the index. Line endings are
     * normalized first: a Windows checkout with autocrlf must not invalidate the APK's own copies.
     */
    fun sha16(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.replace("\r\n", "\n").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(16)
}
