package es.alejandro.grandarbiter.domain.repository

import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.domain.model.rules.PageRef
import es.alejandro.grandarbiter.domain.model.rules.RulesDocumentInfo
import es.alejandro.grandarbiter.domain.model.rules.RulesPage
import es.alejandro.grandarbiter.domain.model.rules.RulesSearchResult
import es.alejandro.grandarbiter.domain.model.rules.TocEntry
import kotlinx.coroutines.flow.Flow

/** Read access to the rules in rules.db (filled from the bundled rules at startup). */
interface RulesRepository {
    /** Installed documents; emits again after a new bundle is imported. */
    fun observeDocuments(): Flow<List<RulesDocumentInfo>>

    suspend fun contents(documentId: DocumentId): List<TocEntry>

    /** The page with this id, or null if it doesn't exist (e.g. an old bookmark). */
    suspend fun page(pageId: String): RulesPage?

    /** The page that shows [sectionId] ("cr/603.2a" -> page "cr/603", anchor "cr/603.2a"). */
    suspend fun resolve(sectionId: String): PageRef?

    /** Free text or a number ("603.2a", "2.5"). [documentId] null searches all documents. */
    suspend fun search(query: String, documentId: DocumentId?): List<RulesSearchResult>
}
