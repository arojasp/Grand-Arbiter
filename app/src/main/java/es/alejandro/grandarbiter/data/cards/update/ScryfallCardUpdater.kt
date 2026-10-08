package es.alejandro.grandarbiter.data.cards.update

import es.alejandro.grandarbiter.data.cards.local.CardDataVersion
import es.alejandro.grandarbiter.data.cards.local.CardImportResult
import es.alejandro.grandarbiter.data.cards.local.FileCardDataSource
import es.alejandro.grandarbiter.data.update.AndroidUpdateLog
import es.alejandro.grandarbiter.data.update.UpdateLog
import es.alejandro.grandarbiter.domain.model.cards.CardUpdateState
import es.alejandro.grandarbiter.domain.model.cards.CardUpdateState.Downloading.Part
import es.alejandro.grandarbiter.domain.repository.CardDataUpdater
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
import java.io.File
import java.io.IOException

/**
 * Manual update: ask Scryfall's catalog, and if oracle-cards or rulings are newer than what's
 * installed, download both (~30 MB) and import them with the same CardImporter as the seed.
 *
 * Runs in its own app-lifetime scope, so leaving Settings doesn't stop it. If the app process
 * dies mid-update, nothing is lost: the import is one transaction and the old data stays.
 * (The weekly WorkManager update will reuse this class.)
 */
class ScryfallCardUpdater(
    private val client: ScryfallBulkClient,
    private val installed: InstalledCards,
    private val workDir: File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val log: UpdateLog = AndroidUpdateLog(TAG),
) : CardDataUpdater {

    private val _state = MutableStateFlow<CardUpdateState>(CardUpdateState.Idle)
    override val state: StateFlow<CardUpdateState> = _state.asStateFlow()
    private var job: Job? = null

    @Synchronized
    override fun checkForUpdates() {
        if (job?.isActive == true) return
        job = scope.launch { _state.value = run() }
    }

    private suspend fun run(): CardUpdateState {
        _state.value = CardUpdateState.Checking
        val dir = File(workDir, "cards-update")
        return try {
            val catalog = client.catalog()
            val oracle = catalog.firstOrNull { it.type == "oracle_cards" } ?: return failed("no oracle_cards in Scryfall's catalog")
            val rulings = catalog.firstOrNull { it.type == "rulings" } ?: return failed("no rulings in Scryfall's catalog")
            val remote = CardDataVersion(oracle.updatedAt, rulings.updatedAt)
            val installedVersion = installed.version()
            if (installedVersion != null && !remote.isNewerThan(installedVersion)) {
                return CardUpdateState.UpToDate(installedVersion.oracleUpdatedAt)
            }

            dir.deleteRecursively()
            dir.mkdirs()
            val oracleFile = File(dir, "oracle-cards")
            val rulingsFile = File(dir, "rulings")
            client.download(client.downloadUrl(oracle), oracleFile) { done, total ->
                _state.value = CardUpdateState.Downloading(Part.CARDS, done, total ?: oracle.compressedSize)
            }
            client.download(client.downloadUrl(rulings), rulingsFile) { done, total ->
                _state.value = CardUpdateState.Downloading(Part.RULINGS, done, total ?: rulings.compressedSize)
            }

            _state.value = CardUpdateState.Importing
            when (val result = installed.import(FileCardDataSource(remote, oracleFile, rulingsFile))) {
                is CardImportResult.Imported -> {
                    log.info(result.toString())
                    CardUpdateState.Updated(result.cards, remote.oracleUpdatedAt)
                }
                is CardImportResult.UpToDate -> CardUpdateState.UpToDate(result.installed.oracleUpdatedAt)
                is CardImportResult.Rejected -> failed(result.reason)
                CardImportResult.NoData -> failed("downloaded files were empty")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            failed(e.message ?: e.javaClass.simpleName, e)
        } catch (e: SerializationException) {
            failed("unexpected answer from Scryfall", e)
        } catch (e: Exception) {
            // E.g. SQLiteFullException after a 30 MB download: must end as Failed, not crash the app.
            failed(e.message ?: e.javaClass.simpleName, e)
        } finally {
            dir.deleteRecursively()   // ~30 MB: never leave it in the cache
        }
    }

    private fun failed(reason: String, e: Throwable? = null): CardUpdateState.Failed {
        log.warn("Card update failed: $reason", e)
        return CardUpdateState.Failed(reason)
    }

    private companion object {
        const val TAG = "CardUpdate"
    }
}
