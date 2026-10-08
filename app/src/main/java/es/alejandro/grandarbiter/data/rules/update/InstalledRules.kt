package es.alejandro.grandarbiter.data.rules.update

import es.alejandro.grandarbiter.data.rules.local.ImportResult
import es.alejandro.grandarbiter.data.rules.local.RulesBundleImporter
import es.alejandro.grandarbiter.data.rules.local.RulesBundleSource
import es.alejandro.grandarbiter.data.rules.local.RulesDao
import es.alejandro.grandarbiter.domain.model.rules.RulesDocumentInfo
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import kotlinx.coroutines.flow.first

/** What the rules updater needs from rules.db. Narrow on purpose: faked in unit tests. */
interface InstalledRules {
    suspend fun bundleVersion(): Long?
    suspend fun documents(): List<RulesDocumentInfo>
    suspend fun import(bundleJson: String): ImportResult
}

class RoomInstalledRules(
    private val importer: RulesBundleImporter,
    private val dao: RulesDao,
    private val repository: RulesRepository,
) : InstalledRules {
    override suspend fun bundleVersion(): Long? = dao.installedBundleVersion()
    override suspend fun documents(): List<RulesDocumentInfo> = repository.observeDocuments().first()
    override suspend fun import(bundleJson: String): ImportResult = importer.import(RulesBundleSource { bundleJson })
}
