package es.alejandro.grandarbiter.data.rules

import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.data.rules.bundle.RulesBundle
import es.alejandro.grandarbiter.data.rules.local.ImportResult
import es.alejandro.grandarbiter.data.rules.update.InstalledRules
import es.alejandro.grandarbiter.data.rules.update.PublishedRulesClient
import es.alejandro.grandarbiter.data.rules.update.PublishedRulesUpdater
import es.alejandro.grandarbiter.data.update.FakeHttp
import es.alejandro.grandarbiter.data.update.SilentLog
import es.alejandro.grandarbiter.domain.model.rules.RulesDocumentInfo
import es.alejandro.grandarbiter.domain.model.rules.RulesUpdateState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The decisions of Settings › "Check for updates" for the rules documents: when to download,
 * when not, and that every failure ends as Failed with the installed rules untouched.
 * HTTP is faked at the OkHttp level, so the real PublishedRulesClient runs too.
 */
class PublishedRulesUpdaterTest {

    private val base = "https://raw.example/main/rules/"
    private val manifestUrl = base + "manifest.json"
    private val bundleUrl = base + "bundle.json"
    private val http = FakeHttp()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun tearDown() = scope.cancel()

    private fun manifest(bundleVersion: Long, schema: Int = RulesBundle.SUPPORTED_SCHEMA_VERSION, minVersionCode: Int? = null) =
        """{"schemaVersion":$schema,"bundleVersion":$bundleVersion,"documents":{"cr":"2026-11-14"}""" +
            (minVersionCode?.let { ""","minVersionCode":$it""" } ?: "") + "}"

    private fun doc(id: DocumentId, date: String) = RulesDocumentInfo(id, id.key, LocalDate.parse(date), "https://x/$id")

    private val oldDocs = listOf(doc(DocumentId.CR, "2026-09-25"), doc(DocumentId.MTR, "2026-06-01"))

    private inner class FakeInstalled(
        var version: Long? = 100,
        var docs: List<RulesDocumentInfo> = oldDocs,
        val docsAfterImport: List<RulesDocumentInfo> = oldDocs,
        val result: () -> ImportResult = { ImportResult.Imported(200, 4, 3000) },
        val gate: CompletableDeferred<Unit>? = null,
    ) : InstalledRules {
        val imported = mutableListOf<String>()
        var stateDuringImport: RulesUpdateState? = null
        override suspend fun bundleVersion(): Long? { gate?.await(); return version }
        override suspend fun documents() = docs
        override suspend fun import(bundleJson: String): ImportResult {
            imported += bundleJson
            stateDuringImport = updater.state.value
            return result().also { if (it is ImportResult.Imported) docs = docsAfterImport }
        }
    }

    private lateinit var updater: PublishedRulesUpdater

    private fun updaterWith(installed: InstalledRules, appVersionCode: Int = 5) =
        PublishedRulesUpdater(PublishedRulesClient(http.client, base), installed, appVersionCode, scope, SilentLog).also { updater = it }

    /** Clicks the button and waits for that run to finish. */
    private fun PublishedRulesUpdater.runOnce(): RulesUpdateState = runBlocking {
        checkForUpdates()
        withTimeout(5_000) { scope.coroutineContext.job.children.toList().joinAll() }
        state.value
    }

    // ------------------------------------------------------------ nothing to do

    @Test fun nothingPublishedYet_isUpToDate_andDownloadsNothing() {
        val installed = FakeInstalled()
        assertEquals(RulesUpdateState.UpToDate, updaterWith(installed).runOnce())
        assertEquals(0, http.count(bundleUrl))
    }

    @Test fun sameVersionPublished_isUpToDate_withoutDownloadingTheBundle() {
        http.on(manifestUrl, body = manifest(100))
        assertEquals(RulesUpdateState.UpToDate, updaterWith(FakeInstalled(version = 100)).runOnce())
        assertEquals(0, http.count(bundleUrl))
    }

