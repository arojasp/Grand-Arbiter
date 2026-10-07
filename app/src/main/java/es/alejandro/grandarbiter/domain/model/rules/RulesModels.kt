package es.alejandro.grandarbiter.domain.model.rules

import es.alejandro.grandarbiter.data.rules.bundle.Block
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.data.rules.bundle.Infraction
import es.alejandro.grandarbiter.data.rules.bundle.Penalty
import java.time.LocalDate

/** One of the four documents as installed in rules.db. */
data class RulesDocumentInfo(
    val id: DocumentId,
    val title: String,
    val effectiveDate: LocalDate,
)

/**
 * An entry of a document's contents. Level-0 entries are groups (CR "1. Game Concepts",
 * IPG "2. Game Play Errors"); their children are the pages.
 *
 * [opensPage] is true when tapping the entry opens a page: always for level 1, and for a level-0
 * entry that has its own text (IPG chapter intros, MTR appendices) or no children.
 */
data class TocEntry(
    val sectionId: String,
    val number: String?,
    val title: String?,
    val penalty: Penalty?,
    val opensPage: Boolean,
    val children: List<TocEntry>,
)

/** Where a link or search result goes: a page, optionally scrolled to one section in it. */
data class PageRef(
    val pageId: String,
    val anchorId: String? = null,
) {
    val documentId: DocumentId get() = documentIdOf(pageId)
}

/** A readable page: a level-1 section with everything under it, or a level-0 section's own text. */
data class RulesPage(
    val documentId: DocumentId,
    val sections: List<PageSection>,
) {
    val root: PageSection get() = sections.first()
}

data class PageSection(
    val id: String,
    val number: String?,
    val title: String?,
    /** 0 for the page root, 1 for its children, and so on. */
    val depth: Int,
    val blocks: List<Block>,
    val infraction: Infraction?,
)

data class RulesSearchResult(
    val target: PageRef,
    val number: String?,
    val title: String?,
    /** Plain text; the matched words are wrapped in [SNIPPET_MATCH_START] / [SNIPPET_MATCH_END]. */
    val snippet: String,
)

const val SNIPPET_MATCH_START = '\u0002'
const val SNIPPET_MATCH_END = '\u0003'

fun documentIdOf(sectionId: String): DocumentId {
    val key = sectionId.substringBefore('/')
    return DocumentId.entries.firstOrNull { it.key == key }
        ?: throw IllegalArgumentException("not a rules section id: $sectionId")
}
