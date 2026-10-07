package es.alejandro.grandarbiter.data.rules.local

import es.alejandro.grandarbiter.data.rules.bundle.Block
import es.alejandro.grandarbiter.data.rules.bundle.RulesBundle
import es.alejandro.grandarbiter.data.rules.bundle.RulesJson
import es.alejandro.grandarbiter.data.rules.bundle.plainText
import kotlinx.serialization.json.Json

/** Everything one bundle turns into, ready to insert. */
data class RulesBundleRows(
    val documents: List<RulesDocumentEntity>,
    val sections: List<RulesSectionEntity>,
    val glossary: List<RulesGlossaryEntity>,
)

/**
 * Pure mapping (no Android, no DB): unit-testable on the JVM.
 * Assumes a bundle that passed RulesBundleValidator (parents come before children).
 */
fun RulesBundle.toRows(json: Json = RulesJson): RulesBundleRows {
    val documents = ArrayList<RulesDocumentEntity>(this.documents.size)
    val sections = ArrayList<RulesSectionEntity>()
    val glossary = ArrayList<RulesGlossaryEntity>()

    for (doc in this.documents) {
        val docId = doc.id.key
        documents += RulesDocumentEntity(
            id = docId,
            title = doc.title,
            effectiveDate = doc.effectiveDate,
            sourceUrl = doc.sourceUrl,
            bundleVersion = bundleVersion,
        )

        val levels = HashMap<String, Int>(doc.sections.size)
        doc.sections.forEachIndexed { ordinal, s ->
            val level = s.parentId?.let { (levels[it] ?: -1) + 1 } ?: 0
            levels[s.id] = level
            sections += RulesSectionEntity(
                id = s.id,
                documentId = docId,
                parentId = s.parentId,
                number = s.number,
                title = s.title,
                level = level,
                ordinal = ordinal,
                blocksJson = json.encodeToString<List<Block>>(s.blocks),
                plainText = s.plainText(),
                penalty = s.infraction?.penalty?.name,
                infractionCategory = s.infraction?.category?.name,
            )
        }

        doc.glossary.mapTo(glossary) { RulesGlossaryEntity(docId, it.term, it.definition) }
    }
    return RulesBundleRows(documents, sections, glossary)
}

/** Inverse of the blocksJson column, for the reader UI. */
fun RulesSectionEntity.blocks(json: Json = RulesJson): List<Block> =
    json.decodeFromString<List<Block>>(blocksJson)
