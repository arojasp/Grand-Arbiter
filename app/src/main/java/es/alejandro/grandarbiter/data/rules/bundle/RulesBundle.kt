package es.alejandro.grandarbiter.data.rules.bundle

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire format of the rules bundle (assets/rules/bundle.json and the OTA copy on GitHub).
 *
 * Source of truth for the format. Pure Kotlin (no Android imports) so it can later move to a
 * shared JVM module used by both the app and the build pipeline.
 *
 * Inline markup allowed in every `text` field (and list items / glossary definitions):
 *   *emphasis*   **strong**   [label](ref:ipg/2.1)   [label](https://…)
 * Literal `*`, `[, ]` and `\\` must be escaped with a backslash (the CR has *-* creatures).
 * Nothing else. Anything richer means a new Block type, not more markup.
 */
@Serializable
data class RulesBundle(
    /** Bumped only on breaking format changes. The app rejects bundles with a higher value. */
    val schemaVersion: Int,
    /** Monotonic, e.g. 2026100701 (yyyyMMddNN). The app imports when this is > installed. */
    val bundleVersion: Long,
    /** ISO-8601 instant, informational only. */
    val generatedAt: String,
    val documents: List<RulesDocument>,
) {
    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}

@Serializable
enum class DocumentId(val key: String) {
    @SerialName("cr") CR("cr"),
    @SerialName("mtr") MTR("mtr"),
    @SerialName("ipg") IPG("ipg"),
    @SerialName("jar") JAR("jar"),
}

@Serializable
data class RulesDocument(
    val id: DocumentId,
    val title: String,
    /** ISO date (yyyy-MM-dd) the document takes effect. */
    val effectiveDate: String,
    /** Official source the content was derived from (the PDF or TXT URL). */
    val sourceUrl: String,
    /** SHA-256 of that source file, so the pipeline can tell if the upstream file changed. */
    val sourceSha256: String? = null,
    /** Flat list in reading order. Parents always come before their children. */
    val sections: List<RulesSection>,
    /** CR only for now. */
    val glossary: List<GlossaryEntry> = emptyList(),
)

@Serializable
data class RulesSection(
    /**
     * Stable id: "<doc>/<local>", e.g. "cr/603.1a", "mtr/3.2", "ipg/2.1", "jar/missed-triggers".
     * NEVER reuse an id for different content: bookmarks and history in the user DB point here.
     */
    val id: String,
    val parentId: String? = null,
    /** Printed number ("2.1", "603.1a"). Null for the JAR, which has no numbering. */
    val number: String? = null,
    /** Null for CR rules and subrules, which have no title. */
    val title: String? = null,
    val blocks: List<Block> = emptyList(),
    /** IPG infraction sections only (2.x, 3.x, 4.x leaves). */
    val infraction: Infraction? = null,
)

@Serializable
sealed interface Block {
    @Serializable @SerialName("p")
    data class Paragraph(val text: String) : Block

    /** In-section heading. For the IPG: Definition / Examples / Philosophy / Additional Remedy / Upgrade / Downgrade. */
    @Serializable @SerialName("heading")
    data class Heading(val text: String, val role: HeadingRole? = null) : Block

    /** CR "Example:" lines, IPG lettered examples (label = "A"). */
    @Serializable @SerialName("example")
    data class Example(val text: String, val label: String? = null) : Block

    @Serializable @SerialName("list")
    data class BulletList(val items: List<String>, val ordered: Boolean = false) : Block

    /** MTR appendices (time limits, tiebreakers, rounds). Cells use inline markup; "\n" is a line break in a cell. */
    @Serializable @SerialName("table")
    data class Table(val header: List<String>, val rows: List<List<String>>) : Block
}

@Serializable
enum class HeadingRole {
    @SerialName("definition") DEFINITION,
    @SerialName("examples") EXAMPLES,
    @SerialName("philosophy") PHILOSOPHY,
    @SerialName("additional_remedy") ADDITIONAL_REMEDY,
    @SerialName("upgrade") UPGRADE,
    @SerialName("downgrade") DOWNGRADE,
}

/**
 * Structured penalty data, used for quick-reference UI (badges, a penalty cheat sheet).
 * The penalty lives ONLY here, never repeated as a paragraph in the blocks, so there is one
 * value to check by hand against the PDF.
 */
@Serializable
data class Infraction(
    val category: InfractionCategory,
    val penalty: Penalty,
)

@Serializable
enum class InfractionCategory {
    @SerialName("game_play_error") GAME_PLAY_ERROR,
    @SerialName("tournament_error") TOURNAMENT_ERROR,
    @SerialName("unsporting_conduct") UNSPORTING_CONDUCT,
}

@Serializable
enum class Penalty {
    @SerialName("none") NONE,
    @SerialName("warning") WARNING,
    @SerialName("game_loss") GAME_LOSS,
    @SerialName("match_loss") MATCH_LOSS,
    @SerialName("disqualification") DISQUALIFICATION,
}

@Serializable
data class GlossaryEntry(
    val term: String,
    val definition: String,
)

/**
 * Json used by the app to read bundles. Lenient on unknown keys so an older app can read a newer
 * bundle that only ADDED optional fields. Unknown block types or enum values still fail, on purpose:
 * those are breaking changes and need a schemaVersion bump.
 */
val RulesJson: Json = Json { ignoreUnknownKeys = true }

// ---------- helpers shared by the importer, FTS and the validator ----------

private val LINK = Regex("""(?<!\\)\[([^\]]+)]\(([^)]+)\)""")
/** *x* or **x**, not escaped, no whitespace just inside the markers. */
private val EMPHASIS = Regex("""(?<!\\)(\*\*|\*)(?=\S)(.+?)(?<=\S)(?<!\\)\1""")
private val ESCAPED = Regex("""\\([*\[\]\\])""")
private val BARE_ASTERISK = Regex("""(?<!\\)\*""")

/** Every link target in a markup string ("ref:ipg/2.1", "https://…"). */
fun String.linkTargets(): List<String> = LINK.findAll(this).map { it.groupValues[2] }.toList()

/** Markup removed and escapes resolved: what goes into FTS and what search snippets show. */
fun String.stripMarkup(): String = replace(LINK, "$1").replace(EMPHASIS, "$2").replace(ESCAPED, "$1")

/** True if a `*` is left that is neither escaped nor part of emphasis: a source-text bug. */
fun String.hasStrayAsterisk(): Boolean = BARE_ASTERISK.containsMatchIn(replace(LINK, "$1").replace(EMPHASIS, "$2"))

fun Block.texts(): List<String> = when (this) {
    is Block.Paragraph -> listOf(text)
    is Block.Heading -> listOf(text)
    is Block.Example -> listOf(text)
    is Block.BulletList -> items
    is Block.Table -> header + rows.flatten()
}

fun RulesSection.plainText(): String =
    blocks.flatMap { it.texts() }.joinToString("\n") { it.stripMarkup() }
