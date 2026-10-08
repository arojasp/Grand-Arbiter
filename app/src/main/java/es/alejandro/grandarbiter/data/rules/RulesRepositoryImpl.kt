package es.alejandro.grandarbiter.data.rules

import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.data.rules.bundle.GlossaryEntry
import es.alejandro.grandarbiter.data.rules.bundle.Infraction
import es.alejandro.grandarbiter.data.rules.bundle.InfractionCategory
import es.alejandro.grandarbiter.data.rules.bundle.Penalty
import es.alejandro.grandarbiter.data.rules.bundle.RulesJson
import es.alejandro.grandarbiter.data.rules.local.RulesDao
import es.alejandro.grandarbiter.data.rules.local.RulesSectionEntity
import es.alejandro.grandarbiter.data.rules.local.blocks
import es.alejandro.grandarbiter.domain.model.rules.PageRef
import es.alejandro.grandarbiter.domain.model.rules.PageSection
import es.alejandro.grandarbiter.domain.model.rules.RulesDocumentInfo
import es.alejandro.grandarbiter.domain.model.rules.RulesPage
import es.alejandro.grandarbiter.domain.model.rules.RulesSearchResult
import es.alejandro.grandarbiter.domain.model.rules.TocEntry
import es.alejandro.grandarbiter.domain.model.rules.documentIdOf
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate

class RulesRepositoryImpl(
    private val dao: RulesDao,
    private val json: Json = RulesJson,
) : RulesRepository {

    override fun observeDocuments(): Flow<List<RulesDocumentInfo>> =
        dao.observeDocuments().map { rows ->
            rows.mapNotNull { row ->
                val id = DocumentId.entries.firstOrNull { it.key == row.id } ?: return@mapNotNull null
                RulesDocumentInfo(id, row.title, LocalDate.parse(row.effectiveDate), row.sourceUrl)
            }.sortedBy { it.id.ordinal }
        }

    override suspend fun contents(documentId: DocumentId): List<TocEntry> {
        val rows = dao.toc(documentId.key, maxLevel = 1)
        val pagesByParent = rows.filter { it.level == 1 }.groupBy { it.parentId }
        return rows.filter { it.level == 0 }.map { group ->
            val pages = pagesByParent[group.id].orEmpty().map { it.toTocEntry(opensPage = true) }
            group.toTocEntry(opensPage = pages.isEmpty() || group.hasOwnText(), children = pages)
        }
    }

    override suspend fun page(pageId: String): RulesPage? {
        val ref = resolve(pageId) ?: return null
        val root = dao.section(ref.pageId) ?: return null
        val rows = if (root.level == 0 && dao.childCount(root.id) > 0) {
            listOf(root)                                   // a group's own text (IPG chapter intro)
        } else {
            val end = dao.nextOrdinalAtOrAbove(root.documentId, root.ordinal, root.level) ?: Int.MAX_VALUE
            dao.range(root.documentId, root.ordinal, end)  // the section and everything under it
        }
        val sections = withContext(Dispatchers.Default) { rows.map { it.toPageSection(root.level) } }
        return RulesPage(documentIdOf(root.id), sections)
    }

    override suspend fun resolve(sectionId: String): PageRef? {
        val target = dao.section(sectionId) ?: return null
        var page = target
        while (page.level > 1) {
            page = page.parentId?.let { dao.section(it) } ?: break
        }
        return PageRef(pageId = page.id, anchorId = target.id.takeIf { it != page.id })
    }

    override suspend fun glossary(documentId: DocumentId): List<GlossaryEntry> =
        dao.glossary(documentId.key, "").map { GlossaryEntry(it.term, it.definition) }

    override suspend fun search(query: String, documentId: DocumentId?): List<RulesSearchResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val results = LinkedHashMap<String, RulesSearchResult>()   // by section id, keeps order, no duplicates

        if (RulesSearchQuery.looksLikeNumber(q)) {
            val number = RulesSearchQuery.normalizedNumber(q)
            val docs = documentId?.let { listOf(it) } ?: DocumentId.entries
            for (doc in docs) {
                dao.byNumber(doc.key, number)
                    .filter { RulesSearchQuery.numberMatches(number, it.number) }
                    .forEach { row ->
                        resolve(row.id)?.let { ref ->
                            results[row.id] = RulesSearchResult(ref, row.number, row.title, row.plainText.take(160))
                        }
                    }
            }
        }
        RulesSearchQuery.toFts(q)?.let { fts ->
            for (hit in dao.search(fts, documentId?.key)) {
                if (hit.id in results) continue
                resolve(hit.id)?.let { ref -> results[hit.id] = RulesSearchResult(ref, hit.number, hit.title, hit.snippet) }
            }
        }
        return results.values.toList()
    }

    private fun RulesSectionEntity.hasOwnText() = blocksJson != "[]"

    private fun RulesSectionEntity.toTocEntry(opensPage: Boolean, children: List<TocEntry> = emptyList()) =
        TocEntry(id, number, title, penalty?.let { Penalty.valueOf(it) }, opensPage, children)

    private fun RulesSectionEntity.toPageSection(rootLevel: Int) = PageSection(
        id = id,
        number = number,
        title = title,
        depth = level - rootLevel,
        blocks = blocks(json),
        infraction = if (penalty != null && infractionCategory != null) {
            Infraction(InfractionCategory.valueOf(infractionCategory), Penalty.valueOf(penalty))
        } else null,
    )
}

/** Turning what a judge types into queries. Pure, so it's unit-tested. */
object RulesSearchQuery {
    private val NUMBER = Regex("""^\d{1,3}(\.\d+[a-z]?)?\.?$""")
    private val WORD = Regex("""[\p{L}\p{N}]+""")

    /** "603", "603.2a", "2.5", "4.8." */
    fun looksLikeNumber(query: String): Boolean = NUMBER.matches(query.trim())

    fun normalizedNumber(query: String): String = query.trim().trimEnd('.')

    /** "603" finds 603 and 603.x but not 6030; "2.1" finds 2.1 but not 2.10. */
    fun numberMatches(query: String, number: String?): Boolean {
        if (number == null) return false
        return number == query ||
            number.startsWith("$query.") ||
            (query.contains('.') && number.startsWith(query) && number.drop(query.length).all { it.isLetter() })
    }

    /** Every word must match, as a prefix: "missed trig" -> "missed* trig*". Null if nothing to search. */
    fun toFts(query: String): String? {
        val words = WORD.findAll(query.lowercase()).map { it.value }.toList()
        return if (words.isEmpty()) null else words.joinToString(" ") { "$it*" }
    }
}
