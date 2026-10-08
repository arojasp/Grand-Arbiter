package es.alejandro.grandarbiter.data.rules.update

import es.alejandro.grandarbiter.data.rules.bundle.RulesBundle
import es.alejandro.grandarbiter.data.rules.local.ImportResult
import es.alejandro.grandarbiter.data.update.AndroidUpdateLog
import es.alejandro.grandarbiter.data.update.UpdateLog
import es.alejandro.grandarbiter.domain.model.rules.RulesUpdateState
import es.alejandro.grandarbiter.domain.repository.RulesDataUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val installed: InstalledRules,
    /** BuildConfig.VERSION_CODE, compared with the manifest's minVersionCode. */
    private val appVersionCode: Int,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val log: UpdateLog = AndroidUpdateLog(TAG),
) : RulesDataUpdater {

    private val _state = MutableStateFlow<RulesUpdateState>(RulesUpdateState.Idle)
    override val state: StateFlow<RulesUpdateState> = _state.asStateFlow()
    private val _appUpdateRequired = MutableStateFlow(false)
    override val appUpdateRequired: StateFlow<Boolean> = _appUpdateRequired.asStateFlow()
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
            val schemaTooNew = manifest.schemaVersion > RulesBundle.SUPPORTED_SCHEMA_VERSION
            if (schemaTooNew || manifest.minVersionCode > appVersionCode) _appUpdateRequired.value = true
            // A minVersionCode alone doesn't stop the documents: they still update below.
            if (schemaTooNew) return RulesUpdateState.NeedsAppUpdate
            val installedVersion = installed.bundleVersion()
            if (installedVersion != null && manifest.bundleVersion <= installedVersion) return RulesUpdateState.UpToDate

            val before = installed.documents().associate { it.id to it.effectiveDate }
            _state.value = RulesUpdateState.Downloading
            val text = client.bundle()
            _state.value = RulesUpdateState.Importing
            when (val result = installed.import(text)) {
                is ImportResult.Imported -> {
                    log.info(result.toString())
                    val after = installed.documents()
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
        } catch (e: Exception) {
            // Anything else (e.g. SQLiteFullException, a RuntimeException) must end as Failed:
            // uncaught, it would crash the app and leave the state stuck in Importing.
            failed(e.message ?: e.javaClass.simpleName, e)
        }
    }

    private fun failed(reason: String, e: Throwable? = null): RulesUpdateState.Failed {
        log.warn("Rules update failed: $reason", e)
        return RulesUpdateState.Failed(reason)
    }

    companion object {
        private const val TAG = "RulesUpdate"

        /** Branch main of the public repo: what's merged there is what users can download. */
        const val PUBLISHED_RULES_URL =
            "https://raw.githubusercontent.com/arojasp/Grand-Arbiter/main/app/src/main/assets/rules/"
    }
}
