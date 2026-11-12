package es.alejandro.grandarbiter.data.rules.bundle

import java.time.LocalDate

/**
 * Structural checks for a bundle. Pure Kotlin: run it from the build pipeline AND from a JVM
 * unit test over app/src/main/assets/rules/bundle.json, so a broken bundle fails `./gradlew test`
 * before it ever ships.
 *
 * Catches broken structure. It CANNOT tell whether a penalty or a remedy is correct: that is
 * the manual check against the PDF, and it stays mandatory.
 */
object RulesBundleValidator {

    private val ID = Regex("""^(cr|mtr|ipg|jar)/[a-z0-9][a-z0-9.\-]*$""")

    fun validate(bundle: RulesBundle): List<String> = buildList {
        if (bundle.schemaVersion != RulesBundle.SUPPORTED_SCHEMA_VERSION) {
            add("schemaVersion ${bundle.schemaVersion} != ${RulesBundle.SUPPORTED_SCHEMA_VERSION}")
        }

        val docIds = bundle.documents.map { it.id }
        docIds.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { add("duplicate document $it") }

        val allIds = bundle.documents.flatMap { d -> d.sections.map { it.id } }.toSet()

        for (doc in bundle.documents) {
            val where = doc.id.key
            runCatching { LocalDate.parse(doc.effectiveDate) }
                .onFailure { add("$where: bad effectiveDate '${doc.effectiveDate}'") }
            // Settings opens it in the browser: only an https page, never another scheme.
            if (!doc.sourceUrl.startsWith("https://")) add("$where: sourceUrl must be https://")
            if (doc.sections.isEmpty()) add("$where: no sections")

            val seen = HashSet<String>()
            val parents = doc.sections.mapNotNull { it.parentId }.toSet()
            for (s in doc.sections) {
                if (!ID.matches(s.id)) add("${s.id}: id doesn't match ${ID.pattern}")
                if (!s.id.startsWith("${doc.id.key}/")) add("${s.id}: id prefix != document '${doc.id.key}'")
                if (!seen.add(s.id)) add("${s.id}: duplicate id")

                s.parentId?.let { p ->
                    if (p !in seen || p == s.id) add("${s.id}: parent '$p' missing or not before child")
                }
                if (s.number == null && s.title == null) add("${s.id}: needs a number or a title")

                // A titled heading with nothing under it is real (CR "600. General"); an untitled empty one is a parse bug.
                if (s.blocks.isEmpty() && s.id !in parents && s.title == null) add("${s.id}: empty leaf section without a title")

                s.infraction?.let {
                    if (doc.id != DocumentId.IPG) add("${s.id}: infraction outside the IPG")
                }
                if (doc.id == DocumentId.JAR && s.number != null) add("${s.id}: JAR sections have no number")

                for (block in s.blocks) {
                    if (block is Block.Table) {
                        if (block.header.isEmpty() || block.rows.isEmpty()) add("${s.id}: empty table")
                        if (block.header.any { it.isBlank() }) add("${s.id}: table header with a blank cell")
                        block.rows.forEachIndexed { i, row ->
                            if (row.size != block.header.size) add("${s.id}: table row $i has ${row.size} cells, header has ${block.header.size}")
                        }
                    }
                    for (text in block.texts()) {
                        // Empty table cells are legitimate (IPG Appendix A group rows); empty text anywhere else is a bug.
                        if (text.isBlank() && block !is Block.Table) add("${s.id}: blank text in ${block::class.simpleName}")
                        if (text.hasStrayAsterisk()) add("${s.id}: unescaped '*' (write \\* for a literal asterisk)")
                        for (target in text.linkTargets()) {
                            when {
                                target.startsWith("ref:") ->
                                    if (target.removePrefix("ref:") !in allIds) add("${s.id}: broken ref '$target'")
                                target.startsWith("https://") -> Unit
                                else -> add("${s.id}: link target '$target' must be ref: or https://")
                            }
                        }
                    }
                }
            }

            doc.glossary.groupBy { it.term }.filter { it.value.size > 1 }.keys
                .forEach { add("$where: duplicate glossary term '$it'") }   // (documentId, term) is the DB key
            for (g in doc.glossary) {
                for (target in g.definition.linkTargets()) {
                    if (target.startsWith("ref:") && target.removePrefix("ref:") !in allIds) {
                        add("$where glossary '${g.term}': broken ref '$target'")
                    }
                }
            }

            if (doc.id == DocumentId.IPG && doc.sections.none { it.infraction != null }) {
                add("ipg: no infraction metadata at all")
            }
        }
    }
}

/*
 * Unit test (app/src/test/...):
 *
 * class BundledRulesTest {
 *     private val json = Json { ignoreUnknownKeys = false }   // strict on purpose
 *
 *     @Test fun bundledBundleIsValid() {
 *         val text = File("src/main/assets/rules/bundle.json").readText()
 *         val bundle = json.decodeFromString<RulesBundle>(text)
 *         assertEquals(emptyList<String>(), RulesBundleValidator.validate(bundle))
 *     }
 * }
 *
 * In the app, decode with ignoreUnknownKeys = true so an older app can read a newer bundle
 * that only ADDED optional fields (that's what "non-breaking" means for schemaVersion).
 * A NEW Block type or enum value IS breaking (older apps fail to decode it): bump schemaVersion.
 */
