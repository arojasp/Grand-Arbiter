package es.alejandro.grandarbiter.data.rules.update

import android.util.Log
import es.alejandro.grandarbiter.data.rules.bundle.RulesBundle
import es.alejandro.grandarbiter.data.rules.local.ImportResult
import es.alejandro.grandarbiter.data.rules.local.RulesBundleImporter
import es.alejandro.grandarbiter.data.rules.local.RulesBundleSource
import es.alejandro.grandarbiter.data.rules.local.RulesDao
import es.alejandro.grandarbiter.domain.model.rules.RulesUpdateState
import es.alejandro.grandarbiter.domain.repository.RulesDataUpdater
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * Manual rules update: read the published manifest; if its bundleVersion is newer than the
 * installed one, download the bundle and import it with the same importer as the APK's bundle
 * (decode, schemaVersion check, validator, one transaction: a bad bundle never replaces good rules).
 *
 * The whole bundle travels (CR, MTR, IPG, JAR), not just the CR: the IPG links into CR rule
 * numbers, so the documents must always come from the same build.
 */
class PublishedRulesUpdater(
    private val client: PublishedRulesClient,
    private val importer: RulesBundleImporter,
    private val dao: RulesDao,
    private val repository: RulesRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : RulesDataUpdater {

    private val _state = MutableStateFlow<RulesUpdateState>(RulesUpdateState.Idle)
    override val state: StateFlow<RulesUpdateState> = _state.asStateFlow()
    private var job: Job? = null

    @Synchronized
    override fun checkForUpdates() {
        if (job?.isActive == true) return
        job = scope.launch { _state.value = run() }
    }

    private suspend fun run(): RulesUpdateState {
        _state.value = RulesUpdateState.Checking
        return try {
            val manifest = client.manifest() ?: return RulesUpdateState.UpToDate   // nothing published yet
            if (manifest.schemaVersion > RulesBundle.SUPPORTED_SCHEMA_VERSION) return RulesUpdateState.NeedsAppUpdate
            val installed = dao.installedBundleVersion()
            if (installed != null && manifest.bundleVersion <= installed) return RulesUpdateState.UpToDate

            val before = repository.observeDocuments().first().associate { it.id to it.effectiveDate }
            _state.value = RulesUpdateState.Downloading
            val text = client.bundle()
            _state.value = RulesUpdateState.Importing
            when (val result = importer.import(RulesBundleSource { text })) {
                is ImportResult.Imported -> {
                    Log.i(TAG, result.toString())
                    val after = repository.observeDocuments().first()
                    RulesUpdateState.Updated(after.filter { before[it.id] != it.effectiveDate })
                }
                is ImportResult.UpToDate -> RulesUpdateState.UpToDate
                is ImportResult.Rejected -> failed(result.reasons.take(3).joinToString("; "))
                ImportResult.NoBundle -> failed("empty bundle")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            failed(e.message ?: e.javaClass.simpleName, e)
        } catch (e: SerializationException) {
            failed("unexpected manifest format", e)
        }
    }

    private fun failed(reason: String, e: Throwable? = null): RulesUpdateState.Failed {
        Log.w(TAG, "Rules update failed: $reason", e)
        return RulesUpdateState.Failed(reason)
    }

    companion object {
        private const val TAG = "RulesUpdate"

        /** Branch main of the public repo: what's merged there is what users can download. */
        const val PUBLISHED_RULES_URL =
            "https://raw.githubusercontent.com/arojasp/Grand-Arbiter/main/app/src/main/assets/rules/"
    }
}