    @Test fun installedIsNewerThanPublished_isUpToDate() {
        // A develop build ships a newer bundle than main has: never "update" backwards.
        http.on(manifestUrl, body = manifest(90))
        val installed = FakeInstalled(version = 100)
        assertEquals(RulesUpdateState.UpToDate, updaterWith(installed).runOnce())
        assertTrue(installed.imported.isEmpty())
    }

    @Test fun newerSchema_asksForAnAppUpdate_andNeverDownloadsTheBundle() {
        http.on(manifestUrl, body = manifest(200, schema = RulesBundle.SUPPORTED_SCHEMA_VERSION + 1))
        val installed = FakeInstalled()
        assertEquals(RulesUpdateState.NeedsAppUpdate, updaterWith(installed).runOnce())
        assertEquals(0, http.count(bundleUrl))
        assertTrue(installed.imported.isEmpty())
    }

    // ------------------------------------------------------------ updating

    @Test fun newerVersion_downloadsAndImports_andReportsOnlyTheDocumentsThatChanged() {
        http.on(manifestUrl, body = manifest(200)).on(bundleUrl, body = "BUNDLE")
        val newCr = doc(DocumentId.CR, "2026-11-14")
        val installed = FakeInstalled(docsAfterImport = listOf(newCr, oldDocs[1]))

        val state = updaterWith(installed).runOnce()

        assertEquals(RulesUpdateState.Updated(listOf(newCr)), state)
        assertEquals(listOf("BUNDLE"), installed.imported)
        assertEquals(RulesUpdateState.Importing, installed.stateDuringImport)
    }

    @Test fun onlyFixes_isUpdatedWithNoChangedDocuments() {
        http.on(manifestUrl, body = manifest(200)).on(bundleUrl, body = "BUNDLE")
        assertEquals(RulesUpdateState.Updated(emptyList()), updaterWith(FakeInstalled()).runOnce())
    }

    @Test fun noRulesInstalled_downloads() {
        http.on(manifestUrl, body = manifest(1)).on(bundleUrl, body = "BUNDLE")
        val installed = FakeInstalled(version = null)
        assertTrue(updaterWith(installed).runOnce() is RulesUpdateState.Updated)
        assertEquals(1, installed.imported.size)
    }

    @Test fun importerFindsItAlreadyInstalled_isUpToDate() {
        http.on(manifestUrl, body = manifest(200)).on(bundleUrl, body = "BUNDLE")
        assertEquals(RulesUpdateState.UpToDate,
            updaterWith(FakeInstalled(result = { ImportResult.UpToDate(200) })).runOnce())
    }

    // ------------------------------------------------------------ failures: always Failed, never a crash

    @Test fun rejectedBundle_fails_withTheFirstReasons() {
        http.on(manifestUrl, body = manifest(200)).on(bundleUrl, body = "BUNDLE")
        val reasons = listOf("a", "b", "c", "d")
        val state = updaterWith(FakeInstalled(result = { ImportResult.Rejected(reasons) })).runOnce()
        assertEquals(RulesUpdateState.Failed("a; b; c"), state)
    }

    @Test fun emptyBundle_fails() {
        http.on(manifestUrl, body = manifest(200)).on(bundleUrl, body = "")
        assertTrue(updaterWith(FakeInstalled(result = { ImportResult.NoBundle })).runOnce() is RulesUpdateState.Failed)
    }

    @Test fun serverError_onManifest_fails() {
        http.on(manifestUrl, code = 500)
        assertTrue(updaterWith(FakeInstalled()).runOnce() is RulesUpdateState.Failed)
    }

    @Test fun bundleMissing_afterAManifest_failsWithoutImporting() {
        http.on(manifestUrl, body = manifest(200))   // bundle.json -> 404
        val installed = FakeInstalled()
        assertTrue(updaterWith(installed).runOnce() is RulesUpdateState.Failed)
        assertTrue(installed.imported.isEmpty())
    }

