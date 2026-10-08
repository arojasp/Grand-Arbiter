package es.alejandro.grandarbiter.domain.model.quickrefs

import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.domain.model.rules.PageRef
import es.alejandro.grandarbiter.domain.model.rules.PageSection

/** A row of the quick references list. */
data class QuickReference(
    val id: Long,
    val title: String,
    val documentId: DocumentId,
    val isPreset: Boolean,
    /** "613.1": the first rule, for the "CR · 613.1" subtitle. Null when the reference is empty. */
    val firstRuleNumber: String?,
    val ruleCount: Int,
)

/** One rule of a reference as shown: the official text (rule + subrules) and the judge's note. */
data class QuickReferenceRule(
    val sectionId: String,
    val note: String?,
    /** The rule and its subrules, from the installed documents. Empty if the rule isn't there anymore. */
    val sections: List<PageSection>,
    /** Where to open it in the reader. Null if the rule isn't in the installed documents. */
    val target: PageRef?,
) {
    val number: String get() = sectionId.substringAfter('/')
    val missing: Boolean get() = sections.isEmpty()
}

data class QuickReferenceDetail(
    val id: Long,
    val title: String,
    val documentId: DocumentId,
    val rules: List<QuickReferenceRule>,
)

/** What the editor works on: rule ids in order, each with its note. */
data class QuickReferenceDraft(
    val id: Long?,
    val title: String,
    val documentId: DocumentId,
    val rules: List<DraftRule>,
)

data class DraftRule(val sectionId: String, val note: String = "") {
    val number: String get() = sectionId.substringAfter('/')
}
