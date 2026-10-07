package es.alejandro.grandarbiter.data.rules.local

import androidx.room.withTransaction
import es.alejandro.grandarbiter.data.rules.bundle.RulesBundle
import es.alejandro.grandarbiter.data.rules.bundle.RulesBundleValidator
import es.alejandro.grandarbiter.data.rules.bundle.RulesJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

sealed interface ImportResult {
    data class Imported(val bundleVersion: Long, val documents: Int, val sections: Int) : ImportResult
    data class UpToDate(val installedVersion: Long) : ImportResult
    data object NoBundle : ImportResult
    data class Rejected(val reasons: List<String>) : ImportResult
}

/**
 * Loads a bundle into rules.db. A bundle is a FULL snapshot: an import replaces all rules content
 * atomically, so a failed or rejected import leaves the previous content untouched.
 *
 * Rejects bundles that don't decode, need a newer app (schemaVersion), or fail validation.
 * Validating in-app costs a few ms and stops a broken OTA bundle from ever reaching a judge.
 */
class RulesBundleImporter(
    private val database: RulesDatabase,
    private val dao: RulesDao,
    private val json: Json = RulesJson,
) {
    private val mutex = Mutex()   // startup import and a future OTA import must not interleave

    suspend fun import(source: RulesBundleSource, force: Boolean = false): ImportResult = mutex.withLock {
        val text = source.read() ?: return ImportResult.NoBundle

        val bundle = withContext(Dispatchers.Default) {
            runCatching { json.decodeFromString<RulesBundle>(text) }
        }.getOrElse { return ImportResult.Rejected(listOf("decode failed: ${it.message}")) }

        if (bundle.schemaVersion > RulesBundle.SUPPORTED_SCHEMA_VERSION) {
            return ImportResult.Rejected(
                listOf("bundle schemaVersion ${bundle.schemaVersion} needs a newer app (supports ${RulesBundle.SUPPORTED_SCHEMA_VERSION})")
            )
        }

        val installed = dao.installedBundleVersion()
        if (!force && installed != null && bundle.bundleVersion <= installed) {
            return ImportResult.UpToDate(installed)
        }

        val errors = withContext(Dispatchers.Default) { RulesBundleValidator.validate(bundle) }
        if (errors.isNotEmpty()) return ImportResult.Rejected(errors)

        val rows = withContext(Dispatchers.Default) { bundle.toRows(json) }
        database.withTransaction {
            dao.deleteAllContent()
            dao.insertDocuments(rows.documents)
            dao.insertSections(rows.sections)
            dao.insertGlossary(rows.glossary)
        }
        ImportResult.Imported(bundle.bundleVersion, rows.documents.size, rows.sections.size)
    }
}