    @Test fun noConnection_fails() {
        http.onThrow(manifestUrl, java.net.UnknownHostException("raw.example"))
        assertTrue(updaterWith(FakeInstalled()).runOnce() is RulesUpdateState.Failed)
    }

    @Test fun malformedManifest_fails() {
        http.on(manifestUrl, body = "<html>captive portal</html>")
        assertEquals(RulesUpdateState.Failed("unexpected manifest format"), updaterWith(FakeInstalled()).runOnce())
    }

    @Test fun unexpectedDatabaseError_fails_insteadOfCrashing() {
        // E.g. SQLiteFullException is a RuntimeException: before the fix it escaped, crashed the
        // app and left the button stuck in "Importing".
        http.on(manifestUrl, body = manifest(200)).on(bundleUrl, body = "BUNDLE")
        val state = updaterWith(FakeInstalled(result = { throw IllegalStateException("disk full") })).runOnce()
        assertEquals(RulesUpdateState.Failed("disk full"), state)
    }

    // ------------------------------------------------------------ the button

    @Test fun tappingTwiceWhileRunning_runsOnce() = runBlocking {
        http.on(manifestUrl, body = manifest(200)).on(bundleUrl, body = "BUNDLE")
        val gate = CompletableDeferred<Unit>()
        val u = updaterWith(FakeInstalled(gate = gate))
        u.checkForUpdates()
        u.checkForUpdates()
        gate.complete(Unit)
        withTimeout(5_000) { scope.coroutineContext.job.children.toList().joinAll() }
        assertEquals(1, http.count(manifestUrl))
        assertEquals(1, http.count(bundleUrl))
    }

    @Test fun canCheckAgainAfterAFailure() {
        http.on(manifestUrl, code = 500)
        val u = updaterWith(FakeInstalled(version = 200))
        assertTrue(u.runOnce() is RulesUpdateState.Failed)
        http.on(manifestUrl, body = manifest(200))
        assertEquals(RulesUpdateState.UpToDate, u.runOnce())
    }

    // ------------------------------------------------------------ mandatory app update (drives Play's immediate update)

    @Test fun minVersionCodeAboveThisApp_requiresAnUpdate_butDocumentsStillUpdate() {
        http.on(manifestUrl, body = manifest(200, minVersionCode = 6)).on(bundleUrl, body = "BUNDLE")
        val u = updaterWith(FakeInstalled(), appVersionCode = 5)
        assertTrue(u.runOnce() is RulesUpdateState.Updated)
        assertTrue(u.appUpdateRequired.value)
    }

    @Test fun minVersionCodeAtOrBelowThisApp_requiresNothing() {
        http.on(manifestUrl, body = manifest(100, minVersionCode = 5))
        val u = updaterWith(FakeInstalled(version = 100), appVersionCode = 5)
        u.runOnce()
        assertFalse(u.appUpdateRequired.value)
    }

    @Test fun manifestWithoutMinVersionCode_requiresNothing() {
        // Manifests published before the field existed.
        http.on(manifestUrl, body = manifest(100))
        val u = updaterWith(FakeInstalled(version = 100), appVersionCode = 1)
        u.runOnce()
        assertFalse(u.appUpdateRequired.value)
    }

    @Test fun newerSchema_alsoRequiresAnAppUpdate() {
        http.on(manifestUrl, body = manifest(200, schema = RulesBundle.SUPPORTED_SCHEMA_VERSION + 1))
        val u = updaterWith(FakeInstalled())
        u.runOnce()
        assertTrue(u.appUpdateRequired.value)
    }

    @Test fun offline_doesNotRequireAnUpdate() {
        http.onThrow(manifestUrl, java.net.UnknownHostException("raw.example"))
        val u = updaterWith(FakeInstalled())
        u.runOnce()
        assertFalse(u.appUpdateRequired.value)
    }
}
