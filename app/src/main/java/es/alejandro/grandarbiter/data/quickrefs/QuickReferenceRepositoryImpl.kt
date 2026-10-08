package es.alejandro.grandarbiter.data.quickrefs

import androidx.room.withTransaction
import es.alejandro.grandarbiter.data.local.AppDatabase
import es.alejandro.grandarbiter.data.local.dao.QuickReferenceDao
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.domain.model.quickrefs.DraftRule
import es.alejandro.grandarbiter.domain.model.quickrefs.QuickReference
import es.alejandro.grandarbiter.domain.model.quickrefs.QuickReferenceDetail
import es.alejandro.grandarbiter.domain.model.quickrefs.QuickReferenceDraft
import es.alejandro.grandarbiter.domain.model.quickrefs.QuickReferenceRule
import es.alejandro.grandarbiter.domain.model.rules.PageSection
import es.alejandro.grandarbiter.domain.repository.QuickReferenceRepository
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

class QuickReferenceRepositoryImpl(
    private val database: AppDatabase,
    private val dao: QuickReferenceDao,
    private val rules: RulesRepository,
) : QuickReferenceRepository {

    override fun observeAll(): Flow<List<QuickReference>> = dao.observeAll().map { rows ->
        rows.map {
            QuickReference(
                id = it.id,
                title = it.title,
                documentId = documentOf(it.documentId),
                isPreset = it.presetKey != null,
                firstRuleNumber = it.firstSectionId?.substringAfter('/'),
                ruleCount = it.itemCount,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDetail(id: Long): Flow<QuickReferenceDetail?> =
        combine(dao.observe(id), dao.observeItems(id), rules.observeDocuments()) { ref, items, _ -> ref to items }
            .mapLatest { (ref, items) ->
                ref ?: return@mapLatest null
                QuickReferenceDetail(
                    id = ref.id,
                    title = ref.title,
                    documentId = documentOf(ref.documentId),
                    rules = items.map { item ->
                        val target = rules.resolve(item.sectionId)
                        val page = target?.let { rules.page(it.pageId) }
                        QuickReferenceRule(
                            sectionId = item.sectionId,
                            note = item.note,
                            sections = page?.let { RuleSubtree.of(it.sections, item.sectionId) }.orEmpty(),
                            target = target,
                        )
                    },
                )
            }

    override suspend fun draft(id: Long): QuickReferenceDraft? {
        val ref = dao.get(id) ?: return null
        return QuickReferenceDraft(
            id = ref.id,
            title = ref.title,
            documentId = documentOf(ref.documentId),
            rules = dao.items(id).map { DraftRule(it.sectionId, it.note.orEmpty()) },
        )
    }

    override suspend fun save(draft: QuickReferenceDraft): Long =
        dao.save(draft.id, draft.title.trim(), draft.documentId.key, draft.rules.map { it.sectionId to it.note })

    override suspend fun delete(id: Long) = dao.delete(id)

    override fun observeCanRestorePresets(): Flow<Boolean> = dao.observePresetKeys().map { keys ->
        QuickReferencePresets.ALL.any { it.key !in keys }
    }

    override suspend fun restorePresets() {
        database.withTransaction {
            val present = dao.presetKeys()
            val missing = QuickReferencePresets.ALL.map { it.key }.filter { it !in present }.toSet()
            if (missing.isNotEmpty()) QuickReferencePresets.insert(database.openHelper.writableDatabase, missing)
        }
    }

    private fun documentOf(key: String) = DocumentId.entries.firstOrNull { it.key == key } ?: DocumentId.CR
}

/** A rule and everything under it on its page (613.1 -> 613.1a…613.1g). Pure, unit-tested. */
object RuleSubtree {
    fun of(pageSections: List<PageSection>, sectionId: String): List<PageSection> {
        val start = pageSections.indexOfFirst { it.id == sectionId }
        if (start < 0) return emptyList()
        val depth = pageSections[start].depth
        val end = (start + 1 until pageSections.size).firstOrNull { pageSections[it].depth <= depth } ?: pageSections.size
        return pageSections.subList(start, end).toList()
    }
}
